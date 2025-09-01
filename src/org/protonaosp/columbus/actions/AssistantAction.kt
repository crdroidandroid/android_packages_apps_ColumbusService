/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import com.android.internal.statusbar.IStatusBarService
import org.protonaosp.columbus.TAG
import org.protonaosp.columbus.dlog

class AssistantAction(context: Context) : Action(context) {
    val service =
        IStatusBarService.Stub.asInterface(ServiceManager.getService(Context.STATUS_BAR_SERVICE))

    override fun run() {
        try {
            service.startAssist(Bundle())
        } catch (e: Exception) {
            dlog(TAG, "Failed to start assistant: unexpected error ${e}")
        }
    }
}
