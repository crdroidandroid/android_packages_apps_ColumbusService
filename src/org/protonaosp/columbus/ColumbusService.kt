/*
 * SPDX-FileCopyrightText: The Proton AOSP Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.media.AudioAttributes
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.UserHandle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import lineageos.providers.LineageSettings
import org.lineageos.internal.util.DeviceKeysConstants.Action
import org.lineageos.internal.util.DeviceKeysConstants.canQuickTapRunWithScreenOff
import org.protonaosp.columbus.gates.*
import org.protonaosp.columbus.sensors.APSensor
import org.protonaosp.columbus.sensors.CHRESensor
import org.protonaosp.columbus.sensors.ColumbusController
import org.protonaosp.columbus.sensors.ColumbusSensor
import org.protonaosp.columbus.sensors.useApSensor

class ColumbusService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    // Services
    private var vibrator: Vibrator? = null
    private var prefs: SharedPreferences? = null
    private var action: Action = Action.NOTHING
    private var hapticEffect = VibrationEffect.EFFECT_HEAVY_CLICK
    private var sensor: ColumbusSensor? = null
    private var controller: ColumbusController? = null
    private var handler: Handler? = null
    private var wakelock: PowerManager.WakeLock? = null
    private var settingsGate: Settings? = null
    private var gates = setOf<Gate>()
    private val binder = Binder()

    // State
    private var screenCallbackRegistered = false

    // Settings
    private var enabled = true
    private var sensitivity = 0.03f
        set(value) {
            field = value
            if (enabled) {
                sendNewSensitivity()
            }
        }

    inner class Binder : android.os.Binder() {
        fun getService(): ColumbusService = this@ColumbusService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibrator = vibratorManager?.defaultVibrator
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakelock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        handler = Handler.createAsync(Looper.getMainLooper())
        prefs = getDePrefs()

        dlog(TAG, "Initializing Quick Tap gesture")

        val handler = handler ?: return
        if (useApSensor(this)) {
            dlog(TAG, "Initializing AP Sensor")
            sensor = APSensor(this, sensitivity, handler)
        } else {
            dlog(TAG, "Initializing CHRE Sensor")
            sensor = CHRESensor(this, sensitivity, handler)
        }
        val sensor = sensor ?: return

        controller = ColumbusController(this, sensor, handler)
        controller?.setGestureListener(columbusControllerListener)
        settingsGate = Settings(this, handler)
        gates =
            setOf(
                TelephonyActivity(this, handler),
                VrMode(this, handler),
                PocketDetection(this, handler),
                TableDetection(this, handler),
            )

        prefs?.migrateLegacyAction(this)

        updateHapticIntensity()
        updateAction()
        updateSensitivity()
        updateEnabled()
        updateScreenCallback()

        PackageStateManager.onCreate(this)

        // Only register for changes after initial pref updates
        prefs?.registerOnSharedPreferenceChangeListener(this)
        contentResolver.registerContentObserver(
            LineageSettings.System.getUriFor(LineageSettings.System.KEY_QUICK_TAP_ACTION),
            false,
            actionObserver,
            UserHandle.USER_ALL,
        )
    }

    override fun onDestroy() {
        // Cleanup preferences listener
        prefs?.unregisterOnSharedPreferenceChangeListener(this)
        contentResolver.unregisterContentObserver(actionObserver)

        PackageStateManager.onDestroy()

        // Only unregister if we previously registered
        if (screenCallbackRegistered) {
            unregisterReceiver(screenCallback)
            screenCallbackRegistered = false
        }

        // Cleanup gates
        deactivateGates()
        gates = emptySet()

        // Stop sensor and controller
        controller?.stopListening()
        sensor?.stopListening()

        // Release wakelock if held
        if (wakelock?.isHeld == true) {
            wakelock?.release()
        }


        super.onDestroy()
    }

    private fun updateSensitivity() {
        val prefs = prefs ?: return
        val value = prefs.getSensitivity(this)
        sensitivity =
            if (value <= 5) {
                value.toFloat() / 100f
            } else {
                (value - 5).toFloat() * 0.15f
            }
        dlog(TAG, "Setting sensitivity to $sensitivity")
    }

    private fun updateAction() {
        val prefs = prefs ?: return
        action = getQuickTapAction()
        dlog(TAG, "Setting action to $action")

        // For settings
        prefs
            .edit()
            .putBoolean(
                getString(R.string.pref_key_allow_screen_off_action_forced),
                !canQuickTapRunWithScreenOff(action),
            )
            .apply()
    }

    private val actionObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                updateAction()
                updateScreenCallback()
            }
        }

    private fun updateEnabled() {
        enabled = prefs?.getEnabled(this) ?: return
        if (enabled) {
            dlog(TAG, "Enabling gesture")
            activateGates()
            if (blockingGate()) {
                disableGesture()
            } else {
                enableGesture()
            }
        } else {
            dlog(TAG, "Disabling gesture")
            deactivateGates()
            disableGesture()
        }
    }

    private fun updateScreenCallback() {
        val allowScreenOff = prefs?.getAllowScreenOff(this) ?: return

        // Listen if either condition *can't* run when screen is off
        if (
            (!allowScreenOff || !canQuickTapRunWithScreenOff(action)) && !screenCallbackRegistered
        ) {
            val filter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
            dlog(TAG, "Listening to screen on/off events")
            registerReceiver(screenCallback, filter)
            screenCallbackRegistered = true
        } else if (screenCallbackRegistered) {
            dlog(TAG, "Stopped listening to screen on/off events")
            unregisterReceiver(screenCallback)
            screenCallbackRegistered = false
        }
    }

    private fun updateHapticIntensity() {
        val prefs = prefs ?: return
        hapticEffect =
            when (prefs.getHapticIntensity(this)) {
                0 -> VibrationEffect.EFFECT_TICK
                1 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                else -> VibrationEffect.EFFECT_HEAVY_CLICK
            }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == null) return
        when (key) {
            getString(R.string.pref_key_enabled) -> updateEnabled()
            getString(R.string.pref_key_sensitivity) -> updateSensitivity()
            getString(R.string.pref_key_haptic_intensity) -> updateHapticIntensity()
            getString(R.string.pref_key_allow_screen_off) -> updateScreenCallback()
        }
    }

    private fun enableGesture() {
        controller?.startListening()
    }

    private fun disableGesture() {
        controller?.stopListening()
    }

    private fun sendNewSensitivity() {
        controller?.updateSensitivity(sensitivity)
    }

    private fun onGestureDetected(msg: Int) {
        if (msg != 1) return
        wakelock?.acquire(2000L)
        try {
            val settingsGate = settingsGate
            if (settingsGate != null && settingsGate.isBlocking() && settingsGate.handleGesture()) {
                // Feedback for the "Quick Tap detected" toast in the settings screen.
                vibrator?.vibrate(VibrationEffect.createPredefined(hapticEffect), sonicAudioAttr)
                return
            }

            if (action == Action.NOTHING) return

            // PhoneWindowManager performs the action set in KEY_QUICK_TAP_ACTION and plays
            // the haptic only if the action could run.
            sendBroadcastAsUser(
                Intent(lineageos.content.Intent.ACTION_QUICK_TAP)
                    .setPackage(PACKAGE_ANDROID)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtra(lineageos.content.Intent.EXTRA_QUICK_TAP_HAPTIC_EFFECT, hapticEffect),
                UserHandle.CURRENT,
            )
        } finally {
            if (wakelock?.isHeld == true) {
                wakelock?.release()
            }
        }
    }

    private val columbusControllerListener =
        object : ColumbusController.GestureListener {
            override fun onGestureDetected(sensor: ColumbusSensor, msg: Int) {
                onGestureDetected(msg)
            }
        }

    private fun activateGates() {
        settingsGate?.registerListener(gateListener)
        gates.forEach { it.registerListener(gateListener) }
    }

    private fun deactivateGates() {
        settingsGate?.unregisterListener(gateListener)
        gates.forEach { it.unregisterListener(gateListener) }
    }

    private fun blockingGate(): Boolean {
        for (it in gates) {
            if (it.isBlocking()) {
                dlog(TAG, "Blocked by ${it::class.simpleName.orEmpty()} gate")
                return true
            }
        }
        return false
    }

    private val gateListener =
        object : Gate.Listener {
            override fun onGateChanged(gate: Gate) {
                if (enabled) {
                    updateEnabled()
                }
            }
        }

    private val screenCallback =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                if (enabled) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_ON -> {
                            dlog(TAG, "Enabling gesture due to screen on")
                            updateEnabled()
                        }
                        // Disable gesture entirely to save power
                        Intent.ACTION_SCREEN_OFF -> {
                            dlog(TAG, "Disabling gesture due to screen off")
                            deactivateGates()
                            disableGesture()
                        }
                    }
                }
            }
        }

    companion object {
        private const val PACKAGE_ANDROID = "android"

        private val sonicAudioAttr: AudioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
    }
}
