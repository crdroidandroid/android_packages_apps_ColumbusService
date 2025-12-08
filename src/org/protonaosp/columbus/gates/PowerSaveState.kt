/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.PowerManager
import android.os.PowerManager.ServiceType

class PowerSaveState(context: Context, handler: Handler) : Gate(context, handler, 2) {
    private var batterySaverEnabled: Boolean = false
    private var isDeviceInteractive: Boolean = false
    private val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                refreshStatus()
            }
        }

    fun refreshStatus() {
        if (pm == null) return
        val state = pm.getPowerSaveState(PowerManager.ServiceType.OPTIONAL_SENSORS)
        batterySaverEnabled = state?.batterySaverEnabled ?: false

        isDeviceInteractive = pm.isInteractive

        setBlocking(batterySaverEnabled && !isDeviceInteractive)
    }

    override fun onActivate() {
        val intentFilter =
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        context.registerReceiver(powerReceiver, intentFilter)
        refreshStatus()
    }

    override fun onDeactivate() {
        context.unregisterReceiver(powerReceiver)
        setBlocking(false)
    }
}
