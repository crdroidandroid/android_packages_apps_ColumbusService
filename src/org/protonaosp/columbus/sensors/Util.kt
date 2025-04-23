/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

object Util {
    fun getMaxId(input: ArrayList<Float>): Int {
        var currentMax = -Float.MAX_VALUE
        var id = 0
        for (i in 0 until input.size) {
            if (currentMax < input.get(i)) {
                currentMax = input.get(i)
                id = i
            }
        }
        return id
    }
}
