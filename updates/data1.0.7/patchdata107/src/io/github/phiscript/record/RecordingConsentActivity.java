package io.github.phiscript.record;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.root.ManualChartControl;

/** Non-exported, explicit user-consent bridge. Does not auto-start game touches. */
public final class RecordingConsentActivity extends Activity {
    private static Chart pending;
    private static String label;
    private final Handler handler=new Handler();
    private long deadline;
    private boolean requested,waiting,opened;
    public static void open(Activity activity,Chart chart,String name){
        if(!new AppSettings(activity).flag("recording_on_play",true)){
            ManualChartControl.open(activity.getApplicationContext(),chart,name);return;
        }
        if(pending!=null||ManualChartControl.isOpen())throw new IllegalStateException("请先完成或取消当前录屏授权");
        pending=chart;label=name;
        try{activity.startActivity(new Intent(activity,RecordingConsentActivity.class));}
        catch(RuntimeException e){pending=null;label=null;throw e;}
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        TextView hint=new TextView(this);hint.setPadding(32,64,32,32);
        hint.setText(io.github.phiscript.i18n.UiText.text("打谱录屏（无声）\n\n请授权录制游戏画面。进入游戏后再点悬浮控制条“开始”。\n\n仅在开始演奏后写入视频；停止或演奏结束后保存。安卓 10+ 保存到 Movies/PhigrosScript。\n\n可在首页关闭自动录屏。"));setContentView(hint);
        if(pending==null){finish();return;}
        requested=saved!=null&&saved.getBoolean("requested");waiting=saved!=null&&saved.getBoolean("waiting");
        if(waiting){deadline=android.os.SystemClock.uptimeMillis()+15000;handler.post(check);return;}
        if(!requested){
            requested=true;ManualRecordingService.resetError();
            try{
                MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
                Intent intent=Build.VERSION.SDK_INT>=34?manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()):manager.createScreenCaptureIntent();
                startActivityForResult(intent,30311);
            }catch(Exception e){cancel("无法打开录屏授权："+e.getMessage());}
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("requested",requested);state.putBoolean("waiting",waiting);super.onSaveInstanceState(state);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=30311)return;
        if(result!=RESULT_OK||data==null){cancel("录屏授权已取消，未开始打谱");return;}
        if(pending==null){finish();return;}
        try{
            Intent service=new Intent(this,ManualRecordingService.class).putExtra("result",result).putExtra("data",data);
            startForegroundService(service);waiting=true;deadline=android.os.SystemClock.uptimeMillis()+15000;handler.post(check);
        }catch(Exception e){cancel("无法启动录屏服务："+e.getMessage());}
    }
    private final Runnable check=new Runnable(){public void run(){
        if(isFinishing()||isDestroyed())return;
        if(ManualRecordingService.ready()){
            try{ManualChartControl.open(getApplicationContext(),pending,label);opened=true;pending=null;label=null;finish();}
            catch(Exception e){cancel(e.getMessage());}
        }else if(ManualRecordingService.lastError()!=null)cancel(ManualRecordingService.lastError());
        else if(android.os.SystemClock.uptimeMillis()>deadline)cancel("录屏准备超时，请重试或关闭首页自动录屏");
        else handler.postDelayed(this,100);
    }};
    private void cancel(String reason){Toast.makeText(this,io.github.phiscript.i18n.UiText.text(reason),Toast.LENGTH_LONG).show();pending=null;label=null;ManualRecordingService.stopVideo();finish();}
    @Override public void onBackPressed(){cancel("已取消录屏与打谱准备");}
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(isFinishing()&&!opened&&pending!=null){pending=null;label=null;ManualRecordingService.stopVideo();}
        super.onDestroy();
    }
}
