/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.app.ActivityManager
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.util.DisplayMetrics
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.protonaosp.columbus.R
import org.protonaosp.columbus.TAG
import org.protonaosp.columbus.dlog
import org.protonaosp.columbus.utils.AppIconCacheManager

class SettingsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch { preloadAppIcons() }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    SettingsFragment(),
                )
                .commit()
        }
    }

    private suspend fun preloadAppIcons() {
        val launcherApps = getSystemService(LauncherApps::class.java)
        val userHandle = UserHandle.of(ActivityManager.getCurrentUser())

        withContext(Dispatchers.IO) {
            val cacheManager = AppIconCacheManager.getInstance()
            val activityLists = launcherApps.getActivityList(null, userHandle)

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

    override fun onDestroy() {
        AppIconCacheManager.getInstance().release()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
