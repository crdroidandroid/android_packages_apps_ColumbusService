/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

abstract class ColumbusSensor : Sensor {
    interface Listener {
        fun onGestureDetected(sensor: ColumbusSensor, msg: Int)
    }

    private var listener: Listener? = null

    fun reportGestureDetected(msg: Int) {
        listener?.onGestureDetected(this, msg)
    }

    fun setGestureListener(listener: Listener?) {
        this.listener = listener
    }

    open fun updateSensitivity(sensitivity: Float) {}
}
