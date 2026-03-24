package org.pixel.customparts.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import org.pixel.customparts.AppConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object AnimThemeCompiler {

    private const val TAG = "AnimThemeCompiler"
    private const val THEME_PACKAGE_PREFIX = "org.pixel.customparts.anim."

    data class CompileResult(
        val success: Boolean,
        val apkPath: String? = null,
        val packageName: String? = null,
        val log: List<String> = emptyList(),
        val error: String? = null
    )

    fun interface LogCallback {
        fun onLog(line: String)
    }

    fun compile(
        context: Context,
        styleName: String,
        openEnterUri: Uri,
        openExitUri: Uri,
        closeEnterUri: Uri?,
        closeExitUri: Uri?,
        logCallback: LogCallback? = null
    ): CompileResult {
        return compileInternal(context, styleName, logCallback) { resDir ->
            copyUriToFile(context, openEnterUri, File(resDir, "custom_open_enter.xml"))
            copyUriToFile(context, openExitUri, File(resDir, "custom_open_exit.xml"))
            if (closeEnterUri != null) {
                copyUriToFile(context, closeEnterUri, File(resDir, "custom_close_enter.xml"))
            } else {
                copyUriToFile(context, openExitUri, File(resDir, "custom_close_enter.xml"))
            }
            if (closeExitUri != null) {
                copyUriToFile(context, closeExitUri, File(resDir, "custom_close_exit.xml"))
            } else {
                copyUriToFile(context, openEnterUri, File(resDir, "custom_close_exit.xml"))
            }
        }
    }

    fun compileFromXml(
        context: Context,
        styleName: String,
        openEnterXml: String,
        openExitXml: String,
        closeEnterXml: String?,
        closeExitXml: String?,
        logCallback: LogCallback? = null
    ): CompileResult {
        return compileInternal(context, styleName, logCallback) { resDir ->
            File(resDir, "custom_open_enter.xml").writeText(openEnterXml)
            File(resDir, "custom_open_exit.xml").writeText(openExitXml)
            File(resDir, "custom_close_enter.xml").writeText(
                closeEnterXml ?: openExitXml
            )
            File(resDir, "custom_close_exit.xml").writeText(
                closeExitXml ?: openEnterXml
            )
        }
    }

    private fun compileInternal(
        context: Context,
        styleName: String,
        logCallback: LogCallback?,
        writeAnims: (resDir: File) -> Unit
    ): CompileResult {
        val log = mutableListOf<String>()
        fun emit(msg: String) {
            log.add(msg)
            logCallback?.onLog(msg)
            Log.d(TAG, msg)
        }

        try {
            val packageName = THEME_PACKAGE_PREFIX + sanitizeStyleName(styleName)
            emit("Package: $packageName")

            // 1. Prepare workspace
            val baseCache = context.cacheDir
            val workDir = File(baseCache, "anim_compile_$styleName")
            if (workDir.exists()) workDir.deleteRecursively()
            workDir.mkdirs()

            val resDir = File(workDir, "res/anim")
            resDir.mkdirs()
            emit("Workspace: ${workDir.absolutePath}")

            // 2. Write animation XMLs
            writeAnims(resDir)
            emit("Animation files written")

            // 3. Generate AndroidManifest.xml
            val manifest = File(workDir, "AndroidManifest.xml")
            manifest.writeText(generateManifest(packageName))
            emit("Generated AndroidManifest.xml")

            // 4. Находим легальный бинарник aapt2
            val aapt2 = getAapt2(context)
            emit("aapt2 binary: ${aapt2.absolutePath}")

            // 5. Find framework-res.apk
            val frameworkRes = File("/system/framework/framework-res.apk")
            if (!frameworkRes.exists()) {
                return CompileResult(false, log = log, error = "framework-res.apk not found")
            }

            // 6. aapt2 compile
            val compiledZip = File(workDir, "compiled.zip")
            val compileCmd = arrayOf(
                aapt2.absolutePath, "compile",
                "--dir", File(workDir, "res").absolutePath,
                "-o", compiledZip.absolutePath
            )
            emit("Running compile...")
            val compileResult = runCommand(compileCmd, workDir)
            if (compileResult.first.isNotEmpty()) emit("compile output: ${compileResult.first}")
            if (!compiledZip.exists()) {
                return CompileResult(false, log = log, error = "aapt2 compile failed: ${compileResult.first}")
            }

            // 7. aapt2 link
            val unsignedApk = File(workDir, "unsigned.apk")
            val linkCmd = arrayOf(
                aapt2.absolutePath, "link",
                "-I", frameworkRes.absolutePath,
                "--manifest", manifest.absolutePath,
                "--min-sdk-version", "33",
                "--target-sdk-version", "35",
                "-o", unsignedApk.absolutePath,
                compiledZip.absolutePath
            )
            emit("Running link...")
            val linkResult = runCommand(linkCmd, workDir)
            if (linkResult.first.isNotEmpty()) emit("link output: ${linkResult.first}")
            if (!unsignedApk.exists()) {
                return CompileResult(false, log = log, error = "aapt2 link failed: ${linkResult.first}")
            }

            // 8. Sign APK
            val signedApk = File(workDir, "$styleName.apk")
            emit("Signing APK...")
            val signResult = AnimThemeSigner.sign(context, unsignedApk, signedApk)
            if (!signResult) {
                return CompileResult(false, log = log, error = "APK signing failed")
            }
            emit("Signed APK: ${signedApk.absolutePath} (${signedApk.length()} bytes)")

            return CompileResult(true, apkPath = signedApk.absolutePath, packageName = packageName, log = log)

        } catch (e: Exception) {
            emit("ERROR: ${e.message}")
            Log.e(TAG, "Compile failed", e)
            return CompileResult(false, log = log, error = e.message)
        }
    }

    // Чистый, нативный API с легальным обходом Play Protect через рефлексию
    fun install(context: Context, apkPath: String, packageName: String, logCallback: LogCallback? = null): Boolean {
        val log = fun(msg: String) {
            logCallback?.onLog(msg)
            Log.d(TAG, msg)
        }
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            log("APK file not found: $apkPath")
            return false
        }

        try {
            log("Installing via native PackageInstaller API...")
            
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(apkFile.length())
                setAppPackageName(packageName)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            // === МАГИЯ ОБХОДА PLAY PROTECT (БЕЗ БУТЛУПОВ) ===
            // Так как приложение имеет системные права (uid 1000), 
            // мы можем легально внедрить скрытый флаг прямо в параметры сессии.
            try {
                val installFlagsField = params.javaClass.getDeclaredField("installFlags")
                installFlagsField.isAccessible = true
                var flags = installFlagsField.getInt(params)
                
                // 0x00080000 = PackageManager.INSTALL_DISABLE_VERIFICATION
                // 0x00000004 = PackageManager.INSTALL_ALLOW_TEST (опционально, для надежности)
                flags = flags or 0x00080000 or 0x00000004
                
                installFlagsField.setInt(params, flags)
                log("Successfully injected INSTALL_DISABLE_VERIFICATION flag!")
            } catch (e: Exception) {
                log("Notice: Failed to inject verification bypass flag: ${e.message}")
            }
            // ===================================================

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            
            apkFile.inputStream().use { input ->
                session.openWrite("package", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            
            val countDownLatch = CountDownLatch(1)
            var installSuccess = false
            var installMsg = ""
            
            val action = "org.pixel.customparts.INSTALL_COMMIT_${System.currentTimeMillis()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No message"
                    log("Broadcast received! Status: $status, Msg: $msg")
                    
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        log("Warning: Bypass failed. Launching fallback prompt...")
                        val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirmationIntent != null) {
                            confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            c.startActivity(confirmationIntent)
                        }
                        return
                    }
                    
                    installMsg = msg
                    installSuccess = (status == PackageInstaller.STATUS_SUCCESS)
                    countDownLatch.countDown()
                }
            }
            
            val handlerThread = android.os.HandlerThread("InstallReceiverThread").apply { start() }
            val handler = android.os.Handler(handlerThread.looper)
            
            context.registerReceiver(
                receiver, 
                IntentFilter(action),
                null,
                handler,
                Context.RECEIVER_EXPORTED
            )
            
            val intent = Intent(action).setPackage(context.packageName)
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            countDownLatch.await(60, TimeUnit.SECONDS)
            
            context.unregisterReceiver(receiver)
            handlerThread.quitSafely()
            session.close()
            
            if (installSuccess) {
                log("Install successful!")
                return true
            } else {
                log("API install failed. Status: $installMsg")
            }
            
            return fallbackSuInstall(apkPath, log)
        } catch (e: Exception) {
            log("Install exception: ${e.message}")
            return fallbackSuInstall(apkPath, log)
        }
    }

    private fun fallbackSuInstall(apkPath: String, log: (String) -> Unit): Boolean {
        log("Trying fallback to su...")
        try {
            val suResult = runCommand(arrayOf("su", "-c", "cmd package install -r -d -t $apkPath"))
            if (suResult.first.contains("Success")) {
                log("SU install successful.")
                return true
            }
            log("SU install failed. Output: ${suResult.first}")
        } catch (e: Exception) {
            log("SU fallback not available: ${e.message}")
        }
        return false
    }

    // Возвращаем надежное удаление через нативный PackageInstaller API
    fun uninstall(context: Context, packageName: String, logCallback: LogCallback? = null): Boolean {
        val log = fun(msg: String) {
            logCallback?.onLog(msg)
            Log.d(TAG, msg)
        }
        try {
            log("Uninstalling via native PackageInstaller API: $packageName")
            
            val packageInstaller = context.packageManager.packageInstaller
            val countDownLatch = CountDownLatch(1)
            var uninstallSuccess = false
            var uninstallMsg = ""
            
            val action = "org.pixel.customparts.UNINSTALL_COMMIT_${System.currentTimeMillis()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "No message"
                    log("Uninstall broadcast received! Status: $status, Msg: $msg")
                    
                    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                        val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirmationIntent != null) {
                            confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            c.startActivity(confirmationIntent)
                        }
                        return
                    }
                    
                    uninstallMsg = msg
                    uninstallSuccess = (status == PackageInstaller.STATUS_SUCCESS)
                    countDownLatch.countDown()
                }
            }
            
            val handlerThread = android.os.HandlerThread("UninstallReceiverThread").apply { start() }
            val handler = android.os.Handler(handlerThread.looper)
            
            context.registerReceiver(
                receiver, 
                IntentFilter(action),
                null,
                handler,
                Context.RECEIVER_EXPORTED
            )
            
            val intent = Intent(action).setPackage(context.packageName)
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)
            countDownLatch.await(60, TimeUnit.SECONDS)
            
            context.unregisterReceiver(receiver)
            handlerThread.quitSafely()
            
            if (uninstallSuccess) {
                log("Uninstall successful (via API).")
                return true
            } else {
                log("API uninstall failed. Status: $uninstallMsg")
            }
            
            // Если нативное удаление не сработало, пытаемся через root
            try {
                val suResult = runCommand(arrayOf("su", "-c", "cmd package uninstall $packageName"))
                if (suResult.first.contains("Success")) {
                    log("SU uninstall successful.")
                    return true
                }
                log("SU uninstall failed. Output: ${suResult.first}")
            } catch (e: Exception) {
                log("SU fallback not available: ${e.message}")
            }
            
            return false
        } catch (e: Exception) {
            log("Uninstall exception: ${e.message}")
            return false
        }
    }

    private fun generateManifest(packageName: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="$packageName"
            android:versionCode="1"
            android:versionName="1.0">
            <application android:hasCode="false" />
        </manifest>
    """.trimIndent()

    private fun sanitizeStyleName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trimStart('_').trimEnd('_')
            .ifEmpty { "custom" }
    }

    private fun getAapt2(context: Context): File {
        val candidates = mutableListOf<File>()

        if (!AppConfig.IS_XPOSED) {
            candidates.add(File("/system/bin/aapt2_pixelparts"))
            candidates.add(File("/system_ext/bin/aapt2_pixelparts"))
            candidates.add(File("/system/bin/aapt2"))
            candidates.add(File("/system_ext/bin/aapt2"))
            candidates.add(File("/system_ext/lib64/libaapt2.so"))
            candidates.add(File("/system/lib64/libaapt2.so"))
        }

        candidates.add(File(context.applicationInfo.nativeLibraryDir, "libaapt2.so"))

        for (candidate in candidates) {
            if (!candidate.exists()) continue
            
            if (candidate.canExecute()) return candidate
            
            try {
                val process = Runtime.getRuntime().exec(arrayOf(candidate.absolutePath, "version"))
                process.waitFor()
                return candidate
            } catch (_: Exception) {
                continue
            }
        }

        throw RuntimeException(
            "aapt2 не найден! Проверенные пути: ${candidates.joinToString { it.absolutePath }}"
        )
    }

    private fun copyUriToFile(context: Context, uri: Uri, dest: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        } ?: throw RuntimeException("Cannot open: $uri")
    }

    private fun runCommand(cmd: Array<String>, workDir: File? = null): Pair<String, String> {
        val process = ProcessBuilder(*cmd)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
            
        process.outputStream.close()
            
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return Pair(output.trim(), "")
    }
}