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
import android.os.Looper
import android.os.PowerManager
import android.view.Choreographer
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.MotionEvent
import com.android.systemui.shared.system.InputChannelCompat
import com.android.systemui.shared.system.InputMonitorCompat
import org.protonaosp.columbus.TAG

class ScreenTouch(context: Context, val handler: Handler) : Gate(context, handler, 2) {
    private val clearBlocking = Runnable { setBlocking(false) }
    private val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshStatus()
            }
        }
    private var inputEventReceiver: InputChannelCompat.InputEventReceiver? = null
    private var inputMonitor: InputMonitorCompat? = null
    private val inputEventListener =
        object : InputChannelCompat.InputEventListener {
            override fun onInputEvent(ev: InputEvent) {
                if (ev !is MotionEvent) {
                    return
                }
                val motionEvent: MotionEvent = ev as? MotionEvent ?: return
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        setBlocking(true)
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_OUTSIDE -> {
                        handler.removeCallbacks(clearBlocking)
                        handler.postDelayed(clearBlocking, duration)
                    }
                }
            }
        }

    private fun dispose() {
        if (inputEventReceiver != null) {
            inputEventReceiver!!.dispose()
        }
        if (inputMonitor != null) {
            inputMonitor!!.dispose()
        }
    }

    fun startListeningForTouch() {
        if (inputEventReceiver != null) return
        inputMonitor = InputMonitorCompat(TAG, 0)
        inputEventReceiver =
            inputMonitor!!.getInputReceiver(
                Looper.getMainLooper(),
                Choreographer.getInstance(),
                inputEventListener,
            )
    }

    fun stopListeningForTouch() {
        setBlocking(false)
        dispose()
        inputEventReceiver = null
        inputMonitor = null
    }

    fun refreshStatus() {
        if (pm?.isInteractive == true) {
            startListeningForTouch()
        } else {
            stopListeningForTouch()
        }
    }

    override fun onActivate() {
        val filter: IntentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        context.registerReceiver(powerReceiver, filter)
        refreshStatus()
        setBlocking(false)
    }

    override fun onDeactivate() {
        stopListeningForTouch()
        context.unregisterReceiver(powerReceiver)
    }
}
