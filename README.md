# lineage-storage-fix

An LSPosed/Xposed module that fixes the Storage page in Settings by returning the real filesystem size for `/data` instead of the rounded retail capacity.

## What it hooks

- `com.android.settingslib.deviceinfo.StorageManagerVolumeProvider#getTotalBytes(...)`
- `com.android.settingslib.deviceinfo.StorageManagerVolumeProvider#getFreeBytes(...)`

## Scope

Enable the module only for:

- `com.android.settings`

## Build

This repository includes a GitHub Actions workflow. Push to `main` or run **Actions -> Build APK -> Run workflow**.

The generated APK will be available in the workflow artifacts.
