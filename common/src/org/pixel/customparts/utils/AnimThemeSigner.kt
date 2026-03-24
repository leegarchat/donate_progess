package org.pixel.customparts.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.android.apksig.ApkSigner
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

object AnimThemeSigner {

    private const val TAG = "AnimThemeSigner"
    private const val KEY_ALIAS = "AnimThemeSignerKey"

    fun sign(context: Context, unsigned: File, signed: File): Boolean {
        return try {
            val (privateKey, cert) = getOrCreateKeyPair()

            val signerConfig = ApkSigner.SignerConfig.Builder(
                KEY_ALIAS,
                privateKey,
                listOf(cert)
            ).build()

            val signer = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsigned)
                .setOutputApk(signed)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()

            signer.sign()
            Log.d(TAG, "Successfully signed APK via apksig (v2/v3)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "APK signing failed", e)
            false
        }
    }

    private fun getOrCreateKeyPair(): Pair<PrivateKey, X509Certificate> {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        if (!ks.containsAlias(KEY_ALIAS)) {
            Log.d(TAG, "Generating new RSA key pair in AndroidKeyStore")
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()

            kpg.initialize(spec)
            kpg.generateKeyPair()
        }

        val privateKey = ks.getKey(KEY_ALIAS, null) as PrivateKey
        val cert = ks.getCertificate(KEY_ALIAS) as X509Certificate
        return Pair(privateKey, cert)
    }
}