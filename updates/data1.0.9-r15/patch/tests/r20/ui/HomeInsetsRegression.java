package io.github.phiscript.root;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** Exercise the actual APK padding callback as the window changes its navigation mode. */
public final class HomeInsetsRegression {
    private static final Insets NONE = new Insets(0, 0, 0, 0);
    private static void verify(View root, WindowInsets window, int api, int l, int t, int r, int b) {
        Build.VERSION.SDK_INT = api;
        if (HomeInsets.apply(root, window) != window || root.getPaddingLeft() != l
                || root.getPaddingTop() != t || root.getPaddingRight() != r || root.getPaddingBottom() != b)
            throw new AssertionError("Incorrect safe area for API " + api);
    }
    public static void main(String[] args) {
        View root = new View(new Context());
        WindowInsets window = new WindowInsets();
        window.bars = new Insets(0, 24, 0, 16);
        window.gestures = new Insets(0, 0, 0, 28);
        window.notch = new Insets(0, 34, 0, 0);
        verify(root, window, 26, 0, 24, 0, 16);
        verify(root, window, 28, 0, 34, 0, 16);
        verify(root, window, 29, 0, 34, 0, 28);
        verify(root, window, 30, 0, 34, 0, 28);
        if (window.requested != 15) throw new AssertionError("Missing inset type");
        int layouts = root.paddingWrites;
        verify(root, window, 35, 0, 34, 0, 28);
        if (root.paddingWrites != layouts) throw new AssertionError("Unchanged insets requested layout again");
        window.keyboard = new Insets(0, 0, 0, 320);
        verify(root, window, 35, 0, 34, 0, 320);
        window.keyboard = window.gestures = NONE;
        window.bars = new Insets(0, 18, 48, 0);
        window.notch = new Insets(36, 0, 0, 0);
        verify(root, window, 35, 36, 18, 48, 0);
        verify(root, window, 28, 36, 18, 48, 0);
        window.bars = window.notch = NONE;
        verify(root, window, 35, 0, 0, 0, 0);
        System.out.println("PASS window insets: bottom/side navigation, cutout, keyboard, rotation, hidden bars and idempotence on APIs 26/28/29/30/35 (host model).");
    }
}
