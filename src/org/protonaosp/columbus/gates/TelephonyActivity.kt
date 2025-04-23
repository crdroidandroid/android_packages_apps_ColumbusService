/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.Context
import android.os.Handler
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import java.util.concurrent.Executor

class TelephonyActivity(context: Context, handler: Handler) : Gate(context, handler, 2) {
    private var callBlocked: Boolean = false

    private val telephonyManager: TelephonyManager? =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager: TelecomManager? =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    inner class PhoneCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            callBlocked = telecomManager?.let { it.isInCall() } ?: false
            updateBlocking()
        }
    }

    private val phoneCallback: PhoneCallback = PhoneCallback()
    private val executor: Executor = context.mainExecutor

    fun updateBlocking() {
        setBlocking(callBlocked)
    }

    override fun onActivate() {
        telephonyManager?.let { tm ->
            callBlocked = telecomManager?.let { it.isInCall() } ?: false

            tm.registerTelephonyCallback(executor, phoneCallback)
        }
        updateBlocking()
    }

    override fun onDeactivate() {
        setBlocking(false)
        telephonyManager?.unregisterTelephonyCallback(phoneCallback)
    }
}
