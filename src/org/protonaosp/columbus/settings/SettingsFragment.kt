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
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
import org.protonaosp.columbus.PREFS_NAME
import org.protonaosp.columbus.R
import org.protonaosp.columbus.getAction
import org.protonaosp.columbus.getActionName
import org.protonaosp.columbus.getAllowScreenOff
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getEnabled
import org.protonaosp.columbus.getHapticIntensity
import org.protonaosp.columbus.getHapticIntensityEntry
import org.protonaosp.columbus.getLaunchActionAppName
import org.protonaosp.columbus.getSensitivity

class SettingsFragment :
    SettingsBasePreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: SharedPreferences
    private lateinit var mContext: Context

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = PREFS_NAME

        mContext = requireContext()
        prefs = mContext.getDePrefs()
        prefs.registerOnSharedPreferenceChangeListener(this)
        updateUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        updateUi()
    }

    private fun updateUi() {
        // Enabled
        val enabled = prefs.getEnabled(mContext)
        findPreference<MainSwitchPreference>(getString(R.string.pref_key_enabled))?.apply {
            setChecked(enabled)
        }

        // Sensitivity value
        findPreference<SliderPreference>(getString(R.string.pref_key_sensitivity))?.apply {
            value = prefs.getSensitivity(mContext)
            setHapticFeedbackMode(SliderPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS)
            sliderIncrement = 1
            setTickVisible(true)
        }

        // Action value and summary
        val key_action = getString(R.string.pref_key_action)
        val action_value = prefs.getAction(mContext)
        findPreference<ListPreference>(key_action)?.apply {
            value = action_value
            summary = prefs.getActionName(mContext)
        }

        val launch_app: Preference? =
            findPreference<Preference>(getString(R.string.pref_key_launch_app))
        launch_app?.apply {
            val appname = prefs.getLaunchActionAppName(mContext)
            if (appname != getString(R.string.launch_app_default)) {
                summary = appname
            } else {
                summary = getString(R.string.launch_app_select_summary)
            }
        }
        launch_app?.setVisible(enabled && action_value == getString(R.string.action_launch_value))

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
                    setChecked(prefs.getAllowScreenOff(mContext))
                }
            }

        // Haptic Intensity
        findPreference<ListPreference>(getString(R.string.pref_key_haptic_intensity))?.apply {
            value = prefs.getHapticIntensity(mContext)
            summary = prefs.getHapticIntensityEntry(mContext)
        }
    }
}
