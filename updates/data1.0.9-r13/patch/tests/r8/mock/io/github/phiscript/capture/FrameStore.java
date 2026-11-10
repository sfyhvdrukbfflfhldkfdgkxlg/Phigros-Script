package io.github.phiscript.capture;
import android.graphics.Bitmap;import android.os.SystemClock;
public final class FrameStore {
 public CapturedFrame snapshot(){Bitmap bitmap=SessionTrace.frame();return bitmap==null?null:new CapturedFrame(bitmap,SystemClock.uptimeMillis(),bitmap.width,bitmap.height);}
}
