package org.x04g.carouseldemo;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Java entry for the narrowly targeted native loader; this class never changes HOME. */
public final class Bootstrap {
    static final String PACKAGE = "com.google.assistant.launcher";
    static final String PROCESS = "com.google.android.apps.mediashell";
    static final String SOURCE = "/product/app/com.google.assistant.launcher/app.apk";
    private static final String OEM_SHA = "1e5b52f356ef7c078619dc369cccf704060aca484754cef454fa921598ed8c21";
    private static final String OEM_SIGNER = "e4f68be9a363883745e97766e83760d80cbd22e7705a38fbd2f322c0683ea152";
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    // Only the main looper reads or changes these identity sets/references.
    private static final Set<Application> APPLICATIONS = Collections.newSetFromMap(new IdentityHashMap<Application, Boolean>());
    private static WeakReference<Activity> candidate = new WeakReference<Activity>(null);
    private static WeakReference<Activity> resumed = new WeakReference<Activity>(null);
    private static volatile Handler main;
    private static volatile boolean verified;
    private static boolean enabled = true, rebindQueued;
    private static volatile int expectedUid = -1;
    private static long retrySerial;
    public enum State { NEW, WAITING_APPLICATION, REJECTED, REGISTERED, WAITING_HOME, INSTALLED, DISABLED, CLEANUP_INCOMPLETE }
    private static volatile State state = State.NEW;
    private Bootstrap() {}

    static void note(State next) {
        if (state != next) { state = next; Log.i("X04GCarouselDemo", next.name()); }
    }
    public static String state() { return state.name(); }

