package io.github.phiscript.record;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.root.ManualChartControl;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One fresh MediaProjection token per armed manual session; never caches/reuses tokens. */
public final class ManualRecordingService extends Service {
    private static volatile ManualRecordingService current;
    private static volatile String error;
    private static final int ID=30310;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopping=new AtomicBoolean();
    private HandlerThread thread;
    private Handler media;
    private MediaRecorder recorder;
    private MediaProjection projection;
    private VirtualDisplay display;
    private RecordingFiles output;
    private volatile boolean armed,recording;
    private long began;
    private final MediaProjection.Callback callback=new MediaProjection.Callback(){
        @Override public void onStop(){
            if(!stopping.get()){RuntimeState.INSTANCE.log("系统已结束录屏授权，停止本次打谱");ManualChartControl.stop();end();}
        }
    };
    public static boolean ready(){ManualRecordingService c=current;return c!=null&&c.armed&&!c.stopping.get();}
    public static String lastError(){return error;}
    public static void resetError(){error=null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||"stop".equals(intent.getAction())){ManualChartControl.stop();end();return START_NOT_STICKY;}
        if(current!=null){if(current!=this)stopSelf();return START_NOT_STICKY;}
        current=this;
        try{
            notification("录屏授权已开启，等待游戏内点击“开始”；无声视频");
            int result=intent.getIntExtra("result",0);Intent data=intent.getParcelableExtra("data");
            if(result!=Activity.RESULT_OK||data==null)throw new IllegalArgumentException("缺少本次录屏授权");
            // Android 14+: service must already be foreground before consuming the token.
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(result,data);
            if(projection==null)throw new IllegalStateException("录屏授权已失效");
            projection.registerCallback(callback,main);
            thread=new HandlerThread("phi-record-media");thread.start();media=new Handler(thread.getLooper());
            armed=true;
            // Actual capture/output is deferred to Start, after the game is in landscape.
            // An abandoned consent/control screen must not leave authorization forever.
            main.postDelayed(()->{if(!recording&&!ManualChartControl.isOpen())end();},60000);
        }catch(Exception e){fail(e.getMessage());}
        return START_NOT_STICKY;
    }
    private void prepareVideo()throws Exception{
        DisplayMetrics dm=new DisplayMetrics();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        if(dm.widthPixels<=dm.heightPixels)throw new IllegalStateException("录屏前请切换 Phigros 横屏画面");
        int[] size=RecordingPolicy.size(dm.widthPixels,dm.heightPixels);
        output=new RecordingFiles(this);recorder=new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(output.descriptor.getFileDescriptor());recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(size[0],size[1]);recorder.setVideoFrameRate(30);recorder.setVideoEncodingBitRate(5000000);
        recorder.setOnErrorListener((r,what,extra)->fail("视频编码器错误 "+what+" / "+extra));
        recorder.prepare();
        if(stopping.get())throw new IllegalStateException("录屏已取消");
        display=projection.createVirtualDisplay("Phigros recording",size[0],size[1],dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,recorder.getSurface(),null,media);
    }
    private void notification(String text){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("phi_recording",io.github.phiscript.i18n.UiText.text("打谱录屏"),NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,ID,new Intent(this,ManualRecordingService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"phi_recording").setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(io.github.phiscript.i18n.UiText.text("Phigros 打谱录屏")).setContentText(io.github.phiscript.i18n.UiText.text(text)).setOngoing(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"停止录屏和打谱",stop).build()).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(ID,n);
    }
    public static void startVideo()throws Exception{
        ManualRecordingService c=current;
        if(c==null||!c.armed||c.stopping.get())throw new IllegalStateException("本次录屏未就绪，请重新授权");
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<Exception> failure=new AtomicReference<>();
        c.media.post(()->{
            try{
                if(c.stopping.get())throw new IllegalStateException("录屏已停止");
                if(c.recording)throw new IllegalStateException("本次录屏已经开始");
                c.prepareVideo();
                if(c.stopping.get())throw new IllegalStateException("录屏已取消");
                c.recorder.start();c.began=android.os.SystemClock.uptimeMillis();c.recording=true;
                c.main.post(()->{if(!c.stopping.get())c.notification("正在录制游戏画面（无声）；停止打谱将自动保存");});
            }catch(Exception e){failure.set(e);c.fail(e.getMessage());}
            catch(OutOfMemoryError e){failure.set(new IllegalStateException("录屏内存不足",e));c.fail("录屏内存不足");}
            finally{latch.countDown();}
        });
        if(!latch.await(8,TimeUnit.SECONDS)){c.end();throw new IllegalStateException("启动录屏超时");}
        if(failure.get()!=null)throw failure.get();
    }
    public static void stopVideo(){ManualRecordingService c=current;if(c!=null)c.end();}
    private void fail(String text){error="录屏失败："+text;RuntimeState.INSTANCE.log(error);ManualChartControl.stop();end();}
    private void end(){
        if(!stopping.compareAndSet(false,true))return;armed=false;
        Runnable cleanup=()->{
            boolean valid=false;
            if(recorder!=null){
                try{if(recording){recorder.stop();valid=android.os.SystemClock.uptimeMillis()-began>=300;}}catch(Exception e){RuntimeState.INSTANCE.log("录屏过短或编码未完成，未保存损坏视频");}
                try{recorder.release();}catch(Exception ignored){}recorder=null;
            }
            recording=false;
            if(display!=null){display.release();display=null;}
            if(output!=null){output.finish(valid);output=null;}
            MediaProjection p=projection;projection=null;
            if(p!=null){try{p.unregisterCallback(callback);p.stop();}catch(Exception ignored){}}
            main.post(()->{if(current==this)current=null;stopForeground(true);stopSelf();});
            if(thread!=null)thread.quitSafely();
        };
        if(media!=null)media.post(cleanup);else cleanup.run();
    }
    @Override public void onDestroy(){end();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
