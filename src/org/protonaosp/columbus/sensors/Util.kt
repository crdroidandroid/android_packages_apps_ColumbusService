/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.sensors

object Util {
    fun getMaxId(input: ArrayList<Float>): Int {
        var currentMax = -Float.MAX_VALUE
        var i = 0
        var id = 0
        while (i < input.size) {
            var previousMax = currentMax
            if (currentMax < input.get(i)) {
                previousMax = input.get(i)
                id = i
            }
            i++
            currentMax = previousMax
        }
        return id
    }
}
