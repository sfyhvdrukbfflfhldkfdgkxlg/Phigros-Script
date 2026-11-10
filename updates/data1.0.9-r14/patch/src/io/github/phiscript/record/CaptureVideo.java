package io.github.phiscript.record;

import android.content.Context;
import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.input.TouchService;

/** One capture display. Image capture, OCR sampling, and the 60-Hz encoding clock are independent. */
public final class CaptureVideo {
    private static volatile Context owner;
    private static volatile CaptureVideo active;
    private final Context context;
    private final HandlerThread thread=new HandlerThread("phi-60fps");
    private final Handler media;
    private final FramePixels[] pool={new FramePixels(),new FramePixels()};
    private FramePixels latest,reading;
    private boolean closed;
    private long firstSource=-1,slot=-1,frame,epoch,received;
    private BitmapVideoEncoder encoder;

    private CaptureVideo(Context c){context=c.getApplicationContext();thread.start();media=new Handler(thread.getLooper());}
    public static synchronized void begin(Context c){finished();owner=new AppSettings(c).flag("recording_on_play",true)?c:null;}
    public static double captureScale(Context c,int w,int h,double limit){return RecordingPolicy.captureScale(w,h,limit,c==owner);}
    public static synchronized void playing(){
        if(owner==null||active!=null)return;
        try{active=new CaptureVideo(owner);}catch(RuntimeException|OutOfMemoryError e){RuntimeState.INSTANCE.log("无法启动录屏，打谱继续 "+e.getMessage());}
    }
    public static void image(Context c,Image image){
        CaptureVideo target=active;
        if(c!=owner||target==null)return;
        TouchService touch=TouchService.Companion.getCurrent();
        if(touch!=null&&touch.getGameForeground())target.offer(image);
    }
    private synchronized void offer(Image image){
        if(closed)return;
        long stamp=image.getTimestamp();if(stamp<=0)stamp=System.nanoTime();
        if(firstSource<0)firstSource=stamp;
        long index=RecordingPolicy.sourceSlot(stamp-firstSource);
        if(index<=slot)return;
        FramePixels free=null;
        for(FramePixels b:pool)if(b!=reading&&b!=latest){free=b;break;}
        if(free==null)return;
        try{
            free.copy(image);
            boolean first=latest==null;
            latest=free;slot=index;received++;
            if(first)media.post(this::encode);
        }catch(RuntimeException|OutOfMemoryError e){fail(e);}
    }
    private void encode(){
        final FramePixels pixels;
        synchronized(this){if(closed||latest==null)return;pixels=reading=latest;}
        try{
            if(encoder==null){
                encoder=new BitmapVideoEncoder(context,pixels.bitmap.getWidth(),pixels.bitmap.getHeight());
                epoch=SystemClock.uptimeMillis();RuntimeState.INSTANCE.log("开始录屏，1080p60");
            }
            TouchService touch=TouchService.Companion.getCurrent();
            if(touch==null||!touch.getGameForeground()){end(true);return;}
            encoder.frame(pixels.bitmap,RecordingPolicy.presentationNs(frame++));
            long due=epoch+RecordingPolicy.dueMs(frame);
            if(SystemClock.uptimeMillis()-due>1000)throw new IllegalStateException("设备编码速度低于 1080p60 要求");
            synchronized(this){if(!closed)media.postAtTime(this::encode,due);}
        }catch(Exception|OutOfMemoryError e){fail(e);}
        finally{synchronized(this){reading=null;}}
    }
    private void fail(Throwable e){RuntimeState.INSTANCE.log("录屏失败，打谱继续 "+e.getMessage());end(false);}
    private synchronized void end(boolean save){
        if(closed)return;closed=true;
        media.post(()->{
            try{
                if(encoder!=null){
                    encoder.close(save);
                    if(save)RuntimeState.INSTANCE.log("录屏已结束，提交编码 "+frame+" 帧，接收游戏画面 "+received+" 帧");
                }
            }finally{for(FramePixels b:pool)b.release();thread.quitSafely();}
        });
    }
    public static synchronized void finished(){CaptureVideo c=active;active=null;if(c!=null)c.end(true);}
    public static synchronized void stop(Context c){if(c==owner){owner=null;finished();}}
}
