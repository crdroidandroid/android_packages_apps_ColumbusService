/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: Apache-2.0
 */

package org.protonaosp.columbus.settings.launch

import android.app.ActivityManager
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
import android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.util.DisplayMetrics
import android.view.View
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
import org.protonaosp.columbus.TAG
import org.protonaosp.columbus.dlog
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getEnabled
import org.protonaosp.columbus.getLaunchActionApp
import org.protonaosp.columbus.setAction
import org.protonaosp.columbus.setLaunchActionApp
import org.protonaosp.columbus.setLaunchActionAppShortcut
import org.protonaosp.columbus.settings.ObservablePreferenceFragment
import org.protonaosp.columbus.utils.AppIconCacheManager
import org.protonaosp.columbus.widget.RadioButtonPreference

class LaunchSettingsFragment :
    ObservablePreferenceFragment(),
    SelectorWithWidgetPreference.OnClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    PackageStateManager.PackageStateListener {

    private var currentUser: Int = -1
    private val _context by lazy { requireContext() }
    private lateinit var prefs: SharedPreferences
    private lateinit var launcherApps: LauncherApps
    private lateinit var openAppValue: String
    private lateinit var launchAppKey: String
    private lateinit var launchAppShortcutKey: String
    private var applistCategory: PreferenceCategory? = null

    // Keys
    private val keyEnabled by lazy { _context.getString(R.string.pref_key_enabled) }
    private val keyLaunchAppSummary by lazy {
        _context.getString(R.string.pref_key_launch_app_summary)
    }

    // Prefs
    private val prefLaunchAppSummary by lazy {
        findPreference<TopIntroPreference>(keyLaunchAppSummary)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.launch_settings, rootKey)
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
        launchAppKey = _context.getString(R.string.pref_key_launch_app)
        launchAppShortcutKey = _context.getString(R.string.pref_key_launch_app_shortcut)
        applistCategory =
            preferenceScreen.findPreference<PreferenceCategory>(
                getString(R.string.categ_key_app_list)
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

    override fun onPackageAdded(packageName: String, uid: Int) {
        lifecycleScope.launch { populateRadioPreferences() }
    }

    override fun onPackageRemoved(packageName: String, uid: Int) {
        val applistCategory = applistCategory ?: return

        val preferenceCount = applistCategory.preferenceCount
        if (preferenceCount == 0) {
            return
        }

        for (i in 0 until preferenceCount) {
            val pref = applistCategory.getPreference(i)

            val prefKey = pref.key
            if (prefKey == null) continue
            if (prefKey.startsWith("${packageName}/")) {
                applistCategory.removePreference(pref)
            }
        }
    }

    override fun onPackageChanged(packageName: String, uid: Int) {
        lifecycleScope.launch { populateRadioPreferences() }
    }

    override fun onRadioButtonClicked(emiter: SelectorWithWidgetPreference) {
        if (emiter !is RadioButtonPreference) return

        val key = emiter.key

        prefs.setAction(_context, openAppValue)
        prefs.setLaunchActionApp(_context, key)
        prefs.setLaunchActionAppShortcut(_context, key)

        updateState()
    }

    private fun queryForShortcuts(): ArrayList<ShortcutInfo?> {
        val query = LauncherApps.ShortcutQuery()
        query.setQueryFlags(FLAG_MATCH_DYNAMIC or FLAG_MATCH_MANIFEST)
        return try {
            ArrayList(launcherApps.getShortcuts(query, UserHandle.of(currentUser)))
        } catch (e: Exception) {
            dlog(TAG, "Failed to query shortcuts. ${e}")
            arrayListOf<ShortcutInfo?>()
        }
    }

    private fun updateIntro() {
        prefLaunchAppSummary?.setTitle(
            if (prefs.getEnabled(_context)) {
                R.string.setting_app_selection_help_text
            } else {
                R.string.setting_app_selection_help_text_disabled
            }
        )
    }

    private fun updateState() {
        val applistCategory = applistCategory ?: return

        val preferenceCount = applistCategory.preferenceCount
        if (preferenceCount == 0) {
            return
        }

        var currentApp = prefs.getLaunchActionApp(_context)
        for (i in 0 until preferenceCount) {
            val pref = applistCategory.getPreference(i)
            if (pref is RadioButtonPreference) {
                pref.setEnabled(prefs.getEnabled(_context))
                pref.setChecked(currentApp == pref.key)
            }
        }
    }

    private data class AppPreferenceData(
        val key: String,
        val componentName: android.content.ComponentName,
        val label: CharSequence,
        val icon: Drawable?,
        val shortcuts: ArrayList<ShortcutInfo?>,
    )

    private suspend fun populateRadioPreferences(iconRefreshPackageName: String? = null) {
        val applistCategory = applistCategory ?: return

        val cacheManager: AppIconCacheManager = AppIconCacheManager.getInstance()

        val appDataList =
            withContext(Dispatchers.IO) {
                val activityLists =
                    launcherApps.getActivityList(null, UserHandle.of(currentUser)).sortedBy {
                        it.label.toString()
                    }
                val shortcutLists = queryForShortcuts()
                val shortcutsByPackage = shortcutLists.groupBy { it?.getPackage() }

                activityLists.map { activity ->
                    val packageName = activity.componentName.packageName
                    val uid = activity.applicationInfo.uid
                    var packageIcon = cacheManager.get(packageName, uid)

                    if (packageIcon == null || iconRefreshPackageName == packageName) {
                        packageIcon = activity.getIcon(DisplayMetrics.DENSITY_DEVICE_STABLE)
                        cacheManager.put(packageName, uid, packageIcon)
                    }

                    val shortcuts =
                        (shortcutsByPackage[packageName] ?: emptyList()).let { ArrayList(it) }
                    AppPreferenceData(
                        key = activity.componentName.flattenToString(),
                        componentName = activity.componentName,
                        label = activity.label,
                        icon = packageIcon,
                        shortcuts = shortcuts,
                    )
                }
            }

        withContext(Dispatchers.Main) {
            val existingPreferences = mutableMapOf<String, RadioButtonPreference>()
            for (i in 0 until applistCategory.preferenceCount) {
                val pref = applistCategory.getPreference(i)
                if (pref is RadioButtonPreference) {
                    existingPreferences[pref.key] = pref
                }
            }

            val preferencesToRemove = existingPreferences.keys.toMutableSet()

            for (appData in appDataList) {
                preferencesToRemove.remove(appData.key)

                val radioPref =
                    existingPreferences[appData.key]
                        ?: RadioButtonPreference(_context).also {
                            it.apply {
                                setKey(appData.key)
                                setOnClickListener(this@LaunchSettingsFragment)
                                isPersistent = false
                                applistCategory.addPreference(it)
                            }
                        }

                radioPref.apply {
                    setTitle(appData.label)
                    setIcon(appData.icon)

                    val args =
                        Bundle().apply {
                            putParcelable(launchAppKey, appData.componentName)
                            putParcelableArrayList(launchAppShortcutKey, appData.shortcuts)
                        }
                    val onClickListener: View.OnClickListener? =
                        if (appData.shortcuts.isEmpty()) {
                            null
                        } else {
                            View.OnClickListener {
                                val fragment = LaunchAppShortcutSettingsFragment()
                                fragment.arguments = args
                                requireActivity()
                                    .supportFragmentManager
                                    .beginTransaction()
                                    .setReorderingAllowed(true)
                                    .replace(
                                        com.android.settingslib.collapsingtoolbar.R.id
                                            .content_frame,
                                        fragment,
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
                    applistCategory.removePreference(pref)
                }
            }

            updateState()
        }
    }
}
