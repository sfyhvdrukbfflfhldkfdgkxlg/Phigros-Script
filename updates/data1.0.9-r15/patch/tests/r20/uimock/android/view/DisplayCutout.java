package android.view;

import android.graphics.Insets;

public final class DisplayCutout {
    private final Insets safe;
    public DisplayCutout(Insets safe) { this.safe = safe; }
    public int getSafeInsetLeft() { return safe.left; }
    public int getSafeInsetTop() { return safe.top; }
    public int getSafeInsetRight() { return safe.right; }
    public int getSafeInsetBottom() { return safe.bottom; }
}
