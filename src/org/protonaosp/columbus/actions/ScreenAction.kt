/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock

class ScreenAction(context: Context) : Action(context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    override fun run() {
        if (pm == null) return
        if (pm.isInteractive) {
            pm.goToSleep(
                SystemClock.uptimeMillis(),
                PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON,
                0,
            )
        } else {
            pm.wakeUp(
                SystemClock.uptimeMillis(),
                PowerManager.WAKE_REASON_GESTURE,
                "org.protonaosp.columbus:GESTURE",
            )
        }
    }
}
