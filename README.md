# Quick Tap Service

This is an open-source implementation of the Pixel Quick Tap gesture, written from scratch for portability and customizability.

Quick Tap, codename Columbus, is a gesture powered by AP sensors and CHRE sensor on the Pixel 4a (5G) and later that is used to activate the Google Assistant and several other actions on stock.

This app is a reverse-engineered Android client that runs as a standalone service and talks to the AP sensor and CHRE sensor for gesture functionality.

## Features

- Seamless integration in Settings → System → Gestures → Quick Tap with no extra changes
- Integration with Settings search
- Many actions to perform on gesture trigger
  - Take screenshot
  - Open assistant
  - Silence call
  - Play or pause media
  - Open notification shelf
  - See recent apps
  - Open camera
  - Toggle power menu
  - Mute calls & notifications (replicates default power + volume-up "prevent ringing" gesture)
  - Toggle flashlight
  - Toggle screen
  - Launch app and shortcut
- Setting to control whether gesture is enabled when the screen is off
- Contextually-appropriate haptic feedback with modern effects
  - Configurable haptic intensity for back tap
  - Reject for unavailable action (e.g. if flashlight can't turn on because camera is in use)
  - Blocking action in certain conditions (e.g. touch gesture in progress)

## Integration

This service depends on following features:

- Mendatory:
  - When using AP Sensor:
    - Accelerometer
    - Gyroscope
  - When using CHRE Sensor (Google Pixel devices):
    - Contexthub
- General:
  - Vibrator (For haptic feedback. Optional)

This app doesn't provide proprietary resources which generally can be found on vendor apps for legal compliance.<br>
All files (including lottie animation) in `res/raw/` directory in this project are empty dummy.<br>
If you want to use TensorFlow Lite integration or lottie animations,<br>
create your own overlay and put those in there (with `aaptflags: [ "-0 .tflite", ],` if you are using RRO).<br>

Sync this repo to packages/apps/ColumbusService.

Add the following to your device tree:

```make
# Quick Tap
PRODUCT_PACKAGES += \
    ColumbusService
```

The gesture actions are the key actions of lineage-sdk, the same ones offered for hardware keys and navigation gestures. The chosen action is stored in `LineageSettings.System.KEY_QUICK_TAP_ACTION` (with `key_quick_tap_action_launch_app` and `key_quick_tap_action_launch_shortcut` for the "Open app" action), and each detected gesture sends the protected `lineageos.intent.action.QUICK_TAP` broadcast, on which PhoneWindowManager performs that action. This requires a lineage-sdk and frameworks/base with Quick Tap support.

Default settings can be changed by overlaying [res/values/config.xml](https://github.com/TheParasiteProject/packages_apps_ColumbusService/blob/main/res/values/config.xml).

## Acknowledgements

- [Active Edge Service](https://github.com/ProtonAOSP/android_packages_apps_ElmyraService) by [ProtonAOSP](https://github.com/ProtonAOSP) : Code base
- [vendor_pixel-framework](https://github.com/PixelExperience/vendor_pixel-framework) by [PixelExperience](https://github.com/PixelExperience) : APSensor support for wider devices
- [TapTap](https://github.com/KieronQuinn/TapTap) by [Kieron Quinn](https://github.com/KieronQuinn) : Port of the double tap on back of device feature from Android 12 to any Android 7.0+ device

## License

All code in this repository is licensed under the GPL-3.0 License.

Copyright (C) 2024-2025 TheParasiteProject
