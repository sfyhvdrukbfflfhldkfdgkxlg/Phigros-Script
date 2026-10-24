package io.github.phiscript.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;

/** Persist the last real stop reason; cancellation is deliberately not an error. */
public final class SessionFeedback {
    private static final String CHANNEL="phi-auto-errors";
    private static final int ID=42012;
    private SessionFeedback(){}
    public static void clear(Context context){
        AppSettings prefs=new AppSettings(context);prefs.save("phigros_last_error","");prefs.save("phigros_last_stage","正在启动自动检测");
        try{((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID);}catch(Exception ignored){}
    }
    public static void stage(Context context,String text){new AppSettings(context).save("phigros_last_stage",text);}
    public static void failed(Context context,Throwable error){
        String detail=error.getMessage();if(detail==null||detail.trim().isEmpty())detail=error.getClass().getSimpleName();
        report(context,detail);
    }
    public static void captureStopped(Context context,String reason){
        if(reason==null)return;
        try{
            if(!new AppSettings(context).flag("phigros_auto_mode",false))return;
            java.lang.reflect.Field field=CaptureService.class.getDeclaredField("stop");field.setAccessible(true);
            boolean stopped=((java.util.concurrent.atomic.AtomicBoolean)field.get(context)).get();
            if(!SessionStopPolicy.shouldReport(reason,stopped))return;
            report(context,reason.equals("录屏已结束")?"截图会话已被系统或用户停止；请重新开始并完成截屏授权":reason);
        }catch(Exception ignored){}
    }
    private static void report(Context context,String reason){
        try{
            AppSettings prefs=new AppSettings(context);String stage=prefs.text("phigros_last_stage","自动检测");
            String detail="停止阶段："+stage+"\n原因："+reason;
            if(detail.length()>1200)detail=detail.substring(0,1200);
            prefs.save("phigros_last_error",detail);RuntimeState.INSTANCE.log("Phigros 检测已停止；"+detail);
            final String brief=reason.length()>120?reason.substring(0,120)+"…":reason;
            new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,io.github.phiscript.i18n.UiText.text("自动打谱已停止："+brief+"\n返回应用查看停止原因"),Toast.LENGTH_LONG).show());
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,io.github.phiscript.i18n.UiText.text("打谱停止原因"),NotificationManager.IMPORTANCE_DEFAULT));
            Notification.Builder notice=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(io.github.phiscript.i18n.UiText.text("Phigros 自动打谱已停止")).setContentText(io.github.phiscript.i18n.UiText.text(brief)).setStyle(new Notification.BigTextStyle().bigText(io.github.phiscript.i18n.UiText.text(detail))).setAutoCancel(true);
            Intent open=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if(open!=null)notice.setContentIntent(PendingIntent.getActivity(context,ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            try{manager.notify(ID,notice.build());}catch(SecurityException ignored){}
        }catch(Exception e){RuntimeState.INSTANCE.log("停止原因提示未能显示："+reason);}
    }
}
