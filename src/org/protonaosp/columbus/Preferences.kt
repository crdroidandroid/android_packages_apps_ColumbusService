/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.Settings
import org.protonaosp.columbus.widget.RadioButtonPreference

const val PREFS_NAME = "columbus_preferences"

val LAUNCH_ACTION_SUMMARY: RadioButtonPreference.ContextualSummaryProvider =
    object : RadioButtonPreference.ContextualSummaryProvider {
        override fun getSummary(context: Context): CharSequence {
            val laucnApp =
                Settings.Secure.getStringForUser(
                    context.getContentResolver(),
                    context.getString(R.string.pref_key_launch_app),
                    ActivityManager.getCurrentUser(),
                )
            if (laucnApp == null || laucnApp.isEmpty()) {
                return context.getString(R.string.setting_action_launch_summary_no_selection)
            }

            val launchAppComponent = ComponentName.unflattenFromString(laucnApp)
            if (launchAppComponent == null) {
                return context.getString(R.string.setting_action_launch_summary_no_selection)
            }

            val pm = context.packageManager
            return try {
                pm.getApplicationLabel(pm.getActivityInfo(launchAppComponent, 0).applicationInfo)
            } catch (unused: PackageManager.NameNotFoundException) {
                context.getString(R.string.setting_action_launch_summary_not_installed)
            }
        }
    }

fun Context.getDePrefs(): SharedPreferences {
    return createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

fun SharedPreferences.setEnabled(context: Context, enabled: Boolean) {
    val enabledKey = context.getString(R.string.pref_key_enabled)
    edit().putBoolean(enabledKey, enabled).commit()

    // Compat for 3rd party apps
    Settings.Secure.putIntForUser(
        context.contentResolver,
        enabledKey,
        if (enabled) 1 else 0,
        UserHandle.USER_CURRENT,
    )
}

fun SharedPreferences.getEnabled(context: Context): Boolean {
    return getBoolean(
        context.getString(R.string.pref_key_enabled),
        context.resources.getBoolean(R.bool.default_enabled),
    )
}

fun SharedPreferences.setAction(context: Context, action: String) {
    val actionKey = context.getString(R.string.pref_key_action)
    edit().putString(actionKey, action).commit()

    // Compat for 3rd party apps
    Settings.Secure.putStringForUser(
        context.contentResolver,
        actionKey,
        action,
        ActivityManager.getCurrentUser(),
    )
}

fun SharedPreferences.getAction(context: Context): String {
    return getString(
        context.getString(R.string.pref_key_action),
        context.getString(R.string.default_action),
    ) ?: context.getString(R.string.default_action)
}

fun SharedPreferences.getAllowScreenOff(context: Context): Boolean {
    return getBoolean(
        context.getString(R.string.pref_key_allow_screen_off),
        context.resources.getBoolean(R.bool.default_allow_screen_off),
    )
}

fun SharedPreferences.getSensitivity(context: Context): Int {
    return getInt(
        context.getString(R.string.pref_key_sensitivity),
        context.resources.getInteger(R.integer.default_sensitivity),
    )
}

fun SharedPreferences.getActionName(context: Context): String {
    val actionNames = context.resources.getStringArray(R.array.action_names)
    val actionValues = context.resources.getStringArray(R.array.action_values)
    return actionNames[actionValues.indexOf(getAction(context))]
}

fun SharedPreferences.setLaunchActionApp(context: Context, app: String) {
    val key = context.getString(R.string.pref_key_launch_app)
    edit().putString(key, app).commit()

    // Compat for 3rd party apps
    Settings.Secure.putStringForUser(
        context.contentResolver,
        key,
        app,
        ActivityManager.getCurrentUser(),
    )
}

fun SharedPreferences.getLaunchActionApp(context: Context): String? {
    return getString(context.getString(R.string.pref_key_launch_app), null)
}

fun SharedPreferences.setLaunchActionAppShortcut(context: Context, shortcut: String) {
    val key = context.getString(R.string.pref_key_launch_app_shortcut)
    edit().putString(key, shortcut).commit()

    // Compat for 3rd party apps
    Settings.Secure.putStringForUser(
        context.contentResolver,
        key,
        shortcut,
        ActivityManager.getCurrentUser(),
    )
}

fun SharedPreferences.getLaunchActionAppShortcut(context: Context): String? {
    return getString(context.getString(R.string.pref_key_launch_app_shortcut), null)
}

fun SharedPreferences.getHapticIntensity(context: Context): Int {
    val hapticIntensityKey = context.getString(R.string.pref_key_haptic_intensity)
    val defaultHapticIntensity = context.resources.getInteger(R.integer.default_haptic_intensity)

    return try {
        getInt(hapticIntensityKey, defaultHapticIntensity)
    } catch (e: ClassCastException) {
        val stringValue = getString(hapticIntensityKey, null)
        val intValue = stringValue?.toIntOrNull() ?: defaultHapticIntensity

        with(edit()) {
            remove(hapticIntensityKey)
            putInt(hapticIntensityKey, intValue)
            apply()
        }

        intValue
    }
}
