package io.github.phiscript.record;
import android.content.Context;import android.graphics.Bitmap;import android.media.Image;import android.os.Handler;import android.os.HandlerThread;import android.os.SystemClock;
import io.github.phiscript.AppSettings;import io.github.phiscript.input.TouchService;
public class CaptureRegression{
    static void ok(boolean x){if(!x)throw new AssertionError();}
    public static void main(String[] args){
        Context c=new Context();SystemClock.now=1000;
        CaptureVideo.begin(c);CaptureVideo.playing();CaptureVideo.image(new Context(),new Image(1000000,255));
        Handler.until(1000);ok(BitmapVideoEncoder.pts.isEmpty());
        for(int f=0;f<60;f++){
            Handler.until(1000+RecordingPolicy.dueMs(f));CaptureVideo.image(c,new Image(1000000+RecordingPolicy.presentationNs(f),f));
            Handler.until(1000+RecordingPolicy.dueMs(f));
        }
        Handler.until(2000);ok(BitmapVideoEncoder.pts.size()==61);
        for(int i=0;i<61;i++)ok(BitmapVideoEncoder.pts.get(i)==RecordingPolicy.presentationNs(i));
        ok(new java.util.HashSet<>(BitmapVideoEncoder.pixels).size()>=58&&Bitmap.allocations==2);
        Handler.until(2100);ok(BitmapVideoEncoder.pts.size()==67&&BitmapVideoEncoder.pixels.get(66)==59);
        TouchService.foreground=false;Handler.until(2200);ok(BitmapVideoEncoder.pts.size()==67&&BitmapVideoEncoder.saved&&Bitmap.live==0);
        CaptureVideo.stop(c);TouchService.foreground=true;AppSettings.enabled=false;CaptureVideo.begin(c);CaptureVideo.playing();
        CaptureVideo.image(c,new Image(1,1));Handler.until(2300);ok(BitmapVideoEncoder.pts.size()==67);
        AppSettings.enabled=true;CaptureVideo.begin(c);CaptureVideo.playing();BitmapVideoEncoder.fail=true;
        CaptureVideo.image(c,new Image(1,4));Handler.until(2301);CaptureVideo.stop(c);ok(!BitmapVideoEncoder.saved&&Bitmap.live==0&&HandlerThread.closed==2);
        System.out.println("PASS final APK capture coordinator: real frame ingress, 60Hz timestamps, source updates and holds, two reusable buffers, exact-owner and foreground gates, disabled capture, cleanup and encoder failure.");
    }
}
