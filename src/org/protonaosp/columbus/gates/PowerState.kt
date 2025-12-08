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

class PowerState(context: Context, handler: Handler) : TransientGate(context, handler) {
    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    blockForMillis(duration)
                } else {
                    setBlocking(false)
                }
            }
        }

    override fun onActivate() {
        val intentFilter =
            IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        context.registerReceiver(powerReceiver, intentFilter)
    }

    override fun onDeactivate() {
        context.unregisterReceiver(powerReceiver)
        setBlocking(false)
    }
}
