package com.spandan.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/**
 * Remaps the remote's app buttons (YouTube, Netflix, Prime Video...). It sees keys before the TV's
 * own shortcut handler, so a mapped key launches your app from anywhere and the stock action never runs.
 * Which physical button is which is learned in Customize > Remote buttons.
 */
public class KeyService extends AccessibilityService {
    static final String[] SLOT_NAME = { "YouTube", "Netflix", "Prime Video", "Sony LIV", "TCL Channel", "Web browser" };
    static final String[] SLOT_PKG = {
            "org.smarttube.stable", "com.lagradost.cloudstream3", "com.stremio.one",
            "com.crunchyroll.crunchyroid", "com.android.vending", "com.tcl.browser" };

    static volatile KeyService sInstance;
    static volatile int sLearnSlot = -1;
    static volatile Runnable sLearned;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("tvhome_global", MODE_PRIVATE); }

    static int keyFor(Context c, int slot) { return prefs(c).getInt("rk_" + slot, -1); }

    static String pkgFor(Context c, int slot) { return prefs(c).getString("rkp_" + slot, SLOT_PKG[slot]); }

    static boolean running() { return sInstance != null; }

    @Override protected void onServiceConnected() { sInstance = this; }

    @Override public boolean onUnbind(Intent i) { sInstance = null; return super.onUnbind(i); }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override public void onInterrupt() { }

    private static boolean reserved(int kc) {
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
                return true;
            default: return false;
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        int kc = e.getKeyCode();
        if (reserved(kc)) return false;
        int slot = sLearnSlot;
        if (slot >= 0) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                SharedPreferences.Editor ed = prefs(this).edit();
                for (int i = 0; i < SLOT_NAME.length; i++) if (keyFor(this, i) == kc) ed.remove("rk_" + i);
                ed.putInt("rk_" + slot, kc).apply();
                sLearnSlot = -1;
                Runnable r = sLearned;
                if (r != null) r.run();
            }
            return true;                                   // swallow the learn press (down and up)
        }
        for (int i = 0; i < SLOT_NAME.length; i++) {
            if (keyFor(this, i) != kc) continue;
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) launch(pkgFor(this, i));
            return true;
        }
        return false;
    }

    private void launch(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) {
                Intent lean = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER").setPackage(pkg);
                List<ResolveInfo> r = pm.queryIntentActivities(lean, 0);
                if (r.isEmpty()) return;
                i = new Intent(Intent.ACTION_MAIN).setClassName(r.get(0).activityInfo.packageName, r.get(0).activityInfo.name);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) { }
    }
}
