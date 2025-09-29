/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.os.Bundle
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.protonaosp.columbus.LAUNCH_ACTION_SUMMARY
import org.protonaosp.columbus.PREFS_NAME
import org.protonaosp.columbus.R
import org.protonaosp.columbus.getAction
import org.protonaosp.columbus.getAllowScreenOff
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getEnabled
import org.protonaosp.columbus.getHapticIntensity
import org.protonaosp.columbus.getSensitivity
import org.protonaosp.columbus.setAction
import org.protonaosp.columbus.settings.launch.LaunchSettingsFragment
import org.protonaosp.columbus.widget.RadioButtonPreference

class SettingsFragment :
    SettingsBasePreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    SelectorWithWidgetPreference.OnClickListener {

    private lateinit var prefs: SharedPreferences
    private val _context by lazy { requireContext() }

    private val vibrator by lazy {
        (_context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
    }

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
        requireActivity().setTitle(R.string.settings_entry_title)
        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = PREFS_NAME

        prefs = _context.getDePrefs()
        prefs.registerOnSharedPreferenceChangeListener(this)
        lifecycleScope.launch { populateRadioPreferences() }

        updateEnabled()
        updateSensitivity(true)
        updateAllowScreenOff()
        updateHapticIntensity(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionPreferences.clear()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
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

        val key = emiter.key
        if (key == prefs.getAction(_context)) {
            return
        }

        prefs.setAction(_context, key)

        updateActionState()
    }

    private fun updateActionState() {
        if (actionPreferences.isEmpty()) {
            return
        }
        var currentAction = prefs.getAction(_context)
        if (!actionPreferences.containsKey(currentAction)) {
            currentAction = _context.getString(R.string.default_action)
        }
        for (action in actionPreferences.values) {
            val isActionChecked = action.key == currentAction
            if (action.isChecked() != isActionChecked) {
                action.setChecked(isActionChecked)
            }
            action.setEnabled(prefs.getEnabled(_context))
            if (action.key == _context.getString(R.string.action_launch_value)) {
                lifecycleScope.launch {
                    val summary =
                        withContext(Dispatchers.IO) { LAUNCH_ACTION_SUMMARY.getSummary(_context) }
                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            action.summary = summary
                        }
                    }
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
        val actionCategory =
            preferenceScreen.findPreference<PreferenceCategory>(
                getString(R.string.categ_key_action)
            ) ?: return

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
            if (!isAdded) return@withContext

            actionCategory.removeAll()
            actionPreferences.clear()

            prefInfoList.forEach { info ->
                val onClickListener =
                    if (info.isLaunch) {
                        View.OnClickListener {
                            requireActivity()
                                .supportFragmentManager
                                .beginTransaction()
                                .replace(
                                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                                    LaunchSettingsFragment(),
                                )
                                .addToBackStack(null)
                                .commit()
                        }
                    } else {
                        null
                    }

                val pref =
                    RadioButtonPreference(_context).apply {
                        key = info.key
                        title = info.title
                        summary = info.summary
                        if (info.isLaunch) {
                            setContextualSummaryProvider(LAUNCH_ACTION_SUMMARY)
                        }
                        setOnClickListener(this@SettingsFragment)
                        setExtraWidgetOnClickListener(onClickListener)
                        isPersistent = false
                    }
                actionCategory.addPreference(pref)
                actionPreferences[info.key] = pref
            }
            updateActionState()
        }
    }

    private fun updateEnabled() {
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
                vibrator.vibrate(vibDoubleTap, sonicAudioAttr)
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