    public static void start(int uid) {
        if (uid < 10000 || uid > 19999 || Process.myUid() != uid || !STARTED.compareAndSet(false, true)) return;
        expectedUid = uid;
        note(State.WAITING_APPLICATION);
        Thread worker = new Thread(() -> {
            try {
                Application app = null;
                for (int i = 0; i < 240; i++) {
                    if (Looper.getMainLooper() != null) {
                        Object current = Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication").invoke(null);
                        if (current instanceof Application) { app = (Application) current; break; }
                    }
                    Thread.sleep(250);
                }
                // Early-start crashes were observed during file/digest work in the
                // predecessor. Defer that work until Application/Looper readiness.
                if (app == null || !verifyStock(app)) { note(State.REJECTED); return; }
                verified = true;
                main = new Handler(Looper.getMainLooper());
                final Application initial = app;
                main.post(() -> { if (registerApplication(initial)) findAlreadyResumed(0); });
            } catch (Exception rejected) { note(State.REJECTED); }
        }, "X04G-demo-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean verifyStock(Application app) throws Exception {
        if (Process.myUid() != expectedUid || !PROCESS.equals(Application.getProcessName())) return false;
        PackageManager pm = app.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(PACKAGE, 0);
        File source = new File(info.sourceDir).getCanonicalFile();
        if (info.uid != expectedUid || !source.equals(new File(SOURCE).getCanonicalFile()) || source.length() != 33126600L) return false;
        PackageInfo version = pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES);
        if (version.getLongVersionCode() != 111706L || !"11.51.6.416171605".equals(version.versionName)
                || version.signingInfo == null || version.signingInfo.hasMultipleSigners()) return false;
        Signature[] signers = version.signingInfo.getApkContentsSigners();
        if (signers == null || signers.length != 1 || !OEM_SIGNER.equals(hex(MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray())))) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(source)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return OEM_SHA.equals(hex(digest.digest()))
                && SOURCE.equals(pm.getApplicationInfo(PACKAGE, 0).sourceDir);
    }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) { out.append(Character.forDigit((value & 255) >>> 4, 16)); out.append(Character.forDigit(value & 15, 16)); }
        return out.toString();
    }
    static boolean isStockActivity(Activity activity) {
        return verified && Process.myUid() == expectedUid && activity != null
                && OemCarousel.ACTIVITY.equals(activity.getClass().getName())
                && !activity.isFinishing() && !activity.isDestroyed()
                && PACKAGE.equals(activity.getPackageName())
                && activity.getApplicationInfo().uid == expectedUid
                && SOURCE.equals(activity.getApplicationInfo().sourceDir);
    }

    private static final Application.ActivityLifecycleCallbacks CALLBACKS = new Application.ActivityLifecycleCallbacks() {
        public void onActivityCreated(Activity a, Bundle saved) {}
        public void onActivityStarted(Activity a) {}
        public void onActivityResumed(Activity a) {
            if (!isStockActivity(a)) return;
            if (!registerApplication(a.getApplication())) return;
            candidate = new WeakReference<Activity>(a);
            resumed = new WeakReference<Activity>(a);
            if (enabled) scheduleRetry(a);
            OemCarousel.resume(a, true);
        }
        public void onActivityPaused(Activity a) { pause(a); }
        public void onActivityStopped(Activity a) { pause(a); }
        public void onActivitySaveInstanceState(Activity a, Bundle saved) {}
        public void onActivityPreSaveInstanceState(Activity a, Bundle saved) { OemCarousel.teardown(a); }
        public void onActivityPostSaveInstanceState(Activity a, Bundle saved) { rebind(a); }
        public void onActivityPreDestroyed(Activity a) { OemCarousel.teardown(a); }
        public void onActivityDestroyed(Activity a) {
            OemCarousel.teardown(a);
            if (candidate.get() == a) { candidate.clear(); retrySerial++; }
            if (resumed.get() == a) resumed.clear();
        }
    };
    private static void pause(Activity activity) {
        if (resumed.get() == activity) { resumed.clear(); retrySerial++; }
        OemCarousel.resume(activity, false);
    }
    private static boolean registerApplication(Application app) {
        if (app == null || Looper.myLooper() != Looper.getMainLooper()) return false;
        if (!APPLICATIONS.add(app)) return true;
        // Add before registration and never recursively rediscover from this method.
        // The initial shell Application can differ from Activity.getApplication().
        ComponentCallbacks2 memory = new ComponentCallbacks2() {
            public void onConfigurationChanged(Configuration ignored) {}
            public void onLowMemory() { OemCarousel.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL); }
            public void onTrimMemory(int level) { OemCarousel.onTrimMemory(level); }
        };
        boolean lifecycleRegistered = false;
        try {
            app.registerActivityLifecycleCallbacks(CALLBACKS);
            lifecycleRegistered = true;
            app.registerComponentCallbacks(memory);
            note(State.REGISTERED);
            return true;
        } catch (RuntimeException rejected) {
            if (lifecycleRegistered) try { app.unregisterActivityLifecycleCallbacks(CALLBACKS); } catch (RuntimeException ignored) {}
            try { app.unregisterComponentCallbacks(memory); } catch (RuntimeException ignored) {}
            APPLICATIONS.remove(app); note(State.REJECTED); return false;
        }
    }
    private static void findAlreadyResumed(int attempt) {
        if (!enabled || !verified || main == null || candidate.get() != null) return;
        try {
            Class<?> type = Class.forName("android.app.ActivityThread");
            Object thread = type.getDeclaredMethod("currentActivityThread").invoke(null);
            Object records = field(type, "mActivities").get(thread);
            if (records instanceof Map) for (Object record : ((Map<?, ?>) records).values()) {
                Object value = field(record.getClass(), "activity").get(record);
                if (!(value instanceof Activity) || !isStockActivity((Activity) value)) continue;
                Activity a = (Activity) value;
                if (!registerApplication(a.getApplication())) continue;
                boolean paused = field(record.getClass(), "paused").getBoolean(record);
                boolean stopped = field(record.getClass(), "stopped").getBoolean(record);
                if (!paused && !stopped) {
                    candidate = new WeakReference<Activity>(a);
                    resumed = new WeakReference<Activity>(a);
                    scheduleRetry(a); return;
                }
            }
        } catch (Exception notReady) { /* A later lifecycle callback or bounded scan may succeed. */ }
        if (attempt < 120 && candidate.get() == null) main.postDelayed(() -> findAlreadyResumed(attempt + 1), 500);
    }
    private static void scheduleRetry(Activity a) { retry(a, 0, ++retrySerial); }
    private static void retry(Activity activity, int attempt, long serial) {
        if (!enabled || !isStockActivity(activity) || candidate.get() != activity || resumed.get() != activity || serial != retrySerial) return;
        try {
            if (!OemCarousel.installedFor(activity)) OemCarousel.installVerified(activity);
            if (!OemCarousel.installedFor(activity)) throw new IllegalStateException();
            OemCarousel.resume(activity, true);
            note(State.INSTALLED);
        } catch (Exception notReady) {
            note(State.WAITING_HOME);
            if (attempt < 120) main.postDelayed(() -> retry(activity, attempt + 1, serial), 500);
        }
    }
    static void attach(Activity activity) {
        Handler handler = main;
        if (handler == null || !isStockActivity(activity)) return;
        handler.post(() -> {
            if (!isStockActivity(activity)) return;
            enabled = true;
            if (!registerApplication(activity.getApplication())) return;
            candidate.clear(); resumed.clear(); retrySerial++;
            findAlreadyResumed(0);
        });
    }
    static void disable(Activity activity) {
        if (activity == null || !OemCarousel.ACTIVITY.equals(activity.getClass().getName())
                || candidate.get() != null && candidate.get() != activity) return;
        enabled = false; retrySerial++; candidate.clear(); resumed.clear(); note(State.DISABLED);
    }
    static void rebind(Activity activity) {
        Handler handler = main;
        if (handler == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post(() -> rebind(activity)); return; }
        if (!enabled || candidate.get() != activity || resumed.get() != activity || rebindQueued) return;
        rebindQueued = true;
        handler.post(() -> { rebindQueued = false; if (enabled && candidate.get() == activity && resumed.get() == activity) scheduleRetry(activity); });
    }
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException missing) {}
        throw new NoSuchFieldException();
    }
}
