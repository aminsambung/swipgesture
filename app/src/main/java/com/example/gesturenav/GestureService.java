package com.example.gesturenav;

import android.accessibilityservice.AccessibilityService;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class GestureService extends AccessibilityService {

    private WindowManager windowManager;
    private View overlayView;
    private GestureDetector gestureDetector;

    // Ambang batas gestur
    private static final int EDGE_WIDTH_DP = 24;      // area tepi untuk back
    private static final int BOTTOM_HEIGHT_DP = 40;   // area bawah untuk recents
    private static final int SWIPE_THRESHOLD_DP = 80;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        setupOverlay();
    }

    private void setupOverlay() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        overlayView = new View(this);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                flags,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;

        gestureDetector = new GestureDetector(this, new GestureListener());
        gestureDetector.setIsLongpressEnabled(false);

        overlayView.setOnTouchListener((v, event) -> {
            // Hanya tangkap sentuhan di area tepi/bawah agar tidak ganggu aplikasi lain
            return gestureDetector.onTouchEvent(event);
        });

        windowManager.addView(overlayView, params);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            lockScreen();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (e1 == null || e2 == null) return false;

            float dx = e2.getX() - e1.getX();
            float dy = e2.getY() - e1.getY();

            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int edgePx = dp(EDGE_WIDTH_DP);
            int bottomPx = dp(BOTTOM_HEIGHT_DP);
            int thresholdPx = dp(SWIPE_THRESHOLD_DP);

            // Swipe dari tepi kiri (ke kanan) → Back
            if (e1.getX() < edgePx && dx > thresholdPx && Math.abs(dy) < Math.abs(dx)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                return true;
            }
            // Swipe dari tepi kanan (ke kiri) → Back
            if (e1.getX() > screenW - edgePx && dx < -thresholdPx && Math.abs(dy) < Math.abs(dx)) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                return true;
            }
            // Swipe dari bawah ke atas → Recents
            if (e1.getY() > screenH - bottomPx && dy < -thresholdPx && Math.abs(dx) < Math.abs(dy)) {
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                return true;
            }
            return false;
        }
    }

    private void lockScreen() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.lockNow();
            showToast("Layar dikunci");
        } else {
            showToast("Aktifkan Device Admin dulu di app GestureNav");
        }
    }

    private void showToast(String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (windowManager != null && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
