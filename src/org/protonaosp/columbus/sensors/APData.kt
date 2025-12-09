/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

import android.content.Context
import kotlin.math.max
import org.protonaosp.columbus.R

enum class TapClass {
    Front,
    Back,
    Left,
    Right,
    Top,
    Bottom,
    Others,
}

fun isHeuristicMode(context: Context): Boolean =
    context.resources.getBoolean(R.bool.default_apsensor_heuristic_mode)

fun useApSensor(context: Context): Boolean =
    context.resources.getBoolean(R.bool.default_use_apsensor) ||
        !context.packageManager.hasSystemFeature("android.hardware.context_hub")

fun getModelFileRes(context: Context): Int {
    val model = context.getString(R.string.default_model)
    return when (model) {
        "bramble" -> R.raw.tap7cls_bramble
        "coral" -> R.raw.tap7cls_coral
        "crosshatch" -> R.raw.tap7cls_crosshatch
        "flame" -> R.raw.tap7cls_flame
        "redfin" -> R.raw.tap7cls_redfin
        "custom" -> R.raw.tap7cls_custom
        else -> R.raw.quickTapBaseModel
    }
}

fun getApSensorThrottleMs(context: Context): Long =
    context.resources.getInteger(R.integer.default_apsensor_throttle_ms).toLong()

data class Point3f(var x: Float, var y: Float, var z: Float)

class Highpass1C {
    var _para: Float = 1f
    var lastX: Float = 0f
    var lastY: Float = 0f

    fun init(last: Float) {
        lastX = last
        lastY = last
    }

    fun update(update: Float): Float {
        val updatedParam: Float = _para
        if (updatedParam == 1f) {
            return update
        }
        val updatedY: Float = (lastY * updatedParam) + (updatedParam * (update - lastX))
        lastY = updatedY
        lastX = update
        return updatedY
    }
}

class Highpass3C {
    var highpassX: Highpass1C = Highpass1C()
    var highpassY: Highpass1C = Highpass1C()
    var highpassZ: Highpass1C = Highpass1C()

    fun init(point: Point3f) {
        highpassX.init(point.x)
        highpassY.init(point.y)
        highpassZ.init(point.z)
    }

    var para: Float = 1f
        set(value) {
            highpassX._para = value
            highpassY._para = value
            highpassZ._para = value
        }

    fun update(point: Point3f): Point3f {
        return Point3f(
            highpassX.update(point.x),
            highpassY.update(point.y),
            highpassZ.update(point.z),
        )
    }
}

open class Lowpass1C {
    var _para: Float = 1f
    var lastX: Float = 0f

    fun init(last: Float) {
        lastX = last
    }

    fun update(update: Float): Float {
        var updatedParam: Float = _para
        if (updatedParam == 1f) {
            return update
        }
        var updateX: Float = ((1f - updatedParam) * lastX) + (updatedParam * update)
        lastX = updateX
        return updateX
    }
}

class Lowpass3C : Lowpass1C() {
    var lowpassX: Lowpass1C = Lowpass1C()
    var lowpassY: Lowpass1C = Lowpass1C()
    var lowpassZ: Lowpass1C = Lowpass1C()

    fun init(point: Point3f) {
        lowpassX.init(point.x)
        lowpassY.init(point.y)
        lowpassZ.init(point.z)
    }

    var para: Float = 1f
        set(value) {
            lowpassX._para = value
            lowpassY._para = value
            lowpassZ._para = value
        }

    fun update(point: Point3f): Point3f {
        return Point3f(lowpassX.update(point.x), lowpassY.update(point.y), lowpassZ.update(point.z))
    }
}

class Sample3C(x: Float, y: Float, z: Float, var time: Long) {
    var point: Point3f
        private set

    init {
        val p = Point3f(0f, 0f, 0f)
        p.x = x
        p.y = y
        p.z = z
        point = p
    }
}

open class Resample1C {
    var interval = 0L
    var rawLastT: Long = 0L
    var resampledLastT: Long = 0L
    var rawLastX = 0f
    var resampledThisX = 0f

    fun init(x: Float, time: Long, interval: Long) {
        rawLastX = x
        rawLastT = time
        resampledThisX = x
        resampledLastT = time
        this.interval = interval
    }
}

