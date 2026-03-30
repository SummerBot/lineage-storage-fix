package io.github.summerbot.lineagestoragefix;

import android.os.Environment;

import java.io.File;
import java.util.UUID;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.android.settings";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("LineageStorageFix: loaded into " + lpparam.packageName);

        try {
            final File dataDir = Environment.getDataDirectory();

            // Hook StorageStatsManager.getTotalBytes(UUID)
            XposedHelpers.findAndHookMethod(
                    "android.app.usage.StorageStatsManager",
                    null,
                    "getTotalBytes",
                    UUID.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            long total = dataDir.getTotalSpace();
                            if (total > 0L) {
                                XposedBridge.log("LineageStorageFix: getTotalBytes -> " + total);
                                param.setResult(total);
                            }
                        }
                    }
            );

            // Hook StorageStatsManager.getFreeBytes(UUID)
            XposedHelpers.findAndHookMethod(
                    "android.app.usage.StorageStatsManager",
                    null,
                    "getFreeBytes",
                    UUID.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            long free = dataDir.getUsableSpace();
                            if (free >= 0L) {
                                XposedBridge.log("LineageStorageFix: getFreeBytes -> " + free);
                                param.setResult(free);
                            }
                        }
                    }
            );

            // Hook com.android.settings.Utils.getPrimaryStorageSize()
            XposedHelpers.findAndHookMethod(
                    "com.android.settings.Utils",
                    lpparam.classLoader,
                    "getPrimaryStorageSize",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            long total = dataDir.getTotalSpace();
                            if (total > 0L) {
                                XposedBridge.log("LineageStorageFix: getPrimaryStorageSize -> " + total);
                                param.setResult(total);
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
}
