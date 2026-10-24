package android.view;
import android.util.DisplayMetrics;
public final class Display {public final DisplayMetrics metrics=new DisplayMetrics();public void getRealMetrics(DisplayMetrics m){m.widthPixels=metrics.widthPixels;m.heightPixels=metrics.heightPixels;m.density=metrics.density;}}
