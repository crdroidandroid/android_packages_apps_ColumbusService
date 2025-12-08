/*
 * SPDX-FileCopyrightText: Kieron Quinn
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.gates

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sqrt

// References:
// https://stackoverflow.com/questions/30948131/how-to-know-if-android-device-is-flat-on-table
// https://stackoverflow.com/questions/11175599/how-to-measure-the-tilt-of-the-phone-in-xy-plane-using-accelerometer-in-android/15149421#15149421
class TableDetection(context: Context, val handler: Handler) : Gate(context, handler, 2) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sensorListener =
        object : SensorEventListener {
            private var wasFlat: Boolean = false

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return

                // Movement
                val x = values[0].toDouble()
                val y = values[1].toDouble()
                val z = values[2].toDouble()
                val norm = sqrt(x * x + y * y + z * z)

                // Normalize the accelerometer vector
                val inclination =
                    if (norm > 0) {
                        Math.toDegrees(acos(z / norm)).roundToInt()
                    } else {
                        0
                    }

                val isFlat =
                    norm > 8.0 && inclination < 5 && abs(x / norm) < 0.25 && abs(y / norm) < 0.25

                if (wasFlat != isFlat) {
                    wasFlat = isFlat
                    setBlocking(isFlat)
                }
            }
        }

    override fun onActivate() {
        if (accelerometer == null) {
            return
        }
        sensorManager?.registerListener(
            sensorListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL,
            handler,
        )
        setBlocking(false)
    }

    override fun onDeactivate() {
        setBlocking(false)
        sensorManager?.unregisterListener(sensorListener)
    }
}
