package io.github.phiscript.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.CheckBox;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.capture.CaptureService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class AutoEntry {
    private AutoEntry(){}
    public static void start(Activity activity){start(activity,false);}
    public static void start(Activity activity,boolean preview){
        if(RootPanel.isBusy()||RuntimeState.INSTANCE.getRunning().get()){RootPanel.message(activity,"请先结束当前任务","当前仍有授权、识别或演奏任务，请先停止再切换。");return;}
        if(!io.github.phiscript.assets.ExternalCharts.ensurePhigrosSelected(activity))return;
        if(RuntimeState.INSTANCE.getLibrary()==null){RootPanel.message(activity,"尚无 Phigros 谱库","请先用 Root 一键导入已安装 Phigros 谱库，或手动导入游戏 APK。");return;}
        ModeRouting.select(new AppSettings(activity),0);
        launch(activity,preview);
    }
    static void launch(Activity activity,boolean preview){
        try{
            Method start=activity.getClass().getDeclaredMethod("startSession",boolean.class);start.setAccessible(true);start.invoke(activity,preview);
        }catch(Exception e){Throwable t=e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;RootPanel.message(activity,"无法开启自动检测",String.valueOf(t.getMessage()));}
    }
    public static void stop(Activity activity){
        RootPhiraImport.cancel();
        PhiraImportControl.cancel();
        ManualChartControl.stop();activity.startService(new Intent(activity,CaptureService.class).setAction(CaptureService.ACTION_STOP));
    }
    public static void settings(Activity activity){RetryCalibration.open(activity);}
}
