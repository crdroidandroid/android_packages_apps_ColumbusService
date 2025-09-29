/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus

import android.app.Application
import android.content.Context
import org.protonaosp.columbus.utils.AppIconCacheManager

class ColumbusApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onTerminate() {
        super.onTerminate()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppIconCacheManager.getInstance().trimMemory(level)
    }

    companion object {
        private const val TAG: String = "ColumbusApplication"
    }
}
