/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

interface Sensor {
    fun isListening(): Boolean

    fun startListening()

    fun stopListening()
}
