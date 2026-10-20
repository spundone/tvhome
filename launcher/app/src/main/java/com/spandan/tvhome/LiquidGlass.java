package com.spandan.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Frosted "liquid glass" surface for a static wallpaper: it draws the pre-blurred wallpaper
 * exactly where the view sits on screen (so it reads as real backdrop blur), then a tint, a
 * specular sheen, and a rim that is bright at the top-left and fades toward the bottom-right.
 * No live blur, so it costs one bitmap draw per surface.
 */
final class LiquidGlass extends Drawable {
    private static Bitmap sBlur;
    private static int sScreenW, sScreenH;

    private final float radius;
    private final int tint;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];

    LiquidGlass(float radiusPx, int tint) {
        this.radius = radiusPx;
        this.tint = tint;
    }

    static boolean ready() { return sBlur != null; }

    /** Builds the blurred backdrop from the wallpaper once. */
    static void setBackdrop(Bitmap wallpaper, int screenW, int screenH) {
        sScreenW = screenW;
        sScreenH = screenH;
        int sw = 160, sh = Math.max(1, Math.round(160f * screenH / screenW));
        Bitmap small = Bitmap.createScaledBitmap(wallpaper, sw, sh, true);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        for (int i = 0; i < 3; i++) { box(px, sw, sh, 5, true); box(px, sw, sh, 5, false); }
        Bitmap out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, sw, 0, 0, sw, sh);
        Paint lift = new Paint();
        Canvas lc = new Canvas(out);
        lift.setColor(0x14FFFFFF);
        lc.drawRect(0, 0, sw, sh, lift);
        sBlur = out;
    }

    private static void box(int[] p, int w, int h, int r, boolean horiz) {
        int[] tmp = new int[horiz ? w : h];
        int n = horiz ? h : w, len = horiz ? w : h;
        for (int line = 0; line < n; line++) {
            for (int i = 0; i < len; i++) tmp[i] = horiz ? p[line * w + i] : p[i * w + line];
            for (int i = 0; i < len; i++) {
                int a = 0, rr = 0, g = 0, b = 0, c = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int v = tmp[j];
                    a += v >>> 24; rr += (v >> 16) & 255; g += (v >> 8) & 255; b += v & 255; c++;
                }
                int v = ((a / c) << 24) | ((rr / c) << 16) | ((g / c) << 8) | (b / c);
                if (horiz) p[line * w + i] = v; else p[i * w + line] = v;
            }
        }
    }

    @Override public void draw(Canvas c) {
        if (sBlur == null) return;
        int w = getBounds().width(), h = getBounds().height();
        if (w <= 0 || h <= 0) return;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.clipPath(clip);

        Drawable.Callback cb = getCallback();
        if (cb instanceof View) {
            View v = (View) cb;
            v.getLocationOnScreen(loc);
            RectF dst = new RectF(-loc[0] + getBounds().left, -loc[1] + getBounds().top,
                    sScreenW - loc[0] + getBounds().left, sScreenH - loc[1] + getBounds().top);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            c.drawBitmap(sBlur, null, dst, paint);
        }
        // tint
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(tint);
        c.drawRect(rect, paint);
        // specular sheen on the upper half
        paint.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(rect, paint);
        paint.setShader(null);
        c.restore();

        // rim: bright top-left, soft bottom-right
        c.save();
        c.translate(getBounds().left, getBounds().top);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, radius * 0.07f));
        paint.setShader(new LinearGradient(0, 0, w, h, 0xD0FFFFFF, 0x18FFFFFF, Shader.TileMode.CLAMP));
        RectF r2 = new RectF(rect);
        r2.inset(0.75f, 0.75f);
        c.drawRoundRect(r2, radius, radius, paint);
        paint.setShader(null);
        c.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
