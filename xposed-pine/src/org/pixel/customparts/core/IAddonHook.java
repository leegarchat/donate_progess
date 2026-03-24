package org.pixel.customparts.core;

import android.content.Context;
import java.util.Set;

/**
 * Interface for external addon hooks loaded from JAR files.
 *
 * To create an addon:
 * 1. Implement this interface
 * 2. Compile into a DEX JAR
 * 3. Place the JAR into /data/pixelparts/addons/
 * 4. Include META-INF/addon.json inside the JAR:
 *    {
 *      "id": "my_addon",
 *      "entryClass": "com.example.MyAddonHook",
 *      "name": "My Addon",
 *      "author": "Author Name",
 *      "description": "What this addon does",
 *      "version": "1.0",
 *      "targetPackages": ["com.target.app"],
 *      "enabled": true
 *    }
 *
 * The addon can use Pine's Xposed compatibility layer (XposedHelpers, XC_MethodHook, etc.)
 */
public interface IAddonHook {

    /**
     * Unique identifier for this addon.
     */
    String getId();

    /**
     * Human-readable name.
     */
    default String getName() {
        return getId();
    }

    /**
     * Author of the addon.
     */
    default String getAuthor() {
        return "Unknown";
    }

    /**
     * Description of what the addon does.
     */
    default String getDescription() {
        return "";
    }

    /**
     * Version string.
     */
    default String getVersion() {
        return "1.0";
    }

    /**
     * Set of package names this addon targets.
     * Return null or empty set to apply to ALL injected packages.
     * NOTE: These are default targets. User can override via AddonManager UI.
     */
    Set<String> getTargetPackages();

    /**
     * Called when a target package process is being initialized.
     *
     * @param context     Application context of the target process
     * @param classLoader ClassLoader of the target app (use for class lookups)
     * @param packageName Package name of the running app
     */
    void handleLoadPackage(Context context, ClassLoader classLoader, String packageName);

    /**
     * Optional: priority for ordering addon execution.
     * Higher values execute first. Default: 0.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Optional: check if this addon is enabled.
     * Can read from Settings.Global or any other source.
     * Default: true.
     */
    default boolean isEnabled(Context context) {
        return true;
    }
}
