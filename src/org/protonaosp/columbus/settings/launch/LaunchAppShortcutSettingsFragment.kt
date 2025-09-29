/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: Apache-2.0
 */

package org.protonaosp.columbus.settings.launch

import android.app.ActivityManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceCategory
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.TopIntroPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.protonaosp.columbus.PREFS_NAME
import org.protonaosp.columbus.PackageStateManager
import org.protonaosp.columbus.R
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getEnabled
import org.protonaosp.columbus.getLaunchActionAppShortcut
import org.protonaosp.columbus.setAction
import org.protonaosp.columbus.setLaunchActionApp
import org.protonaosp.columbus.setLaunchActionAppShortcut
import org.protonaosp.columbus.settings.ObservablePreferenceFragment
import org.protonaosp.columbus.widget.RadioButtonPreference

class LaunchAppShortcutSettingsFragment :
    ObservablePreferenceFragment(),
    SelectorWithWidgetPreference.OnClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    PackageStateManager.PackageStateListener {

    private var currentUser: Int = -1
    private val _context by lazy { requireContext() }
    private lateinit var prefs: SharedPreferences
    private lateinit var launcherApps: LauncherApps
    private lateinit var openAppValue: String
    private var application: ComponentName? = null
    private var shortcutInfos: ArrayList<ShortcutInfo?>? = null
    private var shortcutlistCategory: PreferenceCategory? = null

    // Keys
    private val keyEnabled by lazy { _context.getString(R.string.pref_key_enabled) }
    private val keyLaunchShortcutAppSummary by lazy {
        _context.getString(R.string.pref_key_launch_shortcut_app_summary)
    }

    // Prefs
    private val prefLaunchShortcutAppSummary by lazy {
        findPreference<TopIntroPreference>(keyLaunchShortcutAppSummary)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.launch_app_shortcut_settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().setTitle(R.string.launch_settings_activity_title)
        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = PREFS_NAME

        prefs = _context.getDePrefs()
        prefs.registerOnSharedPreferenceChangeListener(this)
        currentUser = ActivityManager.getCurrentUser()
        launcherApps = _context.getSystemService(LauncherApps::class.java)
        openAppValue = _context.getString(R.string.action_launch_value)
        shortcutlistCategory =
            preferenceScreen.findPreference<PreferenceCategory>(
                getString(R.string.categ_key_app_shortcut_list)
            )

        val args = getArguments()
        application =
            args?.getParcelable(
                _context.getString(R.string.pref_key_launch_app),
                ComponentName::class.java,
            )
        shortcutInfos =
            args?.getParcelableArrayList(
                _context.getString(R.string.pref_key_launch_app_shortcut),
                ShortcutInfo::class.java,
            )
        updateIntro()
        lifecycleScope.launch { populateRadioPreferences() }
        PackageStateManager.registerListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        PackageStateManager.unregisterListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == keyEnabled) {
            updateIntro()
            updateState()
        }
    }

    override fun onPackageRemoved(packageName: String, uid: Int) {
        val pkgName: String = application?.packageName ?: return

        if (pkgName != packageName) {
            return
        }

        Toast.makeText(
                requireActivity(),
                getString(R.string.setting_action_launch_summary_not_installed),
                Toast.LENGTH_SHORT,
            )
            .show()
        requireActivity().finish()
    }

    override fun onPackageChanged(packageName: String, uid: Int) {
        val pkgName: String = application?.packageName ?: return

        if (pkgName != packageName) {
            return
        }

        lifecycleScope.launch { populateRadioPreferences() }
    }

    override fun onRadioButtonClicked(emiter: SelectorWithWidgetPreference) {
        if (emiter !is RadioButtonPreference) return
        val application = application ?: return

        val key = emiter.key

        prefs.setAction(_context, openAppValue)
        prefs.setLaunchActionApp(_context, application.flattenToString())
        prefs.setLaunchActionAppShortcut(_context, key)

        updateState()
    }

    private fun updateIntro() {
        prefLaunchShortcutAppSummary?.setTitle(
            if (prefs.getEnabled(_context)) {
                R.string.setting_app_shortcut_selection_help_text
            } else {
                R.string.setting_app_shortcut_selection_help_text_disabled
            }
        )
    }

    private fun updateState() {
        val shortcutlistCategory = shortcutlistCategory ?: return

        val preferenceCount = shortcutlistCategory.preferenceCount
        if (preferenceCount == 0) {
            return
        }

        var currentShortcut = prefs.getLaunchActionAppShortcut(_context)
        for (i in 0 until preferenceCount) {
            val pref = shortcutlistCategory.getPreference(i)
            if (pref is RadioButtonPreference) {
                pref.setEnabled(prefs.getEnabled(_context))
                pref.setChecked(currentShortcut == pref.key)
            }
        }
    }

    private data class ShortcutPreferenceData(
        val key: String,
        val title: CharSequence?,
        val icon: Drawable?,
    )

    private suspend fun populateRadioPreferences() {
        val shortcutlistCategory = shortcutlistCategory ?: return
        val application = application ?: return
        val shortcutInfos = shortcutInfos ?: return

        val shortcutDataList =
            withContext(Dispatchers.IO) {
                val data = mutableListOf<ShortcutPreferenceData>()

                val appIcon =
                    launcherApps
                        .getActivityList(application.packageName, UserHandle.of(currentUser))
                        .firstOrNull { it.componentName == application }
                        ?.getIcon(DisplayMetrics.DENSITY_DEVICE_STABLE)
                data.add(
                    ShortcutPreferenceData(
                        key = application.flattenToString(),
                        title = _context.getString(R.string.action_launch_name),
                        icon = appIcon,
                    )
                )

                shortcutInfos.filterNotNull().forEach { shortcutInfo ->
                    data.add(
                        ShortcutPreferenceData(
                            key = shortcutInfo.id,
                            title = shortcutInfo.label,
                            icon =
                                launcherApps.getShortcutIconDrawable(
                                    shortcutInfo,
                                    DisplayMetrics.DENSITY_DEVICE_STABLE,
                                ),
                        )
                    )
                }
                data
            }

        withContext(Dispatchers.Main) {
            val existingPreferences = mutableMapOf<String, RadioButtonPreference>()
            for (i in 0 until shortcutlistCategory.preferenceCount) {
                val pref = shortcutlistCategory.getPreference(i)
                if (pref is RadioButtonPreference) {
                    existingPreferences[pref.key] = pref
                }
            }

            val preferencesToRemove = existingPreferences.keys.toMutableSet()

            for (data in shortcutDataList) {
                preferencesToRemove.remove(data.key)

                val radioPref =
                    existingPreferences[data.key]
                        ?: RadioButtonPreference(_context).also {
                            it.apply {
                                setKey(data.key)
                                setOnClickListener(this@LaunchAppShortcutSettingsFragment)
                                isPersistent = false
                                shortcutlistCategory.addPreference(it)
                            }
                        }

                radioPref.apply {
                    setTitle(data.title)
                    setIcon(data.icon)
                }
            }

            preferencesToRemove.forEach { key ->
                val pref = existingPreferences[key]
                if (pref != null) {
                    shortcutlistCategory.removePreference(pref)
                }
            }

            updateState()
        }
    }
}
