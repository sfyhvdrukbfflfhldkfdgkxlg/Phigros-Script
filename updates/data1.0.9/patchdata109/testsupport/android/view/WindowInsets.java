package android.view;
public class WindowInsets {
 public static android.graphics.Insets bars=new android.graphics.Insets(0,0,0,0),gestures=new android.graphics.Insets(0,0,0,0);
 public android.graphics.Insets getInsetsIgnoringVisibility(int mask){return bars;}
 public android.graphics.Insets getInsets(int mask){return gestures;}
 public static class Type {public static int systemBars(){return 1;}public static int displayCutout(){return 2;}public static int systemGestures(){return 4;}public static int mandatorySystemGestures(){return 8;}}
}
