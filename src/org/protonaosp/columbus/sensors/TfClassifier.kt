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

    @Suppress("UNCHECKED_CAST")
    fun predict(input: ArrayList<Float>, size: Int): ArrayList<ArrayList<Float>> {
        val interpreter = interpreter ?: return ArrayList()

        val tfliteIn =
            java.lang.reflect.Array.newInstance(Float::class.javaPrimitiveType, 1, input.size, 1, 1)
                as Array<Array<Array<FloatArray>>>

        for (i in 0 until input.size) {
            tfliteIn[0][i][0][0] = input[i]
        }

        val tfliteOut =
            HashMap<Int, Any>().apply {
                this[0] =
                    java.lang.reflect.Array.newInstance(Float::class.javaPrimitiveType, 1, size)
            }

        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(tfliteIn), tfliteOut)

        val tfliteContent = tfliteOut[0] as Array<FloatArray>

        val output = ArrayList<ArrayList<Float>>()
        val outputInner = ArrayList<Float>()
        for (i in 0 until size) {
            outputInner.add(tfliteContent[0][i])
        }
        output.add(outputInner)

        return output
    }
}
