package io.github.phiscript.root;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import java.util.function.Consumer;

/** Apply the system compositor's full-window blur and track power-saving fallbacks. */
final class GlassWindows {
    private GlassWindows() {}

    static void home(Window window, View background) {
        if (Build.VERSION.SDK_INT >= 31) new Blur(window, background).install();
    }

    static void dialog(Window window) {
        window.setBackgroundDrawable(GlassSurface.dialog(window.getContext()));
        window.setDimAmount(.32f);
        if (Build.VERSION.SDK_INT >= 31) new Blur(window, null).install();
    }

    private static final class Blur implements View.OnAttachStateChangeListener, Consumer<Boolean> {
        private final Window window;
        private final View background;
        private final WindowManager manager;
        private final int radius;
        private boolean listening;

        Blur(Window window, View background) {
            this.window = window;
            this.background = background;
            manager = window.getWindowManager();
            radius = Math.min(120, UiDesign.dp(window.getContext(), background == null ? 18 : 28));
        }

        void install() {
            if (manager == null) return;
            View decor = window.getDecorView();
            decor.addOnAttachStateChangeListener(this);
            if (decor.isAttachedToWindow()) onViewAttachedToWindow(decor);
        }

        @Override public void onViewAttachedToWindow(View view) {
            if (listening) return;
            listening = true;
            manager.addCrossWindowBlurEnabledListener(this);
            accept(manager.isCrossWindowBlurEnabled());
        }

        @Override public void onViewDetachedFromWindow(View view) {
            if (listening) manager.removeCrossWindowBlurEnabledListener(this);
            listening = false;
        }

        @Override public void accept(Boolean enabled) {
            if (!listening) return;
            WindowManager.LayoutParams attrs = window.getAttributes();
            if (enabled) attrs.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            else attrs.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            attrs.setBlurBehindRadius(enabled ? radius : 0);
            if (background == null) {
                attrs.dimAmount = enabled ? .16f : .32f;
                window.setBackgroundBlurRadius(enabled ? radius : 0);
            } else {
                if (enabled) attrs.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
                else attrs.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setFormat(PixelFormat.TRANSLUCENT);
                background.setBackground(GlassSurface.backdrop(enabled));
            }
            window.setAttributes(attrs);
        }
    }
}
