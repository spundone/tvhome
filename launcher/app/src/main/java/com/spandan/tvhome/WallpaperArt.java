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

/**
 * Generated home wallpaper: Material 3 Expressive layered shapes on a near-black base,
 * a carbon-fibre twill that fades in away from the clock, and an ordered-dither veil
 * that hides gradient banding on QLED panels.
 */
final class WallpaperArt {
    private WallpaperArt() {}

    static Bitmap render(int w, int h, boolean texture) {
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        // base
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF04050B, 0xFF090C18, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // expressive shapes: cookie, clover, soft hexagon. Each has a lighter echo inside it.
        blob(c, w * 0.88f, h * 0.10f, h * 0.36f, 9, 0.055f, 0.2f, 0xFF6C6CF0, 0xFF2A2A8C, 0x66);
        blob(c, w * 0.88f, h * 0.10f, h * 0.25f, 9, 0.055f, 0.2f, 0xFFA3A3FF, 0xFF5050C8, 0x1C);
        blob(c, w * 0.04f, h * 0.60f, h * 0.30f, 4, 0.11f, 0.0f, 0xFF12B5BA, 0xFF0A4B63, 0x50);
        blob(c, w * 0.04f, h * 0.60f, h * 0.20f, 4, 0.11f, 0.0f, 0xFF6FE3E6, 0xFF168A9E, 0x16);
        blob(c, w * 0.80f, h * 1.00f, h * 0.42f, 6, 0.05f, 0.5f, 0xFFD2478F, 0xFF5A1F5E, 0x48);
        blob(c, w * 0.80f, h * 1.00f, h * 0.29f, 6, 0.05f, 0.5f, 0xFFFF9CCB, 0xFF9A3C86, 0x16);

        if (texture) {
            // carbon fibre, masked so it stays out of the clock area
            Bitmap layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas lc = new Canvas(layer);
            Paint tp = new Paint();
            tp.setShader(new BitmapShader(weave(10), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
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

            // ordered dither veil
            Paint dp = new Paint();
            dp.setShader(new BitmapShader(bayer(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            c.drawRect(0, 0, w, h, dp);
        }
        return bm;
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
