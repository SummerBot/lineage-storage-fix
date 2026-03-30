package io.github.summerbot.lineagestoragefix;

import android.app.usage.StorageStatsManager;
import android.os.storage.VolumeInfo;

import java.io.File;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "LineageStorageFix";
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String TARGET_CLASS = "com.android.settingslib.deviceinfo.StorageManagerVolumeProvider";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": loaded into " + lpparam.packageName);

        final Class<?> providerClass = XposedHelpers.findClass(TARGET_CLASS, lpparam.classLoader);

        XposedHelpers.findAndHookMethod(providerClass,
                "getTotalBytes",
                StorageStatsManager.class,
                VolumeInfo.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        VolumeInfo volume = (VolumeInfo) param.args[1];
                        File path = resolvePath(volume);
                        if (path == null) {
                            return;
                        }
                        long total = path.getTotalSpace();
                        if (total > 0L) {
                            XposedBridge.log(TAG + ": totalBytes(" + path + ") = " + total);
                            param.setResult(total);
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(providerClass,
                "getFreeBytes",
                StorageStatsManager.class,
                VolumeInfo.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        VolumeInfo volume = (VolumeInfo) param.args[1];
                        File path = resolvePath(volume);
                        if (path == null) {
                            return;
                        }
                        long free = path.getUsableSpace();
                        if (free >= 0L) {
                            XposedBridge.log(TAG + ": freeBytes(" + path + ") = " + free);
                            param.setResult(free);
                        }
                    }
                });
    }

    private static File resolvePath(VolumeInfo volume) {
        if (volume == null) {
            return new File("/data");
        }
        File path = volume.getPath();
        if (path != null) {
            return path;
        }
        return new File("/data");
    }
}
