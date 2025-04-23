/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus

import android.content.Context
import android.content.SharedPreferences

const val PREFS_NAME = "columbus_preferences"

fun Context.getDePrefs(): SharedPreferences {
    return createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

fun SharedPreferences.getEnabled(context: Context): Boolean {
    return getBoolean(
        context.getString(R.string.pref_key_enabled),
        context.resources.getBoolean(R.bool.default_enabled),
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

fun SharedPreferences.getLaunchActionApp(context: Context): String {
    val app_default = context.getString(R.string.launch_app_default)
    return getString(context.getString(R.string.pref_key_launch_app), app_default) ?: app_default
}

fun SharedPreferences.getLaunchActionAppName(context: Context): String {
    val app_default = context.getString(R.string.launch_app_default)
    return getString(context.getString(R.string.pref_key_launch_app_name), app_default)
        ?: app_default
}

fun SharedPreferences.getLaunchActionAppShortcut(context: Context): String {
    val app_default = context.getString(R.string.launch_app_default)
    return getString(context.getString(R.string.pref_key_launch_app_shortcut), app_default)
        ?: app_default
}

fun SharedPreferences.getHapticIntensity(context: Context): String {
    val haptic_default = context.getString(R.string.default_haptic_intensity)
    return getString(context.getString(R.string.pref_key_haptic_intensity), haptic_default)
        ?: haptic_default
}

fun SharedPreferences.getHapticIntensityEntry(context: Context): String {
    val hapticEntries = context.resources.getStringArray(R.array.pref_haptic_intensity_entries)
    val hapticValues = context.resources.getStringArray(R.array.pref_haptic_intensity_values)
    return hapticEntries[hapticValues.indexOf(getHapticIntensity(context))]
}
