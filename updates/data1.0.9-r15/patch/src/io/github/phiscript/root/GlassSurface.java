package io.github.phiscript.root;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/** Translucent glass with a refracted rim. Shaders are built only when bounds change. */
final class GlassSurface extends Drawable {
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rimBounds = new RectF();
    private final Paint reflection = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix lens = new Matrix();
    private final float radius, stroke;
    private final int[] colors;
    private boolean pressed;

    GlassSurface(Context context, int radiusDp, boolean mint) {
        this(context, radiusDp, mint ? new int[]{0xe8f7fff9, 0xc6cfeddf, 0xe2e3f1ee}
                                     : new int[]{0xf0ffffff, 0xbef7fcfa, 0xddf0f4f8});
    }

    private GlassSurface(Context context, int radiusDp, int[] colors) {
        radius = UiDesign.dp(context, radiusDp);
        stroke = Math.max(1f, context.getResources().getDisplayMetrics().density);
        this.colors = colors;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
        reflection.setShader(new RadialGradient(0, 0, UiDesign.dp(context, 52),
                new int[]{0x82ffffff, 0x20d9fffa, 0x00d9fffa}, null, Shader.TileMode.CLAMP));
    }

    static Drawable backdrop(boolean translucent) {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                translucent ? new int[]{0xc7c6e9dd, 0xcbf1f7ed, 0xcceef0fa, 0xc7d5e8ed}
                            : new int[]{0xffc6e9dd, 0xffedf4ed, 0xffeef0fa, 0xffd5e8ed});
    }

    static Drawable dialog(Context context) {
        // Keep body text readable even when power saving disables cross-window blur.
        return new GlassSurface(context, 22, new int[]{0xf4ffffff, 0xddf7fcfa, 0xeeeef4f8});
    }

    static Drawable dock(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 31) return new GlassSurface(context, 24, false);
        return new GlassSurface(context, 24, new int[]{0xc4ffffff, 0x89edf9f4, 0xa7edf2fa});
    }

    @Override protected void onBoundsChange(Rect b) {
        if (b.isEmpty()) return;
        body.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                colors, null, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{0xffffffff, 0xaaffffff, 0x737ba79c, 0xe8ffffff},
                new float[]{0, .38f, .76f, 1}, Shader.TileMode.CLAMP));
        rimBounds.set(b);
        rimBounds.inset(stroke / 2, stroke / 2);
        setHotspot(b.exactCenterX(), b.exactCenterY());
    }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] states) {
        boolean down = false;
        for (int state : states) if (state == android.R.attr.state_pressed) down = true;
        if (down == pressed) return false;
        pressed = down;
        invalidateSelf();
        return true;
    }

    @Override public void setHotspot(float x, float y) {
        lens.setTranslate(x, y);
        reflection.getShader().setLocalMatrix(lens);
        if (pressed) invalidateSelf();
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, radius, radius, body);
        if (pressed) canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, radius, radius, reflection);
        canvas.drawRoundRect(rimBounds, radius - stroke / 2, radius - stroke / 2, rim);
    }

    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
        outline.setAlpha(body.getAlpha() / 255f);
    }

    @Override public void setColorFilter(ColorFilter filter) {
        body.setColorFilter(filter);
        reflection.setColorFilter(filter);
        rim.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public void setAlpha(int alpha) {
        body.setAlpha(alpha);
        reflection.setAlpha(alpha);
        rim.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
