package io.github.phiscript.record;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.content.Context;
import android.media.Image;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.input.TouchService;

/** One capture display. Image capture, OCR sampling, and the selected encoding clock are independent. */
public final class CaptureVideo {
    private static volatile Context owner;
    private static volatile CaptureVideo active;
    private static MediaProjection authorizedProjection;
    private static volatile RecordingPolicy.Profile sessionProfile=RecordingPolicy.DEFAULT;
    private final Context context;
    private final RecordingPolicy.Profile profile;
    private final MediaProjection projection;
    private final HandlerThread thread=new HandlerThread("phi-video");
    private final Handler media;
    private final FramePixels[] pool={new FramePixels(),new FramePixels()};
    private FramePixels latest,reading;
    private boolean closed;
    private long firstSource=-1,slot=-1,frame,epoch,received;
    private BitmapVideoEncoder encoder;

    private CaptureVideo(Context c,RecordingPolicy.Profile profile){context=c.getApplicationContext();this.profile=profile;projection=authorizedProjection;thread.start();media=new Handler(thread.getLooper());}
    public static synchronized void begin(Context c){finished();authorizedProjection=null;sessionProfile=RecordingPreferences.load(c);owner=new AppSettings(c).flag("recording_on_play",true)?c:null;}
    public static synchronized void authorized(Context c,MediaProjection projection){if(c==owner)authorizedProjection=projection;}
    public static double captureScale(Context c,int w,int h,double limit){return RecordingPolicy.captureScale(w,h,limit,c==owner,sessionProfile);}
    public static synchronized void playing(){
        if(owner==null||active!=null)return;
        try{active=new CaptureVideo(owner,sessionProfile);}catch(RuntimeException|OutOfMemoryError e){RuntimeState.INSTANCE.log(tr("无法启动录屏，打谱继续 ")+e.getMessage());}
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
        long index=RecordingPolicy.sourceSlot(stamp-firstSource,profile);
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
                encoder=new BitmapVideoEncoder(context,pixels.bitmap.getWidth(),pixels.bitmap.getHeight(),profile);
                epoch=SystemClock.uptimeMillis();RuntimeState.INSTANCE.log(tr("开始录屏，")+profile.label());
                if(projection!=null)encoder.startGameAudio(context,projection,System.nanoTime());
            }
            TouchService touch=TouchService.Companion.getCurrent();
            if(touch==null||!touch.getGameForeground()){end(true);return;}
            encoder.frame(pixels.bitmap,RecordingPolicy.presentationNs(frame++,profile));
            long due=epoch+RecordingPolicy.dueMs(frame,profile);
            if(SystemClock.uptimeMillis()-due>1000)throw new IllegalStateException(tr("设备编码速度低于 ")+profile.label()+tr(" 要求，请降低录屏档位"));
            synchronized(this){if(!closed)media.postAtTime(this::encode,due);}
        }catch(Exception|OutOfMemoryError e){fail(e);}
        finally{synchronized(this){reading=null;}}
    }
    private void fail(Throwable e){RuntimeState.INSTANCE.log(tr("录屏失败，打谱继续 ")+e.getMessage());end(false);}
    private synchronized void end(boolean save){
        if(closed)return;closed=true;
        media.post(()->{
            try{
                if(encoder!=null){
                    encoder.close(save);
                    if(save)RuntimeState.INSTANCE.log(tr("录屏已结束，提交编码 ")+frame+tr(" 帧，接收游戏画面 ")+received+tr(" 帧"));
                }
            }finally{for(FramePixels b:pool)b.release();thread.quitSafely();}
        });
    }
    public static synchronized void finished(){CaptureVideo c=active;active=null;if(c!=null)c.end(true);}
    public static synchronized void stop(Context c){if(c==owner){owner=null;authorizedProjection=null;finished();}}
}
