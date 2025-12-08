/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.media.AudioManager
import android.os.UserHandle
import android.provider.Settings
import android.widget.Toast
import com.android.internal.R as InternalR

class MuteAction(context: Context) : Action(context) {
    val service = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun canRun() =
        service != null &&
            context.resources.getBoolean(InternalR.bool.config_volumeHushGestureEnabled)

    override fun run() {
        if (service == null) return
        // We can't call AudioService#silenceRingerModeInternal from here, so this is a partial copy
        // of it
        var silenceRingerSetting =
            Settings.Secure.getIntForUser(
                context.contentResolver,
                Settings.Secure.VOLUME_HUSH_GESTURE,
                Settings.Secure.VOLUME_HUSH_OFF,
                UserHandle.USER_CURRENT,
            )

        var ringerMode: Int
        var toastText: Int
        when (silenceRingerSetting) {
            Settings.Secure.VOLUME_HUSH_VIBRATE -> {
                ringerMode = AudioManager.RINGER_MODE_VIBRATE
                toastText = com.android.internal.R.string.volume_dialog_ringer_guidance_vibrate
            }
            // VOLUME_HUSH_MUTE and VOLUME_HUSH_OFF
            else -> {
                ringerMode = AudioManager.RINGER_MODE_SILENT
                toastText = com.android.internal.R.string.volume_dialog_ringer_guidance_silent
            }
        }

        service.setRingerModeInternal(ringerMode)
        Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
    }
}
