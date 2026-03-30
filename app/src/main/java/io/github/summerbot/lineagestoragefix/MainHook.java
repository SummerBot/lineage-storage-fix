package io.github.summerbot.lineagestoragefix;

import android.app.usage.StorageStatsManager;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "LineageStorageFix";
    private static final String TARGET_PACKAGE = "com.android.settings";

    private static final AtomicInteger TOTAL_CALLS = new AtomicInteger(0);
    private static final AtomicInteger FREE_CALLS = new AtomicInteger(0);
    private static final AtomicInteger PROVIDER_TOTAL_CALLS = new AtomicInteger(0);
    private static final AtomicInteger PROVIDER_FREE_CALLS = new AtomicInteger(0);
    private static final AtomicInteger PRIMARY_SIZE_CALLS = new AtomicInteger(0);

    private static final AtomicBoolean TOTAL_TRACE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean FREE_TRACE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean PROVIDER_TOTAL_TRACE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean PROVIDER_FREE_TRACE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean PRIMARY_SIZE_TRACE_LOGGED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        final File dataDir = Environment.getDataDirectory();
        log("loaded into package=" + lpparam.packageName
                + " process=" + lpparam.processName
                + " dataDir=" + dataDir.getAbsolutePath());

        int ok = 0;
        int skipped = 0;

        if (hookStorageStatsTotal(dataDir)) ok++; else skipped++;
        if (hookStorageStatsFree(dataDir)) ok++; else skipped++;
        if (hookProviderTotal(lpparam.classLoader)) ok++; else skipped++;
        if (hookProviderFree(lpparam.classLoader)) ok++; else skipped++;
        if (hookPrimaryStorageSize(lpparam.classLoader, dataDir)) ok++; else skipped++;

        log("hook summary: installed=" + ok + " skipped=" + skipped);
    }

    private static boolean hookStorageStatsTotal(final File dataDir) {
        try {
            Class<?> cls = XposedHelpers.findClass("android.app.usage.StorageStatsManager", null);
            Method target = null;
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().equals("getTotalBytes")
                        && m.getReturnType() == long.class
                        && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0] == UUID.class) {
                    target = m;
                    break;
                }
            }

            if (target == null) {
                log("hook skipped: StorageStatsManager#getTotalBytes(UUID) not found");
                return false;
            }

            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    long total = dataDir.getTotalSpace();
                    if (total > 0L) {
                        UUID uuid = safeUuid(param.args);
                        int n = TOTAL_CALLS.incrementAndGet();
                        if (n <= 3) {
                            log("getTotalBytes uuid=" + uuid
                                    + " => " + total
                                    + " path=" + dataDir.getAbsolutePath()
                                    + " call=" + n);
                        }
                        logTraceOnce("getTotalBytes", TOTAL_TRACE_LOGGED);
                        param.setResult(total);
                    }
                }
            });

            log("hook ok: StorageStatsManager#getTotalBytes(UUID)");
            return true;
        } catch (Throwable t) {
            log("hook failed: StorageStatsManager#getTotalBytes(UUID): " + t);
            return false;
        }
    }

    private static boolean hookStorageStatsFree(final File dataDir) {
        try {
            Class<?> cls = XposedHelpers.findClass("android.app.usage.StorageStatsManager", null);
            Method target = null;
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().equals("getFreeBytes")
                        && m.getReturnType() == long.class
                        && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0] == UUID.class) {
                    target = m;
                    break;
                }
            }

            if (target == null) {
                log("hook skipped: StorageStatsManager#getFreeBytes(UUID) not found");
                return false;
            }

            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    long free = dataDir.getUsableSpace();
                    if (free >= 0L) {
                        UUID uuid = safeUuid(param.args);
                        int n = FREE_CALLS.incrementAndGet();
                        if (n <= 3) {
                            log("getFreeBytes uuid=" + uuid
                                    + " => " + free
                                    + " path=" + dataDir.getAbsolutePath()
                                    + " call=" + n);
                        }
                        logTraceOnce("getFreeBytes", FREE_TRACE_LOGGED);
                        param.setResult(free);
                    }
                }
            });

            log("hook ok: StorageStatsManager#getFreeBytes(UUID)");
            return true;
        } catch (Throwable t) {
            log("hook failed: StorageStatsManager#getFreeBytes(UUID): " + t);
            return false;
        }
    }

    private static boolean hookProviderTotal(final ClassLoader classLoader) {
        try {
            Class<?> providerClass = XposedHelpers.findClass(
                    "com.android.settingslib.deviceinfo.StorageManagerVolumeProvider",
                    classLoader
            );
            Class<?> volumeInfoClass = XposedHelpers.findClass("android.os.storage.VolumeInfo", null);

            Method target = null;
            for (Method m : providerClass.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (m.getName().equals("getTotalBytes")
                        && m.getReturnType() == long.class
                        && params.length == 2
                        && params[0] == StorageStatsManager.class
                        && params[1] == volumeInfoClass) {
                    target = m;
                    break;
                }
            }

            if (target == null) {
                log("hook skipped: StorageManagerVolumeProvider#getTotalBytes(...) not found");
                return false;
            }

            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object volume = param.args[1];
                    File path = resolvePath(volume);
                    if (path != null) {
                        long total = path.getTotalSpace();
                        if (total > 0L) {
                            int n = PROVIDER_TOTAL_CALLS.incrementAndGet();
                            if (n <= 3) {
                                log("provider.getTotalBytes path=" + path.getAbsolutePath()
                                        + " => " + total
                                        + " call=" + n);
                            }
                            logTraceOnce("provider.getTotalBytes", PROVIDER_TOTAL_TRACE_LOGGED);
                            param.setResult(total);
                        }
                    }
                }
            });

            log("hook ok: StorageManagerVolumeProvider#getTotalBytes(...)");
            return true;
        } catch (Throwable t) {
            log("hook skipped/failed: StorageManagerVolumeProvider#getTotalBytes(...): " + t);
            return false;
        }
    }

    private static boolean hookProviderFree(final ClassLoader classLoader) {
        try {
            Class<?> providerClass = XposedHelpers.findClass(
                    "com.android.settingslib.deviceinfo.StorageManagerVolumeProvider",
                    classLoader
            );
            Class<?> volumeInfoClass = XposedHelpers.findClass("android.os.storage.VolumeInfo", null);

            Method target = null;
            for (Method m : providerClass.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (m.getName().equals("getFreeBytes")
                        && m.getReturnType() == long.class
                        && params.length == 2
                        && params[0] == StorageStatsManager.class
                        && params[1] == volumeInfoClass) {
                    target = m;
                    break;
                }
            }

            if (target == null) {
                log("hook skipped: StorageManagerVolumeProvider#getFreeBytes(...) not found");
                return false;
            }

            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object volume = param.args[1];
                    File path = resolvePath(volume);
                    if (path != null) {
                        long free = path.getUsableSpace();
                        if (free >= 0L) {
                            int n = PROVIDER_FREE_CALLS.incrementAndGet();
                            if (n <= 3) {
                                log("provider.getFreeBytes path=" + path.getAbsolutePath()
                                        + " => " + free
                                        + " call=" + n);
                            }
                            logTraceOnce("provider.getFreeBytes", PROVIDER_FREE_TRACE_LOGGED);
                            param.setResult(free);
                        }
                    }
                }
            });

            log("hook ok: StorageManagerVolumeProvider#getFreeBytes(...)");
            return true;
        } catch (Throwable t) {
            log("hook skipped/failed: StorageManagerVolumeProvider#getFreeBytes(...): " + t);
            return false;
        }
    }

    private static boolean hookPrimaryStorageSize(final ClassLoader classLoader, final File dataDir) {
        try {
            Class<?> utilsClass = XposedHelpers.findClass("com.android.settings.Utils", classLoader);

            Method target = null;
            for (Method m : utilsClass.getDeclaredMethods()) {
                if (m.getName().equals("getPrimaryStorageSize")
                        && m.getReturnType() == long.class
                        && m.getParameterTypes().length == 0) {
                    target = m;
                    break;
                }
            }

            if (target == null) {
                log("hook skipped: com.android.settings.Utils#getPrimaryStorageSize() not found");
                return false;
            }

            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    long total = dataDir.getTotalSpace();
                    if (total > 0L) {
                        int n = PRIMARY_SIZE_CALLS.incrementAndGet();
                        if (n <= 3) {
                            log("getPrimaryStorageSize => " + total + " call=" + n);
                        }
                        logTraceOnce("getPrimaryStorageSize", PRIMARY_SIZE_TRACE_LOGGED);
                        param.setResult(total);
                    }
                }
            });

            log("hook ok: com.android.settings.Utils#getPrimaryStorageSize()");
            return true;
        } catch (Throwable t) {
            log("hook skipped/failed: com.android.settings.Utils#getPrimaryStorageSize(): " + t);
            return false;
        }
    }

    private static UUID safeUuid(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object arg = args[0];
        if (arg instanceof UUID) {
            return (UUID) arg;
        }
        return null;
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
            log("resolvePath failed: " + t);
        }
        return null;
    }

    private static void logTraceOnce(String label, AtomicBoolean once) {
        if (once.compareAndSet(false, true)) {
            log(label + " first-hit stack:\n" + Log.getStackTraceString(new Throwable(label)));
        }
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }
}
