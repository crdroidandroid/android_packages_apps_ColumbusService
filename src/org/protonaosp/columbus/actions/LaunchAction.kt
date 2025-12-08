/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.IShortcutService
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager.FLAG_MATCH_DYNAMIC
import android.content.pm.ShortcutManager.FLAG_MATCH_MANIFEST
import android.os.ServiceManager
import android.os.UserHandle
import android.view.WindowManager.LayoutParams
import org.protonaosp.columbus.KEY_SOURCE_QUICK_TAP
import org.protonaosp.columbus.PackageStateManager
import org.protonaosp.columbus.R
import org.protonaosp.columbus.TAG
import org.protonaosp.columbus.dlog
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getLaunchActionApp
import org.protonaosp.columbus.getLaunchActionAppShortcut

class LaunchAction(context: Context) :
    Action(context),
    SharedPreferences.OnSharedPreferenceChangeListener,
    PackageStateManager.PackageStateListener {

    override fun canRun() = isShortcutValid && isDeviceInteractiveAndUnlocked(context)

    override fun canRunWhenScreenOff() = false

    private val launcherApps: LauncherApps?
    private val shortcutService: IShortcutService?
    private val prefs: SharedPreferences
    private var currentUser: Int = -1
    private var isShortcutValid: Boolean = true
    private val pm: PackageManager
    private val shortcutMap = mutableMapOf<String, List<ShortcutInfo>>()
    private val launchAppKey: String
    private val launchAppShortcutKey: String

    init {
        launchAppKey = context.getString(R.string.pref_key_launch_app)
        launchAppShortcutKey = context.getString(R.string.pref_key_launch_app_shortcut)
        pm = context.packageManager
        launcherApps = context.getSystemService(LauncherApps::class.java)
        shortcutService = getShortcutService()
        prefs = context.getDePrefs()
        prefs.registerOnSharedPreferenceChangeListener(this)
        currentUser = ActivityManager.getCurrentUser()
        populateShortcuts()
        PackageStateManager.registerListener(this)
        updateShortcutValidity()
    }

    private fun populateShortcuts() {
        shortcutMap.clear()

        val app: String = prefs.getLaunchActionApp(context) ?: return
        val component: ComponentName = ComponentName.unflattenFromString(app) ?: return
        val packageName: String = component.packageName

        val allShortcuts = queryForShortcuts(packageName)
        shortcutMap.putAll(allShortcuts.groupBy { it.getPackage() })
    }

    private fun getShortcutService(): IShortcutService {
        return IShortcutService.Stub.asInterface(
            ServiceManager.getService(Context.SHORTCUT_SERVICE)
        )
    }

    private fun queryForShortcuts(packageName: String?): List<ShortcutInfo> {
        if (packageName == null) return emptyList()
        return try {
            val rawlist =
                shortcutService
                    ?.getShortcuts(
                        packageName,
                        FLAG_MATCH_DYNAMIC or FLAG_MATCH_MANIFEST,
                        currentUser,
                    )
                    ?.getList() as? java.util.List<ShortcutInfo>
            rawlist?.toList() ?: emptyList()
        } catch (e: Exception) {
            dlog(TAG, "Failed to query shortcuts. ${e}")
            emptyList()
        }
    }

    private fun checkShortcutValidity(shortcut: String?): Boolean {
        val app: String = prefs.getLaunchActionApp(context) ?: return false
        if (shortcut == null) return false
        if (app == shortcut) return true

        val pkgName = ComponentName.unflattenFromString(app)?.packageName ?: return false
        val shortcuts = shortcutMap.getOrPut(pkgName) { queryForShortcuts(pkgName) } ?: return false

        return shortcuts.any { it.id == shortcut }
    }

    private fun isInstalledAndEnabled(pkgName: String?): Boolean {
        if (pkgName == null) return false
        return try {
            pm.getApplicationInfo(pkgName, ApplicationInfoFlags.of(0))?.enabled == true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun onPackageAdded(packageName: String, uid: Int) {
        val shortcuts = queryForShortcuts(packageName)
        if (shortcuts.isNotEmpty()) {
            shortcutMap[packageName] = shortcuts
        }
        onPackageChanged(packageName, uid)
    }

    override fun onPackageRemoved(packageName: String, uid: Int) {
        shortcutMap.remove(packageName)

        val app: String = prefs.getLaunchActionApp(context) ?: return
        val pkgName: String = ComponentName.unflattenFromString(app)?.packageName ?: return

        if (pkgName == packageName) {
            isShortcutValid = false
        }
    }

    override fun onPackageChanged(packageName: String, uid: Int) {
        val shortcuts = queryForShortcuts(packageName)
        if (shortcuts.isNotEmpty()) {
            shortcutMap[packageName] = shortcuts
        } else {
            shortcutMap.remove(packageName)
        }

        updateShortcutValidity(packageName)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == launchAppKey || key == launchAppShortcutKey) {
            updateShortcutValidity()
        }
    }

    private fun updateShortcutValidity(pkgNameFilter: String? = null) {
        val app: String? = prefs.getLaunchActionApp(context)
        val pkgName: String? =
            if (app != null) {
                ComponentName.unflattenFromString(app)?.packageName
            } else {
                null
            }

        if (pkgName == null) {
            isShortcutValid = false
            return
        }

        if (pkgNameFilter != null && pkgName != pkgNameFilter) {
            return
        }

        val shortcut: String? = prefs.getLaunchActionAppShortcut(context)

        isShortcutValid = isInstalledAndEnabled(pkgName) && checkShortcutValidity(shortcut)
    }

    private fun launchApp() {
        val app: String = prefs.getLaunchActionApp(context) ?: return
        val component: ComponentName = ComponentName.unflattenFromString(app) ?: return
        val packageName: String = component.packageName
        val shortcut: String = prefs.getLaunchActionAppShortcut(context) ?: app
        val currentUserHandle = UserHandle.of(currentUser)

        val options =
            ActivityOptions.makeBasic()
                .apply {
                    setDisallowEnterPictureInPictureWhileLaunching(true)
                    setRotationAnimationHint(LayoutParams.ROTATION_ANIMATION_JUMPCUT)
                    setPendingIntentLaunchFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                            Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                    )
                }
                .toBundle()

        if (app == shortcut) {
            val intent =
                launcherApps
                    ?.getMainActivityLaunchIntent(component, null, currentUserHandle)
                    ?.intent ?: return
            intent.putExtra(KEY_SOURCE_QUICK_TAP, true)
            try {
                context.startActivityAsUser(intent, options, currentUserHandle)
            } catch (e: Exception) {
                dlog(TAG, "Failed to launch main activity for app ${packageName}. ${e}")
            }
            return
        }

        val shortcuts = shortcutMap[packageName]
        val shortcutInfo = shortcuts?.firstOrNull { it.id == shortcut }

        if (shortcutInfo != null) {
            try {
                val shortcutIntent = shortcutInfo.intent
                if (shortcutIntent == null) {
                    dlog(TAG, "Shortcut has no intent for app ${packageName}")
                    return
                }
                shortcutIntent.putExtra(KEY_SOURCE_QUICK_TAP, true)
                context.startActivityAsUser(shortcutIntent, options, currentUserHandle)
            } catch (e: Exception) {
                dlog(TAG, "Failed to start shortcut intent for app ${packageName}. ${e}")
            }
        } else {
            dlog(TAG, "Shortcut not found for app ${packageName} and shortcut id ${shortcut}")
        }
    }

    override fun run() {
        launchApp()
    }

    override fun destroy() {
        shortcutMap.clear()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        PackageStateManager.unregisterListener(this)
    }
}
