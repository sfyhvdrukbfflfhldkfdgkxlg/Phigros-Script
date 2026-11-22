package android.view;

import android.graphics.Insets;

public final class WindowInsets {
    public static final class Type {
        public static int systemBars() { return 1; }
        public static int displayCutout() { return 2; }
        public static int ime() { return 4; }
        public static int mandatorySystemGestures() { return 8; }
    }
    public Insets bars = new Insets(0, 0, 0, 0);
    public Insets gestures = bars, notch = bars, keyboard = bars;
    public int requested;

    public Insets getInsets(int mask) {
        requested = mask;
        int l = 0, t = 0, r = 0, b = 0;
        Insets[] edges = {bars, notch, keyboard, gestures};
        for (int i = 0; i < edges.length; i++) {
            if ((mask & (1 << i)) == 0) continue;
            Insets edge = edges[i];
            l = Math.max(l, edge.left); t = Math.max(t, edge.top);
            r = Math.max(r, edge.right); b = Math.max(b, edge.bottom);
        }
        return new Insets(l, t, r, b);
    }

    public int getSystemWindowInsetLeft() { return bars.left; }
    public int getSystemWindowInsetRight() { return bars.right; }
    public int getSystemWindowInsetTop() { return bars.top; }
    public int getSystemWindowInsetBottom() { return bars.bottom; }
    public DisplayCutout getDisplayCutout() { return new DisplayCutout(notch); }
    public Insets getMandatorySystemGestureInsets() { return gestures; }
}
