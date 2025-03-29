package org.protonaosp.columbus.sensors

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.util.SparseLongArray
import org.protonaosp.columbus.TAG
import org.protonaosp.columbus.actions.*
import org.protonaosp.columbus.dlog
import org.protonaosp.columbus.gates.*

class ColumbusController(val context: Context, val sensor: ColumbusSensor, val handler: Handler) {
    interface GestureListener {
        fun onGestureDetected(sensor: ColumbusSensor, msg: Int)
    }

    private val lastTimestampMap = SparseLongArray()
    private var gestureListener: GestureListener? = null
    private val softGates: Set<Gate>
    private val softGateListener: Gate.Listener
    private val gestureSensorListener: ColumbusSensor.Listener

    init {
        softGates =
            setOf(
                ChargingState(context, handler),
                UsbState(context, handler),
                ScreenTouch(context, handler),
                SystemKeyPress(context, handler),
                PowerState(context, handler),
            )

        softGateListener =
            object : Gate.Listener {
                override fun onGateChanged(gate: Gate) {}
            }

        gestureSensorListener =
            object : ColumbusSensor.Listener {
                override fun onGestureDetected(sensor: ColumbusSensor, msg: Int) {
                    if (isThrottled(msg)) {
                        dlog(TAG, "Gesture $msg throttled")
                        return
                    }
                    if (blockingGate()) return
                    gestureListener?.onGestureDetected(sensor, msg)
                }
            }

        sensor.setGestureListener(gestureSensorListener)
    }

    fun isThrottled(tapTiming: Int): Boolean {
        val throttleMs: Long =
            if (useApSensor(context)) {
                getApSensorThrottleMs(context)
            } else {
                500L
            }

        if (throttleMs == 0L) return false

        var lastMs: Long = lastTimestampMap.get(tapTiming)
        var currentMs: Long = SystemClock.uptimeMillis()
        lastTimestampMap.put(tapTiming, currentMs)
        return currentMs - lastMs <= throttleMs
    }

    fun setGestureListener(gestureListener: GestureListener) {
        this.gestureListener = gestureListener
    }

    fun startListening(): Boolean {
        if (!sensor.isListening()) {
            activateGates()
            sensor.startListening()
            return true
        }
        return false
    }

    fun stopListening(): Boolean {
        if (sensor.isListening()) {
            deactivateGates()
            sensor.stopListening()
            return true
        }
        return false
    }

    fun updateSensitivity(sensitivity: Float): Boolean {
        if (sensor.isListening()) {
            sensor.updateSensitivity(sensitivity)
            return true
        }
        return false
    }

    private fun activateGates() {
        softGates.forEach { it.registerListener(softGateListener) }
    }

    private fun deactivateGates() {
        softGates.forEach { it.unregisterListener(softGateListener) }
    }

    private fun blockingGate(): Boolean {
        for (it in softGates) {
            if (it.isBlocking()) {
                dlog(TAG, "Blocked by ${it.toString()}")
                return true
            }
        }
        return false
    }
}
