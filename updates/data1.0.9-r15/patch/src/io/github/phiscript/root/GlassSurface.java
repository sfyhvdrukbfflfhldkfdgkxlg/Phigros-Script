package io.github.phiscript.root;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Translucent glass with a refracted rim. Shaders are built only when bounds change. */
final class GlassSurface extends Drawable {
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rimBounds = new RectF();
    private final Paint reflection = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF innerBounds = new RectF();
    private final Matrix lens = new Matrix();
    private final Paint caustic;
    private final Path curve;
    private final float radius, stroke;
    private final int[] colors;
    private boolean pressed;

    GlassSurface(Context context, int radiusDp, boolean mint) {
        this(context, radiusDp, mint ? new int[]{0xf8eefffc, 0xd9c6ede8, 0xdabdedef}
                                     : new int[]{0xecffffff, 0xc7f3fafb, 0xd9e1ecf4});
    }

    private GlassSurface(Context context, int radiusDp, int[] colors) {
        this(context,radiusDp,colors,false);
    }
    private GlassSurface(Context context, int radiusDp, int[] colors, boolean panel) {
        radius = UiDesign.dp(context, radiusDp);
        stroke = Math.max(1f, context.getResources().getDisplayMetrics().density);
        this.colors = colors;
        caustic=panel?new Paint(Paint.ANTI_ALIAS_FLAG):null;
        curve=panel?new Path():null;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(stroke);
        reflection.setShader(new RadialGradient(0, 0, UiDesign.dp(context, 52),
                new int[]{0x82ffffff, 0x20d9fffa, 0x00d9fffa}, null, Shader.TileMode.CLAMP));
    }

    static Drawable backdrop(boolean translucent) {
        return new Backdrop(translucent);
    }

    static Drawable library(Context context, boolean phira) {
        return new GlassSurface(context, 24, phira
                ? new int[]{0xf7f7f3ff, 0xe2e8e4fa, 0xd6d8e3fa}
                : new int[]{0xf6f0fdff, 0xdcdbf3f2, 0xd5cde6ee},true);
    }

    static Drawable transport(Context context){
        return new GlassSurface(context,24,new int[]{0xf8f4fff9,0xd8c5ede5,0xd2c9e8ee},true);
    }

    static Drawable command(Context context, boolean stop) {
        return new GlassSurface(context, 16, stop
                ? new int[]{0xff913741, 0xff893040, 0xff6d2636}
                : new int[]{0xff176660, 0xff155858, 0xff164456});
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
                new int[]{0xffffffff, 0xfff8ffff, 0x577998a8, 0xbab9cedd, 0xf7ffffff},
                new float[]{0, .24f, .54f, .78f, 1}, Shader.TileMode.CLAMP));
        inner.setShader(new LinearGradient(b.left,b.top,b.left,b.bottom,
                new int[]{0xbfffffff,0x10ffffff,0x0687c2d2,0x6694bdc7},
                new float[]{0,.15f,.74f,1},Shader.TileMode.CLAMP));
        rimBounds.set(b);
        rimBounds.inset(stroke / 2, stroke / 2);
        innerBounds.set(b);
        innerBounds.inset(stroke*1.5f, stroke*1.5f);
        if(curve!=null){
            float x=b.left,y=b.top,w=b.width(),h=Math.min(b.height(),radius*6);
            curve.reset();
            curve.moveTo(x+w*.46f,y+stroke*3);
            curve.cubicTo(x+w*.74f,y+h*.10f,x+w*.68f,y+h*.47f,b.right-stroke*2,y+h*.43f);
            curve.lineTo(b.right-stroke*2,y+h*.52f);
            curve.cubicTo(x+w*.64f,y+h*.52f,x+w*.72f,y+h*.13f,x+w*.46f,y+stroke*3);
            curve.close();
            caustic.setShader(new LinearGradient(x+w*.46f,y,b.right,y+h*.46f,
                    new int[]{0x06ffffff,0x74ffffff,0x30ffffff,0x00ffffff},
                    new float[]{0,.54f,.82f,1},Shader.TileMode.CLAMP));
        }
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
        if(curve!=null)canvas.drawPath(curve,caustic);
        if(!innerBounds.isEmpty())canvas.drawRoundRect(innerBounds,Math.max(0,radius-stroke),Math.max(0,radius-stroke),inner);
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
        inner.setColorFilter(filter);
        if(caustic!=null)caustic.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public void setAlpha(int alpha) {
        body.setAlpha(alpha);
        reflection.setAlpha(alpha);
        rim.setAlpha(alpha);
        inner.setAlpha(alpha);
        if(caustic!=null)caustic.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** Static light fields behind translucent chrome. All gradients follow the actual window bounds. */
    private static final class Backdrop extends Drawable {
        private final boolean translucent;
        private final Paint base=new Paint(), aqua=new Paint(), blue=new Paint(), violet=new Paint();
        Backdrop(boolean translucent){this.translucent=translucent;}

        @Override protected void onBoundsChange(Rect b){
            if(b.isEmpty())return;
            float w=b.width(),h=b.height(),span=Math.min(w,h);
            base.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,
                    translucent?new int[]{0xd6e6f3f3,0xd8f2f6fb,0xd3e4eff3}
                               :new int[]{0xffe6f3f3,0xfff2f6fb,0xffe4eff3},null,Shader.TileMode.CLAMP));
            pool(aqua,b.left+w*.07f,b.top+h*.09f,span*.85f,0x9a7adbc7,0x407edcca);
            pool(blue,b.right,b.top+h*.27f,span*.72f,0x898ccde6,0x308ccde6);
            pool(violet,b.left+w*.69f,b.bottom,span*.86f,0x839fa9e8,0x389fa9e8);
        }
        private static void pool(Paint paint,float x,float y,float r,int core,int middle){
            paint.setShader(new RadialGradient(x,y,r,new int[]{core,middle,core&0xffffff},
                    new float[]{0,.55f,1},Shader.TileMode.CLAMP));
        }
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();
            canvas.drawRect(b,base);canvas.drawRect(b,aqua);canvas.drawRect(b,blue);canvas.drawRect(b,violet);
        }
        @Override public void setAlpha(int alpha){
            base.setAlpha(alpha);aqua.setAlpha(alpha);blue.setAlpha(alpha);violet.setAlpha(alpha);invalidateSelf();
        }
        @Override public void setColorFilter(ColorFilter f){
            base.setColorFilter(f);aqua.setColorFilter(f);blue.setColorFilter(f);violet.setColorFilter(f);invalidateSelf();
        }
        @Override public int getOpacity(){return translucent||base.getAlpha()<255?PixelFormat.TRANSLUCENT:PixelFormat.OPAQUE;}
    }
}
