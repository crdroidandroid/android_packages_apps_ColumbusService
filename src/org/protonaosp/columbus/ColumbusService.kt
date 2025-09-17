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
import android.media.AudioAttributes
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.protonaosp.columbus.actions.*
import org.protonaosp.columbus.gates.*
import org.protonaosp.columbus.sensors.APSensor
import org.protonaosp.columbus.sensors.CHRESensor
import org.protonaosp.columbus.sensors.ColumbusController
import org.protonaosp.columbus.sensors.ColumbusSensor
import org.protonaosp.columbus.sensors.useApSensor

class ColumbusService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    // Services
    private lateinit var vibrator: Vibrator
    private lateinit var prefs: SharedPreferences
    private lateinit var action: Action
    private lateinit var vibDoubleTap: VibrationEffect
    private lateinit var sensor: ColumbusSensor
    private lateinit var controller: ColumbusController
    private lateinit var handler: Handler
    private lateinit var wakelock: PowerManager.WakeLock
    private lateinit var settingsGate: Settings
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
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        handler = Handler.createAsync(Looper.getMainLooper())
        prefs = getDePrefs()

        dlog(TAG, "Initializing Quick Tap gesture")

        if (useApSensor(this)) {
            dlog(TAG, "Initializing AP Sensor")
            sensor = APSensor(this, sensitivity, handler)
        } else {
            dlog(TAG, "Initializing CHRE Sensor")
            sensor = CHRESensor(this, sensitivity, handler)
        }
        controller = ColumbusController(this, sensor, handler)
        controller.setGestureListener(columbusControllerListener)
        settingsGate = Settings(this, handler)
        gates =
            setOf(
                TelephonyActivity(this, handler),
                VrMode(this, handler),
                PocketDetection(this, handler),
                TableDetection(this, handler),
            )

        updateHapticIntensity()
        updateAction()
        updateSensitivity()
        updateEnabled()
        updateScreenCallback()

        PackageStateManager.onCreate(this)

        // Only register for changes after initial pref updates
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        // Cleanup preferences listener
        prefs.unregisterOnSharedPreferenceChangeListener(this)

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
        controller.stopListening()
        sensor.stopListening()

        // Release wakelock if held
        if (wakelock.isHeld) {
            wakelock.release()
        }

        // Clear references
        action = DummyAction(this)
        super.onDestroy()
    }

    private fun createAction(key: String): Action {
        return when (key) {
            "screenshot" -> ScreenshotAction(this)
            "assistant" -> AssistantAction(this)
            "silence_call" -> SilenceCallAction(this)
            "media" -> PlayPauseAction(this)
            "notifications" -> NotificationAction(this)
            "overview" -> RecentsAction(this)
            "camera" -> CameraAction(this)
            "power_menu" -> PowerMenuAction(this)
            "mute" -> MuteAction(this)
            "flashlight" -> FlashlightAction(this)
            "screen" -> ScreenAction(this)
            "launch" -> LaunchAction(this)

            else -> DummyAction(this)
        }
    }

    private fun updateSensitivity() {
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
        val key = prefs.getAction(this)
        dlog(TAG, "Setting action to $key")
        if (::action.isInitialized) {
            action.destroy()
        }
        action = createAction(key)

        // For settings
        prefs
            .edit()
            .putBoolean(
                getString(R.string.pref_key_allow_screen_off_action_forced),
                !action.canRunWhenScreenOff(),
            )
            .apply()
    }

    private fun updateEnabled() {
        enabled = prefs.getEnabled(this)
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
        val allowScreenOff = prefs.getAllowScreenOff(this)

        // Listen if either condition *can't* run when screen is off
        if ((!allowScreenOff || !action.canRunWhenScreenOff()) && !screenCallbackRegistered) {
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
        val value = prefs.getHapticIntensity(this)
        vibDoubleTap =
            when (value) {
                0 -> EFFECT_TICK
                1 -> EFFECT_DOUBLE_CLICK
                2 -> EFFECT_HEAVY_CLICK
                else -> EFFECT_HEAVY_CLICK
            }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == null) return
        when (key) {
            getString(R.string.pref_key_enabled) -> updateEnabled()
            getString(R.string.pref_key_sensitivity) -> updateSensitivity()
            // Action might change screen callback behavior
            getString(R.string.pref_key_action) -> {
                updateAction()
                updateScreenCallback()
            }
            getString(R.string.pref_key_haptic_intensity) -> updateHapticIntensity()
            getString(R.string.pref_key_allow_screen_off) -> updateScreenCallback()
        }
    }

    private fun enableGesture() {
        controller.startListening()
    }

    private fun disableGesture() {
        controller.stopListening()
    }

    private fun sendNewSensitivity() {
        controller.updateSensitivity(sensitivity)
    }

    private fun onGestureDetected(msg: Int) {
        if (msg != 1) return

        wakelock.acquire(2000L)
        try {
            if (!action.canRun()) return

            vibrator.vibrate(vibDoubleTap, sonicAudioAttr)
            if (settingsGate.isBlocking() && settingsGate.handleGesture()) {
                return
            }
            action.run()
        } finally {
            if (wakelock.isHeld) {
                wakelock.release()
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
        settingsGate.registerListener(gateListener)
        gates.forEach { it.registerListener(gateListener) }
    }

    private fun deactivateGates() {
        settingsGate.unregisterListener(gateListener)
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
        // Vibration effects from HapticFeedbackConstants
        // Duplicated because we can't use performHapticFeedback in a background service
        private val sonicAudioAttr: AudioAttributes =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

        private val EFFECT_TICK =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        private val EFFECT_DOUBLE_CLICK =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            } else {
                VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        private val EFFECT_HEAVY_CLICK =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            } else {
                VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE)
            }
    }
}
