package com.spandan.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import java.io.File;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * tvHome v4 - liquid-glass home for the TCL T89C (Android 14).
 * Wallpaper hero, always-on HDMI strip, dense pins. No AndroidX. No live blur.
 */
public class MainActivity extends Activity {

    private static final int ZONE_TOP = 0, ZONE_HDMI = 1, ZONE_DOCK = 2, ZONE_GRID = 3, ZONE_PANEL = 4;

    private int mZone = ZONE_DOCK;
    private int mTopIdx = 0, mGridIdx = 0, mDockIdx = 0, mPanelIdx = 0, mHdmiIdx = 0;
    private boolean mOnSchedule = false;
    private boolean mAdvancedGlass = true;

    private FrameLayout mRoot;
    private ImageView mWallpaper;
    private View mVignette;
    private LinearLayout mTopRow, mPills, mHero;
    private TextView[] mPillViews;
    private TextView mGreeting, mDate;
    private View mSchedulePill;
    private String[] mPillLabels;
    private int mPillSig = Integer.MIN_VALUE;
    private View mHdmiDivider;
    private int[] mHdmiIcons;
    private final List<HdmiItem> mAllInputs = new ArrayList<>();
    private int mGridRowH = 0;
    private TextClock mAmPm;
    private TextView mWeather;
    private LinearLayout mEditBanner;
    private View mScrim;
    private boolean mEditMode = false;
    private boolean mWxBusy = false;
    private LinearLayout mHeroDateRow;
    private TextView mScheduleLine;
    private TextClock mClock;
    private LinearLayout mGridWrap;
    private List<TextView> mGridLabels = new ArrayList<>();
    private LinearLayout mDockCol;
    private View[] mDockTiles;
    private FrameLayout mDockBand;
    private ScrollView mGridScroll;
    private View mGlass;
    private View mDim;
    private LinearLayout mPanel;
    private List<TextView> mPanelRows = new ArrayList<>();
    private List<TextView> mPanelInfo = new ArrayList<>();
    private List<Runnable> mPanelRuns = new ArrayList<>();
    private TextView mWifiLive;
    private int mPanelKind = -1;
    private TvView mTvView;
    private LinearLayout mLiveHud;
    private TextView mLiveHudName, mLiveHudStatus;
    /** App grid visibility: home is pins + HDMI until "All apps" is pressed. */
    private boolean mAppsOpen = false;
    private View mGridFade, mGridTopFade;
    private int mGridTop = -1;
    private LinearLayout mHdmiStrip;
    private TextView[] mHdmiCaps;
    private final List<HdmiItem> mHdmiItems = new ArrayList<>();
    private int mPinCols = 6;

    static class HdmiItem {
        Drawable appIcon;   // non-null => a recent-app capsule
        String label;
        String inputId;   // null => Live TV app launch
        Runnable run;
    }

    private Typeface mFont;
    private List<ResolveInfo> mGridApps = new ArrayList<>();
    private List<DockItem> mDockItems = new ArrayList<>();
    private PackageManager mPM;
    private SharedPreferences mPrefs;
    private SharedPreferences mGlobal;
    private String mProfileId = "p0";
    private static final int[] AVATAR_COLORS = {
            0xFF5B8CFF, 0xFFE0568F, 0xFF2FB8A6, 0xFFF2A33A, 0xFF9B6CFF, 0xFF58B957 };

    static class DockItem { String key, pkg, label, url, id; ResolveInfo info; int iconRes; boolean custom; }
    static class Rect { int l, t, r, b; void set(int l, int t, int r, int b){ this.l=l; this.t=t; this.r=r; this.b=b; } }

    private static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Rect mFrom = new Rect(), mTo = new Rect();
    private ValueAnimator mAnim;

