/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.app.contextualsearch.ContextualSearchManager
import android.content.Context

class ContextualSearchAction(context: Context) : Action(context) {
    private val contextualSearchManager =
        context.getSystemService(Context.CONTEXTUAL_SEARCH_SERVICE) as? ContextualSearchManager

    override fun canRun() = contextualSearchManager != null

    override fun canRunWhenScreenOff() = false

    override fun run() {
        contextualSearchManager?.startContextualSearch(
            ContextualSearchManager.ENTRYPOINT_SYSTEM_ACTION
        )
    }
}
