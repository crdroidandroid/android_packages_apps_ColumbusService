/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.Context
import android.os.Handler
import android.os.Vibrator
import android.os.VibratorManager

class Haptic(context: Context, handler: Handler) : Gate(context, handler, 2) {
    private var vibrating: Boolean = false
    private val clearBlocking = Runnable { setBlocking(false) }
    private val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
    private val vibratorEventListener =
        object : Vibrator.OnVibratorStateChangedListener {
            override fun onVibratorStateChanged(isVibrating: Boolean) {
                if (vibrating == isVibrating) {
                    return
                }
                vibrating = isVibrating
                if (isVibrating) {
                    setBlocking(true)
                } else {
                    handler.removeCallbacks(clearBlocking)
                    handler.postDelayed(clearBlocking, duration)
                }
            }
        }

    override fun onActivate() {
        vm?.defaultVibrator?.addVibratorStateListener(context.mainExecutor, vibratorEventListener)
    }

    override fun onDeactivate() {
        vm?.defaultVibrator?.removeVibratorStateListener(vibratorEventListener)
        setBlocking(false)
    }
}
