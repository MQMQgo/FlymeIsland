package io.github.flymeisland.diagnostic;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

final class ChargingPowerDisplay implements Runnable, View.OnAttachStateChangeListener {
    private static final long UPDATE_INTERVAL_MS = 1000L;
    private static final String UNAVAILABLE = "— W";
    private static final Executor READER = Executors.newSingleThreadExecutor();
    private static ChargingPowerDisplay current;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final TextView label;

    private ChargingPowerDisplay(TextView label) {
        this.label = label;
    }

    static void setActive(TextView label, boolean charging) {
        if (charging && current != null && current.label == label) {
            return;
        }
        stop();
        if (charging) {
            current = new ChargingPowerDisplay(label);
            label.setText(UNAVAILABLE);
            label.addOnAttachStateChangeListener(current);
            if (label.isAttachedToWindow()) {
                label.post(current);
            }
        }
    }

    static void stop() {
        if (current != null) {
            current.label.removeCallbacks(current);
            current.label.removeOnAttachStateChangeListener(current);
            current = null;
        }
    }

    @Override public void onViewAttachedToWindow(View view) {
        label.post(this);
    }

    @Override public void onViewDetachedFromWindow(View view) {
        if (current == this) {
            stop();
        }
    }

    @Override public void run() {
        if (current != this || !label.isAttachedToWindow()) {
            return;
        }
        if (!visible()) {
            label.postDelayed(this, UPDATE_INTERVAL_MS);
            return;
        }
        Context context = label.getContext();
        // Reading the battery HAL can block, so leave the SystemUI thread free.
        READER.execute(() -> {
            String reading = readPower(context);
            main.post(() -> showReading(reading));
        });
    }

    private void showReading(String reading) {
        if (current != this || !label.isAttachedToWindow()) {
            return;
        }
        if (visible() && !TextUtils.equals(label.getText(), reading)) {
            label.setText(reading);
        }
        label.postDelayed(this, UPDATE_INTERVAL_MS);
    }

    private boolean visible() {
        return label.isShown() && label.getWindowVisibility() == View.VISIBLE;
    }

    private static String readPower(Context context) {
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null || battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) {
                return UNAVAILABLE;
            }
            BatteryManager manager = context.getSystemService(BatteryManager.class);
            if (manager == null) {
                return UNAVAILABLE;
            }
            int currentMicroamps = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            int millivolts = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            return formatPower(currentMicroamps, millivolts);
        } catch (RuntimeException unavailable) {
            return UNAVAILABLE;
        }
    }

    static String formatPower(int currentMicroamps, int millivolts) {
        if (currentMicroamps == Integer.MIN_VALUE || millivolts <= 0) {
            return UNAVAILABLE;
        }
        // The Android APIs report net current entering the battery in µA and voltage in mV.
        long tenths = Math.round(Math.max(0, currentMicroamps) * (millivolts / 100_000_000d));
        if (tenths % 10 == 0 || tenths >= 1000) {
            return ((tenths + 5) / 10) + " W";
        }
        return (tenths / 10) + "." + (tenths % 10) + " W";
    }
}
