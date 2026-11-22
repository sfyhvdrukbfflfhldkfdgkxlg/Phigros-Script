package android.view;
import android.util.DisplayMetrics;
public class Display {
    public static int usableWidth = 1000, usableHeight = 600, rotation;
    public void getRealMetrics(DisplayMetrics d) {}
    public void getMetrics(DisplayMetrics d) { d.widthPixels = usableWidth; d.heightPixels = usableHeight; }
    public int getRotation() { return rotation; }
}
