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
        if(RuntimeState.INSTANCE.getLibrary()==null){RootPanel.message(activity,"尚无 Phigros 谱库","请先用 Root 一键导入已安装 Phigros 谱库，或手动导入游戏 APK。");return;}
        AppSettings prefs=new AppSettings(activity);prefs.save("mode","VISUAL");prefs.saveFlag("phigros_manual_mode",false);prefs.saveFlag("phigros_auto_mode",true);
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
    public static void settings(Activity activity){
        if(RootPanel.isBusy()||RuntimeState.INSTANCE.getRunning().get()){RootPanel.message(activity,"请先停止任务","停止自动检测后再修改同步参数。");return;}
        AppSettings prefs=new AppSettings(activity);LinearLayout form=new LinearLayout(activity);form.setOrientation(1);
        int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,0,pad,0);
        TextView info=new TextView(activity);info.setText(io.github.phiscript.i18n.UiText.text("先识别清晰的曲名与难度，再定位暂停和中央重试。默认使用实际音符与谱面几何连续匹配来校准起点。\n\n起点偏移用于额外校准；正数稍后、负数提前。若关闭音符校准，则按 HUD 和手动偏移同步。"));
        form.addView(info);
        CheckBox align=new CheckBox(activity);align.setText(io.github.phiscript.i18n.UiText.text("根据实际音符校准起点"));align.setChecked(prefs.flag("phigros_sync_visible_notes",true));form.addView(align);
        CheckBox fit=new CheckBox(activity);fit.setText(io.github.phiscript.i18n.UiText.text("Phigros 居中 16:9 玩法区域"));fit.setChecked(prefs.flag("phigros_fit_16_9",true));form.addView(fit);
        EditText offset=field(activity,form,"起点微调（ms；音符校准时 ±500）",prefs.text(align.isChecked()?"phigros_note_clock_offset_ms":"phigros_auto_start_offset_ms","0"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        align.setOnCheckedChangeListener((button,checked)->offset.setText(prefs.text(checked?"phigros_note_clock_offset_ms":"phigros_auto_start_offset_ms","0")));
        new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("Phigros 自动同步"))
            .setView(form).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null)
            .setNeutralButton(io.github.phiscript.i18n.UiText.text("识别区域设置"),(d,w)->HomeScreen.invoke(activity,"calibration"))
            .setPositiveButton(io.github.phiscript.i18n.UiText.text("保存"),(d,w)->{
                try{int value=Integer.parseInt(offset.getText().toString().trim());if(value<(align.isChecked()?-500:-3000)||value>(align.isChecked()?500:10000))throw new NumberFormatException();prefs.save(align.isChecked()?"phigros_note_clock_offset_ms":"phigros_auto_start_offset_ms",String.valueOf(value));prefs.saveFlag("phigros_sync_visible_notes",align.isChecked());prefs.saveFlag("phigros_fit_16_9",fit.isChecked());}
                catch(NumberFormatException e){RootPanel.message(activity,"参数无效","音符校准微调范围 ±500 ms；手动 HUD 模式为 -3000～10000 ms。");}
            }).show();
    }
    private static EditText field(Activity a,LinearLayout form,String label,String value,int type){TextView text=new TextView(a);text.setText(io.github.phiscript.i18n.UiText.text(label));form.addView(text);EditText edit=new EditText(a);edit.setSingleLine(true);edit.setInputType(type);edit.setText(io.github.phiscript.i18n.UiText.text(value));form.addView(edit);return edit;}
}
