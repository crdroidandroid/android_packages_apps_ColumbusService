/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.os.Bundle
import android.view.MenuItem
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import org.protonaosp.columbus.R
import org.protonaosp.columbus.utils.AppIconCacheManager

class SettingsActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    SettingsFragment(),
                )
                .commit()
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