    // grid lane metrics (single source of truth for layout + focus math)
    private static final int GRID_COLS = 6;
    private static final int TILE_H_DP = 108;
    private static final int GRID_GAP_DP = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPM = getPackageManager();
        mGlobal = getSharedPreferences("tvhome_global", MODE_PRIVATE);
        initProfiles();
        try { mFont = Typeface.createFromAsset(getAssets(), "outfit.ttf"); }
        catch (Exception e) { mFont = Typeface.create("sans-serif", Typeface.NORMAL); }
        setUp();
        if (savedInstanceState == null) {
            mRoot.postDelayed(new Runnable() { @Override public void run() { startupFlow(); } }, 450);
        }
    }

    // ==================================================================== UI

    private void setUp() {
        mAdvancedGlass = mPrefs.getBoolean("advanced_glass", true);
        try { mPinCols = getResources().getInteger(R.integer.pin_cols); } catch (Exception e) { mPinCols = 6; }
        mRoot = new FrameLayout(this);
        mRoot.setBackgroundColor(0xFF000000);
        setContentView(mRoot, new ViewGroup.LayoutParams(MP, MP));

        buildWallpaper();
        buildTopBar();
        buildHdmiStrip();
        buildGrid();
        buildDock();
        noClip(mRoot);

        mGlass = new View(this);
        mGlass.setBackground(GlassHelper.focusHalo(this));
        mGlass.setElevation(dp(22));
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(10), dp(10));
        glp.setMargins(-9999, -9999, 0, 0);
        mRoot.addView(mGlass, glp);

        restoreFocus();
        mRoot.post(new Runnable() { @Override public void run() { applyFocus(true); } });
    }

    private void buildWallpaper() {
        mWallpaper = new ImageView(this);
        mWallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Drawable wall = loadWallpaperDrawable();
        mWallpaper.setImageDrawable(wall);
        prepareGlass(wall);
        mRoot.addView(mWallpaper, lpF(MP, MP, Gravity.CENTER, 0, 0, 0, 0));
        mVignette = new View(this);
        mVignette.setBackground(GlassHelper.vignette(this));
        mRoot.addView(mVignette, lpF(MP, MP, Gravity.CENTER, 0, 0, 0, 0));
        // darkens the wallpaper behind the All apps drawer
        mScrim = new View(this);
        mScrim.setBackgroundColor(0xB8000000);
        mScrim.setAlpha(0f);
        mRoot.addView(mScrim, lpF(MP, MP, Gravity.CENTER, 0, 0, 0, 0));
    }

    /** Pre-blurs the wallpaper (cover-fit to the screen) so glass surfaces can frost it. */
    private void prepareGlass(Drawable wall) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int w = dm.widthPixels, h = dm.heightPixels;
            Bitmap src = Bitmap.createBitmap(320, Math.max(1, 320 * h / w), Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(src);
            int iw = wall.getIntrinsicWidth() > 0 ? wall.getIntrinsicWidth() : src.getWidth();
            int ih = wall.getIntrinsicHeight() > 0 ? wall.getIntrinsicHeight() : src.getHeight();
            float sc = Math.max((float) src.getWidth() / iw, (float) src.getHeight() / ih);
            int dw = Math.round(iw * sc), dh = Math.round(ih * sc);
            wall.setBounds((src.getWidth() - dw) / 2, (src.getHeight() - dh) / 2,
                    (src.getWidth() + dw) / 2, (src.getHeight() + dh) / 2);
            wall.draw(cv);
            LiquidGlass.setBackdrop(src, w, h);
        } catch (Throwable ignored) { }
    }

    private File wallDir() {
        File d = new File(getExternalFilesDir(null), "wallpapers");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    private List<File> wallFiles() {
        List<File> out = new ArrayList<>();
        File[] fs = wallDir().listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            String n = f.getName().toLowerCase(Locale.US);
            if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp")) out.add(f);
        }
        Collections.sort(out, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        return out;
    }

    private String wallMode() {
        String m = mPrefs.getString("wallpaper", null);
        if (m != null) return m;
        return mPrefs.getBoolean("wallpaper_art", true) ? "art" : "plain";
    }

    private Drawable loadWallpaperDrawable() {
        String mode = wallMode();
        mWallpaper.clearColorFilter();
        if (mode.startsWith("file:")) {
            try {
                File f = new File(wallDir(), mode.substring(5));
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int ss = 1;
                while (o.outWidth / (ss * 2) >= dm.widthPixels && o.outHeight / (ss * 2) >= dm.heightPixels) ss *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = ss;
                Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (bm != null) {
                    mWallpaper.setColorFilter(0x66000000, android.graphics.PorterDuff.Mode.SRC_ATOP);
                    return new BitmapDrawable(getResources(), bm);
                }
            } catch (Throwable ignored) { }
        }
        if (!"plain".equals(mode)) {
            try {
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                return new BitmapDrawable(getResources(),
                        WallpaperArt.render(dm.widthPixels, dm.heightPixels, true));
            } catch (Throwable ignored) { }
        }
        return getDrawable(R.drawable.wallpaper_default);
    }

    private void applyWallpaper() {
        Drawable nw = loadWallpaperDrawable();
        mWallpaper.setImageDrawable(nw);
        prepareGlass(nw);
        if (mPillViews != null) for (int i = 0; i < mPillViews.length; i++) stylePill(i, false);
        renderHdmiStrip();
    }

    private void downloadWallpaper(final String url) {
        android.widget.Toast.makeText(this, "Downloading wallpaper\u2026", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(10000); c.setReadTimeout(20000);
                    c.setInstanceFollowRedirects(true);
                    final String name = "wp_" + System.currentTimeMillis() + ".jpg";
                    File out = new File(wallDir(), name);
                    java.io.InputStream in = c.getInputStream();
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[16384]; int n; long tot = 0;
                    while ((n = in.read(buf)) > 0) { fo.write(buf, 0, n); tot += n; if (tot > 25L * 1024 * 1024) break; }
                    fo.close(); in.close(); c.disconnect();
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(out.getAbsolutePath(), o);
                    if (o.outWidth <= 0) { out.delete(); throw new RuntimeException("not an image"); }
                    runOnUiThread(new Runnable() { @Override public void run() {
                        mPrefs.edit().putString("wallpaper", "file:" + name).apply();
                        applyWallpaper();
                        android.widget.Toast.makeText(MainActivity.this, "Wallpaper set", android.widget.Toast.LENGTH_SHORT).show();
                    }});
                } catch (Throwable t) {
                    runOnUiThread(new Runnable() { @Override public void run() {
                        android.widget.Toast.makeText(MainActivity.this, "Couldn't download that image", android.widget.Toast.LENGTH_LONG).show();
                    }});
                }
            }
        }, "tvhome-wallpaper").start();
    }

    private GradientDrawable round(int fillColor, int strokeColor, int strokeW, float radiusDp) {
        return GlassHelper.round(this, fillColor, strokeColor, strokeW, radiusDp);
    }

    private GradientDrawable roundGrad(int topColor, int bottomColor, int strokeColor, float radiusDp) {
        return GlassHelper.roundGrad(this, topColor, bottomColor, strokeColor, radiusDp);
    }

    private TextView newText(String s, float sizeSp, int weight, int color) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        if (mFont != null) {
            int style = weight >= 600 ? Typeface.BOLD : Typeface.NORMAL;
            try {
                tv.setTypeface(Typeface.create(mFont, Math.max(400, Math.min(800, weight)), false));
            } catch (Throwable t) {
                tv.setTypeface(Typeface.create(mFont, style));
            }
            try { tv.setFontVariationSettings("'wght' " + weight); } catch (Throwable ignored) { }
        }
        return tv;
    }

    /** Lifted cards, shadows and expanding pills must never be cut by a parent's padding box. */
    private void noClip(View v) {
        if (!(v instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) v;
        g.setClipChildren(false);
        g.setClipToPadding(false);
        for (int i = 0; i < g.getChildCount(); i++) noClip(g.getChildAt(i));
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** Pill indices after Inputs was removed: All apps, Home, Wi-Fi, Settings. */
    private static final int PILL_APPS = 0, PILL_GEMINI = 1, PILL_HOME = 2, PILL_WIFI = 3,
            PILL_CUSTOM = 4, PILL_SETTINGS = 5, PILL_PROFILE = 6;

    private Drawable pillBg(int idx, boolean focused) {
        boolean accent = idx == PILL_APPS && mAppsOpen;
        return GlassHelper.pill(this, focused, accent, mAdvancedGlass);
    }

    private int pillFg(int idx, boolean focused) {
        if (focused) return 0xFF0C0D10;           // dark on solid white focus
        if (idx == PILL_APPS && mAppsOpen) return 0xFFE8F0FF;
        return 0xFFF2F4F7;
    }

    // ------------------------------------------------ configurable top bar

    private static final int BUILTIN_PILLS = 7, MAX_TOP_CUSTOM = 4;
    private final String[] PILL_NAMES = { "All apps", "Gemini", "Home", "Wi-Fi", "Customize", "Settings", "Profile" };

    /** Rows are "label\tpackage\turl". Pill index = BUILTIN_PILLS + row. */
    private List<String[]> topCustoms() {
        List<String[]> out = new ArrayList<>();
        for (String line : mPrefs.getString("top_customs", "").split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length >= 3 && !p[1].isEmpty()) out.add(p);
        }
        return out;
    }

    private void saveTopCustoms(List<String[]> l) {
        StringBuilder sb = new StringBuilder();
        for (String[] c : l) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(c[0].replace('\t', ' ').replace('\n', ' ')).append('\t').append(c[1]).append('\t')
                    .append(c[2].replace('\t', ' ').replace('\n', ' '));
        }
        mPrefs.edit().putString("top_customs", sb.toString()).putString("pill_order", mPrefs.getString("pill_order", "")).apply();
    }

    private int pillCount() { return BUILTIN_PILLS + topCustoms().size(); }

    private String pillName(int i) {
        if (i < BUILTIN_PILLS) return i == PILL_PROFILE ? profileName() : PILL_NAMES[i];
        List<String[]> c = topCustoms();
        return i - BUILTIN_PILLS < c.size() ? c.get(i - BUILTIN_PILLS)[0] : "?";
    }

    private List<Integer> pillOrder() {
        int n = pillCount();
        List<Integer> out = new ArrayList<>();
        for (String t : mPrefs.getString("pill_order", "").split(",")) {
            try { int v = Integer.parseInt(t.trim()); if (v >= 0 && v < n && !out.contains(v)) out.add(v); } catch (Exception ignored) { }
        }
        for (int i = 0; i < n; i++) if (!out.contains(i)) out.add(i);
        return out;
    }

    private void savePillOrder(List<Integer> o) {
        StringBuilder sb = new StringBuilder();
        for (int v : o) { if (sb.length() > 0) sb.append(','); sb.append(v); }
        mPrefs.edit().putString("pill_order", sb.toString()).apply();
    }

    private java.util.Set<Integer> hiddenPills() {
        java.util.Set<Integer> h = new java.util.HashSet<>();
        for (String t : mPrefs.getString("pill_hidden", "").split(",")) {
            try { h.add(Integer.parseInt(t.trim())); } catch (Exception ignored) { }
        }
        return h;
    }

    private void saveHiddenPills(java.util.Set<Integer> h) {
        StringBuilder sb = new StringBuilder();
        for (int v : h) { if (sb.length() > 0) sb.append(','); sb.append(v); }
        mPrefs.edit().putString("pill_hidden", sb.toString()).apply();
    }

    private void buildPills() {
        mPills.removeAllViews();
        int n = pillCount();
        mPillLabels = new String[n];
        mPillViews = new TextView[n];
        for (int i = 0; i < n; i++) {
            mPillLabels[i] = pillName(i);
            TextView p = newText("", 14, 560, 0xFFF2F4F7);
            p.setBackground(pillBg(i, false));
            p.setPadding(dp(15), dp(12), dp(15), dp(12));
            p.setGravity(Gravity.CENTER);
            p.setSingleLine(true);
            final int idx = i;
            p.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { mOnSchedule = false; mTopIdx = idx; activatePill(idx); }
            });
            mPillViews[i] = p;
            p.setCompoundDrawablesWithIntrinsicBounds(pillIcon(i, 0xFFF2F4F7), null, null, null);
        }
        List<Integer> order = pillOrder();
        for (int k = 0; k < order.size(); k++) {
            mPills.addView(mPillViews[order.get(k)], lpW(WC, WC, 0, k == 0 ? 0 : dp(10), 0, 0, 0));
        }
        mPillSig = Integer.MIN_VALUE;
        if (mTopIdx >= n) mTopIdx = PILL_CUSTOM;
    }

    private void rebuildPills() {
        buildPills();
        applyPillVisibility();
        for (int i = 0; i < mPillViews.length; i++) stylePill(i, false);
        applyFocus(false);
    }

    private void launchTopCustom(int idx) {
        List<String[]> c = topCustoms();
        int k = idx - BUILTIN_PILLS;
        if (k < 0 || k >= c.size()) return;
        String[] t = c.get(k);
        if (!t[2].isEmpty()) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(t[2]));
            i.setPackage(t[1]);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            recordRecent(t[1]);
            startExternal(i);
        } else launchByPackage(t[1]);
    }

    private Drawable pillIcon(int idx, int color) {
        if (idx >= BUILTIN_PILLS) {
            try {
                Drawable d = iconPlated(mPM.getApplicationIcon(topCustoms().get(idx - BUILTIN_PILLS)[1]), dp(26), 7);
                d.setBounds(0, 0, dp(26), dp(26));
                return d;
            } catch (Throwable t) { return geminiGlyph(dp(20)); }
        }
        if (idx == PILL_GEMINI) return geminiGlyph(dp(20));
        if (idx == PILL_PROFILE) return avatar(dp(26), activeProfile());
        int[] res = { R.drawable.ic_apps, 0, R.drawable.ic_home, R.drawable.ic_wifi,
                R.drawable.ic_tune, R.drawable.ic_settings };
        Drawable d = getDrawable(res[idx]).mutate();
        d.setTint(color);
        return d;
    }

    private void stylePill(int idx, boolean focused) {
        if (mPillViews == null || idx < 0 || idx >= mPillViews.length) return;
        TextView pv = mPillViews[idx];
        boolean showLabel = focused || (idx == PILL_APPS && mAppsOpen);
        String label = (idx == PILL_APPS && mAppsOpen) ? "Hide apps" : mPillLabels[idx];
        if (idx >= BUILTIN_PILLS) label = pillName(idx);
        if (idx == PILL_PROFILE) label = profileName();
        int fg = pillFg(idx, focused);
        pv.setText(showLabel ? label : "");
        Drawable pic = pillIcon(idx, fg);
        if (idx >= BUILTIN_PILLS) pv.setCompoundDrawables(pic, null, null, null);
        else pv.setCompoundDrawablesWithIntrinsicBounds(pic, null, null, null);
        pv.setCompoundDrawablePadding(showLabel ? dp(8) : 0);
        mPillViews[idx].setBackground(pillBg(idx, focused));
        mPillViews[idx].setTextColor(fg);
        lift(mPillViews[idx], focused, 1.08f);
    }

    /** Four-point sparkle in the Gemini blue-to-violet gradient. */
    private BitmapDrawable geminiGlyph(int sizePx) {
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, sizePx, sizePx, 0, 0xFF4F8CFF, 0xFFC07BFF, Shader.TileMode.CLAMP));
        float m = sizePx / 2f, k = sizePx * 0.5f;
        Path path = new Path();
        path.moveTo(m, 0);
        path.quadTo(m, m, sizePx, m);
        path.quadTo(m, m, m, sizePx);
        path.quadTo(m, m, 0, m);
        path.quadTo(m, m, m, 0);
        path.close();
        c.drawPath(path, p);
        return new BitmapDrawable(getResources(), b);
    }

    /** Tiny 2x2 "app grid" glyph used on the drawer pill. */
    private BitmapDrawable appsGlyph(int sizePx, int color) {
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float gap = sizePx * 0.18f;
        float cell = (sizePx - gap) / 2f;
        float r = cell * 0.28f;
        RectF rect = new RectF();
        for (int i = 0; i < 4; i++) {
            float x = (i % 2) * (cell + gap);
            float y = (i / 2) * (cell + gap);
            rect.set(x, y, x + cell, y + cell);
            c.drawRoundRect(rect, r, r, p);
        }
        return new BitmapDrawable(getResources(), b);
    }

    // ------------------------------------------------------------- top bar

    private void buildTopBar() {
        // top bar: system/action pills only, pinned to the top-right corner
        mTopRow = new LinearLayout(this);
        mTopRow.setOrientation(LinearLayout.HORIZONTAL);
        mTopRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        mTopRow.setPadding(dp(48), dp(28), dp(48), 0);
        mTopRow.setClipChildren(false);

        // hero block: greeting + clock + date, held in the free space above the dock
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        mGreeting = newText(greeting(), 16, 520, 0xFFE8ECF2);
        mClock = new TextClock(this);
        mClock.setFormat12Hour("h:mm");
        mClock.setFormat24Hour("HH:mm");
        mClock.setTextSize(64);
        mClock.setTextColor(0xFFFFFFFF);
        mClock.setLetterSpacing(-0.015f);
        mClock.setIncludeFontPadding(false);
        // Force a real weight. Variation axes are ignored on many TV typefaces.
        if (mFont != null) {
            try { mClock.setTypeface(Typeface.create(mFont, 360, false)); }
            catch (Throwable t) { mClock.setTypeface(Typeface.create(mFont, Typeface.BOLD)); }
        }
        mAmPm = new TextClock(this);
        mAmPm.setFormat12Hour("a");
        mAmPm.setFormat24Hour("a");
        mAmPm.setTextSize(22);
        mAmPm.setAllCaps(true);
        mAmPm.setLetterSpacing(0.04f);
        mAmPm.setTextColor(0xCCFFFFFF);
        mAmPm.setIncludeFontPadding(false);
        if (mFont != null) {
            try { mAmPm.setTypeface(Typeface.create(mFont, 500, false)); } catch (Throwable ignored) { }
        }
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setBaselineAligned(true);
        clockRow.setClipChildren(false);
        clockRow.addView(mClock, lpW(WC, WC, 0, 0, 0, 0, 0));
        clockRow.addView(mAmPm, lpW(WC, WC, 0, dp(10), 0, 0, 0));
        left.addView(mGreeting, lpW(WC, WC, 0, 0, 0, 0, 0));
        left.addView(clockRow, lpW(WC, WC, 0, 0, dp(2), 0, 0));

        // date on its own line, "up next" chip underneath so it never fights the pill row for width
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        mDate = newText("", 15, 480, 0xFFE3E8EF);
        dateRow.addView(mDate, lpW(WC, WC, 0, 0, 0, 0, 0));
        mWeather = newText("", 15, 480, 0xFFE3E8EF);
        mWeather.setVisibility(View.GONE);
        dateRow.addView(mWeather, lpW(WC, WC, 0, dp(10), 0, 0, 0));
        mHeroDateRow = dateRow;

        mSchedulePill = new FrameLayout(this);
        mScheduleLine = newText("", 14, 550, 0xFFF0F4FF);
        mScheduleLine.setSingleLine(true);
        mScheduleLine.setMaxWidth(dp(340));
        mScheduleLine.setEllipsize(TextUtils.TruncateAt.END);
        mSchedulePill.setBackground(GlassHelper.pill(this, false, true, mAdvancedGlass));
        mSchedulePill.setPadding(dp(14), dp(6), dp(14), dp(6));
        mSchedulePill.setVisibility(View.GONE);
        mSchedulePill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (!calLinked()) openScheduleTarget(); }
        });
        ((FrameLayout) mSchedulePill).addView(mScheduleLine, lpF(WC, WC, Gravity.CENTER, 0, 0, 0, 0));
        left.addView(dateRow, lpW(WC, WC, 0, 0, dp(6), 0, 0));
        dateRow.addView(mSchedulePill, lpW(WC, WC, 0, dp(18), 0, 0, 0));
        
        mHero = left;

        // edit-mode banner shares the hero slot (hero fades out while the drawer is open)
        mEditBanner = new LinearLayout(this);
        mEditBanner.setOrientation(LinearLayout.VERTICAL);
        mEditBanner.addView(newText("Edit home apps", 26, 560, 0xFFFFFFFF), lpW(WC, WC, 0, 0, 0, 0, 0));
        mEditBanner.addView(newText("OK pins or unpins an app  \u00B7  BACK when you're done", 14, 420, 0xFFB9C2CE),
                lpW(WC, WC, 0, 0, dp(4), 0, 0));
        mEditBanner.setVisibility(View.GONE);

        mPills = new LinearLayout(this);
        mPills.setOrientation(LinearLayout.HORIZONTAL);
        mPills.setClipChildren(false);
        buildPills();
        mTopRow.addView(mPills, lpW(WC, WC, 0, 0, 0, 0, 0));
        mRoot.addView(mTopRow, lpF(MP, WC, Gravity.TOP, 0, 0, 0, 0));
        applyPillVisibility();
        // Sit the hero in the open field above the shelf (not jammed under the pills).
        // Top-left hero: CENTER_VERTICAL was clipping the greeting under the top edge.
        mRoot.addView(mHero, lpF(WC, WC, Gravity.START | Gravity.TOP, dp(48), dp(36), 0, 0));
        mRoot.addView(mEditBanner, lpF(WC, WC, Gravity.START | Gravity.TOP, dp(48), dp(34), 0, 0));
        applyClockPrefs();

        mPanel = new LinearLayout(this);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        mPanel.setBackground(GlassHelper.panel(this, mAdvancedGlass, 24));
        mPanel.setElevation(dp(44));
        mPanel.setClipChildren(false);
        mPanel.setVisibility(View.GONE);
        mRoot.addView(mPanel, lpF(dp(356), WC, Gravity.TOP, dp(9999), dp(9999), 0, 0));
    }

    private void openScheduleTarget() {
        String pkg = getString(R.string.schedule_launch_pkg);
        try {
            Intent i = mPM.getLaunchIntentForPackage(pkg);
            if (i == null) {
                Intent lean = new Intent(Intent.ACTION_MAIN)
                        .addCategory("android.intent.category.LEANBACK_LAUNCHER")
                        .setPackage(pkg);
                List<ResolveInfo> r = mPM.queryIntentActivities(lean, 0);
                if (!r.isEmpty()) {
                    i = new Intent(Intent.ACTION_MAIN);
                    i.setClassName(r.get(0).activityInfo.packageName, r.get(0).activityInfo.name);
                }
            }
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startExternal(i);
                return;
            }
        } catch (Exception ignored) { }
        ResolveInfo ri = resolveFor(pkg);
        if (ri != null) launchApp(ri);
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = h < 5 ? "Good night" : h < 12 ? "Good morning"
                : h < 17 ? "Good afternoon" : "Good evening";
        String name = profileName();
        return part + (name.isEmpty() ? "" : ", " + name);
    }

    // ---------------------------------------------------------------- grid

    private void buildGrid() {
        mGridApps = queryApps();
        mGridScroll = new ScrollView(this);
        mGridScroll.setVerticalScrollBarEnabled(false);
        mGridScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mGridScroll.setVisibility(View.GONE);
        mGridWrap = new LinearLayout(this);
        mGridWrap.setOrientation(LinearLayout.VERTICAL);
        mGridWrap.setPadding(0, dp(16), 0, dp(24));
        mGridWrap.setClipChildren(false);
        mGridWrap.setClipToPadding(false);
        mGridScroll.setClipToPadding(false);
        mGridScroll.setClipChildren(false);
        mGridScroll.addView(mGridWrap, lpW(MP, WC, 0, 0, 0, 0, 0));
        mRoot.addView(mGridScroll, lpF(MP, MP, Gravity.TOP, 0, dp(172), 0, dp(16)));
        mGridScroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                v.setClipBounds(new android.graphics.Rect(-dp(40), 0, (r - l) + dp(40), b - t));
            }
        });
        mGridScroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                updateGridFade();
            }
        });

        // dims the grid while the dock is focused (depth cue for the shelf)
        mDim = new View(this);
        GradientDrawable gdim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x2805060A, 0xC005060A });
        mDim.setBackground(gdim);
        mDim.setAlpha(0f);
        mRoot.addView(mDim, lpF(MP, MP, Gravity.TOP, 0, dp(172), 0, 0));

        // soft bottom fade: only while there is more grid below the fold
        mGridFade = new View(this);
        mGridFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] { 0xCC05060A, 0x6605060A, 0x0005060A }));
        mGridFade.setClickable(false);
        mGridFade.setFocusable(false);
        mGridFade.setVisibility(View.GONE);
        mRoot.addView(mGridFade, lpF(MP, dp(56), Gravity.BOTTOM, 0, 0, 0, dp(16)));

        // mirror fade at the top edge of the list, only once the grid is scrolled
        mGridTopFade = new View(this);
        mGridTopFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0xF005060A, 0x8805060A, 0x0005060A }));
        mGridTopFade.setClickable(false);
        mGridTopFade.setFocusable(false);
        mGridTopFade.setVisibility(View.GONE);
        mRoot.addView(mGridTopFade, lpF(MP, dp(44), Gravity.TOP, 0, dp(188), 0, 0));

        // the top bar grows when the "up next" pill appears, so follow its measured height
        mTopRow.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    int h = mTopRow.getHeight();
                    if (h <= 0) return;
                    int top = h + dp(16);
                    if (top == mGridTop) return;
                    mGridTop = top;
                    FrameLayout.LayoutParams a = (FrameLayout.LayoutParams) mGridScroll.getLayoutParams();
                    a.topMargin = top;
                    mGridScroll.setLayoutParams(a);
                    FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) mDim.getLayoutParams();
                    b.topMargin = top;
                    mDim.setLayoutParams(b);
                    FrameLayout.LayoutParams c = (FrameLayout.LayoutParams) mGridTopFade.getLayoutParams();
                    c.topMargin = top;
                    mGridTopFade.setLayoutParams(c);
                }
            });

        mRoot.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    mRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    renderGrid();
                    applyFocus(false);
                }
            });
    }

    /** Fade is a hint that the grid scrolls, so it must not stay on at the bottom of the list. */
    private android.animation.ValueAnimator mScrollAnim;

    private void tweenScroll(int to) {
        if (mScrollAnim != null) mScrollAnim.cancel();
        mScrollAnim = android.animation.ValueAnimator.ofInt(mGridScroll.getScrollY(), to);
        mScrollAnim.setDuration(240);
        mScrollAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        mScrollAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
                mGridScroll.scrollTo(0, (Integer) a.getAnimatedValue());
            }
        });
        mScrollAnim.start();
    }

    private void updateGridFade() {
        if (mGridFade == null || mGridScroll == null) return;
        boolean more = mAppsOpen
                && mGridScroll.getHeight() > 0
                && mGridScroll.getScrollY() + mGridScroll.getHeight()
                        < mGridWrap.getHeight() + mGridWrap.getPaddingBottom() - dp(6);
        mGridFade.setVisibility(more ? View.VISIBLE : View.GONE);
        if (mGridTopFade != null) {
            mGridTopFade.setVisibility(mAppsOpen && mGridScroll.getScrollY() > dp(4)
                    ? View.VISIBLE : View.GONE);
        }
    }

    private void renderGrid() {
        mGridWrap.removeAllViews();
        mGridLabels.clear();
        if (mGridApps.isEmpty()) return;
        int cols = GRID_COLS;
        int scrW = getResources().getDisplayMetrics().widthPixels;
        int sidePad = dp(48), gap = dp(10);
        int hGap = 0;
        int tileW = (scrW - sidePad * 2) / cols;
        int tileH = Math.round((tileW - dp(12)) * 9f / 16f) + dp(12);
        mGridRowH = tileH;
        int iconSize = dp(58);
        for (int i = 0; i < mGridApps.size(); i++) {
            if (i % cols == 0) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(sidePad, 0, sidePad, 0);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                mGridWrap.addView(row, lpW(MP, WC, 0, 0, 0, 0, 0));
            }
            LinearLayout r = (LinearLayout) mGridWrap.getChildAt(mGridWrap.getChildCount() - 1);
            View tile = makeGridTile(mGridApps.get(i), tileW, tileH, iconSize);
            r.addView(tile, lpW(tileW, tileH, 0, 0, 0, 0, 0));
        }
        noClip(mGridWrap);
    }

    private View makeGridTile(final ResolveInfo info, int tileW, int tileH, int iconSize) {
        int pad = dp(6);
        FrameLayout tile = new FrameLayout(this);
        tile.setPadding(pad, pad, pad, pad);
        int cardW = tileW - 2 * pad;
        int cardH = Math.round(cardW * 9f / 16f);
        String label = sanitize(String.valueOf(info.loadLabel(mPM)));
        FrameLayout card = buildCard(info, 0, label, cardW, cardH, true);
        TextView badge = newText("", 14, 700, 0xFF0C0D10);
        badge.setTag("badge");
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setVisibility(View.GONE);
        card.addView(badge, lpF(dp(24), dp(24), Gravity.TOP | Gravity.END, 0, dp(6), dp(6), 0));
        tile.addView(card, new FrameLayout.LayoutParams(cardW, cardH));
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mEditMode) togglePin(info.activityInfo.packageName); else launchApp(info);
            }
        });
        return tile;
    }

    // ---- edit mode: pick which apps live on the home shelf

    private void styleBadge(TextView b, boolean pinned) {
        if (b == null) return;
        b.setBackground(round(pinned ? 0xFFFFFFFF : 0xB3101216, pinned ? 0xFFFFFFFF : 0x80FFFFFF, 1, 12));
        b.setTextColor(pinned ? 0xFF0C0D10 : 0xFFFFFFFF);
        b.setText(pinned ? "\u2713" : "+");
    }

    private void updateBadges() {
        if (mGridWrap == null) return;
        List<String> keys = pinKeys();
        for (int r = 0; r < mGridWrap.getChildCount(); r++) {
            LinearLayout row = (LinearLayout) mGridWrap.getChildAt(r);
            for (int c = 0; c < row.getChildCount(); c++) {
                int idx = r * GRID_COLS + c;
                if (idx >= mGridApps.size()) continue;
                View b = row.getChildAt(c).findViewWithTag("badge");
                if (!(b instanceof TextView)) continue;
                b.setVisibility(mEditMode ? View.VISIBLE : View.GONE);
                styleBadge((TextView) b, keys.contains(mGridApps.get(idx).activityInfo.packageName));
            }
        }
    }

    private void togglePin(String pkg) {
        List<String> keys = pinKeys();
        if (keys.contains(pkg)) {
            keys.remove(pkg);
        } else {
            if (keys.size() >= MAX_PINS) {
                android.widget.Toast.makeText(this, "Home is full (" + MAX_PINS + " apps)", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            keys.add(pkg);
        }
        savePinKeys(keys);
        updateBadges();
    }

    private void enterEditMode() {
        hidePanel();
        mEditMode = true;
        mEditBanner.setVisibility(View.VISIBLE);
        mTopIdx = PILL_APPS;
        updateBadges();
        setAppsOpen(true, true);
    }

    private List<ResolveInfo> queryApps() {
        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER");
        Intent reg = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> out = new ArrayList<>();
        for (ResolveInfo ri : mPM.queryIntentActivities(lean, 0)) {
            if (!containsPkg(out, ri)) out.add(ri);
        }
        for (ResolveInfo ri : mPM.queryIntentActivities(reg, 0)) {
            if (!containsPkg(out, ri)) out.add(ri);
        }
        List<ResolveInfo> keep = new ArrayList<>();
        for (ResolveInfo ri : out) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            if (isExcluded(pkg)) continue;
            keep.add(ri);
        }
        Collections.sort(keep, (a, b) ->
                sanitize(String.valueOf(a.loadLabel(mPM))).compareToIgnoreCase(
                        sanitize(String.valueOf(b.loadLabel(mPM)))));
        return keep;
    }

    private boolean containsPkg(List<ResolveInfo> list, ResolveInfo ri) {
        for (ResolveInfo r : list) if (r.activityInfo.packageName.equals(ri.activityInfo.packageName)) return true;
        return false;
    }

    private boolean isExcluded(String pkg) {
        String[] arr = getResources().getStringArray(R.array.grid_exclude);
        for (String p : arr) if (p.equalsIgnoreCase(pkg)) return true;
        return false;
    }

    private boolean isInDock(String pkg) {
        String[] arr = getResources().getStringArray(R.array.dock_pkg);
        for (String raw : arr) {
            String p = raw.split("\\|")[0].trim();
            if (p.equalsIgnoreCase(pkg)) return true;
        }
        return false;
    }

    private static String sanitize(String s) { return s == null ? "" : s.replace("\n", " ").trim(); }

    // ------------------------------------------------------------- HDMI strip

    private void buildHdmiStrip() {
        // Built empty here; attached inside the unified shelf in buildDock().
        mHdmiStrip = new LinearLayout(this);
        mHdmiStrip.setOrientation(LinearLayout.HORIZONTAL);
        mHdmiStrip.setGravity(Gravity.CENTER_VERTICAL);
        mHdmiStrip.setPadding(0, 0, 0, 0);
        mHdmiStrip.setClipChildren(false);
        loadHdmiItems();
        renderHdmiStrip();
    }

    private void loadHdmiItems() {
        mAllInputs.clear();
        HdmiItem live = new HdmiItem();
        live.label = "Live TV";
        live.inputId = null;
        live.run = new Runnable() {
            @Override public void run() {
                try {
                    Intent i = mPM.getLaunchIntentForPackage("com.tcl.tv");
                    if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startExternal(i); }
                } catch (Exception ignored) { }
            }
        };
        mAllInputs.add(live);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TvInputInfo in : orderedInputs()) {
            String name = sanitize(String.valueOf(in.loadLabel(this)));
            if (!seen.add(name.toLowerCase(Locale.US))) continue;
            final String id = in.getId();
            final String label = name;
            HdmiItem h = new HdmiItem();
            h.label = name;
            h.inputId = id;
            h.run = new Runnable() {
                @Override public void run() { tuneToInput(id, label); }
            };
            mAllInputs.add(h);
        }
        final List<String> ord = new ArrayList<>();
        for (String t : mPrefs.getString("input_order", "").split("\n")) if (!t.isEmpty()) ord.add(t);
        Collections.sort(mAllInputs, new java.util.Comparator<HdmiItem>() {
            @Override public int compare(HdmiItem a, HdmiItem b) {
                int ia = ord.indexOf(a.label), ib = ord.indexOf(b.label);
                return Integer.compare(ia < 0 ? 999 : ia, ib < 0 ? 999 : ib);
            }
        });
        java.util.Set<String> hidden = hiddenInputs();
        mHdmiItems.clear();
        for (HdmiItem it : mAllInputs) if (!hidden.contains(it.label)) mHdmiItems.add(it);
        mRecentStart = mHdmiItems.size();
        if (recentOn()) {
            for (final String pkg : recents()) {
                try {
                    Intent li = mPM.getLaunchIntentForPackage(pkg);
                    if (li == null) { Intent lean = new Intent(Intent.ACTION_MAIN)
                            .addCategory("android.intent.category.LEANBACK_LAUNCHER").setPackage(pkg);
                        List<ResolveInfo> rl = mPM.queryIntentActivities(lean, 0);
                        if (rl.isEmpty()) continue; }
                    HdmiItem h = new HdmiItem();
                    h.label = String.valueOf(mPM.getApplicationLabel(mPM.getApplicationInfo(pkg, 0)));
                    h.appIcon = mPM.getApplicationIcon(pkg);
                    h.run = new Runnable() { @Override public void run() { launchByPackage(pkg); } };
                    mHdmiItems.add(h);
                } catch (Exception ignored) { }
            }
        }
    }

    private int mRecentStart = 0;
    private boolean mRecentsDirty = false;

    private boolean recentOn() { return mPrefs.getBoolean("recent_on", true); }

    private List<String> recents() {
        List<String> out = new ArrayList<>();
        for (String k : mPrefs.getString("recents", "").split("\n")) if (!k.trim().isEmpty()) out.add(k.trim());
        return out;
    }

    private void recordRecent(String pkg) {
        if (pkg == null || pkg.equals(getPackageName()) || pkg.equals("com.android.tv.settings")) return;
        List<String> r = recents();
        r.remove(pkg);
        r.add(0, pkg);
        while (r.size() > 6) r.remove(r.size() - 1);
        mPrefs.edit().putString("recents", TextUtils.join("\n", r)).apply();
        mRecentsDirty = true;
    }

    private void launchByPackage(String pkg) {
        Intent i = mPM.getLaunchIntentForPackage(pkg);
        if (i == null) {
            Intent lean = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER").setPackage(pkg);
            List<ResolveInfo> r = mPM.queryIntentActivities(lean, 0);
            if (r.isEmpty()) return;
            i = new Intent(Intent.ACTION_MAIN).setClassName(r.get(0).activityInfo.packageName, r.get(0).activityInfo.name);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        recordRecent(pkg);
        startExternal(i);
    }

    private java.util.Set<String> hiddenInputs() {
        java.util.Set<String> def = new java.util.HashSet<>(
                java.util.Arrays.asList("Live TV", "HDMI 3", "HDMI 4", "TV"));
        return mPrefs.getStringSet("inputs_hidden", def);
    }

    private void reloadInputs() {
        loadHdmiItems();
        renderHdmiStrip();
        renderDock();
        clampAll();
    }

    private void renderHdmiStrip() {
        mHdmiStrip.removeAllViews();
        mHdmiCaps = new TextView[mHdmiItems.size()];
        mHdmiIcons = new int[mHdmiItems.size()];
        java.util.regex.Pattern hd = java.util.regex.Pattern.compile("(?i)hdmi\\s*(\\d+)");
        for (int i = 0; i < mHdmiItems.size(); i++) {
            final int idx = i;
            String lab = mHdmiItems.get(i).label;
            int icon = R.drawable.ic_tv;
            String shortLabel = lab;
            if (mHdmiItems.get(i).appIcon != null) shortLabel = "";
            java.util.regex.Matcher m = hd.matcher(lab);
            if (mHdmiItems.get(i).appIcon != null) { }
            else if (m.find()) { icon = R.drawable.ic_hdmi; shortLabel = m.group(1); }
            else if (lab.toLowerCase(Locale.US).contains("airplay")) { icon = R.drawable.ic_airplay; shortLabel = ""; }
            else if (lab.equalsIgnoreCase("Live TV")) { shortLabel = "Live"; }
            else if (lab.equalsIgnoreCase("TV")) { shortLabel = ""; }
            mHdmiIcons[i] = icon;
            if (mHdmiItems.get(i).appIcon != null && i == mRecentStart) {
                View sep = new View(this);
                sep.setBackgroundColor(0x33FFFFFF);
                mHdmiStrip.addView(sep, lpW(Math.max(1, dp(1)), dp(28), 0, dp(14), 0, dp(4), 0));
            }
            TextView cap = newText(shortLabel, 14, 600, 0xFFF2F4F7);
            cap.setBackground(GlassHelper.hdmiCap(this, false));
            cap.setPadding(dp(15), dp(9), dp(15), dp(9));
            cap.setGravity(Gravity.CENTER);
            cap.setSingleLine(true);
            cap.setContentDescription(lab);
            cap.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    mHdmiIdx = idx; mZone = ZONE_HDMI; applyFocus(true); activateHdmi();
                }
            });
            mHdmiCaps[i] = cap;
            mHdmiStrip.addView(cap, lpW(WC, WC, 0, i == 0 ? 0 : dp(10), 0, 0, 0));
        }
        styleHdmiCaps(-1);
        mHdmiStrip.setVisibility(mHdmiItems.isEmpty() ? View.GONE : View.VISIBLE);
        if (mHdmiDivider != null) mHdmiDivider.setVisibility(mHdmiItems.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void activateHdmi() {
        if (mHdmiIdx < 0 || mHdmiIdx >= mHdmiItems.size()) return;
        Runnable r = mHdmiItems.get(mHdmiIdx).run;
        if (r != null) r.run();
    }

    private void cycleHdmi(int delta) {
        if (mHdmiItems.isEmpty()) return;
        mZone = ZONE_HDMI;
        mHdmiIdx = (mHdmiIdx + delta + mHdmiItems.size()) % mHdmiItems.size();
        applyFocus(true);
        activateHdmi();
    }

    // ---------------------------------------------------------------- dock

    private void buildDock() {
        loadDockItems();

        // One glass shelf: inputs on top, pins below. Ends the orphaned INPUTS row.
        mDockBand = new FrameLayout(this);
        mDockBand.setClipChildren(false);
        mDockBand.setClipToPadding(false);
        // no container: cards float on the wallpaper like tvOS
        // Tall enough for inputs + 2 full pin rows + labels (was clipping row 2).
        mRoot.addView(mDockBand, lpF(MP, dp(330), Gravity.BOTTOM, dp(48), 0, dp(48), dp(14)));

        LinearLayout shelf = new LinearLayout(this);
        shelf.setOrientation(LinearLayout.VERTICAL);
        shelf.setClipChildren(false);
        shelf.setPadding(0, dp(8), 0, dp(8));

        if (mHdmiStrip.getParent() instanceof ViewGroup) {
            ((ViewGroup) mHdmiStrip.getParent()).removeView(mHdmiStrip);
        }
        mHdmiStrip.setPadding(0, 0, 0, 0);
        shelf.addView(mHdmiStrip, lpW(MP, WC, 0, 0, 0, 0, 0));

        View divider = new View(this);
        mHdmiDivider = divider;
        divider.setBackgroundColor(0x00000000);
        shelf.addView(divider, lpW(MP, Math.max(1, dp(1)), 0, 0, dp(8), 0, dp(10)));

        mDockCol = new LinearLayout(this);
        mDockCol.setOrientation(LinearLayout.VERTICAL);
        mDockCol.setGravity(Gravity.START);
        mDockCol.setClipChildren(false);
        shelf.addView(mDockCol, lpW(MP, WC, 0, 0, 0, 0, 0));

        mDockBand.addView(shelf, lpF(MP, MP, Gravity.CENTER, 0, 0, 0, 0));
        renderDock();
    }

    private static final int MAX_PINS = 18;

    /** Custom shortcut rows are "id\tname\turl\tpackage". */
    private List<String[]> customs() {
        List<String[]> out = new ArrayList<>();
        for (String line : mPrefs.getString("customs", "").split("\n")) {
            String[] p = line.split("\t", -1);
            if (p.length >= 4 && !p[0].isEmpty()) out.add(p);
        }
        return out;
    }

    private String[] findCustom(String id) {
        for (String[] c : customs()) if (c[0].equals(id)) return c;
        return null;
    }

    private void saveCustoms(List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (String[] c : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(c[0]).append('\t').append(c[1].replace('\t', ' ').replace('\n', ' '))
                    .append('\t').append(c[2].replace('\t', ' ').replace('\n', ' ')).append('\t').append(c[3]);
        }
        mPrefs.edit().putString("customs", sb.toString()).apply();
    }

    private void addCustom(String name, String url, String pkg) {
        if (name.isEmpty() || url.isEmpty()) return;
        if (url.indexOf("://") < 0) url = "https://" + url;
        List<String[]> list = customs();
        String id = Long.toString(System.currentTimeMillis(), 36);
        list.add(new String[] { id, name, url, pkg == null ? "" : pkg });
        saveCustoms(list);
        List<String> k = pinKeys();
        if (k.size() < MAX_PINS) { k.add("custom:" + id); savePinKeys(k); }
        reloadDock();
        fetchFavicon(id, url);
        android.widget.Toast.makeText(this, "Added \u201C" + name + "\u201D to home", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void removeCustom(String id) {
        List<String[]> list = customs();
        for (java.util.Iterator<String[]> it = list.iterator(); it.hasNext(); ) if (it.next()[0].equals(id)) it.remove();
        saveCustoms(list);
        List<String> k = pinKeys();
        k.remove("custom:" + id);
        savePinKeys(k);
        favFile(id).delete();
        reloadDock();
    }

    private String firstInstalled(String... pkgs) {
        for (String p : pkgs) if (isInstalled(p)) return p;
        return "";
    }

    private List<String> pinKeys() {
        List<String> out = new ArrayList<>();
        String saved = mPrefs.getString("pins", null);
        if (saved == null) {
            for (String k : getResources().getStringArray(R.array.pin_default)) out.add(k.trim());
            return out;
        }
        for (String k : saved.split("\n")) if (!k.trim().isEmpty()) out.add(k.trim());
        return out;
    }

    private void savePinKeys(List<String> keys) {
        mPrefs.edit().putString("pins", TextUtils.join("\n", keys)).apply();
    }

    /** Reloads the home shelf after the pinned set changed. */
    private void reloadDock() {
        loadDockItems();
        renderDock();
        clampAll();
    }

    private void loadDockItems() {
        mDockItems.clear();
        String[] pkgs = getResources().getStringArray(R.array.dock_pkg);
        String[] labels = getResources().getStringArray(R.array.dock_label);
        String[] data = getResources().getStringArray(R.array.dock_data);
        String[] icons = getResources().getStringArray(R.array.dock_icon);
        for (String key : pinKeys()) {
            DockItem d = new DockItem();
            d.key = key;
            d.label = ""; d.url = "";
            if (key.startsWith("custom:")) {
                String[] cu = findCustom(key.substring(7));
                if (cu == null) continue;
                d.custom = true; d.id = cu[0]; d.label = cu[1]; d.url = cu[2]; d.pkg = cu[3];
                if (!d.pkg.isEmpty() && !isInstalled(d.pkg)) continue;
                mDockItems.add(d);
                continue;
            }
            d.pkg = key.split("\\|")[0].trim();
            for (int i = 0; i < pkgs.length; i++) {
                if (!pkgs[i].trim().equals(key)) continue;
                d.label = (i < labels.length) ? labels[i] : "";
                d.url = (i < data.length) ? data[i].trim() : "";
                if (i < icons.length && !icons[i].trim().isEmpty()) {
                    d.iconRes = getResources().getIdentifier(icons[i].trim(), "drawable", getPackageName());
                }
                break;
            }
            if (!isInstalled(d.pkg)) continue;
            d.info = resolveFor(d.pkg);
            if (d.info == null) continue;
            if (d.label.isEmpty()) d.label = sanitize(String.valueOf(d.info.loadLabel(mPM)));
            if (d.label.isEmpty()) continue;
            mDockItems.add(d);
        }
    }

    private boolean isInstalled(String pkg) {
        if ("com.mixplorer".equals(pkg)) return false;
        try { mPM.getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    private ResolveInfo resolveFor(String pkg) {
        Intent lean = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER").setPackage(pkg);
        List<ResolveInfo> r = mPM.queryIntentActivities(lean, 0);
        if (r.isEmpty()) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
            r = mPM.queryIntentActivities(i, 0);
        }
        return r.isEmpty() ? null : r.get(0);
    }

    private void renderDock() {
        mDockCol.removeAllViews();
        mDockTiles = new View[mDockItems.size()];
        mLit.clear();
        int cols = Math.max(4, Math.min(mPinCols, 8));
        int usable = getResources().getDisplayMetrics().widthPixels - dp(48 + 48);
        int gap = dp(4);
        int cell = (usable - gap * (cols - 1)) / cols;
        int pad = dp(6);                       // room for the lift so nothing is clipped
        int cardW = cell - 2 * pad;
        int cardH = Math.round(cardW * 9f / 16f);
        int cellH = cardH + 2 * pad;
        LinearLayout row = null;
        for (int i = 0; i < mDockItems.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                row.setClipChildren(false);
                mDockCol.addView(row, lpW(MP, WC, 0, 0, i == 0 ? 0 : gap, 0, 0));
            }
            final DockItem d = mDockItems.get(i);
            final int idx = i;
            FrameLayout card = d.custom ? buildCardBmp(customCardBitmap(d, cardW, cardH), d.label, cardW, cardH, true)
                    : buildCard(d.info, d.iconRes, d.label, cardW, cardH, true);
            FrameLayout tile = new FrameLayout(this);
            tile.setClipChildren(false);
            tile.setClipToPadding(false);
            tile.setPadding(pad, pad, pad, pad);
            tile.addView(card, new FrameLayout.LayoutParams(cardW, cardH));
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchDock(idx); }
            });
            mDockTiles[idx] = tile;
            int endGap = ((i % cols) == cols - 1) ? 0 : gap;
            row.addView(tile, lpW(cell, cellH, 0, 0, 0, endGap, 0));
        }
        int rows = (mDockItems.size() + cols - 1) / cols;
        FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) mDockBand.getLayoutParams();
        bp.height = dp(16 + 6) + (mHdmiItems.isEmpty() ? 0 : dp(56)) + rows * cellH + Math.max(0, rows - 1) * gap;
        mDockBand.setLayoutParams(bp);
        noClip(mDockCol);
    }

    /**
     * Starts an external activity only after the current key event has been fully consumed.
     * Starting it straight from the ACTION_DOWN of a DPAD key lets the matching ACTION_UP land in
     * the new window, which shows up as a phantom click in the app we just opened.
     */
    private void startExternal(final Intent intent) {
        if (intent == null || mRoot == null) return;
        mRoot.postDelayed(new Runnable() {
            @Override public void run() {
                try { startActivity(intent); } catch (Exception ignored) { }
            }
        }, 180L);
    }

    private void launchDock(int idx) {
        if (idx < 0 || idx >= mDockItems.size()) return;
        DockItem d = mDockItems.get(idx);
        if (d.url != null && !d.url.isEmpty()) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(d.url));
            if (d.pkg != null && !d.pkg.isEmpty()) i.setPackage(d.pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (d.pkg != null && !d.pkg.isEmpty()) recordRecent(d.pkg);
            if (i.resolveActivity(mPM) == null) {
                android.widget.Toast.makeText(this, "No app can open this link", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            startExternal(i);
        } else {
            launchApp(d.info);
        }
    }

    private void launchApp(ResolveInfo ri) {
        if (ri == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            recordRecent(ri.activityInfo.packageName);
            startExternal(i);
        } catch (Exception ignored) { }
    }

    // --------------------------------------------------------------- focus

    private void restoreFocus() {
        mTopIdx = mPrefs.getInt("topIdx", 0);
        mGridIdx = mPrefs.getInt("gridIdx", 0);
        mDockIdx = mPrefs.getInt("dockIdx", 0);
        mHdmiIdx = mPrefs.getInt("hdmiIdx", 0);
        mOnSchedule = false;
        mAppsOpen = false;
        if (mPillViews != null && mPillViews.length > 0) mPillViews[PILL_APPS].setText("All apps");
        if (mHdmiStrip != null) mHdmiStrip.setVisibility(mHdmiItems.isEmpty() ? View.GONE : View.VISIBLE);
        if (mDockBand != null) { mDockBand.animate().cancel(); mDockBand.setAlpha(1f); }
        clampAll();
        mZone = (mDockItems != null && !mDockItems.isEmpty()) ? ZONE_DOCK : ZONE_TOP;
    }

    private void saveFocus() {
        mPrefs.edit()
                .putInt("topIdx", mTopIdx)
                .putInt("gridIdx", mGridIdx)
                .putInt("dockIdx", mDockIdx)
                .putInt("hdmiIdx", mHdmiIdx)
                .apply();
    }

    private void clampAll() {
        if (mPillViews != null && mPillViews.length > 0) mTopIdx = clamp(mTopIdx, 0, mPillViews.length - 1);
        if (mGridApps != null && !mGridApps.isEmpty()) mGridIdx = clamp(mGridIdx, 0, mGridApps.size() - 1);
        if (mDockItems != null && !mDockItems.isEmpty()) mDockIdx = clamp(mDockIdx, 0, mDockItems.size() - 1);
        if (!mHdmiItems.isEmpty()) mHdmiIdx = clamp(mHdmiIdx, 0, mHdmiItems.size() - 1);
    }

    private int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private View gridTileAt(int idx) {
        if (mGridWrap == null || mGridApps == null || mGridApps.isEmpty()) return null;
        int row = idx / GRID_COLS;
        if (row >= mGridWrap.getChildCount()) return null;
        LinearLayout r = (LinearLayout) mGridWrap.getChildAt(row);
        int col = idx % GRID_COLS;
        if (col >= r.getChildCount()) return null;
        return r.getChildAt(col);
    }

    /** Re-snaps the glass once a smooth-scroll settles (fixes highlight lag). */
    private final Runnable mGlassSnap = new Runnable() {
        @Override public void run() {
            if (mZone == ZONE_GRID || mZone == ZONE_DOCK) applyFocus(false);
        }
    };

    private void scheduleGlassSnap() {
        mRoot.removeCallbacks(mGlassSnap);
        mRoot.postDelayed(mGlassSnap, 260);
    }

    // ---------------------------------------------------------- app drawer

    /** Opens/closes the app grid. Home stays dock-only until this is called. */
    private void setAppsOpen(boolean open, boolean animate) {
        if (mAppsOpen == open) return;
        mAppsOpen = open;
        if (mScrim != null) mScrim.animate().alpha(open ? 1f : 0f).setDuration(animate ? 220 : 0).start();
        if (!open && mEditMode) {
            mEditMode = false;
            mEditBanner.setVisibility(View.GONE);
            updateBadges();
            reloadDock();
        }
        if (mPillViews != null && mPillViews.length > 0) {
            mPillViews[PILL_APPS].setText(open ? "Hide apps" : "All apps");
        }
        mGridScroll.animate().cancel();
        if (mHero != null) {
            mHero.animate().cancel();
            mHero.animate().alpha(open ? 0f : 1f)
                    .setDuration(animate ? (open ? 140 : 200) : 0).start();
        }
        if (open) {
            mGridScroll.setVisibility(View.VISIBLE);
            if (animate) {
                mGridScroll.setAlpha(0f);
                mGridScroll.setTranslationY(dp(22));
                mGridScroll.animate().alpha(1f).translationY(0f).setDuration(220)
                        .setInterpolator(new DecelerateInterpolator(1.6f))
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                // the grid slides while the highlight is positioned -> re-snap on settle
                                if (mAppsOpen && mZone == ZONE_GRID) applyFocus(false);
                                updateGridFade();
                            }
                        }).start();
            } else {
                mGridScroll.setAlpha(1f);
                mGridScroll.setTranslationY(0f);
            }
            // opening always starts at the top row; the column is kept so the same app stays in reach
            mGridIdx = Math.min(mGridIdx % GRID_COLS, Math.max(0, mGridApps.size() - 1));
            mZone = ZONE_GRID;
            if (mHdmiStrip != null) mHdmiStrip.setVisibility(View.INVISIBLE);
            if (mDockBand != null) { mDockBand.animate().cancel(); mDockBand.setAlpha(0f); }
        } else {
            if (animate) {
                mGridScroll.animate().alpha(0f).translationY(dp(14)).setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                if (!mAppsOpen) mGridScroll.setVisibility(View.GONE);
                            }
                        }).start();
            } else {
                mGridScroll.setAlpha(1f);
                mGridScroll.setTranslationY(0f);
                mGridScroll.setVisibility(View.GONE);
            }
            mGridFade.setVisibility(View.GONE);
            if (mHdmiStrip != null) mHdmiStrip.setVisibility(mHdmiItems.isEmpty() ? View.GONE : View.VISIBLE);
            if (mDockBand != null) { mDockBand.animate().cancel(); mDockBand.setAlpha(1f); }
            mZone = (mDockItems != null && !mDockItems.isEmpty()) ? ZONE_DOCK : ZONE_TOP;
        }
        applyFocus(true);
        mGridScroll.post(new Runnable() {
            @Override public void run() { updateGridFade(); }
        });
    }

    private void toggleApps() {
        setAppsOpen(!mAppsOpen, true);
    }

    private void applyFocus(boolean animate) {
        int sig = (mZone == ZONE_TOP ? 100 + mTopIdx * 2 + (mOnSchedule ? 1 : 0) : -1) * 2 + (mAppsOpen ? 1 : 0);
        if (sig != mPillSig && mPills != null) {
            mPillSig = sig;
            android.transition.TransitionManager.beginDelayedTransition(mPills,
                    new android.transition.ChangeBounds().setDuration(140));
        }
        if (mZone != ZONE_GRID) unlitGrid();
        if (mAppsOpen && mDockBand != null) {
            mDockBand.animate().alpha(mZone == ZONE_DOCK ? 1f : 0f).setDuration(animate ? 200 : 0).start();
        }
        if (mDim != null) {
            mDim.animate().alpha((mZone == ZONE_DOCK && mAppsOpen) ? 1f : 0f)
                    .setDuration(animate ? 220 : 0).start();
        }
        styleHdmiCaps(-1);
        styleSchedule(false);
        View target = null;
        switch (mZone) {
            case ZONE_TOP:
                resetDockTiles(null);
                for (int i = 0; i < mGridLabels.size(); i++) mGridLabels.get(i).setTextColor(0xFFB4BBC2);
                if (mOnSchedule && mSchedulePill != null && mSchedulePill.getVisibility() == View.VISIBLE) {
                    for (int i = 0; i < mPillViews.length; i++) stylePill(i, false);
                    styleSchedule(true);
                    target = mSchedulePill;
                    mGlass.setVisibility(View.VISIBLE);
                } else {
                    for (int i = 0; i < mPillViews.length; i++) stylePill(i, i == mTopIdx);
                    mGlass.setVisibility(View.GONE);
                    if (mPillViews != null && mTopIdx >= 0 && mTopIdx < mPillViews.length) {
                        target = mPillViews[mTopIdx];
                        mGlass.setVisibility(View.VISIBLE);
                    }
                }
                break;
            case ZONE_HDMI:
                resetPills();
                resetDockTiles(null);
                styleHdmiCaps(mHdmiIdx);
                if (mHdmiCaps != null && mHdmiIdx >= 0 && mHdmiIdx < mHdmiCaps.length) {
                    target = mHdmiCaps[mHdmiIdx];
                    mGlass.setVisibility(View.VISIBLE);
                } else {
                    mGlass.setVisibility(View.GONE);
                }
                break;
            case ZONE_GRID:
                if (!mAppsOpen) { mZone = ZONE_DOCK; applyFocus(animate); return; }
                resetPills();
                for (int i = 0; i < mGridLabels.size(); i++) mGridLabels.get(i).setTextColor(0xFFB4BBC2);
                target = gridTileAt(mGridIdx);
                if (target != null) {
                    TextView lbl = findLabelInside(target);
                    if (lbl != null) lbl.setTextColor(0xFFFFFFFF);
                    int row = mGridIdx / GRID_COLS;
                    int rowH = mGridRowH > 0 ? mGridRowH : dp(TILE_H_DP) + dp(10);
                    if (mGridScroll.getMeasuredHeight() > 0) {
                        int sh = mGridScroll.getMeasuredHeight();
                        int top = mGridWrap.getPaddingTop() + row * rowH;
                        int bottom = top + rowH;
                        int cur = mGridScroll.getScrollY();
                        int margin = dp(10);
                        int want = cur;
                        if (top - margin < cur) want = top - margin;
                        else if (bottom + margin > cur + sh) want = bottom + margin - sh;
                        want = clamp(want, 0, Math.max(0, mGridWrap.getMeasuredHeight() - sh));
                        if (want != cur) tweenScroll(want);
                    }
                    mGlass.setVisibility(View.VISIBLE);
                    if (mLitGrid != null && mLitGrid != target) setCardFocus(mLitGrid, false);
                    setCardFocus(target, true);
                    mLitGrid = target;
                } else {
                    mGlass.setVisibility(View.GONE);
                }
                resetDockTiles(null);
                break;
            case ZONE_DOCK:
                resetPills();
                for (int i = 0; i < mGridLabels.size(); i++) mGridLabels.get(i).setTextColor(0xFFB4BBC2);
                target = (mDockTiles != null && mDockIdx >= 0 && mDockIdx < mDockTiles.length)
                        ? mDockTiles[mDockIdx] : null;
                resetDockTiles(target);
                if (target != null) setCardFocus(target, true);
                break;
            case ZONE_PANEL:
                mGlass.setVisibility(View.GONE);
                for (int i = 0; i < mPillViews.length; i++) stylePill(i, mPanelKind != 13 && i == mTopIdx);
                for (int i = 0; i < mPanelRows.size(); i++) {
                    TextView r = mPanelRows.get(i);
                    boolean on = i == mPanelIdx;
                    r.setBackground(on ? round(mPanelMat ? 0xFFE3E6EC : 0xFFFFFFFF, 0, 0, mPanelMat ? 12 : 14) : null);
                    r.setTextColor(on ? 0xFF0C0D10 : 0xFFE8EAED);
                    if (i < mPanelVals.size() && mPanelVals.get(i) != null) styleChip(mPanelVals.get(i), on);
                    if (on) scrollPanelTo(r);
                }
                return;
        }
        mGlass.setVisibility(View.GONE);   // focus is shown by lift + gloss on the item itself
    }

    private void styleSchedule(boolean focused) {
        if (mSchedulePill == null) return;
        mSchedulePill.setBackground(GlassHelper.pill(this, focused, true, mAdvancedGlass));
        lift(mSchedulePill, focused, 1.06f);
        if (mScheduleLine != null) mScheduleLine.setTextColor(focused ? 0xFF0C0D10 : 0xFFF0F4FF);
    }

    private void styleHdmiCaps(int focusedIdx) {
        if (mHdmiCaps == null) return;
        for (int i = 0; i < mHdmiCaps.length; i++) {
            if (mHdmiCaps[i] == null) continue;
            boolean on = i == focusedIdx;
            lift(mHdmiCaps[i], on, 1.08f);
            mHdmiCaps[i].setBackground(GlassHelper.hdmiCap(this, on));
            int col = on ? 0xFF0C0D10 : 0xFFE8ECF2;
            mHdmiCaps[i].setTextColor(col);
            if (mHdmiItems.get(i).appIcon != null) {
                Drawable ic = mHdmiItems.get(i).appIcon.getConstantState() != null
                        ? mHdmiItems.get(i).appIcon.getConstantState().newDrawable().mutate()
                        : mHdmiItems.get(i).appIcon;
                ic.setBounds(0, 0, dp(30), dp(30));
                mHdmiCaps[i].setCompoundDrawables(ic, null, null, null);
                mHdmiCaps[i].setCompoundDrawablePadding(0);
                mHdmiCaps[i].setPadding(dp(11), dp(9), dp(11), dp(9));
                continue;
            }
            Drawable d = getDrawable(mHdmiIcons[i]).mutate();
            d.setTint(col);
            mHdmiCaps[i].setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);
            mHdmiCaps[i].setCompoundDrawablePadding(mHdmiCaps[i].getText().length() == 0 ? 0 : dp(6));
        }
    }

    private void resetPills() {
        for (int i = 0; i < mPillViews.length; i++) stylePill(i, false);
    }

    private void resetDockTiles(View focused) {
        if (mDockTiles == null) return;
        for (View v : mDockTiles) {
            if (v != null && v != focused) setCardFocus(v, false);
        }
    }

    private void unlitGrid() {
        if (mLitGrid != null) { setCardFocus(mLitGrid, false); mLitGrid = null; }
    }

    private View pinIconWrap(View tile) {
        if (tile instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) tile;
            if (g.getChildCount() > 0) {
                View c = g.getChildAt(0);
                if (c instanceof ImageView || "pinIcon".equals(c.getTag())) return c;
                if (c instanceof FrameLayout) return c;
            }
        }
        return null;
    }

    private TextView findLabelInside(View tile) {
        if (tile instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) tile;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof TextView) return (TextView) c;
                if (c instanceof ViewGroup) {
                    TextView t = findLabelInside(c);
                    if (t != null) return t;
                }
            }
        }
        return null;
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Renders any app icon onto a uniform rounded "plate" so the shelf looks
     * consistent even when apps ship legacy/full-bleed logos.
     *  - adaptive icons: background layer fills the plate (clipped to the
     *    rounded shape), foreground drawn over it, plus a hairline ring.
     *  - everything else: soft neutral gradient plate with the icon inset,
     *    so busy legacy logos sit framed instead of colliding.
     */
    private BitmapDrawable iconPlated(Drawable icon, int sizePx, float radiusDp) {
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = dp(radiusDp);
        RectF full = new RectF(0, 0, sizePx, sizePx);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ai = (AdaptiveIconDrawable) icon;
            Path clip = new Path();
            clip.addRoundRect(full, r, r, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            Drawable bg = ai.getBackground();
            if (bg != null) { bg.setBounds(0, 0, sizePx, sizePx); bg.draw(c); }
            Drawable fg = ai.getForeground();
            if (fg != null) {
                fg.setBounds(0, 0, sizePx, sizePx);
                fg.mutate().setAlpha(255);
                fg.draw(c);
            }
            // uniform sheen so saturated adaptive backgrounds sit at equal weight
            Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
            sheen.setShader(new LinearGradient(0, 0, 0, sizePx,
                    new int[] { 0x12FFFFFF, 0x00FFFFFF, 0x1B000000 },
                    new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
            c.drawRoundRect(full, r, r, sheen);
            c.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0x1AFFFFFF);
            c.drawRoundRect(full, r, r, paint);
        } else {
            paint.setShader(new LinearGradient(0, 0, 0, sizePx,
                    0xFF232730, 0xFF13151B, Shader.TileMode.CLAMP));
            c.drawRoundRect(full, r, r, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(0x14FFFFFF);
            c.drawRoundRect(full, r, r, paint);
            int inset = Math.round(sizePx * 0.16f);
            Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
            icon.setBounds(inset, inset, sizePx - inset, sizePx - inset);
            icon.draw(c);
            sheen.setShader(new LinearGradient(0, 0, 0, sizePx,
                    new int[] { 0x10FFFFFF, 0x00FFFFFF, 0x14000000 },
                    new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
            c.drawRoundRect(full, r, r, sheen);
        }

        BitmapDrawable d = new BitmapDrawable(getResources(), b);
        d.setFilterBitmap(true);
        return d;
    }

    /**
     * Full-bleed variant of {@link #iconPlated} for supplied artwork (e.g. channel logos):
     * the image fills the plate instead of being inset on a neutral frame.
     */
    private BitmapDrawable plateArt(Drawable art, int sizePx, float radiusDp) {
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = dp(radiusDp);
        RectF full = new RectF(0, 0, sizePx, sizePx);
        Path clip = new Path();
        clip.addRoundRect(full, r, r, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        base.setColor(0xFF15171D);
        c.drawRect(full, base);
        if (art != null) {
            art.setBounds(0, 0, sizePx, sizePx);
            art.draw(c);
        }
        Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setShader(new LinearGradient(0, 0, 0, sizePx,
                new int[] { 0x12FFFFFF, 0x00FFFFFF, 0x1B000000 },
                new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
        c.drawRoundRect(full, r, r, sheen);
        c.restore();
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(1));
        ring.setColor(0x1AFFFFFF);
        c.drawRoundRect(full, r, r, ring);
        BitmapDrawable out = new BitmapDrawable(getResources(), b);
        out.setFilterBitmap(true);
        return out;
    }

    /** Average of the saturated pixels of an app icon = ambient accent. */
    private int avgIconColor(ResolveInfo info) {
        try {
            Bitmap bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            Canvas cnv = new Canvas(bmp);
            info.loadIcon(mPM).draw(cnv);
            long rs = 0, gs = 0, bs = 0, n = 0;
            for (int y = 4; y < 28; y += 2) for (int x = 4; x < 28; x += 2) {
                int c = bmp.getPixel(x, y);
                if (Color.alpha(c) < 40) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(c, hsv);
                if (hsv[1] < 0.25f) continue;
                rs += Color.red(c); gs += Color.green(c); bs += Color.blue(c); n++;
            }
            if (n == 0) return 0xFF5C6BFF;
            return Color.argb(255, (int) (rs / n), (int) (gs / n), (int) (bs / n));
        } catch (Throwable t) {
            return 0xFF5C6BFF;
        }
    }

    // ----------------------------------------------------------- profiles

    private static final String[] COLOR_NAMES = { "Blue", "Pink", "Teal", "Amber", "Violet", "Green" };

    /** Profile rows are "id|name|colourIdx". p0 keeps the pre-profile prefs file so nothing is lost. */
    private void initProfiles() {
        String raw = mGlobal.getString("profiles", null);
        if (raw == null) {
            String def = getString(R.string.greeting_name).trim();
            raw = "p0|" + (def.isEmpty() ? "Guest" : def) + "|0";
            mGlobal.edit().putString("profiles", raw).putString("active", "p0").apply();
        }
        mProfileId = mGlobal.getString("active", "p0");
        if (findProfile(mProfileId) == null) mProfileId = profiles().get(0)[0];
        mPrefs = prefsFor(mProfileId);
    }

    private SharedPreferences prefsFor(String id) {
        return getSharedPreferences("p0".equals(id) ? "tvhome" : "tvhome_" + id, MODE_PRIVATE);
    }

    private List<String[]> profiles() {
        List<String[]> out = new ArrayList<>();
        for (String line : mGlobal.getString("profiles", "").split("\n")) {
            String[] p = line.split("\\|", 3);
            if (p.length < 2 || p[0].isEmpty()) continue;
            out.add(new String[] { p[0], p[1], p.length > 2 ? p[2] : "0" });
        }
        return out;
    }

    private String[] findProfile(String id) {
        for (String[] p : profiles()) if (p[0].equals(id)) return p;
        return null;
    }

    private String[] activeProfile() {
        String[] p = findProfile(mProfileId);
        return p != null ? p : new String[] { "p0", "Guest", "0" };
    }

    private String profileName() { return activeProfile()[1]; }

    private void saveProfiles(List<String[]> list) {
        StringBuilder sb = new StringBuilder();
        for (String[] p : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p[0]).append('|').append(p[1].replace("|", " ").replace("\n", " ")).append('|').append(p[2]);
        }
        mGlobal.edit().putString("profiles", sb.toString()).apply();
    }

    private Drawable avatar(int sizePx, String[] pr) {
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int ci = 0;
        try { ci = Integer.parseInt(pr[2]); } catch (Exception ignored) { }
        p.setColor(AVATAR_COLORS[ci % AVATAR_COLORS.length]);
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(sizePx * 0.56f);
        if (mFont != null) p.setTypeface(Typeface.create(mFont, 600, false));
        String ini = pr[1].trim().isEmpty() ? "?" : pr[1].trim().substring(0, 1).toUpperCase(Locale.getDefault());
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(ini, sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2f, p);
        return new BitmapDrawable(getResources(), b);
    }

    private void refreshProfileUi() {
        if (mGreeting != null) mGreeting.setText(greeting());
        if (mPillViews != null) stylePill(PILL_PROFILE, mZone == ZONE_TOP && mTopIdx == PILL_PROFILE);
    }

    private void switchProfile(String id) {
        if (id.equals(mProfileId)) { hidePanel(); return; }
        saveFocus();
        mGlobal.edit().putString("active", id).apply();
        mProfileId = id;
        mPrefs = prefsFor(id);
        hidePanel();
        mEditMode = false;
        mEditBanner.setVisibility(View.GONE);
        applyClockPrefs();
        applyPillVisibility();
        mWallpaper.setImageDrawable(loadWallpaperDrawable());
        reloadInputs();
        mDockIdx = mPrefs.getInt("dockIdx", 0);
        mHdmiIdx = mPrefs.getInt("hdmiIdx", 0);
        clampAll();
        mZone = mDockItems.isEmpty() ? ZONE_TOP : ZONE_DOCK;
        refreshProfileUi();
        refreshWeather(false);
        loadCalCache();
        refreshCalendar(false);
        updateScheduleLine();
        applyFocus(false);
        android.widget.Toast.makeText(this, "Hi, " + profileName(), android.widget.Toast.LENGTH_SHORT).show();
    }

    private void addProfile(String name) {
        List<String[]> list = profiles();
        int n = 1;
        for (String[] p : list) {
            try { n = Math.max(n, Integer.parseInt(p[0].substring(1)) + 1); } catch (Exception ignored) { }
        }
        String id = "p" + n;
        list.add(new String[] { id, name, String.valueOf(list.size() % AVATAR_COLORS.length) });
        saveProfiles(list);
        switchProfile(id);
    }

    private void renameProfile(String id, String name) {
        List<String[]> list = profiles();
        for (String[] p : list) if (p[0].equals(id)) p[1] = name;
        saveProfiles(list);
        refreshProfileUi();
    }

    private void cycleColor() {
        List<String[]> list = profiles();
        for (String[] p : list) if (p[0].equals(mProfileId)) {
            int ci = 0;
            try { ci = Integer.parseInt(p[2]); } catch (Exception ignored) { }
            p[2] = String.valueOf((ci + 1) % AVATAR_COLORS.length);
        }
        saveProfiles(list);
        refreshProfileUi();
    }

    private void deleteProfile(String id) {
        List<String[]> list = profiles();
        if (list.size() <= 1) return;
        String next = null;
        for (java.util.Iterator<String[]> it = list.iterator(); it.hasNext(); ) {
            String[] p = it.next();
            if (p[0].equals(id)) it.remove(); else if (next == null) next = p[0];
        }
        saveProfiles(list);
        prefsFor(id).edit().clear().apply();
        mProfileId = "";                       // force switchProfile to run
        switchProfile(next);
    }

    // ------------------------------------------------ first boot / who's watching

    private FrameLayout mWho;
    private final List<View> mWhoItems = new ArrayList<>();
    private int mWhoIdx = 0;

    // --------------------------------------------------- long-press app menu

    private boolean mCenterDown = false, mLongHandled = false;
    private int mMenuRetZone = ZONE_DOCK;
    private View mMenuAnchor;
    private String mMenuKey = "", mMenuPkg = "", mMenuTitle = "";
    private boolean mMenuFromDock = false;
    private int mMenuDockIdx = 0;

    private final Runnable mLongPress = new Runnable() {
        @Override public void run() {
            if (!mCenterDown) return;
            mLongHandled = true;
            openAppMenu();
        }
    };

    private void openAppMenu() {
        if (mZone == ZONE_DOCK && mDockIdx >= 0 && mDockIdx < mDockItems.size()) {
            DockItem d = mDockItems.get(mDockIdx);
            mMenuFromDock = true; mMenuDockIdx = mDockIdx;
            mMenuKey = d.key; mMenuPkg = d.custom ? d.pkg : d.pkg; mMenuTitle = d.label;
            mMenuAnchor = mDockTiles[mDockIdx];
        } else if (mZone == ZONE_GRID && mGridIdx >= 0 && mGridIdx < mGridApps.size()) {
            ResolveInfo ri = mGridApps.get(mGridIdx);
            mMenuFromDock = false;
            mMenuPkg = ri.activityInfo.packageName; mMenuKey = mMenuPkg;
            mMenuTitle = sanitize(String.valueOf(ri.loadLabel(mPM)));
            mMenuAnchor = gridTileAt(mGridIdx);
        } else return;
        mMenuRetZone = mZone;
        mRoot.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        showPanel(13);
    }

    private void openAppInfo(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.resolveActivity(mPM) != null) startExternal(i);
        else android.widget.Toast.makeText(this, "App info isn't available here", android.widget.Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------ rearrange

    private boolean mReorder = false, mGrabbed = false;

    private void setReorderBanner() {
        mEditBanner.removeAllViews();
        mEditBanner.addView(newText("Rearrange home", 26, 560, 0xFFFFFFFF), lpW(WC, WC, 0, 0, 0, 0, 0));
        mEditBanner.addView(newText(mGrabbed ? "Move with the arrows  \u00B7  OK to drop"
                : "OK picks up an app  \u00B7  BACK when you're done", 14, 420, 0xFFB9C2CE), lpW(WC, WC, 0, 0, dp(4), 0, 0));
    }

    private void enterReorder() {
        hidePanel();
        if (mDockItems.size() < 2) {
            android.widget.Toast.makeText(this, "Pin at least two apps first", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        mReorder = true; mGrabbed = false;
        mHero.animate().cancel();
        mHero.setAlpha(0f);
        setReorderBanner();
        mEditBanner.setVisibility(View.VISIBLE);
        mZone = ZONE_DOCK;
        mDockIdx = clamp(mDockIdx, 0, mDockItems.size() - 1);
        applyFocus(true);
    }

    private void exitReorder() {
        mReorder = false; mGrabbed = false;
        mEditBanner.setVisibility(View.GONE);
        mHero.animate().cancel();
        mHero.animate().alpha(1f).setDuration(200).start();
        mEditBanner.removeAllViews();
        mEditBanner.addView(newText("Edit home apps", 26, 560, 0xFFFFFFFF), lpW(WC, WC, 0, 0, 0, 0, 0));
        mEditBanner.addView(newText("OK pins or unpins an app  \u00B7  BACK when you're done", 14, 420, 0xFFB9C2CE),
                lpW(WC, WC, 0, 0, dp(4), 0, 0));
        applyFocus(true);
    }

    /** Arrow key while rearranging: moves the cursor, or carries the grabbed card. */
    private boolean reorderKey(int code) {
        int cols = Math.max(4, Math.min(mPinCols, 8));
        int d = 0;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: d = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: d = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: d = -cols; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: d = cols; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                mGrabbed = !mGrabbed; setReorderBanner(); return true;
            case KeyEvent.KEYCODE_BACK:
                if (mGrabbed) { mGrabbed = false; setReorderBanner(); } else exitReorder();
                return true;
            default: return false;
        }
        int to = mDockIdx + d;
        if (to < 0 || to >= mDockItems.size()) return true;
        if (!mGrabbed) { mDockIdx = to; applyFocus(true); return true; }
        List<String> keys = pinKeys();
        String a = mDockItems.get(mDockIdx).key, b = mDockItems.get(to).key;
        int ia = keys.indexOf(a), ib = keys.indexOf(b);
        if (ia < 0 || ib < 0) return true;
        keys.remove(ia);
        keys.add(ib, a);
        savePinKeys(keys);
        mDockIdx = to;
        loadDockItems();
        renderDock();
        applyFocus(false);
        return true;
    }

    private boolean askOnStart() { return mGlobal.getBoolean("ask_on_start", true); }

    /** Fresh process (TV boot): welcome the very first run, otherwise offer the profile chooser. */
    private void startupFlow() {
        if (isFinishing()) return;
        if (!mGlobal.getBoolean("onboarded", false)) {
            mGlobal.edit().putBoolean("onboarded", true).apply();
            askName("Welcome to tvHome. What should we call you?", profileName(), new NameCb() {
                @Override public void done(String n) { renameProfile(mProfileId, n); }
            });
            return;
        }
        if (profiles().size() > 1 && askOnStart()) showWho();
    }

    private boolean whoVisible() { return mWho != null && mWho.getParent() != null; }

    private void showWho() {
        if (whoVisible()) return;
        hidePanel();
        mWho = new FrameLayout(this);
        mWho.setBackgroundColor(0xF2050509);
        mWho.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        TextView title = newText("Who\u2019s watching?", 38, 480, 0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        col.addView(title, lpW(WC, WC, 0, 0, 0, 0, dp(44)));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        mWhoItems.clear();
        final List<String[]> list = profiles();
        int cur = 0;
        for (int i = 0; i < list.size(); i++) {
            final String[] pr = list.get(i);
            if (pr[0].equals(mProfileId)) cur = i;
            row.addView(whoItem(avatar(dp(124), pr), pr[1], i), lpW(WC, WC, 0, dp(18), 0, dp(18), 0));
        }
        if (list.size() < 6) {
            ImageView plus = new ImageView(this);
            GradientDrawable pd = new GradientDrawable();
            pd.setShape(GradientDrawable.OVAL);
            pd.setColor(0x22FFFFFF);
            pd.setStroke(dp(2), 0x55FFFFFF);
            plus.setImageDrawable(pd);
            row.addView(whoItem(plusAvatar(dp(124)), "Add profile", list.size()), lpW(WC, WC, 0, dp(18), 0, dp(18), 0));
        }
        col.addView(row, lpW(WC, WC, 0, 0, 0, 0, 0));
        mWho.addView(col, lpF(WC, WC, Gravity.CENTER, 0, 0, 0, 0));
        TextView hint = newText("OK to choose  \u00B7  BACK to stay as " + profileName(), 14, 420, 0xFF9AA3B0);
        mWho.addView(hint, lpF(WC, WC, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, dp(48)));
        mRoot.addView(mWho, lpF(MP, MP, Gravity.CENTER, 0, 0, 0, 0));
        mWho.setAlpha(0f);
        mWho.animate().alpha(1f).setDuration(220).start();
        mWhoIdx = cur;
        styleWho();
    }

    private Drawable plusAvatar(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x26FFFFFF);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(0xFFFFFFFF);
        p.setStrokeWidth(size * 0.07f);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(size * 0.32f, size / 2f, size * 0.68f, size / 2f, p);
        c.drawLine(size / 2f, size * 0.32f, size / 2f, size * 0.68f, p);
        return new BitmapDrawable(getResources(), b);
    }

    private View whoItem(Drawable avatar, String name, final int idx) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.VERTICAL);
        it.setGravity(Gravity.CENTER_HORIZONTAL);
        it.setClipChildren(false);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(avatar);
        iv.setTag("av");
        it.addView(iv, lpW(dp(124), dp(124), 0, 0, 0, 0, 0));
        TextView t = newText(name, 20, 480, 0xFFB4BBC6);
        t.setTag("nm");
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        it.addView(t, lpW(dp(160), WC, 0, 0, dp(14), 0, 0));
        it.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { mWhoIdx = idx; whoActivate(); }
        });
        mWhoItems.add(it);
        return it;
    }

    private void styleWho() {
        for (int i = 0; i < mWhoItems.size(); i++) {
            View it = mWhoItems.get(i);
            boolean on = i == mWhoIdx;
            View av = it.findViewWithTag("av");
            TextView nm = (TextView) it.findViewWithTag("nm");
            av.animate().cancel();
            av.animate().scaleX(on ? 1.16f : 1f).scaleY(on ? 1.16f : 1f).setDuration(200)
                    .setInterpolator(new OvershootInterpolator(0.4f)).start();
            nm.setTextColor(on ? 0xFFFFFFFF : 0xFF8E97A3);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(0);
            if (on) ring.setStroke(dp(3), 0xFFFFFFFF);
            av.setBackground(ring);
        }
    }

    private void whoActivate() {
        final List<String[]> list = profiles();
        hideWho();
        if (mWhoIdx < list.size()) {
            switchProfile(list.get(mWhoIdx)[0]);
        } else {
            askName("New profile", "", new NameCb() { @Override public void done(String n) { addProfile(n); } });
        }
    }

    private void hideWho() {
        final View w = mWho;
        if (w == null) return;
        w.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
            @Override public void run() { mRoot.removeView(w); }
        }).start();
        mWho = null;
    }

    interface NameCb { void done(String name); }
    interface FieldsCb { void done(String[] values); }

    /** Simple multi-field text dialog (TV keyboard friendly). */
    private void askFields(String title, String[] hints, String[] initial, final FieldsCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(8));
        final android.widget.EditText[] ets = new android.widget.EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            android.widget.EditText et = new android.widget.EditText(this);
            et.setHint(hints[i]);
            et.setText(initial != null && i < initial.length ? initial[i] : "");
            et.setSingleLine(true);
            et.setTextSize(20);
            et.setImeOptions(i == hints.length - 1 ? android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    : android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
            ets[i] = et;
            box.addView(et, lpW(MP, WC, 0, 0, i == 0 ? 0 : dp(6), 0, 0));
        }
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle(title).setView(box)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String[] v = new String[ets.length];
                        for (int i = 0; i < ets.length; i++) v[i] = ets[i].getText().toString().trim();
                        cb.done(v);
                    }
                })
                .setNegativeButton("Cancel", null);
        final android.app.AlertDialog dlg = b.create();
        ets[ets.length - 1].setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(android.widget.TextView v, int id, android.view.KeyEvent e) {
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
        });
        dlg.show();
    }

    private void askName(String title, String initial, final NameCb cb) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setText(initial);
        et.setSelectAllOnFocus(true);
        et.setSingleLine(true);
        et.setTextSize(22);
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        et.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(18) });
        et.setPadding(dp(24), dp(16), dp(24), dp(16));
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle(title).setView(et)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String n = et.getText().toString().trim();
                        if (!n.isEmpty()) cb.done(n);
                    }
                })
                .setNegativeButton("Cancel", null);
        final android.app.AlertDialog dlg = b.create();
        et.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(android.widget.TextView v, int id, android.view.KeyEvent e) {
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
        });
        dlg.show();
    }

    // ------------------------------------------------------ clock + weather

    private boolean showTopIcons() { return mPrefs.getBoolean("top_icons", true); }
    private boolean showProfilePill() { return mPrefs.getBoolean("profile_pill", true); }

    /** Customize stays visible so the menu can always be reached. */
    private boolean pillShown(int i) {
        if (i == PILL_CUSTOM) return true;
        if (hiddenPills().contains(i)) return false;
        if (i == PILL_PROFILE) return showProfilePill();
        if (i >= BUILTIN_PILLS) return true;
        return showTopIcons();
    }

    private void applyPillVisibility() {
        if (mPillViews == null) return;
        for (int i = 0; i < mPillViews.length; i++) mPillViews[i].setVisibility(pillShown(i) ? View.VISIBLE : View.GONE);
        if (mTopIdx >= mPillViews.length || !pillShown(mTopIdx)) mTopIdx = PILL_CUSTOM;
    }

    /** Next visible pill in on-screen order; from = -1 gives the first one. */
    private int stepPill(int from, int dx) {
        List<Integer> o = pillOrder();
        int pos = from < 0 ? (dx > 0 ? -1 : o.size()) : o.indexOf(from);
        int i = pos + dx;
        while (i >= 0 && i < o.size() && !pillShown(o.get(i))) i += dx;
        return (i < 0 || i >= o.size()) ? from : o.get(i);
    }

    private boolean clock12() { return mPrefs.getBoolean("clock_12h", true); }
    private boolean weatherOn() { return mPrefs.getBoolean("weather_on", true); }
    private boolean useF() { return mPrefs.getBoolean("temp_f", false); }

    private void applyClockPrefs() {
        String f = clock12() ? "h:mm" : "HH:mm";
        mClock.setFormat12Hour(f);
        mClock.setFormat24Hour(f);
        mAmPm.setVisibility(clock12() ? View.VISIBLE : View.GONE);
        updateWeatherText();
    }

    /** Cached weather is stored as "emoji|tempC|city". */
    private void updateWeatherText() {
        if (mWeather == null) return;
        String t = mPrefs.getString("wx_text", "");
        if (!weatherOn() || t.isEmpty()) { mWeather.setVisibility(View.GONE); return; }
        try {
            String[] p = t.split("\\|", 3);
            double c = Double.parseDouble(p[1]);
            int deg = (int) Math.round(useF() ? c * 9 / 5 + 32 : c);
            String city = p.length > 2 ? p[2] : "";
            mWeather.setText("\u00B7   " + p[0] + " " + deg + "\u00B0" + (city.isEmpty() ? "" : "  " + city));
            mWeather.setVisibility(View.VISIBLE);
        } catch (Throwable e) { mWeather.setVisibility(View.GONE); }
    }

    private static String wxEmoji(int code, boolean day) {
        if (code == 0) return day ? "\u2600\uFE0F" : "\uD83C\uDF19";
        if (code <= 2) return day ? "\uD83C\uDF24\uFE0F" : "\u2601\uFE0F";
        if (code == 3) return "\u2601\uFE0F";
        if (code == 45 || code == 48) return "\uD83C\uDF2B\uFE0F";
        if (code >= 51 && code <= 57) return "\uD83C\uDF26\uFE0F";
        if (code >= 61 && code <= 67) return "\uD83C\uDF27\uFE0F";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "\uD83C\uDF28\uFE0F";
        if (code >= 80 && code <= 82) return "\uD83C\uDF27\uFE0F";
        if (code >= 95) return "\u26C8\uFE0F";
        return "\u2601\uFE0F";
    }

    private static String httpGet(String u) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private void refreshWeather(boolean force) {
        if (!weatherOn() || mWxBusy) return;
        long age = System.currentTimeMillis() - mPrefs.getLong("wx_ts", 0);
        if (!force && age < 20 * 60 * 1000L && !mPrefs.getString("wx_text", "").isEmpty()) return;
        mWxBusy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String lat = mPrefs.getString("wx_lat", "");
                    String lon = mPrefs.getString("wx_lon", "");
                    String city = mPrefs.getString("wx_city", "");
                    if (lat.isEmpty()) {
                        String cl = getString(R.string.weather_lat).trim();
                        if (!cl.isEmpty()) {
                            lat = cl; lon = getString(R.string.weather_lon).trim();
                            city = getString(R.string.weather_city).trim();
                        } else {
                            org.json.JSONObject j = new org.json.JSONObject(httpGet("https://ipwho.is/"));
                            lat = String.valueOf(j.getDouble("latitude"));
                            lon = String.valueOf(j.getDouble("longitude"));
                            city = j.optString("city", "");
                        }
                        mPrefs.edit().putString("wx_lat", lat).putString("wx_lon", lon).putString("wx_city", city).apply();
                    }
                    org.json.JSONObject w = new org.json.JSONObject(httpGet(
                            "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                                    + "&current=temperature_2m,weather_code,is_day&timezone=auto"));
                    org.json.JSONObject cur = w.getJSONObject("current");
                    String txt = wxEmoji(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1)
                            + "|" + cur.getDouble("temperature_2m") + "|" + city;
                    mPrefs.edit().putString("wx_text", txt).putLong("wx_ts", System.currentTimeMillis()).apply();
                    runOnUiThread(new Runnable() { @Override public void run() { updateWeatherText(); } });
                } catch (Throwable ignored) {
                } finally { mWxBusy = false; }
            }
        }, "tvhome-weather").start();
    }

    // ------------------------------------------------- Apple-TV style wide cards

    private static final float CARD_R_DP = 16f;
    private static final float CARD_LIFT = 1.12f;
    private final java.util.HashSet<View> mLit = new java.util.HashSet<>();
    private View mLitGrid;

    private int mix(int a, int b, float t) {
        return Color.argb(255,
                Math.round(Color.red(a) * (1 - t) + Color.red(b) * t),
                Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    /** Dominant saturated colour of an icon; neutral slate when the icon is monochrome. */
    private int tintFor(Drawable d) {
        try {
            Bitmap bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            d.setBounds(0, 0, 32, 32);
            d.draw(cv);
            long rs = 0, gs = 0, bs = 0, n = 0;
            float[] hsv = new float[3];
            for (int y = 2; y < 30; y++) for (int x = 2; x < 30; x++) {
                int c = bmp.getPixel(x, y);
                if (Color.alpha(c) < 200) continue;
                Color.colorToHSV(c, hsv);
                if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
                rs += Color.red(c); gs += Color.green(c); bs += Color.blue(c); n++;
            }
            if (n < 20) return 0xFF4A5064;
            return Color.argb(255, (int) (rs / n), (int) (gs / n), (int) (bs / n));
        } catch (Throwable t) { return 0xFF4A5064; }
    }

    private Bitmap cardBitmap(ResolveInfo info, int iconRes, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = dp(CARD_R_DP);
        RectF full = new RectF(0, 0, w, h);
        Path clip = new Path();
        clip.addRoundRect(full, r, r, Path.Direction.CW);
        c.clipPath(clip);
        Drawable banner = null;
        if (iconRes == 0 && info != null) {
            try { banner = info.activityInfo.loadBanner(mPM); } catch (Throwable ignored) { }
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (banner != null) {
            int bw = banner.getIntrinsicWidth(), bh = banner.getIntrinsicHeight();
            if (bw <= 0 || bh <= 0) { bw = 320; bh = 180; }
            float sc = Math.max((float) w / bw, (float) h / bh);
            int dw = Math.round(bw * sc), dh = Math.round(bh * sc);
            int l = (w - dw) / 2, t = (h - dh) / 2;
            banner.setBounds(l, t, l + dw, t + dh);
            banner.draw(c);
        } else {
            Drawable art = iconRes != 0 ? getDrawable(iconRes) : info.loadIcon(mPM);
            int tint = tintFor(art);
            p.setShader(new LinearGradient(0, 0, w, h,
                    mix(tint, 0xFF000000, 0.22f), mix(tint, 0xFF000000, 0.70f), Shader.TileMode.CLAMP));
            c.drawRect(full, p);
            p.setShader(null);
            int isz = Math.round(h * 0.60f);
            int il = (w - isz) / 2, it = (h - isz) / 2;
            float ir = dp(12);
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setColor(0xFF000000);
            sh.setShadowLayer(dp(9), 0, dp(3), 0x88000000);
            c.drawRoundRect(new RectF(il, it, il + isz, it + isz), ir, ir, sh);
            BitmapDrawable ic = iconRes != 0 ? plateArt(art, isz, 12) : iconPlated(art, isz, 12);
            ic.setBounds(il, it, il + isz, it + isz);
            ic.draw(c);
        }
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[] { 0x18FFFFFF, 0x00FFFFFF, 0x30000000 },
                new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
        c.drawRect(full, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1));
        p.setColor(0x26FFFFFF);
        c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r, r, p);
        return b;
    }

    private File favFile(String id) { return new File(getFilesDir(), "fav_" + id + ".png"); }

    /** Card for a user shortcut: tinted by name, favicon (or initial) centred, app badge if it opens in an app. */
    private Bitmap customCardBitmap(DockItem d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float r = dp(CARD_R_DP);
        RectF full = new RectF(0, 0, w, h);
        Path clip = new Path();
        clip.addRoundRect(full, r, r, Path.Direction.CW);
        c.clipPath(clip);
        int tint = AVATAR_COLORS[Math.abs(d.label.hashCode()) % AVATAR_COLORS.length];
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, mix(tint, 0xFF000000, 0.15f), mix(tint, 0xFF000000, 0.62f), Shader.TileMode.CLAMP));
        c.drawRect(full, p);
        p.setShader(null);
        int isz = Math.round(h * 0.52f);
        int il = (w - isz) / 2, it = (h - isz) / 2 - dp(4);
        Bitmap fav = null;
        try { if (favFile(d.id).exists()) fav = BitmapFactory.decodeFile(favFile(d.id).getAbsolutePath()); } catch (Throwable ignored) { }
        if (fav != null) {
            Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
            sh.setColor(0xFF000000);
            sh.setShadowLayer(dp(9), 0, dp(3), 0x88000000);
            c.drawRoundRect(new RectF(il, it, il + isz, it + isz), dp(12), dp(12), sh);
            Path ic = new Path();
            ic.addRoundRect(new RectF(il, it, il + isz, it + isz), dp(12), dp(12), Path.Direction.CW);
            c.save(); c.clipPath(ic);
            Paint bg = new Paint(); bg.setColor(0xFFFFFFFF);
            c.drawRect(il, it, il + isz, it + isz, bg);
            c.drawBitmap(fav, null, new RectF(il + isz * 0.12f, it + isz * 0.12f, il + isz * 0.88f, it + isz * 0.88f), new Paint(Paint.FILTER_BITMAP_FLAG));
            c.restore();
        } else {
            p.setColor(0xFFFFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(h * 0.5f);
            if (mFont != null) p.setTypeface(Typeface.create(mFont, 600, false));
            String ini = d.label.trim().isEmpty() ? "?" : d.label.trim().substring(0, 1).toUpperCase(Locale.getDefault());
            Paint.FontMetrics fm = p.getFontMetrics();
            c.drawText(ini, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f - dp(4), p);
        }
        if (d.pkg != null && !d.pkg.isEmpty()) {
            try {
                Drawable badge = mPM.getApplicationIcon(d.pkg);
                int bs = Math.round(h * 0.26f);
                int bl = w - bs - dp(8), bt = h - bs - dp(8);
                badge.setBounds(bl, bt, bl + bs, bt + bs);
                badge.draw(c);
            } catch (Throwable ignored) { }
        }
        p.setShader(new LinearGradient(0, 0, 0, h, new int[] { 0x18FFFFFF, 0x00FFFFFF, 0x30000000 },
                new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
        c.drawRect(full, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1));
        p.setColor(0x26FFFFFF);
        c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r, r, p);
        return b;
    }

    private void fetchFavicon(final String id, final String url) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String host = Uri.parse(url).getHost();
                    if (host == null) return;
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(
                            "https://www.google.com/s2/favicons?sz=128&domain=" + host).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(8000);
                    java.io.InputStream in = c.getInputStream();
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(favFile(id));
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                    fo.close(); in.close(); c.disconnect();
                    runOnUiThread(new Runnable() { @Override public void run() { reloadDock(); } });
                } catch (Throwable ignored) { }
            }
        }, "tvhome-favicon").start();
    }

    private FrameLayout buildCard(ResolveInfo info, int iconRes, String labelText, int w, int h, boolean overlayLabel) {
        return buildCardBmp(cardBitmap(info, iconRes, w, h), labelText, w, h, overlayLabel);
    }

    private FrameLayout buildCardBmp(Bitmap bmp, String labelText, int w, int h, boolean overlayLabel) {
        final float rad = dp(CARD_R_DP);
        FrameLayout card = new FrameLayout(this);
        card.setTag("card");
        card.setClipChildren(false);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        iv.setImageBitmap(bmp);
        card.addView(iv, lpF(MP, MP, Gravity.CENTER, 0, 0, 0, 0));

        View gloss = new View(this);
        gloss.setTag("gloss");
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] { 0x4DFFFFFF, 0x10FFFFFF, 0x00FFFFFF });
        gd.setCornerRadius(rad - dp(1));
        gd.setStroke(Math.max(2, dp(2f)), 0xE6FFFFFF);
        LayerDrawable gl = new LayerDrawable(new Drawable[] { gd });
        gl.setLayerInset(0, dp(1), dp(1), dp(1), dp(1));   // keep the rim inside the clip
        gloss.setBackground(gl);
        gloss.setAlpha(0f);
        card.addView(gloss, lpF(MP, MP, Gravity.CENTER, 0, 0, 0, 0));

        if (overlayLabel) {
            TextView t = newText(labelText, 12, 600, 0xFFFFFFFF);
            t.setTag("ovl");
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setIncludeFontPadding(false);
            t.setPadding(dp(8), dp(16), dp(8), dp(7));
            t.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] { 0x00000000, 0xCC000000 }));
            t.setAlpha(0f);
            card.addView(t, lpF(MP, WC, Gravity.BOTTOM, 0, 0, 0, 0));
        }
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        card.setOutlineSpotShadowColor(0xFF000000);
        card.setOutlineAmbientShadowColor(0xFF000000);
        return card;
    }

    private View cardOf(View tile) {
        if ("card".equals(tile.getTag())) return tile;
        View c = tile.findViewWithTag("card");
        return c != null ? c : tile;
    }

    /** tvOS-style focus: lift + soft shadow + moving gloss + name reveal. */
    private void setCardFocus(View tile, boolean on) {
        if (tile == null) return;
        if (on == mLit.contains(tile)) return;
        if (on) mLit.add(tile); else mLit.remove(tile);
        View card = cardOf(tile);
        View gloss = card.findViewWithTag("gloss");
        View ovl = card.findViewWithTag("ovl");
        float s = on ? CARD_LIFT : 1f;
        if (tile != card) tile.setTranslationZ(on ? dp(18) : 0f);   // draw above sibling tiles
        card.animate().cancel();
        card.animate().scaleX(s).scaleY(s).translationZ(on ? dp(18) : 0f)
                .setDuration(on ? 230 : 150)
                .setInterpolator(new DecelerateInterpolator(1.3f))
                .start();
        if (gloss != null) {
            gloss.animate().cancel();
            gloss.setTranslationX(0f);
            gloss.setTranslationY(0f);
            gloss.animate().alpha(on ? 1f : 0f).setDuration(on ? 200 : 120).start();
        }
        if (ovl != null) {
            ovl.animate().cancel();
            ovl.animate().alpha(on && mPrefs.getBoolean("show_names", true) ? 1f : 0f).setDuration(on ? 220 : 120).start();
        }
    }

    /** Lift for capsules (pills, input caps). */
    private void lift(View v, boolean on, float scale) {
        if (v == null) return;
        v.animate().cancel();
        v.animate().scaleX(on ? scale : 1f).scaleY(on ? scale : 1f)
                .translationZ(on ? dp(16) : 0f).setDuration(on ? 200 : 140)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void moveGlassTo(View target, boolean animate) {
        int pad = dp(7);
        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        mTo.set(loc[0] - pad, loc[1] - pad,
                loc[0] + target.getWidth() + pad, loc[1] + target.getHeight() + pad);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) mGlass.getLayoutParams();
        mFrom.set(p.leftMargin, p.topMargin, p.leftMargin + p.width, p.topMargin + p.height);
        // first placement (or coming from offscreen): snap, never fly across the screen
        boolean firstPlace = mFrom.l < 0 || mFrom.t < 0;
        if (firstPlace || mGlass.getVisibility() != View.VISIBLE) { mFrom.set(mTo.l, mTo.t, mTo.r, mTo.b); }
        if (mAnim != null) mAnim.cancel();
        mAnim = ValueAnimator.ofFloat(0f, 1f);
        mAnim.setDuration((animate && !firstPlace) ? 230 : 0);
        mAnim.setInterpolator(new OvershootInterpolator(0.65f));
        mAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                FrameLayout.LayoutParams p2 = (FrameLayout.LayoutParams) mGlass.getLayoutParams();
                int nl = (int) (mFrom.l + (mTo.l - mFrom.l) * f);
                int nt = (int) (mFrom.t + (mTo.t - mFrom.t) * f);
                p2.setMargins(nl, nt, 0, 0);
                p2.width = (int) (mFrom.r + (mTo.r - mFrom.r) * f) - nl;
                p2.height = (int) (mFrom.b + (mTo.b - mFrom.b) * f) - nt;
                mGlass.setLayoutParams(p2);
            }
        });
        mAnim.start();
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int kc0 = event.getKeyCode();
        if ((kc0 == KeyEvent.KEYCODE_DPAD_CENTER || kc0 == KeyEvent.KEYCODE_ENTER)
                && (mCenterDown || ((mZone == ZONE_DOCK || mZone == ZONE_GRID) && !mEditMode && !mReorder
                && !whoVisible() && !liveVisible()
                && !(mPanel.getVisibility() == View.VISIBLE && mZone == ZONE_PANEL)))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    mCenterDown = true; mLongHandled = false;
                    mRoot.postDelayed(mLongPress, 480);
                } else if (mCenterDown && !mLongHandled) {
                    mRoot.removeCallbacks(mLongPress);
                    mLongPress.run();
                }
                return true;        // key repeats while held never reach the menu
            }
            if (event.getAction() == KeyEvent.ACTION_UP && mCenterDown) {
                mCenterDown = false;
                mRoot.removeCallbacks(mLongPress);
                if (!mLongHandled) { activate(); saveFocus(); }
                return true;
            }
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        if (event.getRepeatCount() > 16) return true;
        if (whoVisible()) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (mWhoIdx > 0) { mWhoIdx--; styleWho(); } return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (mWhoIdx < mWhoItems.size() - 1) { mWhoIdx++; styleWho(); } return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    whoActivate(); return true;
                case KeyEvent.KEYCODE_BACK:
                    hideWho(); return true;
                case KeyEvent.KEYCODE_HOME:
                    return super.dispatchKeyEvent(event);
                default:
                    return true;
            }
        }
        if (mReorder && event.getKeyCode() != KeyEvent.KEYCODE_HOME && reorderKey(event.getKeyCode())) return true;
        if (liveVisible()) {                       // HDMI / TV input is on screen
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_BACK: closeLive(); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    return true;                   // never drive the hidden launcher
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        boolean panelOpen = mPanel.getVisibility() == View.VISIBLE && mZone == ZONE_PANEL;
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (whoVisible()) hideWho();
            if (mReorder) exitReorder();
            // HOME inside the launcher means "back to the clean home"
            if (mPanel.getVisibility() == View.VISIBLE) hidePanel();
            if (mAppsOpen) { mTopIdx = PILL_APPS; setAppsOpen(false, true); return true; }
            return super.dispatchKeyEvent(event);
        }
        if (handleRemoteShortcut(event.getKeyCode())) { saveFocus(); return true; }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  if (panelOpen) { panelMove(-1); return true; } moveH(-1); saveFocus(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (panelOpen) { panelMove(1); return true; } moveH(1); saveFocus(); return true;
            case KeyEvent.KEYCODE_DPAD_UP:    moveV(-1); saveFocus(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  moveV(1); saveFocus(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (panelOpen) { activatePanelRow(mPanelIdx); } else { activate(); }
                saveFocus(); return true;
            case KeyEvent.KEYCODE_BACK:
                if (mTvView != null && mTvView.getVisibility() == View.VISIBLE) { closeLive(); return true; }
                if (panelOpen) { hidePanel(); return true; }
                if (mAppsOpen) { mTopIdx = PILL_APPS; setAppsOpen(false, true); return true; }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    /** Color / CH / TV / Guide bindings while home is showing. */
    private boolean handleRemoteShortcut(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_PROG_RED:
            case KeyEvent.KEYCODE_PROG_GREEN:
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_PROG_BLUE: {
                int which = keyCode - KeyEvent.KEYCODE_PROG_RED;
                int[] map = getResources().getIntArray(R.array.quick_pin_idx);
                if (which >= 0 && which < map.length) {
                    int pin = map[which];
                    if (pin >= 0 && pin < mDockItems.size()) {
                        mDockIdx = pin; mZone = ZONE_DOCK; applyFocus(false); launchDock(pin);
                        return true;
                    }
                }
                return true;
            }
            case KeyEvent.KEYCODE_CHANNEL_UP:
                cycleHdmi(1); return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                cycleHdmi(-1); return true;
            case KeyEvent.KEYCODE_TV:
            case KeyEvent.KEYCODE_TV_INPUT:
                if (mZone == ZONE_HDMI) cycleHdmi(1);
                else { mZone = ZONE_HDMI; applyFocus(true); }
                return true;
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_DVR:
            case KeyEvent.KEYCODE_ALL_APPS:
                mTopIdx = PILL_APPS; toggleApps(); return true;
            case KeyEvent.KEYCODE_BOOKMARK:
            case KeyEvent.KEYCODE_INFO:
                mTopIdx = PILL_WIFI; activatePill(PILL_WIFI); return true;
            default:
                return false;
        }
    }

    private void moveH(int dx) {
        switch (mZone) {
            case ZONE_TOP:
                if (mOnSchedule) {
                    if (dx > 0) { mOnSchedule = false; mTopIdx = stepPill(-1, 1); }
                } else if (dx < 0 && mTopIdx == stepPill(-1, 1)
                        && mSchedulePill != null && mSchedulePill.getVisibility() == View.VISIBLE) {
                    mOnSchedule = true;
                } else {
                    mTopIdx = stepPill(mTopIdx, dx);
                }
                applyFocus(true);
                break;
            case ZONE_HDMI:
                if (!mHdmiItems.isEmpty()) {
                    mHdmiIdx = clamp(mHdmiIdx + dx, 0, mHdmiItems.size() - 1);
                    applyFocus(true);
                }
                break;
            case ZONE_GRID: {
                int row = mGridIdx / GRID_COLS, col = mGridIdx % GRID_COLS;
                int rowLen = Math.min(GRID_COLS, mGridApps.size() - row * GRID_COLS);
                int ncol = clamp(col + dx, 0, rowLen - 1);
                mGridIdx = row * GRID_COLS + ncol;
                applyFocus(true);
                break;
            }
            case ZONE_DOCK: {
                int cols = Math.max(4, Math.min(mPinCols, 8));
                int row = mDockIdx / cols, col = mDockIdx % cols;
                int rowStart = row * cols;
                int rowLen = Math.min(cols, mDockItems.size() - rowStart);
                int ncol = clamp(col + dx, 0, rowLen - 1);
                mDockIdx = rowStart + ncol;
                applyFocus(true);
                break;
            }
            case ZONE_PANEL:
                break;
        }
    }

    private void moveV(int dy) {
        switch (mZone) {
            case ZONE_TOP:
                if (dy > 0) {
                    mOnSchedule = false;
                    if (mAppsOpen && mGridApps != null && !mGridApps.isEmpty()) {
                        mGridIdx = mGridIdx % GRID_COLS;
                        mZone = ZONE_GRID;
                    } else if (!mHdmiItems.isEmpty()) {
                        mZone = ZONE_HDMI;
                    } else if (mDockItems != null && !mDockItems.isEmpty()) {
                        mZone = ZONE_DOCK;
                    }
                    applyFocus(true);
                }
                break;
            case ZONE_HDMI:
                if (dy < 0) {
                    mZone = ZONE_TOP;
                } else if (dy > 0 && mDockItems != null && !mDockItems.isEmpty()) {
                    mZone = ZONE_DOCK;
                }
                applyFocus(true);
                break;
            case ZONE_GRID: {
                int rows = mGridWrap.getChildCount();
                int row = mGridIdx / GRID_COLS + dy;
                if (row < 0) {
                    mZone = ZONE_TOP;
                } else if (row > rows - 1) {
                    mZone = ZONE_GRID;          // end of the list: stay put, never fall onto the home shelf
                } else {
                    int rowStart = row * GRID_COLS;
                    int rowLen = Math.min(GRID_COLS, mGridApps.size() - rowStart);
                    mGridIdx = rowStart + Math.min(mGridIdx % GRID_COLS, rowLen - 1);
                    mZone = ZONE_GRID;
                }
                applyFocus(true);
                break;
            }
            case ZONE_DOCK: {
                int cols = Math.max(4, Math.min(mPinCols, 8));
                int rows = (mDockItems.size() + cols - 1) / cols;
                int row = mDockIdx / cols + dy;
                int col = mDockIdx % cols;
                if (row < 0) {
                    if (mAppsOpen) {
                        int grows = mGridWrap.getChildCount();
                        if (grows > 0) {
                            mGridIdx = (grows - 1) * GRID_COLS + Math.min(col, GRID_COLS - 1);
                            mGridIdx = Math.min(mGridIdx, mGridApps.size() - 1);
                        }
                        mZone = ZONE_GRID;
                    } else if (!mHdmiItems.isEmpty()) {
                        mZone = ZONE_HDMI;
                    } else {
                        mZone = ZONE_TOP;
                    }
                } else if (row > rows - 1) {
                    mZone = ZONE_DOCK;
                    mDockIdx = Math.min(mDockIdx, mDockItems.size() - 1);
                } else {
                    int rowStart = row * cols;
                    int rowLen = Math.min(cols, mDockItems.size() - rowStart);
                    mDockIdx = rowStart + Math.min(col, rowLen - 1);
                }
                applyFocus(true);
                break;
            }
            case ZONE_PANEL:
                int n = clamp(mPanelIdx + dy, 0, Math.max(0, mPanelRows.size() - 1));
                if (dy < 0 && mPanelIdx == 0) { hidePanel(); return; }
                mPanelIdx = n;
                applyFocus(true);
                break;
        }
    }

    private void activate() {
        switch (mZone) {
            case ZONE_TOP:
                if (mPanel.getVisibility() == View.VISIBLE) { hidePanel(); return; }
                if (mOnSchedule) {
                    if (calLinked()) android.widget.Toast.makeText(this, String.valueOf(mScheduleLine.getText()).replace("\u25B8 ", ""),
                            android.widget.Toast.LENGTH_LONG).show();
                    else openScheduleTarget();
                    return;
                }
                activatePill(mTopIdx);
                break;
            case ZONE_HDMI:
                activateHdmi();
                break;
            case ZONE_GRID:
                if (mGridIdx < mGridApps.size()) {
                    ResolveInfo gi = mGridApps.get(mGridIdx);
                    if (mEditMode) togglePin(gi.activityInfo.packageName); else launchApp(gi);
                }
                break;
            case ZONE_DOCK:
                launchDock(mDockIdx);
                break;
            case ZONE_PANEL:
                activatePanelRow(mPanelIdx);
                break;
        }
    }

    private void activatePill(int idx) {
        mTopIdx = idx;
        mOnSchedule = false;
        if (idx == PILL_APPS) {
            hidePanel();
            toggleApps();
            return;
        } else if (idx == PILL_GEMINI) {
            hidePanel();
            runAssistant("");
            return;
        } else if (idx == PILL_HOME) {
            togglePanel(1);
        } else if (idx == PILL_WIFI) {
            togglePanel(2);
        } else if (idx == PILL_CUSTOM) {
            togglePanel(3);
        } else if (idx == PILL_PROFILE) {
            togglePanel(5);
        } else if (idx >= BUILTIN_PILLS) {
            hidePanel();
            launchTopCustom(idx);
            return;
        } else {
            hidePanel();
            openSettings();
        }
        applyFocus(false);
    }

    private void openSettings() {
        try {
            Intent i = mPM.getLaunchIntentForPackage("com.android.tv.settings");
            if (i == null) {
                // TV Settings has no LEANBACK/LAUNCHER intent on this build -> address the screen directly
                i = new Intent(Intent.ACTION_MAIN);
                i.setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings");
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startExternal(i);
        } catch (Exception ignored) { }
    }

    // -------------------------------------------------------------- panels

    private TvInputManager tim() { return (TvInputManager) getSystemService(TV_INPUT_SERVICE); }

    private void togglePanel(int kind) {
        if (mPanel.getVisibility() == View.VISIBLE && mPanelKind == kind) { hidePanel(); return; }
        showPanel(kind);
    }

    private boolean materialMenus() { return mPrefs.getBoolean("menu_material", true); }

    /** Glass for the quick panels, plain Material (Leanback-like) for the long lists and menus. */
    private void stylePanel(int kind) {
        boolean mat = materialMenus();
        if (mat) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xFF1E222B);
            g.setCornerRadius(dp(20));
            mPanel.setBackground(g);
        } else {
            mPanel.setBackground(GlassHelper.panel(this, mAdvancedGlass, 24));
        }
        mPanelMat = mat;
        FrameLayout.LayoutParams wp = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
        wp.width = dp(kind == 13 ? 250 : kind == 2 ? 330 : (kind == 1 || kind == 10) ? 310 : 290);        // the app menu is a narrow popup
        mPanel.setLayoutParams(wp);
    }

    private boolean mPanelMat = false;

    /** Long panels slide up so the focused row stays on screen. */
    private void scrollPanelTo(final View row) {
        mPanel.post(new Runnable() {
            @Override public void run() {
                int[] l = new int[2];
                row.getLocationOnScreen(l);
                float ty = mPanel.getTranslationY();
                float top = l[1] - ty, bot = top + row.getHeight();
                int sh = mRoot.getHeight();
                float want = 0f;
                if (bot > sh - dp(28)) want = (sh - dp(28)) - bot;
                if (want < -mPanel.getTop() + dp(10) && false) want = 0f;
                mPanel.animate().cancel();
                mPanel.animate().translationY(want).setDuration(160).start();
            }
        });
    }

    private void showPanel(int kind) {
        mPanelKind = kind;
        mPanel.animate().cancel();
        mPanel.setTranslationY(0f);
        stylePanel(kind);
        mPanel.removeAllViews();
        mPanelRows.clear();
        mPanelVals.clear();
        mPanelInfo.clear();
        mPanelRuns.clear();
        mWifiLive = null;
        mPanel.setPadding(dp(6), dp(12), dp(6), dp(12));

        if (kind == 0) {                                   // ---- Inputs
            mPanel.addView(sectionLabel("Live & inputs"));
            final Runnable liveRun = new Runnable() {
                @Override public void run() {
                    try {
                        Intent i = mPM.getLaunchIntentForPackage("com.tcl.tv");
                        if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startExternal(i); }
                    } catch (Exception ignored) { }
                    hidePanel();
                }
            };
            addPanelRow("Live TV", liveRun);
            List<TvInputInfo> ordered = orderedInputs();
            java.util.Set<String> seenLabels = new java.util.HashSet<>();
            for (TvInputInfo in2 : ordered) {
                String name = sanitize(String.valueOf(in2.loadLabel(this)));
                if (!seenLabels.add(name.toLowerCase(Locale.US))) continue;   // dedupe
                final String inputId = in2.getId();
                addPanelRow(name, new Runnable() {
                    @Override public void run() { tuneToInput(inputId, name); hidePanel(); }
                });
            }
        } else if (kind == 1) {                            // ---- Home (assistant)
            mPanel.addView(sectionLabel("Google Home"));
            for (final String[] hc : homeCmds()) {
                addPanelRow(hc[0], new Runnable() {
                    @Override public void run() { runAssistant(hc[1]); }
                });
            }
            addPanelRow("+  Add command\u2026", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    askFields("Add Google Home command", new String[] { "Button name", "What to say (chain steps with ;)" }, null,
                            new FieldsCb() { @Override public void done(String[] v) {
                                if (v[0].isEmpty() || v[1].isEmpty()) return;
                                List<String[]> l = homeCmds();
                                l.add(new String[] { v[0], v[1] });
                                saveHomeCmds(l);
                            }});
                }
            });
            if (!homeCmds().isEmpty()) {
                addPanelRow("Edit or remove commands\u2026", new Runnable() {
                    @Override public void run() { showPanel(10); }
                });
            }
            TextView hint = newText("Opens the voice assistant \u00B7 OK to run", 11.5f, 350, 0xFF9AA0A6);
            hint.setPadding(dp(18), dp(2), dp(18), dp(8));
            mPanelInfo.add(hint);
            mPanel.addView(hint, lpF(MP, WC, Gravity.CENTER, 0, 0, 0, 0));
        } else if (kind == 11) {                           // ---- Calendar
            mPanel.addView(sectionLabel("Google Calendar"));
            addPanelRow(calLinked() ? "Change calendar link\u2026" : "Link calendar\u2026", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    askFields("Calendar link", new String[] { "Secret address in iCal format (https://\u2026/basic.ics)" },
                            new String[] { calUrl() }, new FieldsCb() { @Override public void done(String[] v) {
                                String u = v[0].trim();
                                if (u.isEmpty()) return;
                                if (!(u.startsWith("http") || u.startsWith("webcal"))) {
                                    android.widget.Toast.makeText(MainActivity.this, "That doesn't look like a calendar link", android.widget.Toast.LENGTH_LONG).show();
                                    return;
                                }
                                mPrefs.edit().putString("cal_url", u).putLong("cal_ts", 0).remove("cal_events").apply();
                                loadCalCache(); updateScheduleLine();
                                refreshCalendar(true);
                                android.widget.Toast.makeText(MainActivity.this, "Calendar linked", android.widget.Toast.LENGTH_SHORT).show();
                            }});
                }
            });
            if (calLinked()) {
                addPanelRow("Refresh now", new Runnable() {
                    @Override public void run() { refreshCalendar(true); android.widget.Toast.makeText(MainActivity.this, "Refreshing\u2026", android.widget.Toast.LENGTH_SHORT).show(); }
                });
                addPanelRow("Status: " + (mPrefs.getBoolean("cal_ok", false) ? "synced" : "not synced"), new Runnable() {
                    @Override public void run() { refreshCalendar(true); reshowPanel(11); }
                });
                addPanelRow("Unlink calendar", new Runnable() {
                    @Override public void run() {
                        mPrefs.edit().remove("cal_url").remove("cal_events").remove("cal_ts").remove("cal_ok").apply();
                        loadCalCache(); updateScheduleLine(); showPanel(3);
                    }
                });
            }
            TextView ct = newText("Google Calendar on the web > Settings > your calendar > Integrate calendar > Secret address in iCal format. Keep it private.", 11.5f, 350, 0xFF9AA0A6);
            ct.setPadding(dp(18), dp(6), dp(18), dp(8));
            ct.setMaxWidth(dp(330));
            mPanelInfo.add(ct);
            mPanel.addView(ct, lpF(MP, WC, Gravity.CENTER, 0, 0, 0, 0));
        } else if (kind == 10) {                           // ---- Manage home commands
            mPanel.addView(sectionLabel("Home commands"));
            final List<String[]> cl = homeCmds();
            for (int ci = 0; ci < cl.size(); ci++) {
                final int idx = ci;
                addPanelRow("\u2715  " + cl.get(ci)[0], new Runnable() {
                    @Override public void run() {
                        List<String[]> l = homeCmds();
                        if (idx < l.size()) l.remove(idx);
                        saveHomeCmds(l);
                        if (l.isEmpty()) showPanel(1); else reshowPanel(10);
                    }
                });
            }
            addPanelRow("Reset to defaults", new Runnable() {
                @Override public void run() { mPrefs.edit().remove("home_cmds").apply(); showPanel(1); }
            });
        } else if (kind == 9) {                            // ---- Remove shortcut
            mPanel.addView(sectionLabel("Remove from home"));
            for (final String[] cu : customs()) {
                addPanelRow("\u2715  " + cu[1], new Runnable() {
                    @Override public void run() {
                        removeCustom(cu[0]);
                        if (customs().isEmpty()) showPanel(3); else reshowPanel(9);
                    }
                });
            }
        } else if (kind == 8) {                            // ---- Add shortcut
            mPanel.addView(sectionLabel("Add to home"));
            addPanelRow("Website or web app", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    askFields("Add website", new String[] { "Name", "Address (e.g. youtube.com/@abpananda)" }, null,
                            new FieldsCb() { @Override public void done(String[] v) { addCustom(v[0], v[1], ""); } });
                }
            });
            addPanelRow("YouTube live / search (SmartTube)", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    askFields("Add YouTube shortcut", new String[] { "Name (e.g. ABP Ananda)", "Search words (e.g. abp ananda live)" }, null,
                            new FieldsCb() { @Override public void done(String[] v) {
                                if (v[1].isEmpty()) return;
                                String q;
                                try { q = java.net.URLEncoder.encode(v[1], "UTF-8"); } catch (Exception e) { q = v[1]; }
                                addCustom(v[0], "https://www.youtube.com/results?search_query=" + q,
                                        firstInstalled("org.smarttube.stable", "org.smarttube.modded", "com.google.android.youtube.tv"));
                            }});
                }
            });
            addPanelRow("App deep link", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    askFields("Add app deep link", new String[] { "Name", "Link (e.g. https://\u2026 or app://\u2026)", "App package (optional)" }, null,
                            new FieldsCb() { @Override public void done(String[] v) { addCustom(v[0], v[1], v[2]); } });
                }
            });
            if (!customs().isEmpty()) mPanel.addView(sectionLabel("Remove"));
            for (final String[] cu : customs()) {
                addPanelRow("Remove  " + cu[1], new Runnable() {
                    @Override public void run() { removeCustom(cu[0]); reshowPanel(8); }
                });
            }
        } else if (kind == 7) {                            // ---- Wallpaper
            mPanel.addView(sectionLabel("Wallpaper"));
            final String cur = wallMode();
            addPanelRow(("art".equals(cur) ? "\u2713  " : "     ") + "Expressive (generated)", new Runnable() {
                @Override public void run() { mPrefs.edit().putString("wallpaper", "art").apply(); applyWallpaper(); reshowPanel(7); }
            });
            addPanelRow(("plain".equals(cur) ? "\u2713  " : "     ") + "Plain dark", new Runnable() {
                @Override public void run() { mPrefs.edit().putString("wallpaper", "plain").apply(); applyWallpaper(); reshowPanel(7); }
            });
            for (final File wf : wallFiles()) {
                String nm = wf.getName();
                if (nm.length() > 24) nm = nm.substring(0, 22) + "\u2026";
                addPanelRow((("file:" + wf.getName()).equals(cur) ? "\u2713  " : "     ") + nm, new Runnable() {
                    @Override public void run() { mPrefs.edit().putString("wallpaper", "file:" + wf.getName()).apply(); applyWallpaper(); reshowPanel(7); }
                });
            }
            addPanelRow("+  Add from a web link\u2026", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    askFields("Wallpaper link", new String[] { "Image URL (https://\u2026)" }, new String[] { "https://" },
                            new FieldsCb() { @Override public void done(String[] v) {
                                if (v[0].startsWith("http")) downloadWallpaper(v[0]);
                            }});
                }
            });
            addPanelRow("Delete downloaded photos", new Runnable() {
                @Override public void run() {
                    for (File wf : wallFiles()) if (wf.getName().startsWith("wp_")) wf.delete();
                    if (wallMode().startsWith("file:") && !new File(wallDir(), wallMode().substring(5)).exists()) {
                        mPrefs.edit().putString("wallpaper", "art").apply();
                        applyWallpaper();
                    }
                    reshowPanel(7);
                }
            });
            TextView tip = newText("Or copy photos into Android/data/com.spandan.tvhome/files/wallpapers", 11.5f, 350, 0xFF9AA0A6);
            tip.setPadding(dp(18), dp(4), dp(18), dp(8));
            mPanelInfo.add(tip);
            mPanel.addView(tip, lpF(MP, WC, Gravity.CENTER, 0, 0, 0, 0));
        } else if (kind == 5) {                            // ---- Profiles
            mPanel.addView(sectionLabel("Who's watching"));
            for (final String[] pr : profiles()) {
                final String pid = pr[0];
                boolean cur = pid.equals(mProfileId);
                addPanelRow((cur ? "\u2713  " : "     ") + pr[1], new Runnable() {
                    @Override public void run() { switchProfile(pid); }
                });
            }
            if (profiles().size() < 6) {
                addPanelRow("+  Add profile", new Runnable() {
                    @Override public void run() {
                        hidePanel();
                        askName("New profile", "", new NameCb() {
                            @Override public void done(String n) { addProfile(n); }
                        });
                    }
                });
            }
            addPanelRow("Rename \u201C" + profileName() + "\u201D", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    askName("Your name", profileName(), new NameCb() {
                        @Override public void done(String n) { renameProfile(mProfileId, n); }
                    });
                }
            });
            addPanelRow("Colour: " + COLOR_NAMES[activeProfile()[2].isEmpty() ? 0 : Integer.parseInt(activeProfile()[2]) % COLOR_NAMES.length], new Runnable() {
                @Override public void run() { cycleColor(); reshowPanel(5); }
            });
            addPanelRow("Ask at startup: " + (askOnStart() ? "on" : "off"), new Runnable() {
                @Override public void run() {
                    mGlobal.edit().putBoolean("ask_on_start", !askOnStart()).apply();
                    reshowPanel(5);
                }
            });
            if (profiles().size() > 1) {
                addPanelRow("Delete \u201C" + profileName() + "\u201D", new Runnable() {
                    @Override public void run() { deleteProfile(mProfileId); }
                });
            }
        } else if (kind == 4) {                            // ---- Show on home
            addPanelRow("Inputs \u2026", new Runnable() {
                @Override public void run() { showPanel(15); }
            });
            mPanel.addView(sectionLabel("Shortcuts"));
            for (final String[] cu : customs()) {
                final String ckey = "custom:" + cu[0];
                addPanelRow(cu[1] + ": " + (pinKeys().contains(ckey) ? "on home" : "hidden"), new Runnable() {
                    @Override public void run() {
                        List<String> k = pinKeys();
                        if (k.contains(ckey)) k.remove(ckey); else k.add(ckey);
                        savePinKeys(k);
                        reloadDock();
                        reshowPanel(4);
                    }
                });
            }
            String[] raw = getResources().getStringArray(R.array.dock_pkg);
            String[] lab = getResources().getStringArray(R.array.dock_label);
            String[] dat = getResources().getStringArray(R.array.dock_data);
            for (int i = 0; i < raw.length && i < dat.length; i++) {
                if (dat[i].trim().isEmpty()) continue;
                final String key = raw[i].trim();
                boolean on = pinKeys().contains(key);
                addPanelRow(lab[i] + ": " + (on ? "on home" : "hidden"), new Runnable() {
                    @Override public void run() {
                        List<String> k = pinKeys();
                        if (k.contains(key)) k.remove(key); else k.add(key);
                        savePinKeys(k);
                        reloadDock();
                        reshowPanel(4);
                    }
                });
            }
            addPanelRow("Reset home apps", new Runnable() {
                @Override public void run() {
                    mPrefs.edit().remove("pins").apply();
                    reloadDock();
                    reshowPanel(4);
                }
            });

        } else if (kind == 3) {                            // ---- Customize
            mPanel.addView(sectionLabel("Home screen"));
            addPanelRow("Edit home apps", new Runnable() {
                @Override public void run() { enterEditMode(); }
            });
            addPanelRow("Show on home \u2026", new Runnable() {
                @Override public void run() { showPanel(4); }
            });
            addPanelRow("Rearrange apps \u2026", new Runnable() {
                @Override public void run() { enterReorder(); }
            });
            addPanelRow("Add shortcut \u2026", new Runnable() {
                @Override public void run() { showPanel(8); }
            });
            if (!customs().isEmpty()) {
                addPanelRow("Remove shortcut \u2026", new Runnable() {
                    @Override public void run() { showPanel(9); }
                });
            }
            addPanelRow("Recent apps: " + (recentOn() ? "on" : "off"), new Runnable() {
                @Override public void run() {
                    mPrefs.edit().putBoolean("recent_on", !recentOn()).apply();
                    loadHdmiItems(); renderHdmiStrip(); clampAll();
                    reshowPanel(3);
                }
            });

            mPanel.addView(sectionLabel("Look"));
            addPanelRow("Wallpaper \u2026", new Runnable() {
                @Override public void run() { showPanel(7); }
            });
            addPanelRow("App names: " + (mPrefs.getBoolean("show_names", true) ? "on focus" : "hidden"), new Runnable() {
                @Override public void run() {
                    mPrefs.edit().putBoolean("show_names", !mPrefs.getBoolean("show_names", true)).apply();
                    reshowPanel(3);
                }
            });
            addPanelRow("Menu style: " + (materialMenus() ? "Material" : "Glass"), new Runnable() {
                @Override public void run() {
                    mPrefs.edit().putBoolean("menu_material", !materialMenus()).apply();
                    reshowPanel(3);
                }
            });
            addPanelRow("Top bar \u2026", new Runnable() {
                @Override public void run() { showPanel(14); }
            });
            addPanelRow("Top icons: " + (showTopIcons() ? "shown" : "hidden"), new Runnable() {
                @Override public void run() {
                    mPrefs.edit().putBoolean("top_icons", !showTopIcons()).apply();
                    applyPillVisibility(); reshowPanel(3);
                }
            });
            addPanelRow("User switcher: " + (showProfilePill() ? "shown" : "hidden"), new Runnable() {
                @Override public void run() {
                    mPrefs.edit().putBoolean("profile_pill", !showProfilePill()).apply();
                    applyPillVisibility(); reshowPanel(3);
                }
            });
            addPanelRow("Calendar: " + (calLinked() ? "linked" : "not linked"), new Runnable() {
                @Override public void run() { showPanel(11); }
            });
            addPanelRow("Your name: " + profileName(), new Runnable() {
                @Override public void run() {
                    hidePanel();
                    askName("Your name", profileName(), new NameCb() {
                        @Override public void done(String n) { renameProfile(mProfileId, n); }
                    });
                }
            });
            addPanelRow("Clock & weather \u2026", new Runnable() {
                @Override public void run() { showPanel(12); }
            });
        } else if (kind == 14) {                           // ---- Top bar
            mPanel.addView(sectionLabel("Top bar"));
            for (final int pid : pillOrder()) {
                boolean shown = pid == PILL_CUSTOM || !hiddenPills().contains(pid);
                addPanelRow(pillName(pid) + ": " + (shown ? "shown" : "hidden"), new Runnable() {
                    @Override public void run() {
                        if (pid == PILL_CUSTOM) {
                            android.widget.Toast.makeText(MainActivity.this, "Customize always stays", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        java.util.Set<Integer> h = hiddenPills();
                        if (!h.remove(pid)) h.add(pid);
                        saveHiddenPills(h);
                        applyPillVisibility();
                        reshowPanel(14);
                    }
                });
            }
            for (int ci = 0; ci < topCustoms().size(); ci++) {
                final int cidx = ci;
                addPanelRow("Remove  " + topCustoms().get(ci)[0], new Runnable() {
                    @Override public void run() { removeTopCustom(cidx); reshowPanel(14); }
                });
            }
            TextView th = newText("OK show or hide  \u00B7  \u25C0 \u25B6 move up or down.\nLong-press an app > Add to top bar.", 11.5f, 350, 0xFF9AA0A6);
            th.setPadding(dp(18), dp(6), dp(18), dp(8));
            mPanelInfo.add(th);
            mPanel.addView(th, lpF(MP, WC, Gravity.CENTER, 0, 0, 0, 0));
        } else if (kind == 15) {                           // ---- Inputs
            mPanel.addView(sectionLabel("Inputs"));
            for (final HdmiItem h : mAllInputs) {
                final String lab0 = h.label;
                boolean shown = !hiddenInputs().contains(lab0);
                addPanelRow(lab0 + ": " + (shown ? "shown" : "hidden"), new Runnable() {
                    @Override public void run() {
                        java.util.Set<String> hs = new java.util.HashSet<>(hiddenInputs());
                        if (!hs.remove(lab0)) hs.add(lab0);
                        mPrefs.edit().putStringSet("inputs_hidden", hs).apply();
                        reloadInputs();
                        reshowPanel(15);
                    }
                });
            }
            TextView th = newText("OK show or hide  \u00B7  \u25C0 \u25B6 move up or down", 11.5f, 350, 0xFF9AA0A6);
            th.setPadding(dp(18), dp(6), dp(18), dp(8));
            mPanelInfo.add(th);
            mPanel.addView(th, lpF(MP, WC, Gravity.CENTER, 0, 0, 0, 0));
        } else if (kind == 13) {                           // ---- App menu
            mPanel.addView(sectionLabel(mMenuTitle));
            final boolean pinned = pinKeys().contains(mMenuKey);
            final boolean custom = mMenuKey.startsWith("custom:");
            addPanelRow("Open", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    if (mMenuFromDock) launchDock(mMenuDockIdx);
                    else if (mGridIdx < mGridApps.size()) launchApp(mGridApps.get(mGridIdx));
                }
            });
            if (mMenuFromDock && mDockItems.size() > 1) {
                addPanelRow("Move", new Runnable() {
                    @Override public void run() {
                        enterReorder();
                        mDockIdx = mMenuDockIdx; mGrabbed = true; setReorderBanner(); applyFocus(true);
                    }
                });
            }
            addPanelRow(pinned ? "Unpin from home" : "Pin to home", new Runnable() {
                @Override public void run() {
                    List<String> k = pinKeys();
                    if (pinned) k.remove(mMenuKey);
                    else if (k.size() >= MAX_PINS) {
                        android.widget.Toast.makeText(MainActivity.this, "Home is full (" + MAX_PINS + " apps)", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    } else k.add(mMenuKey);
                    savePinKeys(k);
                    mMenuRetZone = mMenuFromDock ? ZONE_DOCK : mMenuRetZone;
                    hidePanel();
                    reloadDock();
                    android.widget.Toast.makeText(MainActivity.this, pinned ? "Removed from home" : "Pinned to home", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            if (!mMenuPkg.isEmpty()) {
                addPanelRow("Add to top bar", new Runnable() {
                    @Override public void run() {
                        hidePanel();
                        List<String[]> c = topCustoms();
                        if (c.size() >= MAX_TOP_CUSTOM) {
                            android.widget.Toast.makeText(MainActivity.this, "Top bar is full", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String url = "";
                        if (mMenuFromDock && mMenuDockIdx < mDockItems.size()) url = mDockItems.get(mMenuDockIdx).url;
                        c.add(new String[] { mMenuTitle, mMenuPkg, url == null ? "" : url });
                        saveTopCustoms(c);
                        rebuildPills();
                        android.widget.Toast.makeText(MainActivity.this, "Added to top bar", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
            if (!mMenuPkg.isEmpty() && !(custom && mMenuPkg.isEmpty())) {
                addPanelRow("App info", new Runnable() {
                    @Override public void run() { hidePanel(); openAppInfo(mMenuPkg); }
                });
            }
            if (custom) {
                addPanelRow("Delete shortcut", new Runnable() {
                    @Override public void run() { hidePanel(); removeCustom(mMenuKey.substring(7)); }
                });
            }
        } else if (kind == 12) {                           // ---- Clock & weather
            mPanel.addView(sectionLabel("Clock & weather"));
            addPanelRow("Clock: " + (clock12() ? "12-hour \u00B7 AM/PM" : "24-hour"), new Runnable() {
                @Override public void run() {
                    mPrefs.edit().putBoolean("clock_12h", !clock12()).apply();
                    applyClockPrefs();
                    reshowPanel(12);
                }
            });
            addPanelRow("Weather: " + (weatherOn() ? "on" : "off"), new Runnable() {
                @Override public void run() {
                    mPrefs.edit().putBoolean("weather_on", !weatherOn()).apply();
                    updateWeatherText();
                    refreshWeather(true);
                    reshowPanel(12);
                }
            });
            addPanelRow("Temperature: " + (useF() ? "\u00B0F" : "\u00B0C"), new Runnable() {
                @Override public void run() {
                    mPrefs.edit().putBoolean("temp_f", !useF()).apply();
                    updateWeatherText();
                    reshowPanel(12);
                }
            });
        } else {                                           // ---- Wi-Fi
            mPanel.addView(sectionLabel("Network"));
            addPanelRow("Wi-Fi settings", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    Intent i = new Intent("android.settings.WIFI_SETTINGS");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (i.resolveActivity(mPM) != null) startExternal(i); else openSettings();
                }
            });
            addPanelRow("Speed test", new Runnable() {
                @Override public void run() {
                    hidePanel();
                    String pkg = firstInstalled("com.rma.speedtesttv", "com.aura.speedtest", "org.zwanoo.android.speedtest");
                    if (!pkg.isEmpty()) launchByPackage(pkg);
                    else {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://fast.com"));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if (i.resolveActivity(mPM) != null) startExternal(i);
                        else android.widget.Toast.makeText(MainActivity.this, "No browser installed", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
            mPanel.addView(sectionLabel("Connection"));
            for (String[] kv : wifiStats()) addInfoRow(kv[0], kv[1], false);
            String live = liveMbpsLine();
            mWifiLive = addInfoRow("Speed", live != null ? live : "measuring\u2026", true);
            for (String[] kv : bluetoothDevices2()) addInfoRow(kv[0], kv[1], false);
        }

        mPanelIdx = 0;
        mZone = ZONE_PANEL;
        positionPanel();
        mPanel.setVisibility(View.VISIBLE);
        applyFocus(false);
        mRoot.post(new Runnable() { @Override public void run() { if (mPanel.getVisibility() == View.VISIBLE) positionPanel(); } });
        mRoot.removeCallbacks(mWifiTick);
        if (kind == 2) mRoot.postDelayed(mWifiTick, 2000L);
    }

    /** Voice commands for the Home panel, per profile. Rows are "label\tquery". */
    private List<String[]> homeCmds() {
        List<String[]> out = new ArrayList<>();
        String raw = mPrefs.getString("home_cmds", null);
        if (raw == null) {
            String[] l = getResources().getStringArray(R.array.home_cmd_label);
            String[] q = getResources().getStringArray(R.array.home_cmd_query);
            for (int i = 0; i < l.length && i < q.length; i++) out.add(new String[] { l[i], q[i] });
            return out;
        }
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t", 2);
            if (p.length == 2 && !p[0].isEmpty()) out.add(p);
        }
        return out;
    }

    private void saveHomeCmds(List<String[]> l) {
        StringBuilder sb = new StringBuilder();
        for (String[] c : l) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(c[0].replace('\t', ' ').replace('\n', ' ')).append('\t').append(c[1].replace('\t', ' ').replace('\n', ' '));
        }
        mPrefs.edit().putString("home_cmds", sb.toString()).apply();
    }

    /** In the Top bar and Inputs panels the left/right keys move the highlighted row up or down. */
    private void panelMove(int dir) {
        if (mPanelKind != 14 && mPanelKind != 15) return;
        int row = mPanelIdx;
        if (mPanelKind == 14) {
            List<Integer> o = pillOrder();
            int to = row + dir;
            if (row < 0 || row >= o.size() || to < 0 || to >= o.size()) return;
            int v = o.remove(row); o.add(to, v);
            savePillOrder(o);
            rebuildPills();
            mPanelIdx = to;
            reshowPanel(14);
            mPanelIdx = to; applyFocus(false);
        } else {
            List<String> names = inputOrderNames();
            int to = row + dir;
            if (row < 0 || row >= names.size() || to < 0 || to >= names.size()) return;
            String v = names.remove(row); names.add(to, v);
            mPrefs.edit().putString("input_order", TextUtils.join("\n", names)).apply();
            reloadInputs();
            reshowPanel(15);
            mPanelIdx = to; applyFocus(false);
        }
    }

    private List<String> inputOrderNames() {
        List<String> out = new ArrayList<>();
        for (HdmiItem h : mAllInputs) out.add(h.label);
        return out;
    }

    private void removeTopCustom(int idx) {
        List<String[]> c = topCustoms();
        if (idx < 0 || idx >= c.size()) return;
        c.remove(idx);
        saveTopCustoms(c);
        mPrefs.edit().remove("pill_order").remove("pill_hidden").apply();   // indices shifted; reset layout
        rebuildPills();
    }

    private void reshowPanel(int kind) {
        int keep = mPanelIdx;
        showPanel(kind);
        mPanelIdx = clamp(keep, 0, Math.max(0, mPanelRows.size() - 1));
        applyFocus(false);
    }

    /** Adds one navigable panel row + the action its OK press runs (remote and touch share it). */
    private final List<TextView> mPanelVals = new ArrayList<>();

    private boolean compactPanel() { return mPanelKind == 1 || mPanelKind == 3 || mPanelKind == 4 || mPanelKind >= 6; }

    /** One navigable row. "Name: value" renders the value as a chip on the right. */
    private void addPanelRow(String label, final Runnable action) {
        String name = label, val = null;
        int ci = label.indexOf(": ");
        if (ci > 0 && mPanelKind != 1) { name = label.substring(0, ci); val = label.substring(ci + 2); }
        TextView row = newText(name, compactPanel() ? 15.5f : 16.5f, 420, 0xFFE8EAED);
        row.setSingleLine(true);
        int vp = mPanelMat ? 8 : (compactPanel() ? 4 : 11);
        row.setPadding(dp(16), dp(vp), dp(16), dp(vp));
        row.setGravity(Gravity.CENTER_VERTICAL);
        final int rowIdx = mPanelRuns.size();
        mPanelRuns.add(action);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runPanelRow(rowIdx); }
        });
        mPanelRows.add(row);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(8), dp(1), dp(8), dp(1));
        wrap.addView(row, new FrameLayout.LayoutParams(MP, WC));
        TextView chip = null;
        if (val != null) {
            chip = newText(val, 13, 520, 0xFFE3E7EE);
            chip.setSingleLine(true);
            chip.setPadding(dp(12), dp(3), dp(12), dp(3));
            styleChip(chip, false);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(WC, WC, Gravity.END | Gravity.CENTER_VERTICAL);
            cp.rightMargin = dp(10);
            wrap.addView(chip, cp);
        }
        mPanelVals.add(chip);
        mPanel.addView(wrap, lpF(MP, WC, Gravity.CENTER, 0, 0, 0, 0));
    }

    private void styleChip(TextView cv, boolean rowFocused) {
        String t = String.valueOf(cv.getText()).toLowerCase(Locale.US);
        boolean on = t.startsWith("on") || t.startsWith("shown") || t.startsWith("12") || t.startsWith("expressive");
        boolean off = t.startsWith("off") || t.startsWith("hidden");
        if (rowFocused) {
            cv.setBackground(round(0x1A000000, 0, 0, 12));
            cv.setTextColor(0xFF2A3350);
        } else {
            cv.setBackground(round(off ? 0x1AFFFFFF : (on ? 0x334F7BFF : 0x22FFFFFF), 0, 0, 12));
            cv.setTextColor(off ? 0xFF8B93A0 : (on ? 0xFFBFD4FF : 0xFFE3E7EE));
        }
    }

    private void runPanelRow(int rowIdx) {
        if (rowIdx < 0 || rowIdx >= mPanelRuns.size()) return;
        mPanelRuns.get(rowIdx).run();
    }

    private TextView sectionLabel(String s) {
        TextView t = newText(s.toUpperCase(), 10.5f, 560, 0xFF7D8794);
        t.setPadding(dp(18), dp(compactPanel() ? 7 : 8), dp(18), dp(3));
        t.setLetterSpacing(0.14f);
        return t;
    }

    /** "a ; b ; c" runs the phrases one after another, giving the assistant time to finish each. */
    private void runAssistant(String query) {
        final String[] steps = query.split("\\s*;\\s*");
        runAssistantOne(steps[0]);
        for (int i = 1; i < steps.length; i++) {
            final String q = steps[i];
            if (q.isEmpty()) continue;
            mRoot.postDelayed(new Runnable() {
                @Override public void run() { runAssistantOne(q); }
            }, 7000L * i);
        }
    }

    private void runAssistantOne(String query) {
        hidePanel();
        // On this TCL build the Google TV search UI lives in katniss; the tvsearch package does not
        // exist. GLOBAL_SEARCH resolves to katniss' SearchActivityTrampoline.
        Intent i = new Intent("android.search.action.GLOBAL_SEARCH");
        i.putExtra("query", query);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.resolveActivity(mPM) == null) {
            i = new Intent("com.google.android.googlequicksearchbox.GOOGLE_SEARCH");
            i.putExtra("query", query);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        startExternal(i);
    }

    private final java.util.HashMap<String, Long> mHist = new java.util.HashMap<>();
    private final java.util.HashMap<String, Long> mRxHist = new java.util.HashMap<>();

    /** Refreshes greeting/date/reminder every minute (TextClock handles the clock itself). */
    private final Runnable mMinuteTick = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            mGreeting.setText(greeting());
            mDate.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                    .format(new Date()));
            refreshCalendar(false);
            updateScheduleLine();
            refreshWeather(false);
            mRoot.postDelayed(this, 60000L);
        }
    };

    /** Keeps the live Wi-Fi rate moving while the Wi-Fi panel is open. */
    private final Runnable mWifiTick = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            if (mWifiLive != null && mPanel != null && mPanel.getVisibility() == View.VISIBLE
                    && mPanelKind == 2) {
                String live = liveMbpsLine();
                if (live != null) mWifiLive.setText(live);
                mRoot.postDelayed(this, 2000L);
            }
        }
    };

    /** A read-only "label ... value" row used by the Wi-Fi panel. */
    private TextView addInfoRow(String label, String value, boolean live) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(8), dp(1), dp(8), dp(1));
        TextView l = newText(label, 14, 400, 0xFF9AA3B0);
        l.setPadding(dp(16), dp(5), dp(16), dp(5));
        wrap.addView(l, new FrameLayout.LayoutParams(WC, WC, Gravity.START | Gravity.CENTER_VERTICAL));
        TextView v = newText(value, 14, 520, 0xFFE8EAED);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth(dp(210));
        v.setPadding(dp(16), dp(5), dp(16), dp(5));
        wrap.addView(v, new FrameLayout.LayoutParams(WC, WC, Gravity.END | Gravity.CENTER_VERTICAL));
        mPanel.addView(wrap, lpF(MP, WC, Gravity.CENTER, 0, 0, 0, 0));
        mPanelInfo.add(v);
        return v;
    }

    private String ipAddress() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> e = java.net.NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                java.net.NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (java.util.Enumeration<java.net.InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    java.net.InetAddress ia = a.nextElement();
                    if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) return ia.getHostAddress();
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private java.util.ArrayList<String[]> wifiStats() {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<>();
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getApplicationContext().getSystemService(WIFI_SERVICE);
            android.net.wifi.WifiInfo w = wm.getConnectionInfo();
            String ssid = w != null ? String.valueOf(w.getSSID()).replace("\"", "") : "";
            if (ssid.isEmpty() || ssid.contains("unknown")) ssid = getString(R.string.wifi_name);
            if (w != null && w.getNetworkId() != -1 || !ssid.isEmpty()) {
                if (!ssid.isEmpty()) out.add(new String[] { "Network", ssid });
                String ip = ipAddress();
                if (ip != null) out.add(new String[] { "IP address", ip });
                if (w != null) {
                    int rssi = w.getRssi();
                    String q = rssi >= -50 ? "excellent" : rssi >= -62 ? "good" : rssi >= -72 ? "fair" : "weak";
                    out.add(new String[] { "Signal", q + " (" + rssi + " dBm)" });
                    int freq = w.getFrequency();
                    out.add(new String[] { "Band", freq > 5900 ? "6 GHz" : freq > 4900 ? "5 GHz" : freq > 0 ? "2.4 GHz" : "unknown" });
                    out.add(new String[] { "Link speed", w.getLinkSpeed() + " Mbps" });
                }
            }
        } catch (Throwable ignored) { }
        if (out.isEmpty()) out.add(new String[] { "Status", "Not connected" });
        return out;
    }

    /** One sample of the Wi-Fi throughput delta; null when there is no previous sample yet. */
    private String liveMbpsLine() {
        try {
            long rx = android.net.TrafficStats.getRxBytes("wlan0");
            if (rx == android.net.TrafficStats.UNSUPPORTED || rx <= 0) {
                rx = android.net.TrafficStats.getTotalRxBytes();
            }
            if (rx == android.net.TrafficStats.UNSUPPORTED || rx <= 0) return null;
            long now = System.currentTimeMillis();
            Long prevRx = mRxHist.get("net");
            Long prevTm = mRxHist.get("net:t");
            mRxHist.put("net", rx);
            mRxHist.put("net:t", now);
            if (prevRx == null || prevTm == null) return null;
            long dt = now - prevTm;
            if (dt <= 0 || rx < prevRx) return null;
            double mbps = (rx - prevRx) * 8.0 / dt / 1000.0;
            return String.format(java.util.Locale.US, "%.1f Mbps now", mbps);
        } catch (Throwable t) {
            return null;
        }
    }

    private java.util.ArrayList<String[]> bluetoothDevices2() {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<>();
        for (String t : bluetoothDevices()) {
            String v = t.replaceFirst("^Bluetooth\\s*\u00B7\\s*", "");
            out.add(new String[] { "Bluetooth", v });
        }
        return out;
    }

    private java.util.ArrayList<String> bluetoothDevices() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        try {
            android.bluetooth.BluetoothManager bm =
                    (android.bluetooth.BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            android.bluetooth.BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            if (ad != null) {
                java.util.Set<android.bluetooth.BluetoothDevice> s = null;
                try {
                    s = (java.util.Set<android.bluetooth.BluetoothDevice>) android.bluetooth.BluetoothAdapter.class
                            .getMethod("getDevicesMatchingConnectionStates", int[].class)
                            .invoke(ad, new Object[]{ new int[]{ android.bluetooth.BluetoothProfile.STATE_CONNECTED } });
                } catch (Throwable t) { s = null; }
                if (s != null) for (android.bluetooth.BluetoothDevice d : s) {
                    String n;
                    try { n = d.getAlias(); } catch (Throwable t) { n = d.getAddress(); }
                    out.add("\u266A " + sanitize(n));
                }
            }
        } catch (SecurityException se) {
            out.add("Bluetooth names hidden \u00B7 grant via ADB (see README)");
        } catch (Throwable ignored) { }
        if (out.isEmpty()) out.add("Bluetooth \u00B7 no connected devices");
        return out;
    }

    private void positionPanel() {
        if (mPillViews == null) return;
        {   // size to full content so tall panels can scroll instead of being squeezed
            FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
            int fw = fp.width > 0 ? fp.width : dp(356);
            mPanel.measure(View.MeasureSpec.makeMeasureSpec(fw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            fp.height = mPanel.getMeasuredHeight();
            mPanel.setLayoutParams(fp);
        }
        if (mPanelKind == 13 && mMenuAnchor != null) {
            int[] al = new int[2];
            mMenuAnchor.getLocationOnScreen(al);
            FrameLayout.LayoutParams ap = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
            int pw = dp(250);
            mPanel.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int ph = mPanel.getMeasuredHeight();
            int sw = mRoot.getMeasuredWidth(), sh = mRoot.getMeasuredHeight();
            int x = al[0] + mMenuAnchor.getWidth() / 2 - pw / 2;
            x = Math.max(dp(16), Math.min(x, sw - pw - dp(16)));
            int y = al[1] + mMenuAnchor.getHeight() + dp(8);   // below the card
            if (y + ph > sh - dp(12)) {
                int clamped = sh - ph - dp(12);                  // slide up a little if that still clears the card
                y = clamped >= al[1] + mMenuAnchor.getHeight() * 0.6f ? clamped : al[1] - ph - dp(8);
            }
            ap.setMargins(x, Math.max(dp(16), y), 0, 0);
            mPanel.setLayoutParams(ap);
            return;
        }
        int anchor = (mPanelKind == 1 || mPanelKind == 10) ? PILL_HOME : mPanelKind == 2 ? PILL_WIFI : (mPanelKind == 3 || mPanelKind == 4 || mPanelKind == 6 || mPanelKind == 7 || mPanelKind == 8 || mPanelKind == 9 || mPanelKind == 11 || mPanelKind == 12 || mPanelKind == 14 || mPanelKind == 15) ? PILL_CUSTOM : mPanelKind == 5 ? PILL_PROFILE : PILL_APPS;
        int[] loc = new int[2];
        mPillViews[anchor].getLocationOnScreen(loc);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
        int panelW = p.width > 0 ? p.width : dp(356);
        int right = loc[0] + mPillViews[anchor].getWidth();
        int x = Math.max(dp(16),
                Math.min(loc[0], mRoot.getMeasuredWidth() - panelW - dp(16)));
        if (right + panelW > mRoot.getMeasuredWidth() - dp(16)) {
            x = Math.max(dp(16), right - panelW);
        }
        p.setMargins(x, loc[1] + mPillViews[anchor].getHeight() + dp(14), 0, 0);
        mPanel.setLayoutParams(p);
    }

    /** HDMI / AV passthrough, the tuner, and AirPlay. Soft app "inputs"
     *  (Play Movies, Zee5, etc.) stay out of the Inputs panel. */
    private boolean keepTvInput(TvInputInfo in) {
        if (in == null) return false;
        if (in.isPassthroughInput()) return true;
        String pkg = in.getServiceInfo() != null ? in.getServiceInfo().packageName : "";
        if ("com.tcl.tvinput".equals(pkg)) return true;
        if (pkg.startsWith("com.mediatek.") && pkg.toLowerCase(Locale.US).contains("airplay")) return true;
        return false;
    }

    private List<TvInputInfo> orderedInputs() {
        TvInputManager t = tim();
        List<TvInputInfo> pass = new ArrayList<>();
        List<TvInputInfo> other = new ArrayList<>();
        if (t != null) {
            for (TvInputInfo in : t.getTvInputList()) {
                if (!keepTvInput(in)) continue;
                if (in.isPassthroughInput()) pass.add(in);
                else other.add(in);
            }
        }
        Collections.sort(pass, (a, b) -> {
            String la = sanitize(String.valueOf(a.loadLabel(MainActivity.this)));
            String lb = sanitize(String.valueOf(b.loadLabel(MainActivity.this)));
            return la.compareToIgnoreCase(lb);
        });
        List<TvInputInfo> ordered = new ArrayList<>(pass);
        ordered.addAll(other);
        return ordered;
    }

    private void activatePanelRow(int idx) {
        runPanelRow(idx);
    }

    private void tuneToInput(final String inputId, final String label) {
        if (mTvView == null) {
            mTvView = new TvView(this);
            mTvView.setZOrderOnTop(false);
            mTvView.setBackgroundColor(0xFF000000);
            mRoot.addView(mTvView, lpF(MP, MP, Gravity.CENTER, 0, 0, 0, 0));
        }
        if (mLiveHud == null) buildLiveHud();
        mTvView.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String id) {
                liveHud(label, "BACK to return", true);
            }
            @Override public void onVideoUnavailable(String id, int reason) {
                liveHud(label, "No signal \u00B7 check the source \u00B7 BACK to return", false);
            }
            @Override public void onConnectionFailed(String id) {
                liveHud(label, "Couldn't open this input \u00B7 BACK to return", false);
            }
            @Override public void onDisconnected(String id) { closeLive(); }
        });
        liveHud(label, "Switching input\u2026", false);
        mTvView.setVisibility(View.VISIBLE);
        mTvView.tune(inputId, null);
    }

    /** Small card that tells you which input you are on, and why it may be black. */
    private void buildLiveHud() {
        mLiveHud = new LinearLayout(this);
        mLiveHud.setOrientation(LinearLayout.VERTICAL);
        mLiveHud.setFocusable(false);
        mLiveHud.setClickable(false);
        mLiveHud.setPadding(dp(22), dp(14), dp(22), dp(16));
        mLiveHud.setBackground(roundGrad(0xE614161B, 0xE60B0D11, 0x24FFFFFF, 20));
        mLiveHudName = newText("", 19, 520, 0xFFF4F6F8);
        mLiveHudStatus = newText("", 12.5f, 360, 0xFF9AA0A6);
        mLiveHudStatus.setPadding(0, dp(3), 0, 0);
        mLiveHud.addView(mLiveHudName);
        mLiveHud.addView(mLiveHudStatus);
        mLiveHud.setVisibility(View.GONE);
        mRoot.addView(mLiveHud, lpF(WC, WC, Gravity.BOTTOM | Gravity.START, dp(40), 0, 0, dp(44)));
    }

    private void liveHud(String name, String status, boolean autoHide) {
        if (mLiveHud == null) return;
        mLiveHudName.setText(name);
        mLiveHudStatus.setText(status);
        mLiveHud.setVisibility(View.VISIBLE);
        mLiveHud.bringToFront();
        mRoot.removeCallbacks(mLiveHudTick);
        if (autoHide) mRoot.postDelayed(mLiveHudTick, 4200L);
    }

    private final Runnable mLiveHudTick = new Runnable() {
        @Override public void run() {
            if (mLiveHud != null) mLiveHud.setVisibility(View.GONE);
        }
    };

    private boolean liveVisible() {
        return mTvView != null && mTvView.getVisibility() == View.VISIBLE;
    }

    private void closeLive() {
        mRoot.removeCallbacks(mLiveHudTick);
        if (mLiveHud != null) mLiveHud.setVisibility(View.GONE);
        if (mTvView != null) {
            mTvView.reset();
            mTvView.setVisibility(View.GONE);
        }
    }

    private void hidePanel() {
        if (mPanel != null) {
            mPanel.removeAllViews();
            mPanelRows.clear();
            mPanelInfo.clear();
            mPanelRuns.clear();
            mWifiLive = null;
            mPanel.setVisibility(View.GONE);
        }
        if (mZone == ZONE_PANEL) mZone = (mPanelKind == 13) ? mMenuRetZone : ZONE_TOP;
        if (mPanelKind == 13) mPanelKind = -1;
        applyFocus(false);
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        mGreeting.setText(greeting());
        mDate.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                .format(new Date()));
        loadCalCache();
        refreshCalendar(false);
        updateScheduleLine();
        if (mRecentsDirty) { mRecentsDirty = false; loadHdmiItems(); renderHdmiStrip(); clampAll(); }
        updateWeatherText();
        refreshWeather(false);
        clampAll();
        applyFocus(false);
        saveFocus();
        mRoot.removeCallbacks(mMinuteTick);
        mRoot.postDelayed(mMinuteTick, 60000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mRoot.removeCallbacks(mMinuteTick);
        mRoot.removeCallbacks(mWifiTick);
        closeLive();
        if (mAppsOpen) { mTopIdx = PILL_APPS; setAppsOpen(false, false); }
        saveFocus();
    }

    @Override
    protected void onDestroy() {
        closeLive();
        super.onDestroy();
    }

    // ------------------------------------------------------------ schedule

    /**
     * Reads <getExternalFilesDir>/schedule.txt.
     * Lines: "YYYY-MM-DD HH:mm Label" or "HH:mm Label" (repeats every day), "#" = comment.
     */
    private void updateScheduleLine() {
        if (mScheduleLine == null || mSchedulePill == null) return;
        String next = readNextEvent();
        if (next == null) {
            mSchedulePill.setVisibility(View.GONE);
        } else {
            mScheduleLine.setText("\u25B8 " + next);
            mSchedulePill.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------- calendar

    private String calUrl() { return mPrefs.getString("cal_url", ""); }
    private boolean calLinked() { return !calUrl().isEmpty(); }
    private List<CalendarFeed.Event> mCalEvents = new ArrayList<>();
    private boolean mCalBusy = false;

    /** Cached events are stored as "start|end|allday|title" so the chip works offline. */
    private void loadCalCache() {
        mCalEvents = new ArrayList<>();
        for (String line : mPrefs.getString("cal_events", "").split("\n")) {
            String[] p = line.split("\\|", 4);
            if (p.length < 4) continue;
            try {
                CalendarFeed.Event e = new CalendarFeed.Event();
                e.start = Long.parseLong(p[0]); e.end = Long.parseLong(p[1]);
                e.allDay = "1".equals(p[2]); e.title = p[3];
                mCalEvents.add(e);
            } catch (Exception ignored) { }
        }
    }

    private void refreshCalendar(boolean force) {
        if (!calLinked() || mCalBusy) return;
        long age = System.currentTimeMillis() - mPrefs.getLong("cal_ts", 0);
        if (!force && age < 15 * 60 * 1000L) return;
        mCalBusy = true;
        final String url = calUrl().replaceFirst("^webcal://", "https://");
        final SharedPreferences prefs = mPrefs;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(10000); c.setReadTimeout(20000);
                    c.setInstanceFollowRedirects(true);
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder(); String ln; int total = 0;
                    while ((ln = r.readLine()) != null) { sb.append(ln).append('\n'); total += ln.length(); if (total > 12_000_000) break; }
                    r.close(); c.disconnect();
                    String ics = sb.toString();
                    if (!ics.contains("BEGIN:VCALENDAR")) throw new RuntimeException("not an iCal feed");
                    List<CalendarFeed.Event> ev = CalendarFeed.parse(ics, System.currentTimeMillis(), 21,
                            java.time.ZoneId.systemDefault());
                    StringBuilder out = new StringBuilder();
                    for (int i = 0; i < ev.size() && i < 30; i++) {
                        CalendarFeed.Event e = ev.get(i);
                        if (out.length() > 0) out.append('\n');
                        out.append(e.start).append('|').append(e.end).append('|').append(e.allDay ? "1" : "0")
                                .append('|').append(e.title.replace('|', '/').replace('\n', ' '));
                    }
                    prefs.edit().putString("cal_events", out.toString()).putLong("cal_ts", System.currentTimeMillis())
                            .putBoolean("cal_ok", true).apply();
                } catch (Throwable t) {
                    prefs.edit().putBoolean("cal_ok", false).apply();
                } finally {
                    mCalBusy = false;
                    runOnUiThread(new Runnable() { @Override public void run() {
                        if (prefs == mPrefs) { loadCalCache(); updateScheduleLine(); }
                    }});
                }
            }
        }, "tvhome-calendar").start();
    }

    private String nextCalendarLabel() {
        long now = System.currentTimeMillis();
        CalendarFeed.Event pick = null;
        for (CalendarFeed.Event e : mCalEvents) {
            if (e.end <= now) continue;
            if (e.allDay && e.start > now + 36L * 3600000L) { if (pick == null) pick = e; break; }
            if (pick == null) pick = e;
            if (!e.allDay) { pick = pick.allDay ? e : pick; break; }
        }
        if (pick == null) return null;
        String t = pick.title.length() > 38 ? pick.title.substring(0, 37) + "\u2026" : pick.title;
        if (pick.allDay) {
            java.util.Calendar a = java.util.Calendar.getInstance(); a.setTimeInMillis(pick.start);
            java.util.Calendar b = java.util.Calendar.getInstance();
            boolean today = a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
                    && a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR);
            if (today || pick.start <= now) return t + " \u00B7 today";
            long days = (pick.start - now + 43200000L) / 86400000L;
            return t + " \u00B7 " + (days <= 1 ? "tomorrow" : "in " + days + " d");
        }
        if (pick.start <= now) return t + " \u00B7 now";
        long mins = (pick.start - now) / 60000L;
        String when = mins < 60 ? "in " + Math.max(1, mins) + " min"
                : mins < 60 * 24 ? "in " + (mins + 30) / 60 + " h"
                : "in " + (mins + 720) / 1440 + " d";
        return t + " \u00B7 " + when;
    }

    private String readNextEvent() {
        if (calLinked()) return nextCalendarLabel();
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "schedule.txt");
            if (!f.exists()) return null;
            SimpleDateFormat full = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            String todayKey = new SimpleDateFormat("yyyy-MM-dd ", Locale.US).format(new Date());
            long best = Long.MAX_VALUE; String bestLabel = null;
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+", 3);
                Date d = null; String label = null;
                if (p.length >= 3) {
                    try { d = full.parse(p[0] + " " + p[1]); } catch (Exception ignored) { }
                    if (d != null) label = p[2];
                }
                if (d == null && p.length >= 2) {          // "HH:mm Label" = daily
                    try { d = full.parse(todayKey + p[0]); } catch (Exception ignored) { }
                    label = p[1] + (p.length > 2 ? " " + p[2] : "");
                }
                if (d == null) continue;
                long t = d.getTime();
                if (p.length < 3) {                    // daily entry already past today -> tomorrow
                    while (t <= System.currentTimeMillis()) t += 24L * 60 * 60 * 1000;
                }
                if (t > System.currentTimeMillis() && t < best) { best = t; bestLabel = label; }
            }
            br.close();
            if (best == Long.MAX_VALUE || bestLabel == null) return null;
            long mins = (best - System.currentTimeMillis()) / 60000L;
            String when = mins < 60 ? "in " + Math.max(1, mins) + " min"
                    : mins < 60 * 24 ? "in " + (mins + 30) / 60 + " h"
                    : "in " + (mins + 720) / 1440 + " d";
            // keep it short: the pill has to share the line with the date and the top-bar pills
            return bestLabel + " \u00B7 " + when;
        } catch (Throwable t) {
            return null;
        }
    }

    // -------------------------------------------------------------- helpers

    private LinearLayout.LayoutParams lpW(int w, int h, float weight, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.weight = weight;
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private FrameLayout.LayoutParams lpF(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, gravity);
        lp.setMargins(l, t, r, b);
        return lp;
    }
}
