/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

import android.content.Context
import android.hardware.Sensor
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Deque
import kotlin.math.max
import org.protonaosp.columbus.actions.*

open class TapRT(val context: Context, val sizeWindowNs: Long) :
    EventIMURT(sizeWindowNs, 50, 50 * 6) {

    private val minTimeGapNs: Long = 100000000L
    private val maxTimeGapNs: Long = 500000000L
    private val frameAlignPeak: Int = 12
    private val framePriorPeak: Int = 6
    var result: Int = TapClass.Front.ordinal
    val positivePeakDetector: PeakDetector = PeakDetector()
    val negativePeakDetector: PeakDetector = PeakDetector()
    val timestampsBackTap: Deque<Long> = ArrayDeque()
    var wasPeakApproaching: Boolean = true

    init {
        tflite = TfClassifier(context.getAssets(), getModelFileName(context))
        lowpassAcc.para = 1f
        lowpassGyro.para = 1f
        highpassAcc.para = 0.05f
        highpassGyro.para = 0.05f
    }

    fun addToFeatureVector(points: ArrayDeque<Float>, max: Int, index: Int) {
        var idx = index
        var i: Int = 0
        var pIt = points.iterator()

        while (pIt.hasNext()) {
            if (i < max) {
                pIt.next()
            } else {
                if (i >= sizeFeatureWindow + max) {
                    return
                }
                featureVector[idx] = pIt.next()
                idx++
            }
            i++
        }
    }

    fun checkDoubleTapTiming(timestamp: Long): Int {
        val timestampFirst = timestampsBackTap.iterator()
        while (timestampFirst.hasNext()) {
            val storedTimestamp = timestampFirst.next()
            if (timestamp - storedTimestamp > maxTimeGapNs) {
                timestampFirst.remove()
            }
        }

        if (timestampsBackTap.isEmpty()) {
            return 0
        }

        val timestampSecond = timestampsBackTap.iterator()
        while (timestampSecond.hasNext()) {
            val lastTimestamp = timestampsBackTap.last()
            val storedTimestamp = timestampSecond.next()
            if (lastTimestamp - storedTimestamp > minTimeGapNs) {
                timestampsBackTap.clear()
                return 2
            }
        }

        return 1
    }

    fun processKeySignalHeuristic() {
        val sample = resampleAcc.results
        val samplePoint = sample.point
        val sampleTime = sample.time
        val sampleInterval = resampleAcc.interval

        var update: Point3f =
            highpassAcc.update(
                lowpassAcc.update(slopeAcc.update(samplePoint, 2400000f / sampleInterval.toFloat()))
            )
        val updatedZ = update.z.toFloat()
        positivePeakDetector.update(updatedZ, sampleTime)
        negativePeakDetector.update(-updatedZ, sampleTime)

        accZs.add(updatedZ)

        val interval: Int = (sizeWindowNs / sampleInterval).toInt()
        while (accZs.size > interval) {
            accZs.removeFirst()
        }
        if (accZs.size == interval) {
            recognizeTapHeuristic()
        }
        if (result == TapClass.Back.ordinal) {
            timestampsBackTap.addLast(sampleTime)
        }
    }

    fun recognizeTapHeuristic() {
        val positivePeakId = positivePeakDetector.peakId
        val negativePeakId = negativePeakDetector.peakId - positivePeakId

        if (positivePeakId == 4) {
            featureVector = ArrayList(accZs)
            result =
                if (negativePeakId > 0 && negativePeakId < 3) {
                        TapClass.Back
                    } else {
                        TapClass.Others
                    }
                    .ordinal
        }
    }

    fun recognizeTapML() {
        val resultT =
            ((resampleAcc.results.time - resampleGyro.results.time) / resampleAcc.interval).toInt()

        var positivePeakMax = max(0, positivePeakDetector.peakId)
        var negativePeakMax = max(0, negativePeakDetector.peakId)

        if (positivePeakDetector.amplitude <= negativePeakDetector.amplitude) {
            positivePeakMax = negativePeakMax
        }

        if (positivePeakMax > frameAlignPeak) {
            wasPeakApproaching = true
        }

        val accTuned: Int = positivePeakMax - framePriorPeak
        val gyroTuned: Int = accTuned - resultT
        val accSizeZ: Int = accZs.size

        if (
            accTuned < 0 ||
                gyroTuned < 0 ||
                accTuned + sizeFeatureWindow > accSizeZ ||
                sizeFeatureWindow + gyroTuned > accSizeZ ||
                !wasPeakApproaching ||
                positivePeakMax > frameAlignPeak
        ) {
            return
        }
        wasPeakApproaching = false
        addToFeatureVector(accXs, accTuned, 0)
        addToFeatureVector(accYs, accTuned, sizeFeatureWindow)
        addToFeatureVector(accZs, accTuned, sizeFeatureWindow * 2)
        addToFeatureVector(gyroXs, gyroTuned, sizeFeatureWindow * 3)
        addToFeatureVector(gyroYs, gyroTuned, sizeFeatureWindow * 4)
        addToFeatureVector(gyroZs, gyroTuned, sizeFeatureWindow * 5)

        val _featureVector = featureVector
        featureVector = scaleGyroData(_featureVector, 10f)

        val predict = tflite?.predict(_featureVector, 7)

        if (predict.isNullOrEmpty()) {
            return
        }

        result = Util.getMaxId(predict[0])
    }

    fun reset(clearFv: Boolean) {
        super.reset()
        if (clearFv) {
            featureVector.clear()
        } else {
            featureVector = arrayListOf<Float>()
            for (i in 0 until numberFeature) {
                featureVector.add(0f)
            }
        }
    }

    fun updateAccAndPeakDetectors() {
        updateAcc()
        val lastAccZ = accZs.last()
        positivePeakDetector.update(lastAccZ.toFloat(), resampleAcc.results.time)
        negativePeakDetector.update(-lastAccZ.toFloat(), resampleAcc.results.time)
    }

    fun updateData(
        sensorType: Int,
        rawLastX: Float,
        rawLastY: Float,
        rawLastZ: Float,
        rawLastT: Long,
        interval: Long,
        heuristicMode: Boolean,
    ) {
        result = TapClass.Others.ordinal
        if (heuristicMode) {
            updateHeuristic(sensorType, rawLastX, rawLastY, rawLastZ, rawLastT, interval)
        } else {
            updateML(sensorType, rawLastX, rawLastY, rawLastZ, rawLastT, interval)
        }
    }

    fun updateHeuristic(
        sensorType: Int,
        rawLastX: Float,
        rawLastY: Float,
        rawLastZ: Float,
        rawLastT: Long,
        interval: Long,
    ) {
        if (sensorType == Sensor.TYPE_GYROSCOPE) {
            return
        }
        if (0L == syncTime) {
            syncTime = rawLastT
            resampleAcc.init(rawLastX, rawLastY, rawLastZ, rawLastT, interval)
            resampleAcc.resampledLastT = syncTime
            slopeAcc.init(resampleAcc.results.point)
            return
        }

        while (resampleAcc.update(rawLastX, rawLastY, rawLastZ, rawLastT)) {
            processKeySignalHeuristic()
        }
    }

    fun updateML(
        sensorType: Int,
        rawLastX: Float,
        rawLastY: Float,
        rawLastZ: Float,
        rawLastT: Long,
        interval: Long,
    ) {
        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> {
                gotAcc = true
                if (syncTime == 0L) {
                    resampleAcc.init(rawLastX, rawLastY, rawLastZ, rawLastT, interval)
                }
                if (!gotGyro) return
            }
            Sensor.TYPE_GYROSCOPE -> {
                gotGyro = true
                if (syncTime == 0L) {
                    resampleGyro.init(rawLastX, rawLastY, rawLastZ, rawLastT, interval)
                }
                if (!gotAcc) return
            }
        }

        if (syncTime == 0L) {
            syncTime = rawLastT
            resampleAcc.resampledLastT = rawLastT
            resampleGyro.resampledLastT = syncTime
            slopeAcc.init(resampleAcc.results.point)
            slopeGyro.init(resampleGyro.results.point)
            lowpassAcc.init(Point3f(0f, 0f, 0f))
            lowpassGyro.init(Point3f(0f, 0f, 0f))
            highpassAcc.init(Point3f(0f, 0f, 0f))
            highpassGyro.init(Point3f(0f, 0f, 0f))
            return
        }

        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> {
                while (resampleAcc.update(rawLastX, rawLastY, rawLastZ, rawLastT)) {
                    updateAccAndPeakDetectors()
                }
                return
            }
            Sensor.TYPE_GYROSCOPE -> {
                while (resampleGyro.update(rawLastX, rawLastY, rawLastZ, rawLastT)) {
                    updateGyro()
                    recognizeTapML()
                }
                if (result == TapClass.Back.ordinal) {
                    timestampsBackTap.addLast(rawLastT)
                }
                return
            }
        }
    }
}
