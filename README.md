# Biometric App Lock

An Xposed module to lock apps behind biometric authentication

![Android 11+](https://img.shields.io/badge/Android-11%2B-1B5E20?style=for-the-badge&logo=android&logoColor=white)
![libxposed API 101](https://img.shields.io/badge/libxposed-API_101-ff69b4?style=for-the-badge)

> [!WARNING]
> Pre-1.0 releases (0.x.x) and any build tagged `-test` are experimental.

> [!NOTE]
> For stock AOSP and Pixel ROMs. Other ROMs may work but are untested, use at your own risk. If you're on HyperOS, OxygenOS or ColorOS, your ROM already ships a native app lock and you probably don't need this.

## About this module

Stock Android never had a native per-app lock, and [Private Space](https://source.android.com/docs/security/features/private-space) (available since Android 15 Beta 2) is a secondary isolated profile where apps run as separate installs with their own data.

This module intercepts activity launches at the system framework level before the target app starts. The Activity is never created until auth succeeds. Tapping a locked app from the recents screen is intercepted too.

A reboot is required after install or update because framework hooks only load at boot (for now 😉). Changing which apps are locked takes effect immediately.

## Requirements

- Android 11+ with an enrolled biometric
- Xposed manager with libxposed API 101 support

## Install

1. Install APK from [Releases](../../releases)
2. Enable module in your Xposed manager with System Framework scope
3. Reboot
4. Select apps to lock in the Apps tab

## License

[![GPLv3](https://img.shields.io/badge/LICENSE-GPL_v3-%23A42E2B?style=for-the-badge&logo=gnu&logoColor=white&logoPosition=right)](LICENSE)
