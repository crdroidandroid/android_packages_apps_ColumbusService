/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.CALL_STATE_RINGING

class SilenceCallAction(context: Context) : Action(context) {

    override fun canRun() = isPhoneRinging

    override fun canRunWhenScreenOff() = false

    private var isPhoneRinging: Boolean = false
    private var telecomManager: TelecomManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener =
        object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                isPhoneRinging = state == CALL_STATE_RINGING
            }
        }

    init {
        telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun run() {
        telecomManager?.silenceRinger()
    }

    override fun destroy() {
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        telephonyManager = null
    }
}
