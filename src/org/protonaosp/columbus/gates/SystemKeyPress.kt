/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.KeyEvent
import com.android.systemui.shared.system.InputChannelCompat
import com.android.systemui.shared.system.InputMonitorCompat
import org.protonaosp.columbus.TAG

class SystemKeyPress(context: Context, handler: Handler) : Gate(context, handler, 2) {
    private val monitorName = "$TAG/SystemKeyPress"
    private val clearBlocking = Runnable { setBlocking(false) }
    private val blockingKeys =
        setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER)

    private var inputEventReceiver: InputChannelCompat.InputEventReceiver? = null
    private var inputMonitor: InputMonitorCompat? = null
    private val inputEventListener =
        object : InputChannelCompat.InputEventListener {
            override fun onInputEvent(ev: InputEvent) {
                if (ev !is KeyEvent) {
                    return
                }
                val keyEvent: KeyEvent = ev as? KeyEvent ?: return
                if (!isBlockingKeys(keyEvent)) return

                when (keyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        setBlocking(true)
                    }
                    KeyEvent.ACTION_UP -> {
                        handler.removeCallbacks(clearBlocking)
                        handler.postDelayed(clearBlocking, duration)
                    }
                }
            }
        }

    private fun dispose() {
        inputEventReceiver?.dispose()
        inputEventReceiver = null
        inputMonitor?.dispose()
        inputMonitor = null
    }

    private fun isBlockingKeys(keyEvent: KeyEvent): Boolean {
        return blockingKeys.contains(keyEvent.keyCode)
    }

    fun startListeningForKeyPress() {
        if (inputEventReceiver != null) return
        inputMonitor = InputMonitorCompat(monitorName, 0)
        inputEventReceiver =
            inputMonitor!!.getInputReceiver(
                Looper.getMainLooper(),
                Choreographer.getInstance(),
                inputEventListener,
            )
    }

    fun stopListeningForKeyPress() {
        setBlocking(false)
        dispose()
    }

    override fun onActivate() {
        startListeningForKeyPress()
        setBlocking(false)
    }

    override fun onDeactivate() {
        stopListeningForKeyPress()
    }
}
