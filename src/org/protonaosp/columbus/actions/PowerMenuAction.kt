/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManagerGlobal

class PowerMenuAction(context: Context) : Action(context) {
    val wm = WindowManagerGlobal.getWindowManagerService()
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    override fun run() {
        if (pm == null || wm == null) return

        if (!pm.isInteractive) {
            pm.wakeUp(
                SystemClock.uptimeMillis(),
                PowerManager.WAKE_REASON_GESTURE,
                "org.protonaosp.columbus:GESTURE",
            )
        }

        wm.showGlobalActions()
    }
}
