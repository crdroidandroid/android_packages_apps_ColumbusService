/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.media.AudioAttributes
import android.os.Bundle
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SliderPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.protonaosp.columbus.LAUNCH_ACTION_SUMMARY
import org.protonaosp.columbus.PREFS_NAME
import org.protonaosp.columbus.R
import org.protonaosp.columbus.TAG
import org.protonaosp.columbus.dlog
import org.protonaosp.columbus.getAction
import org.protonaosp.columbus.getAllowScreenOff
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getEnabled
import org.protonaosp.columbus.getHapticIntensity
import org.protonaosp.columbus.getSensitivity
import org.protonaosp.columbus.setAction
import org.protonaosp.columbus.settings.launch.LaunchSettingsFragment
import org.protonaosp.columbus.utils.AppIconCacheManager
import org.protonaosp.columbus.widget.RadioButtonPreference

class SettingsFragment :
    ObservablePreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    SelectorWithWidgetPreference.OnClickListener {

    private var currentUser: Int = -1
    private var prefs: SharedPreferences? = null
    private val _context by lazy { requireContext() }

    private val vibrator by lazy {
        (_context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    }
    private var launcherApps: LauncherApps? = null

    private var actionCategory: PreferenceCategory? = null

    // Keys
    private val keyEnabled by lazy { _context.getString(R.string.pref_key_enabled) }
    private val keyAction by lazy { _context.getString(R.string.pref_key_action) }
    private val keySensitivity by lazy { _context.getString(R.string.pref_key_sensitivity) }
    private val keyAllowScreenOff by lazy { _context.getString(R.string.pref_key_allow_screen_off) }
    private val keyHapticIntensity by lazy {
        _context.getString(R.string.pref_key_haptic_intensity)
    }

    // Prefs
    private val prefEnabled by lazy { findPreference<MainSwitchPreference>(keyEnabled) }
    private val prefSensitivity by lazy { findPreference<SliderPreference>(keySensitivity) }
    private val prefAllowScreenOff by lazy {
        findPreference<SwitchPreferenceCompat>(keyAllowScreenOff)
    }
    private val prefHapticIntensity by lazy { findPreference<SliderPreference>(keyHapticIntensity) }
    private val actionPreferences: MutableMap<String, RadioButtonPreference> =
        mutableMapOf<String, RadioButtonPreference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentUser = ActivityManager.getCurrentUser()
        launcherApps = _context.getSystemService(LauncherApps::class.java)
        lifecycleScope.launch { preloadAppIcons() }

        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = PREFS_NAME

        prefs = _context.getDePrefs()
        prefs?.registerOnSharedPreferenceChangeListener(this)
        actionCategory =
            preferenceScreen.findPreference<PreferenceCategory>(
                getString(R.string.categ_key_action)
            )
        lifecycleScope.launch { populateRadioPreferences() }

        updateEnabled()
        updateSensitivity(true)
        updateAllowScreenOff()
        updateHapticIntensity(true)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.settings_entry_title)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        prefs?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        when (key) {
            keyEnabled -> {
                updateEnabled()
                updateActionState()
            }
            keyAction -> updateActionState()
            keySensitivity -> updateSensitivity()
            keyAllowScreenOff -> updateAllowScreenOff()
            keyHapticIntensity -> updateHapticIntensity()
        }
    }

    override fun onRadioButtonClicked(emiter: SelectorWithWidgetPreference) {
        if (emiter !is RadioButtonPreference) return
        val prefs = prefs ?: return

        val key = emiter.key
        if (key == prefs.getAction(_context)) {
            return
        }

        prefs.setAction(_context, key)

        updateActionState()
    }

    private fun updateActionState() {
        val actionCategory = actionCategory ?: return
        val prefs = prefs ?: return

        val preferenceCount = actionCategory.preferenceCount
        if (preferenceCount == 0) {
            return
        }

        val enabled = prefs.getEnabled(_context)
        var currentAction = prefs.getAction(_context)
        for (i in 0 until preferenceCount) {
            val pref = actionCategory.getPreference(i)
            if (pref !is RadioButtonPreference) {
                continue
            }

            pref.setEnabled(enabled)
            pref.setChecked(currentAction == pref.key)
            if (pref.key == _context.getString(R.string.action_launch_value)) {
                lifecycleScope.launch {
                    val summary =
                        withContext(Dispatchers.IO) { LAUNCH_ACTION_SUMMARY.getSummary(_context) }
                    withContext(Dispatchers.Main) { pref.summary = summary }
                }
            }
        }
    }

    private suspend fun preloadAppIcons() {
        val launcherApps = launcherApps ?: return
        withContext(Dispatchers.IO) {
            val cacheManager = AppIconCacheManager.getInstance()
            val activityLists = launcherApps.getActivityList(null, UserHandle.of(currentUser))

            activityLists.forEach { activity ->
                val packageName = activity.componentName.packageName
                val uid = activity.applicationInfo.uid

                try {
                    val icon = activity.getIcon(DisplayMetrics.DENSITY_DEVICE_STABLE)
                    cacheManager.put(packageName, uid, icon)
                } catch (e: Exception) {
                    dlog(TAG, "Failed to preload icon for $packageName. ${e.message}")
                }
            }
        }
    }

    private data class ActionPreferenceInfo(
        val key: String,
        val title: String,
        val summary: CharSequence?,
        val isLaunch: Boolean,
    )

    private suspend fun populateRadioPreferences() {
        val actionCategory = actionCategory ?: return

        val prefInfoList =
            withContext(Dispatchers.IO) {
                val actionNames = _context.resources.getStringArray(R.array.action_names)
                val actionValues = _context.resources.getStringArray(R.array.action_values)
                val launchAction = _context.getString(R.string.action_launch_value)

                actionValues.mapIndexed { i, action ->
                    val isLaunch = action == launchAction
                    ActionPreferenceInfo(
                        key = action,
                        title = actionNames[i],
                        summary =
                            if (isLaunch) LAUNCH_ACTION_SUMMARY.getSummary(_context) else null,
                        isLaunch = isLaunch,
                    )
                }
            }

        withContext(Dispatchers.Main) {
            val existingPreferences = mutableMapOf<String, RadioButtonPreference>()
            for (i in 0 until actionCategory.preferenceCount) {
                val pref = actionCategory.getPreference(i)
                if (pref is RadioButtonPreference) {
                    existingPreferences[pref.key] = pref
                }
            }

            val preferencesToRemove = existingPreferences.keys.toMutableSet()

            prefInfoList.forEach { info ->
                preferencesToRemove.remove(info.key)

                val radioPref =
                    existingPreferences[info.key]
                        ?: RadioButtonPreference(_context).also {
                            it.key = info.key
                            it.setOnClickListener(this@SettingsFragment)
                            it.isPersistent = false
                            actionCategory.addPreference(it)
                            actionPreferences[info.key] = it
                        }

                radioPref.apply {
                    setTitle(info.title)
                    setSummary(info.summary)

                    if (info.isLaunch) {
                        setContextualSummaryProvider(LAUNCH_ACTION_SUMMARY)
                    }

                    val onClickListener: View.OnClickListener? =
                        if (!info.isLaunch) {
                            null
                        } else {
                            View.OnClickListener {
                                requireActivity()
                                    .supportFragmentManager
                                    .beginTransaction()
                                    .setReorderingAllowed(true)
                                    .replace(
                                        com.android.settingslib.collapsingtoolbar.R.id
                                            .content_frame,
                                        LaunchSettingsFragment(),
                                    )
                                    .addToBackStack(null)
                                    .commit()
                            }
                        }
                    setExtraWidgetOnClickListener(onClickListener)
                }
            }

            preferencesToRemove.forEach { key ->
                val pref = existingPreferences[key]
                if (pref != null) {
                    actionCategory.removePreference(pref)
                }
            }

            updateActionState()
        }
    }

    private fun updateEnabled() {
        val prefs = prefs ?: return
        val enabled = prefs.getEnabled(_context)
        prefEnabled?.apply {
            setChecked(enabled)

            // Compat for 3rd party apps
            Settings.Secure.putIntForUser(
                context.contentResolver,
                keyEnabled,
                if (enabled) 1 else 0,
                UserHandle.USER_CURRENT,
            )
        }
    }

    private fun updateSensitivity(initialize: Boolean = false) {
        val prefs = prefs ?: return
        prefSensitivity?.apply {
            if (initialize) {
                setHapticFeedbackMode(SliderPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS)
                setSliderIncrement(1)
                setTickVisible(true)
                setUpdatesContinuously(true)
            }
            setValue(prefs.getSensitivity(_context))
        }
    }

    private fun updateAllowScreenOff() {
        val prefs = prefs ?: return
        prefAllowScreenOff?.apply {
            val screenForced =
                prefs.getBoolean(getString(R.string.pref_key_allow_screen_off_action_forced), false)
            setEnabled(!screenForced)
            if (screenForced) {
                setSummary(getString(R.string.setting_screen_off_blocked_summary))
                setPersistent(false)
                setChecked(false)
            } else {
                setSummary(getString(R.string.setting_screen_off_summary))
                setPersistent(true)
                setChecked(prefs.getAllowScreenOff(_context))
            }
        }
    }

    private fun updateHapticIntensity(initialize: Boolean = false) {
        val prefs = prefs ?: return
        prefHapticIntensity?.apply {
            if (initialize) {
                sliderIncrement = 1
                setTickVisible(true)
                setUpdatesContinuously(true)
            }
            value = prefs.getHapticIntensity(_context)
            if (!initialize) {
                val vibDoubleTap =
                    when (value) {
                        0 -> EFFECT_TICK
                        1 -> EFFECT_DOUBLE_CLICK
                        2 -> EFFECT_HEAVY_CLICK
                        else -> EFFECT_HEAVY_CLICK
                    }
                vibrator?.vibrate(vibDoubleTap, sonicAudioAttr)
            }
        }
    }

    companion object {
        // Vibration effects from HapticFeedbackConstants
        // Duplicated because we can't use performHapticFeedback in a background service
        private val sonicAudioAttr: AudioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

        private val EFFECT_TICK =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        private val EFFECT_DOUBLE_CLICK =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            } else {
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        private val EFFECT_HEAVY_CLICK =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            } else {
                VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE)
            }
    }
}
