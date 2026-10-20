package com.spandan.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Shader;
import android.util.DisplayMetrics;

/**
 * High-contrast TV chrome. Dense dark panels, crisp borders, no milky wash.
 * A little dither on the shelf only, to kill banding on QLED.
 */
final class GlassHelper {
    private GlassHelper() {}

    // Solid near-black panels. No gray fog, no milky alpha.
    private static final int PANEL_TOP = 0xFF1A1B20;
    private static final int PANEL_BOT = 0xFF0E0F12;
    private static final int PANEL_EDGE = 0x66C0C6D0;

    // Frosted capsules: translucent white over the wallpaper, hairline rim, no hard outline.
    private static final int PILL_IDLE = 0x24FFFFFF;
    private static final int PILL_IDLE_EDGE = 0x1FFFFFFF;
    private static final int PILL_FOCUS = 0xFFFFFFFF;
    private static final int PILL_ACCENT = 0x4D5B8CFF;

    static GradientDrawable round(Context ctx, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(ctx, radiusDp));
        if (stroke != 0 && strokeDp > 0) g.setStroke(Math.max(1, dp(ctx, strokeDp)), stroke);
        return g;
    }

    static GradientDrawable roundGrad(Context ctx, int top, int bottom, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { top, bottom });
        g.setCornerRadius(dp(ctx, radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(ctx, 1f)), stroke);
        return g;
    }

    static Drawable panel(Context ctx, boolean dither, float radiusDp) {
        if (LiquidGlass.ready()) return new LiquidGlass(dp(ctx, radiusDp), 0xC0101218);
        GradientDrawable base = roundGrad(ctx, PANEL_TOP, PANEL_BOT, PANEL_EDGE, radiusDp);
        if (!dither) return base;
        BitmapDrawable noise = new BitmapDrawable(ctx.getResources(), ditherTile(ctx, 48));
        noise.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        noise.setAlpha(16);
        return new LayerDrawable(new Drawable[] { base, noise });
    }

    /** Home shelf: frosted translucent band with a lit top edge and a little grain. */
    static Drawable shelf(Context ctx, boolean dither, float radiusDp) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x1CFFFFFF, 0x0F9FB4FF, 0x1A000000 });
        base.setCornerRadius(dp(ctx, radiusDp));
        base.setStroke(Math.max(1, dp(ctx, 1f)), 0x24FFFFFF);
        GradientDrawable tint = new GradientDrawable();
        tint.setColor(0xB30A0C12);
        tint.setCornerRadius(dp(ctx, radiusDp));
        if (!dither) return new LayerDrawable(new Drawable[] { tint, base });
        BitmapDrawable noise = new BitmapDrawable(ctx.getResources(), ditherTile(ctx, 48));
        noise.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        noise.setAlpha(14);
        return new LayerDrawable(new Drawable[] { tint, base, noise });
    }

    static Drawable pill(Context ctx, boolean focused, boolean accent, boolean dither) {
        if (focused) return round(ctx, PILL_FOCUS, 0xFFFFFFFF, 1.25f, 16);
        if (LiquidGlass.ready()) return new LiquidGlass(dp(ctx, 16), accent ? 0x663C58C8 : 0x2A1E2436);
        if (accent) return round(ctx, PILL_ACCENT, 0x4D9DB8FF, 1f, 16);
        return round(ctx, PILL_IDLE, PILL_IDLE_EDGE, 1f, 16);
    }

    static Drawable focusHalo(Context ctx) {
        // Tight white ring, no fog fill
        return round(ctx, 0x00000000, 0xF2FFFFFF, 2.5f, 20);
    }

    static Drawable pinPlate(Context ctx, boolean dither) {
        return round(ctx, 0x00000000, 0x00000000, 0, 18);
    }

    static Drawable pinPlateFocused(Context ctx) {
        return round(ctx, 0x00000000, 0xF2FFFFFF, 2.5f, 20);
    }

    static Drawable hdmiCap(Context ctx, boolean focused) {
        if (focused) return round(ctx, PILL_FOCUS, 0xFFFFFFFF, 1.25f, 12);
        if (LiquidGlass.ready()) return new LiquidGlass(dp(ctx, 12), 0x2A1E2436);
        return round(ctx, 0x1FFFFFFF, 0x1AFFFFFF, 1f, 12);
    }

    static Drawable vignette(Context ctx) {
        // Light top scrim for the clock, deeper bottom scrim to seat the shelf.
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { 0x40000000, 0x00000000, 0x00000000, 0xB3000000 });
    }

    static Bitmap ditherTile(Context ctx, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int[] bayer = {
                0, 32, 8, 40, 2, 34, 10, 42,
                48, 16, 56, 24, 50, 18, 58, 26,
                12, 44, 4, 36, 14, 46, 6, 38,
                60, 28, 52, 20, 62, 30, 54, 22,
                3, 35, 11, 43, 1, 33, 9, 41,
                51, 19, 59, 27, 49, 17, 57, 25,
                15, 47, 7, 39, 13, 45, 5, 37,
                63, 31, 55, 23, 61, 29, 53, 21
        };
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int t = bayer[(y & 7) * 8 + (x & 7)];
                b.setPixel(x, y, Color.argb(4 + (t / 4), 255, 255, 255));
            }
        }
        return b;
    }

    static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
