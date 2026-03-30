# Lineage Storage Fix

An LSPosed/Xposed module that makes the Android Settings Storage page show the real `/data` filesystem size instead of rounded retail storage capacity.

This is useful on repartitioned devices, including dual-boot setups, where Settings may incorrectly display values such as `256 GB` even though the actual Android `userdata` partition is much smaller.

## What it does

This module hooks the storage size calculation used by Settings and returns the real filesystem values from `/data`.

Current hook targets:

- `android.app.usage.StorageStatsManager#getTotalBytes(UUID)`
- `android.app.usage.StorageStatsManager#getFreeBytes(UUID)`
- `com.android.settingslib.deviceinfo.StorageManagerVolumeProvider#getTotalBytes(...)`
- `com.android.settingslib.deviceinfo.StorageManagerVolumeProvider#getFreeBytes(...)`

## Tested use case

- Redmi K20 Pro / Mi 9T Pro (`raphael`)
- LineageOS 21 based third-party builds
- Repartitioned / dual-boot device layouts

## Scope

Enable the module only for:

- `com.android.settings`

Do not enable it for `system` unless you are debugging.

## Requirements

- Root
- Zygisk
- LSPosed (or compatible Xposed environment)

## Notes

- This module is designed to show the **real `/data` filesystem size**.
- Android may still format file sizes in decimal units, so a partition often called `128G` in modding communities can appear as about `138 GB` in Settings.
- This module does **not** force a fake `128 GB` label. It shows the real byte-based size.

## Build

This repository includes a GitHub Actions workflow.

To build:

1. Push changes to `main`, or
2. Open **Actions** → **Build APK** → **Run workflow**

The generated APK will be available in the workflow artifacts.

## Install

1. Download the generated APK
2. Install it on the phone
3. Enable the module in LSPosed
4. Scope it to `com.android.settings`
5. Force stop Settings or reboot

## Logging

The module logs only:

- module load
- hook install result
- first hit of each active hook
- final hook summary

## Disclaimer

This module only changes how Settings reads and displays storage values. It does not repartition storage and does not modify the actual filesystem size.
