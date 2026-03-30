package io.github.summerbot.lineagestoragefix;

import android.app.usage.StorageStatsManager;

import java.io.File;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String TARGET_CLASS =
            "com.android.settingslib.deviceinfo.StorageManagerVolumeProvider";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("LineageStorageFix: loaded into " + lpparam.packageName);

        try {
            Class<?> providerClass = XposedHelpers.findClass(TARGET_CLASS, lpparam.classLoader);

            // VolumeInfo is a hidden framework class; resolve it at runtime
            Class<?> volumeInfoClass = XposedHelpers.findClass(
                    "android.os.storage.VolumeInfo",
                    null
            );

            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "getTotalBytes",
                    StorageStatsManager.class,
                    volumeInfoClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object volume = param.args[1];
                            File path = resolvePath(volume);
                            if (path != null) {
                                long total = path.getTotalSpace();
                                if (total > 0L) {
                                    XposedBridge.log("LineageStorageFix: total=" + total + " path=" + path);
                                    param.setResult(total);
                                }
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "getFreeBytes",
                    StorageStatsManager.class,
                    volumeInfoClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object volume = param.args[1];
                            File path = resolvePath(volume);
                            if (path != null) {
                                long free = path.getUsableSpace();
                                if (free >= 0L) {
                                    XposedBridge.log("LineageStorageFix: free=" + free + " path=" + path);
                                    param.setResult(free);
                                }
                            }
                        }
                    }
            );

            XposedBridge.log("LineageStorageFix: hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("LineageStorageFix: failed to install hooks");
            XposedBridge.log(t);
        }
    }

    private static File resolvePath(Object volume) {
        if (volume == null) {
            return null;
        }

        try {
            Method getPath = volume.getClass().getMethod("getPath");
            Object result = getPath.invoke(volume);
            if (result instanceof File) {
                return (File) result;
            }
        } catch (Throwable t) {
            XposedBridge.log("LineageStorageFix: resolvePath failed: " + t);
        }

        return null;
    }
}
