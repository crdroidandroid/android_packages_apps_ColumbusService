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
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.protonaosp.columbus.PREFS_NAME
import org.protonaosp.columbus.PackageStateManager
import org.protonaosp.columbus.R
import org.protonaosp.columbus.TAG
import org.protonaosp.columbus.dlog
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getLaunchActionApp
import org.protonaosp.columbus.setAction
import org.protonaosp.columbus.setLaunchActionApp
import org.protonaosp.columbus.setLaunchActionAppShortcut
import org.protonaosp.columbus.widget.RadioButtonPreference

class LaunchSettingsFragment :
    SettingsBasePreferenceFragment(),
    SelectorWithWidgetPreference.OnClickListener,
    PackageStateManager.PackageStateListener {

    private var currentUser: Int = -1
    private val _context by lazy { requireContext() }
    private lateinit var prefs: SharedPreferences
    private lateinit var launcherApps: LauncherApps
    private lateinit var openAppValue: String
    private lateinit var launchAppKey: String
    private lateinit var launchAppShortcutKey: String
    private var applistCategory: PreferenceCategory? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.launch_settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = PREFS_NAME

        prefs = _context.getDePrefs()
        currentUser = ActivityManager.getCurrentUser()
        launcherApps = _context.getSystemService(LauncherApps::class.java)
        openAppValue = _context.getString(R.string.action_launch_value)
        launchAppKey = _context.getString(R.string.pref_key_launch_app)
        launchAppShortcutKey = _context.getString(R.string.pref_key_launch_app_shortcut)
        applistCategory =
            preferenceScreen.findPreference<PreferenceCategory>(
                getString(R.string.categ_key_app_list)
            )
        lifecycleScope.launch { populateRadioPreferences() }
        PackageStateManager.registerListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PackageStateManager.unregisterListener(this)
    }

    override fun onPackageAdded(packageName: String) {
        lifecycleScope.launch { populateRadioPreferences() }
    }

    override fun onPackageRemoved(packageName: String) {
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

    override fun onPackageChanged(packageName: String) {
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
                pref.setChecked(currentApp == pref.key)
            }
        }
    }

    private suspend fun populateRadioPreferences() {
        val applistCategory = applistCategory ?: return

        val activityLists =
            withContext(Dispatchers.IO) {
                launcherApps.getActivityList(null, UserHandle.of(currentUser)).sortedBy {
                    it.label.toString()
                }
            }
        val shortcutLists = withContext(Dispatchers.IO) { queryForShortcuts() }

        withContext(Dispatchers.Main) {
            val existingPreferences = mutableMapOf<String, RadioButtonPreference>()
            for (i in 0 until applistCategory.preferenceCount) {
                val pref = applistCategory.getPreference(i)
                if (pref is RadioButtonPreference) {
                    existingPreferences[pref.key] = pref
                }
            }

            val preferencesToRemove = existingPreferences.keys.toMutableSet()

            for (activity in activityLists) {
                val key = activity.componentName.flattenToString()
                preferencesToRemove.remove(key)

                val radioPref =
                    existingPreferences[key]
                        ?: RadioButtonPreference(_context).also {
                            it.apply {
                                setKey(key)
                                setOnClickListener(this@LaunchSettingsFragment)
                                applistCategory.addPreference(it)
                            }
                        }

                radioPref.apply {
                    setTitle(activity.label)
                    setIcon(activity.getIcon(DisplayMetrics.DENSITY_DEVICE_STABLE))

                    val shortcuts =
                        shortcutLists.filter {
                            it?.getPackage() == activity.componentName.packageName
                        }
                    val args =
                        Bundle().apply {
                            putParcelable(launchAppKey, activity.componentName)
                            putParcelableArrayList(launchAppShortcutKey, ArrayList(shortcuts))
                        }
                    val onClickListener: View.OnClickListener? =
                        if (shortcuts.isEmpty()) {
                            null
                        } else {
                            object : View.OnClickListener {
                                override fun onClick(view: View) {
                                    val fragment = LaunchAppShortcutSettingsFragment()
                                    fragment.arguments = args
                                    requireActivity()
                                        .supportFragmentManager
                                        .beginTransaction()
                                        .replace(
                                            com.android.settingslib.collapsingtoolbar.R.id
                                                .content_frame,
                                            fragment,
                                        )
                                        .addToBackStack(null)
                                        .commit()
                                }
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

    private fun makeRadioPreference(
        category: PreferenceCategory,
        key: String,
        title: CharSequence?,
        icon: Drawable?,
        onClickListener: View.OnClickListener?,
    ): RadioButtonPreference {
        val radioPref = RadioButtonPreference(_context)
        radioPref.apply {
            setKey(key)
            setTitle(title)
            setIcon(icon)
            setOnClickListener(this@LaunchSettingsFragment)
            setExtraWidgetOnClickListener(onClickListener)
            category.addPreference(this)
        }
        return radioPref
    }
}
