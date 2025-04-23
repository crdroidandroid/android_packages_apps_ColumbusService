/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Handler

class UsbState(context: Context, handler: Handler) : TransientGate(context, handler) {
    private var usbConnected: Boolean = false

    private val usbReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val connected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)

                if (connected != usbConnected) {
                    usbConnected = connected
                    if (connected) {
                        blockForMillis(GATE_DURATION)
                    } else {
                        setBlocking(false)
                    }
                }
            }
        }

    override fun onActivate() {
        context.registerReceiver(usbReceiver, IntentFilter(UsbManager.ACTION_USB_STATE))
    }

    override fun onDeactivate() {
        context.unregisterReceiver(usbReceiver)
        setBlocking(false)
    }
}