class Resample3C : Resample1C() {
    var rawLastY: Float = 0f
    var rawLastZ: Float = 0f
    var resampledThisY: Float = 0f
    var resampledThisZ: Float = 0f

    val results: Sample3C
        get() = Sample3C(resampledThisX, resampledThisY, resampledThisZ, resampledLastT)

    fun init(lastX: Float, lastY: Float, lastZ: Float, lastT: Long, intrv: Long) {
        init(lastX, lastT, intrv)
        rawLastY = lastY
        rawLastZ = lastZ
        resampledThisY = lastY
        resampledThisZ = lastZ
    }

    fun update(lastX: Float, lastY: Float, lastZ: Float, lastT: Long): Boolean {
        var savedRawLastT: Long = rawLastT
        if (lastT == savedRawLastT) {
            return false
        }
        var updatedInterval: Long =
            (if (interval <= 0L) {
                lastT - savedRawLastT
            } else {
                interval
            }) + resampledLastT
        if (lastT < updatedInterval) {
            rawLastT = lastT
            rawLastX = lastX
            rawLastY = lastY
            rawLastZ = lastZ
            return false
        }
        var adjustedInterval: Float =
            (updatedInterval - savedRawLastT).toFloat() / (lastT - savedRawLastT).toFloat()
        resampledThisX = ((lastX - rawLastX) * adjustedInterval) + rawLastX
        resampledThisY = ((lastY - rawLastY) * adjustedInterval) + rawLastY
        resampledThisZ = ((lastZ - rawLastZ) * adjustedInterval) + rawLastZ
        resampledLastT = updatedInterval
        if (savedRawLastT >= updatedInterval) {
            return true
        }
        rawLastT = lastT
        rawLastX = lastX
        rawLastY = lastY
        rawLastZ = lastZ
        return true
    }
}

class Slope1C {
    var deltaX: Float = 0f
    var rawLastX: Float = 0f

    fun init(lastX: Float) {
        rawLastX = lastX
    }

    fun update(lastPoint: Float, lastIntv: Float): Float {
        var lastX: Float = lastPoint * lastIntv
        var lastDX: Float = lastX - rawLastX
        deltaX = lastDX
        rawLastX = lastX
        return lastDX
    }
}

class Slope3C {
    var slopeX: Slope1C = Slope1C()
    var slopeY: Slope1C = Slope1C()
    var slopeZ: Slope1C = Slope1C()

    fun init(point: Point3f) {
        slopeX.init(point.x)
        slopeY.init(point.y)
        slopeZ.init(point.z)
    }

    fun update(p: Point3f, intv: Float): Point3f {
        return Point3f(slopeX.update(p.x, intv), slopeY.update(p.y, intv), slopeZ.update(p.z, intv))
    }
}

class PeakDetector {
    var minNoiseTolerate: Float = 0f
    var noiseTolerate: Float = 0f
    var maxTapDuration: Long = 120000000L
    var peakId: Int = -1
    var numberPeak: Int = 0
    var timestamp: Long = 0L
    var amplitude: Float = 0f
    var windowSize: Int = 0
    var amplitudeReference: Float = 0f
    var gotNewHighValue: Boolean = false

    fun reset() {
        amplitude = 0f
        amplitudeReference = 0f
        numberPeak = 0
        timestamp = 0L
        peakId = 0
    }

    fun update(lastZ: Float, lastT: Long) {
        var updatePId: Int = peakId - 1
        peakId = updatePId
        if (updatePId < 0) {
            reset()
        }
        noiseTolerate = minNoiseTolerate
        var maxAmplitude: Float = max(amplitude, lastZ) / 5f
        if (maxAmplitude > noiseTolerate) {
            noiseTolerate = maxAmplitude
        }
        var updatedAmRef: Float = amplitudeReference - lastZ
        if (updatedAmRef < 0f) {
            amplitudeReference = lastZ
            gotNewHighValue = true
            if (
                (timestamp == 0L || (lastT - timestamp < maxTapDuration && amplitude < lastZ)) &&
                    lastZ >= noiseTolerate
            ) {
                peakId = windowSize - 1
                amplitude = lastZ
                timestamp = lastT
            }
        } else if (updatedAmRef > noiseTolerate) {
            amplitudeReference = lastZ
            if (gotNewHighValue) {
                numberPeak++
            }
            gotNewHighValue = false
        }
    }
}
