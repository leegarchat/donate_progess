package org.pixel.customparts.hooks;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for discovering and managing installed custom animation theme APKs.
 *
 * <p>Theme APKs are installed packages matching the prefix
 * {@code org.pixel.customparts.anim.*}. Each theme APK contains standard
 * {@code res/anim/} XML resources with well-known names:
 * <ul>
 *   <li>{@code custom_open_enter} — entering activity on open</li>
 *   <li>{@code custom_open_exit} — exiting activity on open</li>
 *   <li>{@code custom_close_enter} — entering activity on close</li>
 *   <li>{@code custom_close_exit} — exiting activity on close</li>
 * </ul>
 *
 * <p>Animations are loaded by WMS natively via {@code createPackageContext} —
 * no compact string parsing, no fake resource IDs, no hacks.</p>
 */
public class CustomAnimationLoader {

    /** Package name prefix for all custom animation theme APKs */
    public static final String THEME_PACKAGE_PREFIX = "org.pixel.customparts.anim.";

    /** Well-known anim resource names expected inside every theme APK */
    public static final String ANIM_OPEN_ENTER  = "custom_open_enter";
    public static final String ANIM_OPEN_EXIT   = "custom_open_exit";
    public static final String ANIM_CLOSE_ENTER = "custom_close_enter";
    public static final String ANIM_CLOSE_EXIT  = "custom_close_exit";

    /**
     * Scan installed packages for custom animation themes.
     *
     * @param context any context (uses PackageManager)
     * @return list of installed theme package names, may be empty
     */
    public static List<String> getInstalledThemes(Context context) {
        List<String> themes = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            for (PackageInfo pi : packages) {
                if (pi.packageName != null && pi.packageName.startsWith(THEME_PACKAGE_PREFIX)) {
                    themes.add(pi.packageName);
                }
            }
        } catch (Throwable ignored) {
        }
        return themes;
    }

    /**
     * Extract the human-readable style name from a theme package name.
     * E.g. {@code "org.pixel.customparts.anim.bouncy_slide"} → {@code "bouncy_slide"}.
     *
     * @param packageName full package name
     * @return style name, or the full package if prefix doesn't match
     */
    public static String getStyleName(String packageName) {
        if (packageName != null && packageName.startsWith(THEME_PACKAGE_PREFIX)) {
            return packageName.substring(THEME_PACKAGE_PREFIX.length());
        }
        return packageName;
    }

    /**
     * Build the full theme package name from a style name.
     * E.g. {@code "bouncy_slide"} → {@code "org.pixel.customparts.anim.bouncy_slide"}.
     */
    public static String buildPackageName(String styleName) {
        return THEME_PACKAGE_PREFIX + styleName;
    }

    /**
     * Validate that a theme APK contains all required animation resources.
     *
     * @param context  any context
     * @param themePkg package name of the theme APK
     * @return null if valid, or error description if invalid
     */
    public static String validateTheme(Context context, String themePkg) {
        try {
            Context themeCtx = context.createPackageContext(themePkg,
                    Context.CONTEXT_IGNORE_SECURITY);
            android.content.res.Resources res = themeCtx.getResources();

            StringBuilder missing = new StringBuilder();
            for (String name : new String[]{ANIM_OPEN_ENTER, ANIM_OPEN_EXIT,
                    ANIM_CLOSE_ENTER, ANIM_CLOSE_EXIT}) {
                int id = res.getIdentifier(name, "anim", themePkg);
                if (id == 0) {
                    if (missing.length() > 0) missing.append(", ");
                    missing.append(name);
                }
            }

            if (missing.length() > 0) {
                return "Missing animations: " + missing;
            }
            return null; // valid
        } catch (PackageManager.NameNotFoundException e) {
            return "Package not installed: " + themePkg;
        } catch (Throwable t) {
            return "Error: " + t.getMessage();
        }
    }
}
