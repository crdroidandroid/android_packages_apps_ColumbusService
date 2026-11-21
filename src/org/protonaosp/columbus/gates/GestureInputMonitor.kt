/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.hardware.input.InputManagerGlobal
import android.os.Looper
import android.view.BatchedInputEventReceiver
import android.view.Choreographer
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.InputMonitor

class GestureInputMonitor(name: String, displayId: Int) {
    private val inputMonitor: InputMonitor =
        InputManagerGlobal.getInstance().monitorGestureInput(name, displayId)

    fun interface InputEventListener {
        fun onInputEvent(ev: InputEvent)
    }

    fun getInputReceiver(
        looper: Looper,
        choreographer: Choreographer,
        listener: InputEventListener,
    ): InputEventReceiver =
        object : BatchedInputEventReceiver(inputMonitor.inputChannel, looper, choreographer) {
            override fun onInputEvent(event: InputEvent) {
                listener.onInputEvent(event)
                finishInputEvent(event, true)
            }
        }

    fun dispose() {
        inputMonitor.dispose()
    }
}
