/*
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.actions

import android.content.Context
import android.view.KeyEvent

class PlayPauseAction(context: Context) :
    SendKeyCodeAction(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {}
