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
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.protonaosp.columbus.PREFS_NAME
import org.protonaosp.columbus.PackageStateManager
import org.protonaosp.columbus.R
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getLaunchActionAppShortcut
import org.protonaosp.columbus.setAction
import org.protonaosp.columbus.setLaunchActionApp
import org.protonaosp.columbus.setLaunchActionAppShortcut
import org.protonaosp.columbus.widget.RadioButtonPreference

class LaunchAppShortcutSettingsFragment :
    SettingsBasePreferenceFragment(),
    SelectorWithWidgetPreference.OnClickListener,
    PackageStateManager.PackageStateListener {

    private var currentUser: Int = -1
    private val _context by lazy { requireContext() }
    private lateinit var prefs: SharedPreferences
    private lateinit var launcherApps: LauncherApps
    private lateinit var openAppValue: String
    private var application: ComponentName? = null
    private var shortcutInfos: ArrayList<ShortcutInfo?>? = null
    private var shortcutlistCategory: PreferenceCategory? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.launch_app_shortcut_settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = PREFS_NAME

        prefs = _context.getDePrefs()
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
        lifecycleScope.launch { populateRadioPreferences() }
        PackageStateManager.registerListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PackageStateManager.unregisterListener(this)
    }

    override fun onPackageRemoved(packageName: String) {
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

    override fun onPackageChanged(packageName: String) {
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
                pref.setChecked(currentShortcut == pref.key)
            }
        }
    }

    private suspend fun populateRadioPreferences() {
        val shortcutlistCategory = shortcutlistCategory ?: return
        val application = application ?: return
        val shortcutInfos = shortcutInfos ?: return

        shortcutlistCategory.removeAll()

        val appIcon =
            withContext(Dispatchers.IO) {
                launcherApps
                    .getActivityList(application.packageName, UserHandle.of(currentUser))
                    .firstOrNull { it.componentName == application }
                    ?.getIcon(DisplayMetrics.DENSITY_DEVICE_STABLE) as? Drawable
            }

        val shortcutData =
            withContext(Dispatchers.IO) {
                shortcutInfos.filterNotNull().map {
                    it.id to
                        Pair(
                            it.label,
                            launcherApps.getShortcutIconDrawable(
                                it,
                                DisplayMetrics.DENSITY_DEVICE_STABLE,
                            ),
                        )
                }
            }

        withContext(Dispatchers.Main) {
            shortcutlistCategory.removeAll()

            makeRadioPreference(
                shortcutlistCategory,
                application.flattenToString(),
                _context.getString(R.string.action_launch_name),
                appIcon,
            )

            for ((shortcutId, data) in shortcutData) {
                val (label, icon) = data
                makeRadioPreference(shortcutlistCategory, shortcutId, label, icon)
            }

            updateState()
        }
    }

    private fun makeRadioPreference(
        category: PreferenceCategory,
        key: String,
        title: CharSequence?,
        icon: Drawable?,
    ): RadioButtonPreference {
        val radioPref = RadioButtonPreference(_context)
        radioPref.apply {
            setKey(key)
            setTitle(title)
            setIcon(icon)
            setOnClickListener(this@LaunchAppShortcutSettingsFragment)
            category.addPreference(this)
        }
        return radioPref
    }
}
