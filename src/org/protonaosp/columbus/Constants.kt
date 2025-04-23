/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus

import android.util.Log

const val TAG = "Columbus/Service"

fun dlog(tag: String, msg: String) {
    val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    if (DEBUG) Log.d(tag, msg)
}
