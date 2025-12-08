/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.protonaosp.columbus.TAG

class FlashlightAction(context: Context) : Action(context) {
    private val handler = Handler.createAsync(Looper.getMainLooper())
    private val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val torchCamId = findCamera()
    private var available = true
    private var enabled = false

    private val torchCallback =
        object : CameraManager.TorchCallback() {
            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == torchCamId) {
                    available = false
                }
            }

            override fun onTorchModeChanged(cameraId: String, newEnabled: Boolean) {
                if (cameraId == torchCamId) {
                    available = true
                    enabled = newEnabled
                }
            }
        }

    init {
        if (cm != null && torchCamId != null) {
            cm.registerTorchCallback(torchCallback, handler)
        }
    }

    override fun canRun() = torchCamId != null && available

    override fun run() {
        if (cm == null) return
        try {
            cm.setTorchMode(torchCamId!!, !enabled)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to set torch mode to $enabled", e)
            return
        }

        enabled = !enabled
    }

    override fun destroy() {
        if (cm == null) return
        if (torchCamId != null) {
            cm.unregisterTorchCallback(torchCallback)
        }
    }

    private fun findCamera(): String? {
        if (cm == null) return null
        for (id in cm.cameraIdList) {
            val characteristics = cm.getCameraCharacteristics(id)
            val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            if (
                flashAvailable != null &&
                    flashAvailable &&
                    lensFacing != null &&
                    lensFacing == CameraCharacteristics.LENS_FACING_BACK
            ) {
                return id
            }
        }

        return null
    }
}
