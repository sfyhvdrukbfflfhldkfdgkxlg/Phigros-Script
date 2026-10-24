package io.github.phiscript.root;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Viewport;
import io.github.phiscript.input.TouchService;
import io.github.phiscript.input.RootChartPlayer;
import io.github.phiscript.record.ManualRecordingService;
import io.github.phiscript.record.RecordingPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;

/** Manual transport intentionally separate from Phira's automatic retry recognition. */
public final class ManualChartControl {
    private static final String ACTION="io.github.phiscript.MANUAL_CHART_STOP";
    private static final int NOTIFICATION=30230;
    private static volatile ManualChartControl current;
    private final Context context;
    private final TouchService service;
    private final Chart chart;
    private final String label;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped=new AtomicBoolean();
    private final WindowManager windows;
    private LinearLayout panel;
    private Thread worker;
    private volatile boolean ownsRunning;
    private boolean registered;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){stop();}};
    private ManualChartControl(Context context,TouchService service,Chart chart,String label){
        this.context=context;this.service=service;this.chart=chart;this.label=label;
        windows=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
    }
    public static boolean isOpen(){return current!=null;}
    public static void open(Context context,Chart chart,String label){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("请在主线程打开控制条");
        if(current!=null||RuntimeState.INSTANCE.getRunning().get())throw new IllegalStateException("请先停止当前任务");
        TouchService service=TouchService.Companion.getCurrent();
        if(service==null)throw new IllegalStateException("请先启用无障碍服务，用于前台保护和音量减停止");
        service.selectGame(TouchService.GAME_PACKAGE);
        ManualChartControl control=new ManualChartControl(context,service,chart,label);current=control;
        try{control.show();}catch(RuntimeException e){current=null;control.destroy();throw e;}
    }
    private void show(){
        panel=new LinearLayout(service);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(12,8,12,8);panel.setBackgroundColor(0xee202830);
        TextView title=new TextView(service);title.setText(io.github.phiscript.i18n.UiText.text("Phigros 手动谱面\n"+label));title.setTextColor(0xffffffff);title.setTextSize(12);panel.addView(title);
        Button start=new Button(service);start.setText(io.github.phiscript.i18n.UiText.text(ManualRecordingService.ready()?"开始打谱并录屏":"开始（按设置延迟）"));start.setOnClickListener(v->begin());panel.addView(start);
        Button close=new Button(service);close.setText(io.github.phiscript.i18n.UiText.text("关闭 / 停止"));close.setOnClickListener(v->stop());panel.addView(close);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.END;lp.y=64;windows.addView(panel,lp);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,new IntentFilter(ACTION),Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver,new IntentFilter(ACTION));registered=true;
        notifyStatus("控制条已就绪：在游戏中点击开始。需手动同步。");
    }
    private void begin(){
        if(worker!=null||stopped.get())return;
        if(!service.getGameForeground()){notifyStatus("请先切换到 Phigros 游戏画面，再点开始");return;}
        if(!RuntimeState.INSTANCE.getRunning().compareAndSet(false,true)){notifyStatus("已有识别或演奏任务");return;}
        ownsRunning=true;
        panel.setVisibility(android.view.View.GONE);
        worker=new Thread(()->{
            RootClient client=null;
            try{
                AppSettings prefs=new AppSettings(context);int delay=Integer.parseInt(prefs.text("phigros_manual_delay_ms","1000"));
                if(delay<0||delay>15000)throw new IllegalArgumentException("手动同步延迟无效");
                android.util.DisplayMetrics dm=new android.util.DisplayMetrics();windows.getDefaultDisplay().getRealMetrics(dm);
                if(dm.widthPixels<=dm.heightPixels)throw new IllegalStateException("请保持横屏");
                RectF roi=prefs.snapshot().getViewport();
                float left=roi.left*dm.widthPixels,top=roi.top*dm.heightPixels,width=roi.width()*dm.widthPixels,height=roi.height()*dm.heightPixels;
                if(prefs.flag("phigros_manual_16_9",true)){
                    float fitted=Math.min(width,height*16f/9);left+=(width-fitted)/2;width=fitted;
                    float fittedHeight=Math.min(height,width*9f/16);top+=(height-fittedHeight)/2;height=fittedHeight;
                }
                Viewport viewport=new Viewport(left,top,width,height);
                boolean root=InputBackend.rootEnabled;
                if(new AppSettings(context).flag("recording_on_play",true))ManualRecordingService.startVideo();
                if(root){notifyStatus("正在连接 Root 触控；就绪后才开始延迟计时");client=new RootClient(service,dm.widthPixels,dm.heightPixels);}
                if(stopped.get())return;
                if(!service.getGameForeground())throw new IllegalStateException("授权期间离开了 Phigros，请回到游戏重新开始");
                if(stopped.get())return;
                final long epoch=RecordingPolicy.epoch(SystemClock.uptimeMillis(),delay);
                notifyStatus("触控已就绪；"+delay+" ms 后开始；音量减键或通知栏停止");
                if(root)RootChartPlayer.playPrepared(service,chart,epoch,viewport,stopped,s->{notifyStatus(s);return Unit.INSTANCE;},client);
                else service.play(chart,epoch,viewport,stopped,(kotlin.jvm.functions.Function1<String,Unit>)(s->{notifyStatus(s);return Unit.INSTANCE;}));
            }catch(Exception e){
                if(e instanceof InterruptedException)Thread.currentThread().interrupt();
                else RuntimeState.INSTANCE.log("Phigros 谱面演奏停止："+e.getMessage());
            }
            finally{if(client!=null)client.close();ManualRecordingService.stopVideo();main.post(()->destroy());}
        },"phi-manual-chart");worker.start();
    }
    private void notifyStatus(String text){
        RuntimeState.INSTANCE.log(text);
        try{
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel("phi_manual_chart",io.github.phiscript.i18n.UiText.text("Phigros 手动谱面演奏"),NotificationManager.IMPORTANCE_LOW));
            Intent stop=new Intent(ACTION).setPackage(context.getPackageName());
            PendingIntent action=PendingIntent.getBroadcast(context,NOTIFICATION,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(context,"phi_manual_chart").setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(io.github.phiscript.i18n.UiText.text(label)).setContentText(io.github.phiscript.i18n.UiText.text(text)).setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"停止",action).build()).build();
            manager.notify(NOTIFICATION,n);
        }catch(RuntimeException ignored){/* Volume-down and foreground protection remain available. */}
    }
    public static void stop(){
        ManualChartControl c=current;if(c==null)return;c.stopped.set(true);
        Thread worker=c.worker;if(worker!=null)worker.interrupt();
        // Do not release the ownership flag until the input worker actually exits.
        if(worker==null)c.main.post(()->c.destroy());
    }
    private void destroy(){
        stopped.set(true);
        ManualRecordingService.stopVideo();
        if(panel!=null){try{windows.removeView(panel);}catch(RuntimeException ignored){}panel=null;}
        if(registered){try{context.unregisterReceiver(receiver);}catch(RuntimeException ignored){}registered=false;}
        try{((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIFICATION);}catch(RuntimeException ignored){}
        if(ownsRunning){RuntimeState.INSTANCE.getRunning().set(false);ownsRunning=false;}
        if(current==this)current=null;
    }
}
