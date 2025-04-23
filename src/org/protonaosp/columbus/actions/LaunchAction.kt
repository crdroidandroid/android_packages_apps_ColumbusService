/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import org.protonaosp.columbus.R
import org.protonaosp.columbus.getDePrefs
import org.protonaosp.columbus.getLaunchActionApp
import org.protonaosp.columbus.getLaunchActionAppShortcut

class LaunchAction(context: Context) : Action(context) {
    val app_default = context.getString(R.string.launch_app_default)

    override fun canRun() = isDeviceInteractiveAndUnlocked(context)

    override fun canRunWhenScreenOff() = false

    private fun launchApp() {
        val prefs = context.getDePrefs()

        val packageName: String = prefs.getLaunchActionApp(context)
        val activity: String = prefs.getLaunchActionAppShortcut(context)
        val launchActivity: Boolean = activity != app_default

        if (packageName == app_default) return

        val intent: Intent? =
            if (launchActivity) {
                    Intent().apply { setClassName(packageName, activity) }
                } else {
                    context.packageManager.getLaunchIntentForPackage(packageName)
                }
                ?.apply { (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }

        if (intent == null) return

        try {
            context.startActivityAsUser(intent, UserHandle.CURRENT)
        } catch (e: Exception) {}
    }

    override fun run() {
        launchApp()
    }
}
