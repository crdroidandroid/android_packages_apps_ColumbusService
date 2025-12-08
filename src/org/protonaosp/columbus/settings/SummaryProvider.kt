/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.android.settingslib.drawer.TileUtils.META_DATA_PREFERENCE_SUMMARY
import org.protonaosp.columbus.R
import org.protonaosp.columbus.getActionName
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getEnabled

class SummaryProvider : ContentProvider() {
    private var prefs: SharedPreferences? = null

    override fun onCreate(): Boolean {
        prefs = requireContext().getDePrefs()
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val prefs = prefs ?: return null
        val bundle = Bundle()
        val summary =
            when (method) {
                "entry" ->
                    if (prefs.getEnabled(requireContext())) {
                        requireContext()
                            .getString(
                                R.string.settings_entry_summary_on,
                                prefs.getActionName(requireContext()),
                            )
                    } else {
                        requireContext().getString(R.string.settings_entry_summary_off)
                    }
                else -> throw IllegalArgumentException("Unknown method: $method")
            }

        bundle.putString(META_DATA_PREFERENCE_SUMMARY, summary)
        return bundle
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        throw UnsupportedOperationException()
    }

    override fun getType(uri: Uri): String {
        throw UnsupportedOperationException()
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        throw UnsupportedOperationException()
    }
}
