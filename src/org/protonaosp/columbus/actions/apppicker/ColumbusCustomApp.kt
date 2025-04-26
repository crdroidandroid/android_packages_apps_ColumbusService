/*
 * SPDX-FileCopyrightText: The Dirty Unicorns Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions.apppicker

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.view.View
import android.widget.ListView
import org.protonaosp.columbus.R
import org.protonaosp.columbus.getDePrefs

class ColumbusCustomApp : AppPicker() {

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        if (!mIsActivitiesList) {
            // we are in the Apps list
            val packageName = applist!![position].packageName
            val friendlyAppString = applist!![position].loadLabel(pm).toString()
            setPackage(packageName, friendlyAppString)
            setPackageActivity(null)
        } else if (mIsActivitiesList) {
            // we are in the Activities list
            setPackageActivity(mActivitiesList!![position])
        }

        mIsActivitiesList = false
        finish()
    }

    override fun onLongClick(position: Int) {
        if (mIsActivitiesList) return
        val packageName = applist!![position].packageName
        val friendlyAppString = applist!![position].loadLabel(pm).toString()
        // always set xxx_SQUEEZE_CUSTOM_APP so we can fallback if something goes wrong with
        // pm.getPackageInfo
        setPackage(packageName, friendlyAppString)
        setPackageActivity(null)
        showActivitiesDialog(packageName)
    }

    protected fun setPackage(packageName: String, friendlyAppString: String) {
        val prefs: SharedPreferences = this.getDePrefs()

        prefs.edit().putString(getString(R.string.pref_key_launch_app), packageName).apply()

        prefs
            .edit()
            .putString(getString(R.string.pref_key_launch_app_name), friendlyAppString)
            .apply()
    }

    protected fun setPackageActivity(ai: ActivityInfo?) {
        val prefs: SharedPreferences = this.getDePrefs()

        prefs
            .edit()
            .putString(
                getString(R.string.pref_key_launch_app_shortcut),
                ai?.name ?: getString(R.string.launch_app_default),
            )
            .apply()
    }
}
