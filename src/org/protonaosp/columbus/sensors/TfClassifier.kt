/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

import android.content.res.AssetManager
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.ArrayList
import java.util.HashMap
import org.protonaosp.columbus.dlog
import org.tensorflow.lite.Interpreter

class TfClassifier(assetManager: AssetManager, assetFileName: String) {

    companion object {
        private const val TAG = "Columbus/TfClassifier"
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val assetFd = assetManager.openFd(assetFileName)
            interpreter =
                Interpreter(
                    FileInputStream(assetFd.fileDescriptor)
                        .channel
                        .map(
                            FileChannel.MapMode.READ_ONLY,
                            assetFd.startOffset,
                            assetFd.declaredLength,
                        )
                )
        } catch (e: Exception) {
            dlog(TAG, "Failed to load tflite file: ${e.message}")
        }
    }

    fun predict(input: ArrayList<Float>, size: Int): ArrayList<ArrayList<Float>> {
        val interpreter = interpreter ?: return ArrayList()

        val tfliteIn =
            Array(1) { Array(input.size) { Array(1) { FloatArray(1) } } }
                .apply {
                    for (i in input.indices) {
                        this[0][i][0][0] = input[i]
                    }
                }

        val tfliteOut = HashMap<Int, Any>().apply { this[0] = Array(1) { FloatArray(size) } }

        try {
            interpreter.runForMultipleInputsOutputs(arrayOf<Any>(tfliteIn), tfliteOut)
        } catch (e: Exception) {
            dlog(TAG, "Error running inference: ${e.message}")
            return ArrayList()
        }

        if (tfliteOut.isEmpty()) {
            dlog(TAG, "Result is invalid")
            return ArrayList()
        }

        @Suppress("UNCHECKED_CAST") val tfliteContent = tfliteOut[0] as Array<FloatArray>?
        if (tfliteContent == null) {
            dlog(TAG, "Result content is invalid")
            return ArrayList()
        }

        val output =
            arrayListOf(
                ArrayList<Float>().apply {
                    for (i in 0 until size) {
                        add(tfliteContent[0][i])
                    }
                }
            )

        return output
    }
}
