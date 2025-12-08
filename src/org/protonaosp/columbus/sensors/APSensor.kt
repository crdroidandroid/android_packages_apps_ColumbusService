/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import org.protonaosp.columbus.actions.*

class APSensor(val context: Context, var sensitivity: Float, val handler: Handler) :
    ColumbusSensor() {
    val samplingIntervalNs: Long
    val sensorManager: SensorManager?
    val accelerometer: Sensor?
    val gyroscope: Sensor?
    val heuristicMode: Boolean
    val tap: TapRT
    val callback: APCallback
    private var isListening: Boolean = false

    init {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        heuristicMode = isHeuristicMode(context)
        tap = TapRT(context, 153600000L)
        samplingIntervalNs = 2400000L
        callback = APCallback()
    }

    override fun isListening(): Boolean {
        return isListening
    }

    fun setListening(listening: Boolean) {
        isListening = listening
    }

    inner class APCallback : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) {
                return
            }

            val evType: Int = event.sensor?.getType() ?: return
            val evArr: FloatArray = event.values ?: return
            tap.updateData(
                evType,
                evArr[0],
                evArr[1],
                evArr[2],
                event.timestamp,
                samplingIntervalNs,
                heuristicMode,
            )
            val timing: Int = tap.checkDoubleTapTiming(event.timestamp)
            when (timing) {
                1 -> handler.post { reportGestureDetected(2) }
                2 -> handler.post { reportGestureDetected(1) }
            }
        }

        fun setListening(listening: Boolean, samplingPeriod: Int) {
            if (!listening || accelerometer == null || gyroscope == null) {
                sensorManager?.unregisterListener(callback)
                setListening(false)
            } else {
                sensorManager?.registerListener(callback, accelerometer, samplingPeriod, handler)
                sensorManager?.registerListener(callback, gyroscope, samplingPeriod, handler)
                setListening(true)
            }
        }

        fun updateSensitivity() {
            tap.apply {
                positivePeakDetector.minNoiseTolerate = sensitivity
                reset(heuristicMode)
            }
        }
    }

    override fun startListening() {
        callback.setListening(true, SensorManager.SENSOR_DELAY_FASTEST)
        tap.apply {
            lowpassAcc.para = 1f
            lowpassGyro.para = 1f
            highpassAcc.para = 0.05f
            highpassGyro.para = 0.05f
            positivePeakDetector.minNoiseTolerate = sensitivity
            positivePeakDetector.windowSize = 64
            negativePeakDetector.minNoiseTolerate = 0.015f
            negativePeakDetector.windowSize = 64
            reset(heuristicMode)
        }
    }

    override fun stopListening() {
        callback.setListening(false, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun updateSensitivity(sensitivity: Float) {
        this.sensitivity = sensitivity
        callback.updateSensitivity()
    }
}
