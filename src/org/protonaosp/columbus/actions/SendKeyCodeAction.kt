/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent

open class SendKeyCodeAction(context: Context, val keyCode: Int) : Action(context) {
    val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager

    private fun triggerVirtualKeypress() {
        if (im == null) return

        val now: Long = SystemClock.uptimeMillis()
        val downEvent: KeyEvent =
            KeyEvent(
                now,
                now,
                KeyEvent.ACTION_DOWN,
                keyCode,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD,
            )
        val upEvent: KeyEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP)

        im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
        im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
    }

    override fun run() {
        triggerVirtualKeypress()
    }
}
