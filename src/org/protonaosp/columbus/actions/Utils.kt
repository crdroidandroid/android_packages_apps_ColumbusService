/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

fun isDeviceInteractiveAndUnlocked(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    return pm?.isInteractive == true && km?.isDeviceLocked == false
}
