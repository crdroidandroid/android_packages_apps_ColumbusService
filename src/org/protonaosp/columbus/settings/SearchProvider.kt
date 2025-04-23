/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.settings

import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.SearchIndexablesContract.*
import android.provider.SearchIndexablesProvider
import org.protonaosp.columbus.R

class SearchProvider : SearchIndexablesProvider() {
    override fun onCreate() = true

    override fun queryXmlResources(projection: Array<String>?) =
        MatrixCursor(INDEXABLES_XML_RES_COLUMNS)

    override fun queryNonIndexableKeys(projection: Array<String>?) =
        MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS)

    override fun queryRawData(projection: Array<String>?): Cursor {
        val ref = Array<Any?>(INDEXABLES_RAW_COLUMNS.size) { null }
        ref[COLUMN_INDEX_RAW_KEY] = requireContext().getString(R.string.settings_entry_title)
        ref[COLUMN_INDEX_RAW_TITLE] = requireContext().getString(R.string.settings_entry_title)
        ref[COLUMN_INDEX_RAW_SUMMARY_ON] =
            requireContext().getString(R.string.setting_footer_content)
        ref[COLUMN_INDEX_RAW_KEYWORDS] =
            requireContext().getString(R.string.settings_search_keywords)

        // For breadcrumb generation
        ref[COLUMN_INDEX_RAW_SCREEN_TITLE] =
            requireContext().getString(R.string.settings_entry_title)
        ref[COLUMN_INDEX_RAW_CLASS_NAME] = SettingsActivity::class.java.name

        ref[COLUMN_INDEX_RAW_INTENT_ACTION] = Intent.ACTION_MAIN
        ref[COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE] = requireContext().applicationInfo.packageName
        ref[COLUMN_INDEX_RAW_INTENT_TARGET_CLASS] = SettingsActivity::class.java.name

        return MatrixCursor(INDEXABLES_RAW_COLUMNS).apply { addRow(ref) }
    }
}
