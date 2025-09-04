/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
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
    private val actionPreferences: MutableMap<String, RadioButtonPreference> =
        mutableMapOf<String, RadioButtonPreference>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = PREFS_NAME

        prefs = _context.getDePrefs()
        prefs.registerOnSharedPreferenceChangeListener(this)
        populateRadioPreferences()
        updateUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionPreferences.clear()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        updateUi()
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
            action.updateSummary(_context)
        }
    }

    private fun populateRadioPreferences() {
        val actionCategory =
            preferenceScreen.findPreference<PreferenceCategory>(
                getString(R.string.categ_key_action)
            ) ?: return

        actionCategory.removeAll()

        actionPreferences.clear()
        val actionNames = _context.resources.getStringArray(R.array.action_names)
        val actionValues = _context.resources.getStringArray(R.array.action_values)
        val launchAction = _context.getString(R.string.action_launch_value)
        for (i in 0 until actionValues.size) {
            val action = actionValues[i]
            val actionSummary: RadioButtonPreference.ContextualSummaryProvider? =
                if (action == launchAction) {
                    LAUNCH_ACTION_SUMMARY
                } else {
                    null
                }
            val onClickListener: View.OnClickListener? =
                if (action == launchAction) {
                    object : View.OnClickListener {
                        override fun onClick(view: View) {
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
                    }
                } else {
                    null
                }
            actionPreferences.put(
                action,
                makeRadioPreference(
                    actionCategory,
                    actionValues[i],
                    actionNames[i],
                    actionSummary,
                    onClickListener,
                ),
            )
        }
    }

    private fun makeRadioPreference(
        category: PreferenceCategory,
        key: String,
        title: String?,
        summaryProvider: RadioButtonPreference.ContextualSummaryProvider?,
        onClickListener: View.OnClickListener?,
    ): RadioButtonPreference {
        val radioPref = RadioButtonPreference(_context)
        radioPref.apply {
            setKey(key)
            setTitle(title)
            setContextualSummaryProvider(summaryProvider)
            updateSummary(this@SettingsFragment._context)
            setOnClickListener(this@SettingsFragment)
            setExtraWidgetOnClickListener(onClickListener)
            category.addPreference(this)
        }
        return radioPref
    }

    private fun updateUi() {
        // Enabled
        val enabled = prefs.getEnabled(_context)
        val keyEnabled = getString(R.string.pref_key_enabled)
        findPreference<MainSwitchPreference>(keyEnabled)?.apply {
            setChecked(enabled)

            // Compat for 3rd party apps
            Settings.Secure.putIntForUser(
                context.contentResolver,
                keyEnabled,
                if (enabled) 1 else 0,
                UserHandle.USER_CURRENT,
            )
        }

        updateActionState()

        // Sensitivity value
        findPreference<SliderPreference>(getString(R.string.pref_key_sensitivity))?.apply {
            setValue(prefs.getSensitivity(_context))
            setHapticFeedbackMode(SliderPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS)
            setSliderIncrement(1)
            setTickVisible(true)
        }

        // Screen state based on action
        findPreference<SwitchPreferenceCompat>(getString(R.string.pref_key_allow_screen_off))
            ?.apply {
                val screenForced =
                    prefs.getBoolean(
                        getString(R.string.pref_key_allow_screen_off_action_forced),
                        false,
                    )
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

        // Haptic Intensity
        findPreference<SliderPreference>(getString(R.string.pref_key_haptic_intensity))?.apply {
            value = prefs.getHapticIntensity(_context)
            setHapticFeedbackMode(SliderPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS)
            sliderIncrement = 1
            setTickVisible(true)
        }
    }
}
