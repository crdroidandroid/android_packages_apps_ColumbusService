/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

import android.content.Context
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.ArrayList
import java.util.HashMap
import org.protonaosp.columbus.dlog
import org.tensorflow.lite.Interpreter

class TfClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    var isModelLoaded: Boolean = false
        private set

    init {
        init()
    }

    private fun init() {
        try {
            val afd = context.resources.openRawResourceFd(getModelFileRes(context))
            if (afd.length == 0L) {
                dlog(TAG, "tflite model is empty (dummy). Interpreter will not be initialized.")
                afd.close()
                return
            }

            val inputStream = FileInputStream(afd.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = afd.startOffset
            val declaredLength = afd.declaredLength

            val mappedByteBuffer =
                fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            interpreter = Interpreter(mappedByteBuffer)
            isModelLoaded = true

            afd.close()
        } catch (e: Exception) {
            dlog(TAG, "Failed to load tflite file: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun predict(input: ArrayList<Float>, size: Int): ArrayList<ArrayList<Float>> {
        val interpreter = interpreter
        if (!isModelLoaded || interpreter == null) {
            dlog(TAG, "tflite model is not loaded.")
            return ArrayList()
        }

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

    companion object {
        private const val TAG = "Columbus/TfClassifier"
    }
}
