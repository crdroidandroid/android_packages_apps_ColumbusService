/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

import android.content.Context
import android.content.res.Resources
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList
import java.util.HashMap
import org.protonaosp.columbus.dlog
import org.tensorflow.lite.Interpreter

class TfClassifier(context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            val resources: Resources = context.resources
            val inputStream: InputStream = resources.openRawResource(getModelFileRes(context))
            val fileBytes = inputStream.readBytes()
            val byteBuffer = ByteBuffer.allocateDirect(fileBytes.size)
            byteBuffer.order(ByteOrder.nativeOrder())
            byteBuffer.put(fileBytes)
            byteBuffer.rewind()
            inputStream.close()
            interpreter = Interpreter(byteBuffer)
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

    companion object {
        private const val TAG = "Columbus/TfClassifier"
    }
}
