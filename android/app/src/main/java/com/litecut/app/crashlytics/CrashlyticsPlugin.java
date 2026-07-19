package com.litecut.app.crashlytics;

import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

@CapacitorPlugin(name = "Crashlytics")
public class CrashlyticsPlugin extends Plugin {
    private static final String TAG = "CrashlyticsPlugin";

    private FirebaseCrashlytics getCrashlytics() {
        try {
            return FirebaseCrashlytics.getInstance();
        } catch (IllegalStateException e) {
            Log.w(TAG, "Firebase Crashlytics is not initialized (likely missing google-services.json)");
            return null;
        }
    }

    @PluginMethod
    public void log(PluginCall call) {
        String message = call.getString("message");
        if (message == null) {
            call.reject("Must provide message");
            return;
        }

        FirebaseCrashlytics crashlytics = getCrashlytics();
        if (crashlytics != null) {
            crashlytics.log(message);
            call.resolve();
        } else {
            Log.d(TAG, "Crashlytics log: " + message);
            call.resolve(); // resolve gracefully so it doesn't break app when google-services.json is missing
        }
    }

    @PluginMethod
    public void setCustomKey(PluginCall call) {
        String key = call.getString("key");
        String value = call.getString("value");
        if (key == null || value == null) {
            call.reject("Must provide key and value");
            return;
        }

        FirebaseCrashlytics crashlytics = getCrashlytics();
        if (crashlytics != null) {
            crashlytics.setCustomKey(key, value);
            call.resolve();
        } else {
            Log.d(TAG, "Crashlytics custom key: " + key + "=" + value);
            call.resolve();
        }
    }

    @PluginMethod
    public void setUserId(PluginCall call) {
        String userId = call.getString("userId");
        if (userId == null) {
            call.reject("Must provide userId");
            return;
        }

        FirebaseCrashlytics crashlytics = getCrashlytics();
        if (crashlytics != null) {
            crashlytics.setUserId(userId);
            call.resolve();
        } else {
            Log.d(TAG, "Crashlytics userId: " + userId);
            call.resolve();
        }
    }

    @PluginMethod
    public void recordException(PluginCall call) {
        String message = call.getString("message");
        if (message == null) {
            call.reject("Must provide exception message");
            return;
        }

        FirebaseCrashlytics crashlytics = getCrashlytics();
        if (crashlytics != null) {
            crashlytics.recordException(new Exception(message));
            call.resolve();
        } else {
            Log.d(TAG, "Crashlytics recorded non-fatal exception: " + message);
            call.resolve();
        }
    }

    @PluginMethod
    public void crash(PluginCall call) {
        // Trigger a test crash
        Log.i(TAG, "Triggering test crash via CrashlyticsPlugin");
        new Thread(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException("Test Crash requested via CrashlyticsPlugin");
            }
        }).start();
        call.resolve();
    }
}
