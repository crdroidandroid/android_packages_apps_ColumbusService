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

Sync this repo to packages/apps/ColumbusService.

Add the following to your device tree:

```make
# Quick Tap
PRODUCT_PACKAGES += \
    ColumbusService
```

While this service is designed to be as portable and self-contained as possible, Android does not provide the necessary APIs to implement all gesture actions out-of-the-box. This means that some commits must be added to frameworks/base to expose the APIs for full functionality:

- For screenshot action: [SystemUI: Allow privileged system apps to access screenshot service](https://github.com/ProtonAOSP/android_frameworks_base/commit/013c590411435569077228aacf1e246678c366ab)
- For assistant action: [core: Expose method to start assistant through Binder](https://github.com/ProtonAOSP/android_frameworks_base/commit/2b950e103e865aa6a1fe8a917964e0069d4c4037)
- For toggle recents action: [core: Expose method to toggle recent apps through Binder](https://github.com/TheParasiteProject/frameworks_base/commit/903aa739452e47b765434cc77a89b6e7f49f972b)

Default settings can be changed by overlaying [res/values/config.xml](https://github.com/TheParasiteProject/packages_apps_ColumbusService/blob/main/res/values/config.xml).

## Acknowledgements

- [Active Edge Service](https://github.com/ProtonAOSP/android_packages_apps_ElmyraService) by [ProtonAOSP](https://github.com/ProtonAOSP) : Code base
- [vendor_pixel-framework](https://github.com/PixelExperience/vendor_pixel-framework) by [PixelExperience](https://github.com/PixelExperience) : APSensor support for wider devices
- [TapTap](https://github.com/KieronQuinn/TapTap) by [Kieron Quinn](https://github.com/KieronQuinn) : Port of the double tap on back of device feature from Android 12 to any Android 7.0+ device

## License

All code in this repository is licensed under the GPL-3.0 License.

Copyright (C) 2024-2025 TheParasiteProject
