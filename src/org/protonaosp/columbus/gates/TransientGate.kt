/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.Context
import android.os.Handler

abstract class TransientGate(context: Context, handler: Handler) : Gate(context, handler, 2) {
    private val resetGate =
        object : Runnable {
            override fun run() {
                setBlocking(false)
            }
        }
    private val resetGateHandler: Handler = handler

    fun blockForMillis(millis: Long) {
        resetGateHandler.removeCallbacks(resetGate)
        setBlocking(true)
        resetGateHandler.postDelayed(resetGate, millis)
    }
}
