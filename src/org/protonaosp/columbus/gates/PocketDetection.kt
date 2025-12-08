/*
 * SPDX-FileCopyrightText: Kieron Quinn
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventCallback
import android.hardware.SensorManager
import android.os.Handler
import android.os.PowerManager
import org.protonaosp.columbus.dlog

/*
   This one is slightly odd as a sensor listener doesn't stay running in the background to allow for asynchronous listening.
   We therefore use a little bit of a hacky way of detecting - attach a listener on a background thread and then immediately block the main thread waiting for the event (which is almost instantaneous)
*/

class PocketDetection(context: Context, val handler: Handler) : Gate(context, handler, 2) {

    private val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshStatus()
            }
        }
    private var wasBlocked = false
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val proximityMax: Float? = proximitySensor?.maximumRange
    private val sensorListener =
        object : SensorEventCallback() {
            override fun onSensorChanged(event: SensorEvent?) {
                if (proximitySensor == null || proximityMax == null) return

                val value = event?.values?.getOrNull(0)
                if (value == null) {
                    setBlocking(false)
                    return
                }

                val isBlocked = value < proximityMax
                if (isBlocked != wasBlocked) {
                    wasBlocked = isBlocked
                    handler.post { setBlocking(isBlocked) }
                }
            }
        }

    fun refreshStatus() {
        if (pm?.isInteractive == false) {
            startListeningForPocket()
        } else {
            stopListeningForPocket()
        }
    }

    fun startListeningForPocket() {
        proximitySensor?.let { sensor ->
            sensorManager.registerListener(
                sensorListener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                handler,
            )
        }
    }

    fun stopListeningForPocket() {
        proximitySensor?.let { sensorManager.unregisterListener(sensorListener) }
        setBlocking(false)
        wasBlocked = false
    }

    override fun onActivate() {
        val filter: IntentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        context.registerReceiver(powerReceiver, filter)
        if (proximitySensor == null) {
            dlog(TAG, "Proximity sensor not available. Pocket detection disabled.")
            return
        }
        refreshStatus()
        setBlocking(false)
    }

    override fun onDeactivate() {
        if (proximitySensor == null) {
            return
        }
        stopListeningForPocket()
        context.unregisterReceiver(powerReceiver)
    }

    companion object {
        private const val TAG: String = "PocketDetection"
    }
}
