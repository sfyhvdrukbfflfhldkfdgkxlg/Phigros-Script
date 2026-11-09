package io.github.phiscript.capture;
import android.graphics.Bitmap;
public final class CapturedFrame implements AutoCloseable {
 private final long at;private final Bitmap bitmap;
 public CapturedFrame(int p){this(p,android.os.SystemClock.uptimeMillis());}
 public CapturedFrame(int p,long t){at=t;bitmap=new Bitmap(p);}
 public Bitmap getBitmap(){return bitmap;}public long getUptimeMs(){return at;}public int getScreenWidth(){return 1000;}public int getScreenHeight(){return 600;}public void close(){}
}
