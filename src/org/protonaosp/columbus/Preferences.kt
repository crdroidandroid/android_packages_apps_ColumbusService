/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.Settings
import lineageos.providers.LineageSettings
import org.lineageos.internal.util.DeviceKeysConstants.Action
import org.lineageos.internal.util.DeviceKeysConstants.QUICK_TAP_DEFAULT_ACTION
import org.lineageos.internal.util.DeviceKeysConstants.getLaunchAppSetting
import org.lineageos.internal.util.DeviceKeysConstants.getLaunchShortcutSetting
import org.protonaosp.columbus.widget.RadioButtonPreference

const val PREFS_NAME = "columbus_preferences"

val LAUNCH_ACTION_SUMMARY: RadioButtonPreference.ContextualSummaryProvider =
    object : RadioButtonPreference.ContextualSummaryProvider {
        override fun getSummary(context: Context): CharSequence {
            val app =
                context.getQuickTapLaunchApp()
                    ?: return context.getString(R.string.setting_action_launch_summary_no_selection)
            val pm = context.packageManager
            return try {
                pm.getApplicationLabel(pm.getActivityInfo(app, 0).applicationInfo)
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

private const val QUICK_TAP_ACTION_SETTING = LineageSettings.System.KEY_QUICK_TAP_ACTION

val QUICK_TAP_ACTIONS: List<Pair<Action, Int>> =
    listOf(
        Action.MENU to R.string.action_menu_name,
        Action.APP_SWITCH to R.string.action_overview_name,
        Action.SEARCH to R.string.action_assistant_name,
        Action.VOICE_SEARCH to R.string.action_voice_search_name,
        Action.IN_APP_SEARCH to R.string.action_in_app_search_name,
        Action.LAUNCH_CAMERA to R.string.action_camera_name,
        Action.SLEEP to R.string.action_screen_name,
        Action.LAST_APP to R.string.action_last_app_name,
        Action.SPLIT_SCREEN to R.string.action_split_screen_name,
        Action.KILL_APP to R.string.action_kill_app_name,
        Action.PLAY_PAUSE_MUSIC to R.string.action_media_name,
        Action.SCREENSHOT to R.string.action_screenshot_name,
        Action.PARTIAL_SCREENSHOT to R.string.action_partial_screenshot_name,
        Action.CYCLE_RINGER_MODE to R.string.action_ringer_mode_name,
        Action.TORCH to R.string.action_flashlight_name,
        Action.VOLUME_PANEL to R.string.action_volume_panel_name,
        Action.CLEAR_ALL_NOTIFICATIONS to R.string.action_clear_notifications_name,
        Action.NOTIFICATIONS to R.string.action_notifications_name,
        Action.QS_PANEL to R.string.action_qs_panel_name,
        Action.POWER_MENU to R.string.action_power_menu_name,
        Action.CONTEXTUAL_SEARCH to R.string.action_contextual_search_name,
        Action.SILENCE_CALL to R.string.action_silence_call_name,
        Action.LAUNCH_APP to R.string.action_launch_name,
    )

fun Context.getQuickTapAction(): Action =
    Action.fromSettings(contentResolver, QUICK_TAP_ACTION_SETTING, QUICK_TAP_DEFAULT_ACTION)

fun Context.setQuickTapAction(action: Action) {
    LineageSettings.System.putIntForUser(
        contentResolver,
        QUICK_TAP_ACTION_SETTING,
        action.ordinal,
        UserHandle.USER_CURRENT,
    )
}

fun Context.getQuickTapActionName(): String {
    val action = getQuickTapAction()
    val name = QUICK_TAP_ACTIONS.firstOrNull { it.first == action }?.second
    return if (name != null) getString(name) else action.name
}

fun Context.getQuickTapLaunchApp(): ComponentName? {
    val app =
        LineageSettings.System.getStringForUser(
            contentResolver,
            getLaunchAppSetting(QUICK_TAP_ACTION_SETTING),
            UserHandle.USER_CURRENT,
        )
    return if (app.isNullOrEmpty()) null else ComponentName.unflattenFromString(app)
}

fun Context.getQuickTapLaunchShortcut(): String? {
    val shortcut =
        LineageSettings.System.getStringForUser(
            contentResolver,
            getLaunchShortcutSetting(QUICK_TAP_ACTION_SETTING),
            UserHandle.USER_CURRENT,
        )
    return if (shortcut.isNullOrEmpty()) null else shortcut
}

fun Context.setQuickTapLaunchApp(app: ComponentName, shortcutId: String?) {
    LineageSettings.System.putStringForUser(
        contentResolver,
        getLaunchAppSetting(QUICK_TAP_ACTION_SETTING),
        app.flattenToString(),
        UserHandle.USER_CURRENT,
    )
    LineageSettings.System.putStringForUser(
        contentResolver,
        getLaunchShortcutSetting(QUICK_TAP_ACTION_SETTING),
        shortcutId ?: "",
        UserHandle.USER_CURRENT,
    )
    setQuickTapAction(Action.LAUNCH_APP)
}

private val LEGACY_ACTIONS =
    mapOf(
        "screenshot" to Action.SCREENSHOT,
        "assistant" to Action.SEARCH,
        "contextual_search" to Action.CONTEXTUAL_SEARCH,
        "silence_call" to Action.SILENCE_CALL,
        "media" to Action.PLAY_PAUSE_MUSIC,
        "overview" to Action.APP_SWITCH,
        "notifications" to Action.NOTIFICATIONS,
        "camera" to Action.LAUNCH_CAMERA,
        "power_menu" to Action.POWER_MENU,
        "mute" to Action.CYCLE_RINGER_MODE,
        "flashlight" to Action.TORCH,
        "screen" to Action.SLEEP,
        "launch" to Action.LAUNCH_APP,
    )
private const val LEGACY_KEY_ACTION = "columbus_action"
private const val LEGACY_KEY_LAUNCH_APP = "columbus_launch_app"
private const val LEGACY_KEY_LAUNCH_APP_SHORTCUT = "columbus_launch_app_shortcut"

fun SharedPreferences.migrateLegacyAction(context: Context) {
    val legacyAction = getString(LEGACY_KEY_ACTION, null) ?: return
    val action = LEGACY_ACTIONS[legacyAction]
    val app = getString(LEGACY_KEY_LAUNCH_APP, null)?.let { ComponentName.unflattenFromString(it) }
    if (action == Action.LAUNCH_APP && app != null) {
        val shortcut = getString(LEGACY_KEY_LAUNCH_APP_SHORTCUT, null)
        context.setQuickTapLaunchApp(app, shortcut?.takeIf { it != app.flattenToString() })
    } else if (action != null && action != Action.LAUNCH_APP) {
        context.setQuickTapAction(action)
    }
    edit()
        .remove(LEGACY_KEY_ACTION)
        .remove(LEGACY_KEY_LAUNCH_APP)
        .remove(LEGACY_KEY_LAUNCH_APP_SHORTCUT)
        .apply()
    listOf(LEGACY_KEY_ACTION, LEGACY_KEY_LAUNCH_APP, LEGACY_KEY_LAUNCH_APP_SHORTCUT).forEach {
        Settings.Secure.putStringForUser(context.contentResolver, it, null, UserHandle.USER_CURRENT)
    }
}
