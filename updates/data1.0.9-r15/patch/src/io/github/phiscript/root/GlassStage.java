package io.github.phiscript.root;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

/** A full-height scrolling page with a glass lens behind the floating controls. */
final class GlassStage extends FrameLayout {
    final FrameLayout pages;
    private final Lens lens;
    private View dock;

    GlassStage(Context context) {
        super(context);
        pages = new FrameLayout(context);
        pages.setBackground(GlassSurface.backdrop(false));
        addView(pages, new LayoutParams(-1, -1));
        lens = Build.VERSION.SDK_INT >= 31 ? new Lens(context) : null;
    }

    void setDock(View dock) { this.dock = dock; }

    void followScroll(View page) {
        if (lens != null) page.setOnScrollChangeListener((v,x,y,oldX,oldY) -> invalidate());
    }

    @Override protected boolean drawChild(Canvas canvas, View child, long time) {
        boolean drawing = super.drawChild(canvas, child, time);
        if (child == pages && dock != null && dock.getWidth() > 0 && dock.getHeight() > 0
                && lens != null && canvas.isHardwareAccelerated()) {
            lens.draw(canvas, pages, dock);
        }
        return drawing;
    }

    @Override protected void onDetachedFromWindow() {
        if (lens != null) lens.release();
        super.onDetachedFromWindow();
    }

    /** Sample only the dock and a blur halo, never a bitmap of the full screen. */
    private static final class Lens {
        final RenderNode node = new RenderNode("Navigation backdrop");
        final Rect bounds = new Rect();
        final Path clip = new Path();
        final int halo, radius;
        Lens(Context context) {
            int blur = UiDesign.dp(context, 9);
            halo = blur * 3;
            radius = UiDesign.dp(context, 24);
            node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        }

        void release() { node.discardDisplayList(); }

        void draw(Canvas canvas, FrameLayout source, View dock) {
            // The dock's full-size parent shares this stage's origin, including its inset padding.
            int x = dock.getLeft(), y = dock.getTop(), w = dock.getWidth(), h = dock.getHeight();
            if (bounds.left != x || bounds.top != y || bounds.width() != w || bounds.height() != h) {
                bounds.set(x, y, x + w, y + h);
                clip.reset();
                clip.addRoundRect(x, y, x + w, y + h, radius, radius, Path.Direction.CW);
                node.setPosition(x - halo, y - halo, x + w + halo, y + h + halo);
            }
            RecordingCanvas capture = node.beginRecording();
            try {
                capture.translate(halo - x, halo - y);
                // Slight magnification refracts the content where it crosses the glass rim.
                capture.scale(1.045f, 1.08f, bounds.exactCenterX(), bounds.exactCenterY());
                source.draw(capture);
            } finally {
                node.endRecording();
            }
            int save = canvas.save();
            canvas.clipPath(clip);
            canvas.drawRenderNode(node);
            canvas.restoreToCount(save);
        }
    }
}
