/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

import java.util.ArrayDeque
import java.util.ArrayList
import org.protonaosp.columbus.actions.*

open class EventIMURT(val _sizeWindowNs: Long, val sizeFeatureWindow: Int, val numberFeature: Int) {
    var featureVector: ArrayList<Float> = arrayListOf<Float>()
    val accXs: ArrayDeque<Float> = ArrayDeque<Float>()
    val accYs: ArrayDeque<Float> = ArrayDeque<Float>()
    val accZs: ArrayDeque<Float> = ArrayDeque<Float>()
    val gyroXs: ArrayDeque<Float> = ArrayDeque<Float>()
    val gyroYs: ArrayDeque<Float> = ArrayDeque<Float>()
    val gyroZs: ArrayDeque<Float> = ArrayDeque<Float>()
    var gotAcc: Boolean = false
    var gotGyro: Boolean = false
    var syncTime: Long = 0L
    val resampleAcc: Resample3C = Resample3C()
    val resampleGyro: Resample3C = Resample3C()
    val slopeAcc: Slope3C = Slope3C()
    val slopeGyro: Slope3C = Slope3C()
    val lowpassAcc: Lowpass3C = Lowpass3C()
    val lowpassGyro: Lowpass3C = Lowpass3C()
    val highpassAcc: Highpass3C = Highpass3C()
    val highpassGyro: Highpass3C = Highpass3C()
    var tflite: TfClassifier? = null

    fun processAcc() {
        val sample = resampleAcc.results
        var update: Point3f =
            highpassAcc.update(
                lowpassAcc.update(
                    slopeAcc.update(sample.point, 2400000f / resampleAcc.interval.toFloat())
                )
            )
        accXs.add(update.x.toFloat())
        accYs.add(update.y.toFloat())
        accZs.add(update.z.toFloat())
        val interval: Int = (_sizeWindowNs / resampleAcc.interval).toInt()
        while (accXs.size > interval) {
            accXs.removeFirst()
            accYs.removeFirst()
            accZs.removeFirst()
        }
    }

    fun processGyro() {
        val sample = resampleGyro.results
        var update: Point3f =
            highpassGyro.update(
                lowpassGyro.update(
                    slopeGyro.update(sample.point, 2400000f / resampleGyro.interval.toFloat())
                )
            )
        gyroXs.add(update.x.toFloat())
        gyroYs.add(update.y.toFloat())
        gyroZs.add(update.z.toFloat())
        val interval: Int = (_sizeWindowNs / resampleGyro.interval).toInt()
        while (gyroXs.size > interval) {
            gyroXs.removeFirst()
            gyroYs.removeFirst()
            gyroZs.removeFirst()
        }
    }

    fun reset() {
        accXs.clear()
        accYs.clear()
        accZs.clear()
        gyroXs.clear()
        gyroYs.clear()
        gyroZs.clear()
        gotAcc = false
        gotGyro = false
        syncTime = 0L
    }

    fun scaleGyroData(input: ArrayList<Float>, scale: Float): ArrayList<Float> {
        val size = input.size
        for (i in (size / 2) until size) {
            input[i] *= scale
        }
        return input
    }
}
