/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.content.SharedPreferences
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.protonaosp.columbus.R
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getEnabled

class ToggleTileService : TileService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: SharedPreferences

    override fun onStartListening() {
        prefs = getDePrefs()
        prefs.registerOnSharedPreferenceChangeListener(this)
        update()
    }

    override fun onStopListening() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun update(state: Boolean? = null) {
        qsTile.state =
            if (state ?: prefs.getEnabled(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        val newState = !prefs.getEnabled(this)
        prefs.edit().putBoolean(getString(R.string.pref_key_enabled), newState).commit()
        update(newState)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key != null && key == getString(R.string.pref_key_enabled)) {
            update()
        }
    }
}
