package io.github.phiscript.record;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.input.TouchService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records the already-authorized OCR frames; never starts a second MediaProjection. */
public final class CaptureVideo {
    private static volatile CaptureVideo current;
    private static volatile Context sourceOwner;
    private final Context context;
    private final HandlerThread thread=new HandlerThread("phi-auto-video");
    private final Handler handler;
    private final AtomicBoolean queued=new AtomicBoolean();
    private volatile boolean playing,closed;
    private long last;
    private BitmapVideoEncoder encoder;
    private CaptureVideo(Context c){context=c.getApplicationContext();thread.start();handler=new Handler(thread.getLooper());}
    public static synchronized void begin(Context c){
        stop(c);
        if(new AppSettings(c).flag("recording_on_play",true))sourceOwner=c;
    }
    public static synchronized void playing(){
        if(sourceOwner==null)return;
        try{if(current==null)current=new CaptureVideo(sourceOwner);}
        catch(RuntimeException e){RuntimeState.INSTANCE.log("无法启动录屏线程，打谱继续："+e.getMessage());return;}
        catch(OutOfMemoryError e){RuntimeState.INSTANCE.log("内存不足，无法启动录屏，打谱继续");return;}
        current.playing=true;RuntimeState.INSTANCE.log("演奏录屏已开始（无声，复用已授权画面）");
    }
    public static void frame(Context owner,Bitmap source,long captured){
        CaptureVideo c=current;if(c==null||sourceOwner!=owner||!c.playing||c.closed||captured-c.last<33)return;
        TouchService service=TouchService.Companion.getCurrent();if(service==null||!service.getGameForeground())return;
        if(!c.queued.compareAndSet(false,true))return;
        Bitmap bitmap=null;
        try{
            int[] size=RecordingPolicy.size(source.getWidth(),source.getHeight());
            bitmap=Bitmap.createScaledBitmap(source,size[0],size[1],true);
            if(bitmap==source)bitmap=source.copy(Bitmap.Config.ARGB_8888,false);
            if(bitmap==null)throw new IllegalStateException("无法复制录屏画面");
            final Bitmap copy=bitmap;c.last=captured;
            if(!c.handler.post(()->{
                try{
                    if(c.encoder==null)c.encoder=new BitmapVideoEncoder(c.context,copy.getWidth(),copy.getHeight());
                    c.encoder.frame(copy,captured);
                }catch(Exception e){RuntimeState.INSTANCE.log("自动录屏失败（打谱继续）："+e.getMessage());c.closed=true;if(c.encoder!=null)c.encoder.close(false);c.encoder=null;c.thread.quitSafely();}
                catch(OutOfMemoryError e){RuntimeState.INSTANCE.log("录屏内存不足，打谱继续");c.closed=true;if(c.encoder!=null)c.encoder.close(false);c.encoder=null;c.thread.quitSafely();}
                finally{copy.recycle();c.queued.set(false);}
            })){copy.recycle();c.queued.set(false);}
        }catch(Exception e){if(bitmap!=null&&bitmap!=source)bitmap.recycle();c.queued.set(false);RuntimeState.INSTANCE.log("录屏跳过一帧："+e.getMessage());}
        catch(OutOfMemoryError e){if(bitmap!=null&&bitmap!=source)bitmap.recycle();c.queued.set(false);RuntimeState.INSTANCE.log("内存不足，已停止本次录屏，打谱继续");finished();}
    }
    public static synchronized void finished(){
        CaptureVideo c=current;if(c==null)return;
        current=null;c.closed=true;
        c.handler.post(()->{if(c.encoder!=null){c.encoder.close(true);c.encoder=null;}c.thread.quitSafely();});
    }
    public static synchronized void stop(Context owner){sourceOwner=null;finished();}
}
