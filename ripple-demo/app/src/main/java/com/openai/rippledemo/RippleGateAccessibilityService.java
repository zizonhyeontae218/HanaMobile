package com.openai.rippledemo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

public class RippleGateAccessibilityService extends AccessibilityService {
    private static final String PREFS = "ripple_code_v4";
    private static final String KEY_POST_GATE = "post_unlock_gate";
    private static final long GATE_DEBOUNCE_MS = 1800L;

    private boolean armed = false;
    private long lastGateAt = 0L;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                armed = true;
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                tryLaunchGate("user_present");
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardLocked()) armed = true;
            }
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 90;
        info.flags = 0;
        setServiceInfo(info);

        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || !pm.isInteractive()) return;

        if (km != null && km.isKeyguardLocked()) {
            armed = true;
            return;
        }
        if (armed) tryLaunchGate("window_event");
    }

    private void tryLaunchGate(String reason) {
        if (!armed) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_POST_GATE, false)) return;

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (pm == null || !pm.isInteractive()) return;
        if (km != null && km.isKeyguardLocked()) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastGateAt < GATE_DEBOUNCE_MS) return;

        // On modern Android this Activity launch uses the user-granted
        // Display-over-other-apps capability as the BAL exemption.
        if (!Settings.canDrawOverlays(this)) return;

        armed = false;
        lastGateAt = now;
        Intent gate = new Intent(this, MainActivity.class)
                .putExtra("post_unlock_gate", true)
                .putExtra("gate_reason", reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(gate);
        } catch (Throwable ignored) {
            armed = true;
        }
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver); } catch (Throwable ignored) { }
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
