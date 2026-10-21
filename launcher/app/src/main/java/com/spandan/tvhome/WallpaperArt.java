package com.spandan.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.BitmapShader;

import java.util.Random;

/**
 * Generated home wallpapers. Two families:
 *  - Material 3 Expressive: layered polar shapes on a near-black base (the original "art").
 *  - tvOS-style scenes: aurora, dunes, mountains, ocean, ember, graphite.
 * Every style keeps the top-left (clock) and the bottom (shelf) dark, and ends with an
 * ordered-dither veil that hides gradient banding on QLED panels. Sizes are fractions of the
 * output, so a 4K render is just as sharp as a 1080p one.
 */
final class WallpaperArt {
    private WallpaperArt() {}

    /** Style keys, in the order the picker shows them. "art" is the original expressive style. */
    static final String[] STYLES = { "art", "aurora", "dunes", "mountains", "ocean", "bloom", "ember", "graphite" };
    static final String[] NAMES = { "Expressive", "Aurora", "Dunes", "Mountains at night", "Deep ocean",
            "Material bloom", "Ember", "Graphite" };

    static Bitmap render(int w, int h, boolean texture) { return render(w, h, texture, "art"); }

    static Bitmap render(int w, int h, boolean texture, String style) {
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        boolean weave = false;
        switch (style == null ? "art" : style) {
            case "aurora":    aurora(c, w, h); break;
            case "dunes":     dunes(c, w, h); break;
            case "mountains": mountains(c, w, h); break;
            case "ocean":     ocean(c, w, h); break;
            case "bloom":     bloom(c, w, h); break;
            case "ember":     ember(c, w, h); break;
            case "graphite":  graphite(c, w, h); break;
            default:          expressive(c, w, h); weave = texture; break;
        }
        if (weave) carbon(c, w, h);
        if (texture) {
            Paint dp = new Paint();
            dp.setShader(new BitmapShader(bayer(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            c.drawRect(0, 0, w, h, dp);
        }
        return bm;
    }

    // ------------------------------------------------------------------ styles

    private static void expressive(Canvas c, int w, int h) {
        base(c, w, h, 0xFF04050B, 0xFF090C18);
        // cookie, clover, soft hexagon. Each has a lighter echo inside it.
        blob(c, w * 0.88f, h * 0.10f, h * 0.36f, 9, 0.055f, 0.2f, 0xFF6C6CF0, 0xFF2A2A8C, 0x66);
        blob(c, w * 0.88f, h * 0.10f, h * 0.25f, 9, 0.055f, 0.2f, 0xFFA3A3FF, 0xFF5050C8, 0x1C);
        blob(c, w * 0.04f, h * 0.60f, h * 0.30f, 4, 0.11f, 0.0f, 0xFF12B5BA, 0xFF0A4B63, 0x50);
        blob(c, w * 0.04f, h * 0.60f, h * 0.20f, 4, 0.11f, 0.0f, 0xFF6FE3E6, 0xFF168A9E, 0x16);
        blob(c, w * 0.80f, h * 1.00f, h * 0.42f, 6, 0.05f, 0.5f, 0xFFD2478F, 0xFF5A1F5E, 0x48);
        blob(c, w * 0.80f, h * 1.00f, h * 0.29f, 6, 0.05f, 0.5f, 0xFFFF9CCB, 0xFF9A3C86, 0x16);
    }

    /** Material You tonal shapes in green/lime, softer and rounder than Expressive. */
    private static void bloom(Canvas c, int w, int h) {
        base(c, w, h, 0xFF050806, 0xFF0A110C);
        blob(c, w * 0.92f, h * 0.30f, h * 0.46f, 8, 0.07f, 0.1f, 0xFF7BD88F, 0xFF1F5C35, 0x5A);
        blob(c, w * 0.92f, h * 0.30f, h * 0.30f, 8, 0.07f, 0.1f, 0xFFC8F5A8, 0xFF4E9A5C, 0x1E);
        blob(c, w * 0.62f, h * 0.02f, h * 0.20f, 5, 0.09f, 0.3f, 0xFFE4F07A, 0xFF6B7A1E, 0x40);
        blob(c, w * 0.10f, h * 1.02f, h * 0.38f, 12, 0.035f, 0.0f, 0xFF3FB59A, 0xFF0E4A40, 0x4A);
        blob(c, w * 0.10f, h * 1.02f, h * 0.24f, 12, 0.035f, 0.0f, 0xFF9DEBD5, 0xFF2A8C78, 0x16);
    }

    /** Soft curtains of light over a deep navy sky, with a light star field. */
    private static void aurora(Canvas c, int w, int h) {
        base(c, w, h, 0xFF02040A, 0xFF060B18);
        stars(c, w, h, 0.55f, 160, 7);
        band(c, w, h, 0.30f, 0.16f, 0.10f, 0xFF29E0A8, 0x70, 1.3f, 0.0f);
        band(c, w, h, 0.40f, 0.12f, 0.08f, 0xFF3AA0FF, 0x58, 1.8f, 1.7f);
        band(c, w, h, 0.22f, 0.10f, 0.06f, 0xFFA066FF, 0x44, 2.4f, 3.1f);
        glow(c, w * 0.70f, h * 0.38f, w * 0.45f, 0xFF1EC9A0, 0x30);
        floorFade(c, w, h, 0.55f, 0xF0000000);
    }

    /** Layered dunes in late light, saffron to magenta. */
    private static void dunes(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.62f,
                new int[] { 0xFF0B0712, 0xFF2A1230, 0xFF7A2E3A, 0xFFE07A3A },
                new float[] { 0f, 0.45f, 0.8f, 1f }, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        glow(c, w * 0.72f, h * 0.60f, w * 0.30f, 0xFFFFC27A, 0x90);
        ridge(c, w, h, 0.56f, 0.05f, 1.1f, 0.4f, 0xFF4A1C2C, 0xFF2A0F1C);
        ridge(c, w, h, 0.64f, 0.06f, 0.8f, 2.0f, 0xFF35121F, 0xFF1A0910);
        ridge(c, w, h, 0.74f, 0.05f, 1.4f, 3.6f, 0xFF210A12, 0xFF0D0408);
        ridge(c, w, h, 0.85f, 0.04f, 0.9f, 5.1f, 0xFF120509, 0xFF050103);
        topShade(c, w, h);
    }

    /** Mountain silhouettes under a moonlit sky. */
    private static void mountains(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.7f,
                new int[] { 0xFF03050C, 0xFF0D1830, 0xFF2B3E66 }, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        stars(c, w, h, 0.5f, 220, 11);
        glow(c, w * 0.76f, h * 0.22f, h * 0.30f, 0xFFDDE6FF, 0x50);
        Paint moon = new Paint(Paint.ANTI_ALIAS_FLAG);
        moon.setColor(0xFFF2F5FF);
        c.drawCircle(w * 0.76f, h * 0.22f, h * 0.045f, moon);
        peaks(c, w, h, 0.50f, 0.16f, 31, 0xFF2A3A5E, 0xFF1A2540);
        peaks(c, w, h, 0.60f, 0.14f, 47, 0xFF18223A, 0xFF0E1526);
        peaks(c, w, h, 0.72f, 0.10f, 59, 0xFF0B1120, 0xFF060A14);
        floorFade(c, w, h, 0.75f, 0xE6000000);
    }

    /** Deep water with light falling through from the surface. */
    private static void ocean(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h,
                new int[] { 0xFF0A3A5C, 0xFF062440, 0xFF031428, 0xFF01060E },
                new float[] { 0f, 0.35f, 0.7f, 1f }, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Random r = new Random(5);
        for (int i = 0; i < 9; i++) {
            float x = w * (0.35f + r.nextFloat() * 0.6f), half = w * (0.012f + r.nextFloat() * 0.03f);
            float lean = w * (0.10f + r.nextFloat() * 0.08f);
            Path path = new Path();
            path.moveTo(x - half, 0); path.lineTo(x + half, 0);
            path.lineTo(x + half * 3 - lean, h * 0.85f); path.lineTo(x - half * 3 - lean, h * 0.85f);
            path.close();
            ray.setShader(new LinearGradient(0, 0, 0, h * 0.85f, 0x22B8E8FF, 0x00B8E8FF, Shader.TileMode.CLAMP));
            c.drawPath(path, ray);
        }
        glow(c, w * 0.70f, -h * 0.05f, w * 0.45f, 0xFF4FC3F7, 0x40);
        topShade(c, w, h);
    }

    /** Warm glows drifting on black. */
    private static void ember(Canvas c, int w, int h) {
        base(c, w, h, 0xFF070304, 0xFF0B0505);
        glow(c, w * 0.85f, h * 0.15f, w * 0.40f, 0xFFFF5A36, 0x70);
        glow(c, w * 0.62f, h * 0.95f, w * 0.38f, 0xFFE0306A, 0x60);
        glow(c, w * 0.05f, h * 0.70f, w * 0.30f, 0xFFFF9F43, 0x40);
        glow(c, w * 0.95f, h * 0.65f, w * 0.20f, 0xFFFFC371, 0x30);
    }

    /** Quiet neutral spotlight, tvOS settings-like. */
    private static void graphite(Canvas c, int w, int h) {
        base(c, w, h, 0xFF0A0B0D, 0xFF050506);
        glow(c, w * 0.72f, h * 0.25f, w * 0.65f, 0xFF5A6270, 0x60);
        glow(c, w * 0.20f, h * 1.05f, w * 0.40f, 0xFF3A404C, 0x50);
    }

    // ----------------------------------------------------------------- pieces

    private static void base(Canvas c, int w, int h, int top, int bottom) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private static void glow(Canvas c, float cx, float cy, float r, int color, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        int rgb = color & 0x00FFFFFF;
        p.setShader(new RadialGradient(cx, cy, r,
                new int[] { (alpha << 24) | rgb, ((alpha / 3) << 24) | rgb, rgb },
                new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
    }

    /** A wavy aurora curtain: vertical fade drawn along a sine path. */
    private static void band(Canvas c, int w, int h, float yMid, float amp, float thick,
                             int color, int alpha, float freq, float phase) {
        // Whole-pixel columns, no anti-aliasing: overlapping AA edges showed up as regular seams.
        Paint p = new Paint(Paint.DITHER_FLAG);
        int rgb = color & 0x00FFFFFF;
        int steps = 160;
        float colW = (float) w / steps;
        for (int i = 0; i < steps; i++) {
            int x0 = Math.round(i * colW), x1 = Math.round((i + 1) * colW);
            double t = (double) i / steps;
            float y = (float) (h * (yMid + amp * Math.sin(t * Math.PI * freq + phase) * (0.4 + 0.6 * t)));
            float tall = h * thick * (float) (0.6 + 0.4 * Math.sin(t * Math.PI * 3 + phase));
            // fade in from the left so the clock area stays dark
            int a = (int) (alpha * Math.min(1.0, 0.15 + t * 1.4));
            p.setShader(new LinearGradient(0, y - tall * 2.2f, 0, y + tall * 0.3f,
                    new int[] { rgb, (a << 24) | rgb, rgb }, new float[] { 0f, 0.75f, 1f }, Shader.TileMode.CLAMP));
            c.drawRect(x0, y - tall * 2.2f, x1, y + tall * 0.3f, p);
        }
    }

    private static void ridge(Canvas c, int w, int h, float y0, float amp, float freq, float phase, int top, int bottom) {
        Path path = new Path();
        path.moveTo(0, h);
        int steps = 120;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            float y = (float) (h * (y0 + amp * Math.sin(t * Math.PI * 2 * freq + phase)
                    + amp * 0.35 * Math.sin(t * Math.PI * 5.3 * freq + phase * 1.7)));
            path.lineTo((float) (t * w), y);
        }
        path.lineTo(w, h);
        path.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, h * (y0 - amp), 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
    }

    private static void peaks(Canvas c, int w, int h, float y0, float amp, long seed, int top, int bottom) {
        Random r = new Random(seed);
        Path path = new Path();
        path.moveTo(0, h);
        float x = 0;
        path.lineTo(0, h * (y0 + amp * 0.4f));
        while (x < w) {
            x += w * (0.05f + r.nextFloat() * 0.09f);
            float y = h * (y0 + amp * (r.nextFloat() * 0.9f - 0.3f));
            path.lineTo(Math.min(x, w), y);
        }
        path.lineTo(w, h);
        path.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, h * (y0 - amp), 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
    }

    private static void stars(Canvas c, int w, int h, float maxY, int count, long seed) {
        Random r = new Random(seed);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float unit = h / 1080f;
        for (int i = 0; i < count; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * r.nextFloat() * h * maxY;
            p.setColor(((40 + r.nextInt(150)) << 24) | 0x00FFFFFF);
            c.drawCircle(x, y, unit * (0.6f + r.nextFloat() * 1.1f), p);
        }
    }

    private static void floorFade(Canvas c, int w, int h, float from, int color) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, h * from, 0, h, color & 0x00FFFFFF, color, Shader.TileMode.CLAMP));
        c.drawRect(0, h * from, w, h, p);
    }

    /** Keeps the clock corner readable on the brighter scenes. */
    private static void topShade(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(0, 0, w * 0.5f, 0xA0000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    /** Polar "flower" shape r = R(1 + amp*cos(n*t)) filled with a diagonal gradient. */
    private static void blob(Canvas c, float cx, float cy, float r, int n, float amp, float rot,
                             int c0, int c1, int alpha) {
        Path path = new Path();
        int steps = 240;
        for (int i = 0; i <= steps; i++) {
            double t = (Math.PI * 2 * i) / steps;
            double rr = r * (1 + amp * Math.cos(n * t + rot * Math.PI));
            float x = (float) (cx + rr * Math.cos(t));
            float y = (float) (cy + rr * Math.sin(t));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, c0, c1, Shader.TileMode.CLAMP));
        p.setAlpha(alpha);
        c.drawPath(path, p);
    }

    /** Carbon fibre, masked so it stays out of the clock area. Cell scales with the output size. */
    private static void carbon(Canvas c, int w, int h) {
        Bitmap layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas lc = new Canvas(layer);
        Paint tp = new Paint();
        tp.setShader(new BitmapShader(weave(Math.max(6, Math.round(w / 192f))), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        lc.drawRect(0, 0, w, h, tp);
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mask.setShader(new RadialGradient(w * 0.78f, h * 0.42f, w * 0.78f,
                new int[] { 0xFFFFFFFF, 0xB0FFFFFF, 0x20FFFFFF },
                new float[] { 0f, 0.55f, 1f }, Shader.TileMode.CLAMP));
        lc.drawRect(0, 0, w, h, mask);
        Paint lp = new Paint();
        lp.setAlpha(105);
        c.drawBitmap(layer, 0, 0, lp);
        layer.recycle();
    }

    /** 2x2 twill of gradient cells: horizontal / vertical alternating, like woven carbon. */
    private static Bitmap weave(int cell) {
        int s = cell * 2;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        for (int gy = 0; gy < 2; gy++) {
            for (int gx = 0; gx < 2; gx++) {
                boolean horiz = (gx + gy) % 2 == 0;
                float x0 = gx * cell, y0 = gy * cell;
                p.setShader(horiz
                        ? new LinearGradient(x0, y0, x0 + cell, y0, 0x2EFFFFFF, 0x3C000000, Shader.TileMode.CLAMP)
                        : new LinearGradient(x0, y0, x0, y0 + cell, 0x2EFFFFFF, 0x3C000000, Shader.TileMode.CLAMP));
                c.drawRect(x0, y0, x0 + cell, y0 + cell, p);
            }
        }
        return b;
    }

    private static Bitmap bayer() {
        int[] m = {
                0, 32, 8, 40, 2, 34, 10, 42, 48, 16, 56, 24, 50, 18, 58, 26,
                12, 44, 4, 36, 14, 46, 6, 38, 60, 28, 52, 20, 62, 30, 54, 22,
                3, 35, 11, 43, 1, 33, 9, 41, 51, 19, 59, 27, 49, 17, 57, 25,
                15, 47, 7, 39, 13, 45, 5, 37, 63, 31, 55, 23, 61, 29, 53, 21 };
        Bitmap b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int t = m[y * 8 + x];
            int a = 2 + t / 5;                       // 2..14
            b.setPixel(x, y, (a << 24) | 0x00FFFFFF);
        }
        return b;
    }
}
