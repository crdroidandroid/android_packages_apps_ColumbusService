/*
 * SPDX-FileCopyrightText: The Dirty Unicorns Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions.apppicker;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.widget.ListView;

import org.protonaosp.columbus.PreferencesKt;
import org.protonaosp.columbus.R;

public class ColumbusCustomApp extends AppPicker {

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (!mIsActivitiesList) {
            // we are in the Apps list
            String packageName = applist.get(position).packageName;
            String friendlyAppString = (String) applist.get(position).loadLabel(packageManager);
            setPackage(packageName, friendlyAppString);
            setPackageActivity(null);
        } else if (mIsActivitiesList) {
            // we are in the Activities list
            setPackageActivity(mActivitiesList.get(position));
        }

        mIsActivitiesList = false;
        finish();
    }

    @Override
    protected void onLongClick(int position) {
        if (mIsActivitiesList) return;
        String packageName = applist.get(position).packageName;
        String friendlyAppString = (String) applist.get(position).loadLabel(packageManager);
        // always set xxx_SQUEEZE_CUSTOM_APP so we can fallback if something goes wrong with
        // packageManager.getPackageInfo
        setPackage(packageName, friendlyAppString);
        setPackageActivity(null);
        showActivitiesDialog(packageName);
    }

    protected void setPackage(String packageName, String friendlyAppString) {
        final SharedPreferences prefs = PreferencesKt.getDePrefs(this);

        prefs.edit().putString(getString(R.string.pref_key_launch_app), packageName).commit();
        prefs.edit()
                .putString(getString(R.string.pref_key_launch_app_name), friendlyAppString)
                .commit();
    }

    protected void setPackageActivity(ActivityInfo ai) {
        final SharedPreferences prefs = PreferencesKt.getDePrefs(this);

        prefs.edit()
                .putString(
                        getString(R.string.pref_key_launch_app_shortcut),
                        ai != null ? ai.name : getString(R.string.launch_app_default))
                .commit();
    }
}
