package io.github.phiscript.root;

import android.app.*;
import android.widget.*;
import io.github.phiscript.AppSettings;
import java.util.*;

/** Recognition/device parameters; retry delay is edited only by RetryCalibration. */
public final class RecognitionSettings {
    private RecognitionSettings(){}
    private static final String[][] FIELDS={
        {"view","玩法视口 x,y,宽,高","0,0,1,1"},
        {"title","曲名识别区域 x,y,宽,高","0,0,1,1"},
        {"difficulty","当前难度识别区域 x,y,宽,高","0,0,1,1"},
        {"pauseRegion","纯视觉暂停图标区域 x,y,宽,高","0,0,0.18,0.24"},
        {"phiraPauseRegion","读谱模式暂停图标区域 x,y,宽,高","0,0,0.18,0.24"},
        {"doubleTapInterval","双击暂停间隔（毫秒，80–300）","140"},
        {"phiraRestartPoint","Phira 重试键 x,y","0.5,0.5"},
        {"pauseSettle","暂停过渡等待（毫秒，900–4000）","1000"},
        {"phiraGlobalOffset","游戏全局偏移（毫秒，-2000–2000）","0"},
        {"touchOffset","触控偏移（毫秒，-500–500）","0"},
        {"captureLag","纯视觉截图补偿（毫秒，-500–500）","0"}
    };
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        AppSettings prefs=new AppSettings(activity);boolean phigros=!ModeRouting.needsPhira(prefs);LinearLayout form=new LinearLayout(activity);form.setOrientation(1);
        int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);
        TextView help=new TextView(activity);help.setText("Phigros 与 Phira 读谱均从重试按下时建立时钟。起始延迟请在“重试校准”设置；这里的全局偏移和触控偏移按所选游戏分别保存。Phigros 重试位置由三键菜单识别。");form.addView(help);
        CheckBox automatic=new CheckBox(activity);automatic.setText("按谱面比例计算居中玩法视口");automatic.setChecked(prefs.flag("phiraAutomaticViewport",true));form.addView(automatic);
        Map<String,EditText> edits=new LinkedHashMap<>();
        for(String[] field:FIELDS){TextView label=new TextView(activity);label.setText(field[1]);form.addView(label);EditText edit=new EditText(activity);edit.setSingleLine(true);edit.setText(io.github.phiscript.GameTiming.read(prefs,phigros,field[0],field[2]));form.addView(edit);edits.put(field[0],edit);}
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("识别区域与设备设置").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(RootPanel.blocked(activity))return;
            try{
                for(String key:new String[]{"view","title","difficulty","pauseRegion","phiraPauseRegion"})AppSettings.Companion.rect(value(edits,key));
                AppSettings.Companion.point(value(edits,"phiraRestartPoint"));
                range(edits,"doubleTapInterval",80,300);range(edits,"pauseSettle",900,4000);range(edits,"phiraGlobalOffset",-2000,2000);range(edits,"touchOffset",-500,500);range(edits,"captureLag",-500,500);
                prefs.saveFlag("phiraAutomaticViewport",automatic.isChecked());
                for(Map.Entry<String,EditText> edit:edits.entrySet())prefs.save(io.github.phiscript.GameTiming.key(phigros,edit.getKey()),edit.getValue().getText().toString().trim());
                dialog.dismiss();
            }catch(Exception error){Toast.makeText(activity,error.getMessage()==null?"参数无效":error.getMessage(),Toast.LENGTH_LONG).show();}
        }));dialog.show();
    }
    private static String value(Map<String,EditText> edits,String key){return edits.get(key).getText().toString().trim();}
    private static void range(Map<String,EditText> edits,String key,int min,int max){int value=Integer.parseInt(value(edits,key));if(value<min||value>max){String message="请输入 "+min+"～"+max+" 毫秒";edits.get(key).setError(message);throw new IllegalArgumentException(message);}}
}
