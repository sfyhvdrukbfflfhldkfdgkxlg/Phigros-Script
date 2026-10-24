package io.github.phiscript.capture;
public final class RecognitionOverlay {
 public static boolean visible;public void status(String s){visible=true;}
 public long hide(){visible=false;return android.os.SystemClock.uptimeMillis();}
}
