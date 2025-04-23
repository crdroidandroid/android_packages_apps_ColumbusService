/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler

class ChargingState(context: Context, handler: Handler) : TransientGate(context, handler) {
    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                blockForMillis(GATE_DURATION)
            }
        }

    override fun onActivate() {
        val intentFilter: IntentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
        context.registerReceiver(powerReceiver, intentFilter)
    }

    override fun onDeactivate() {
        context.unregisterReceiver(powerReceiver)
        setBlocking(false)
    }
}
