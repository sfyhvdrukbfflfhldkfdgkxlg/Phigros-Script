package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;
import static io.github.phiscript.root.UiDesign.words;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import io.github.phiscript.AppSettings;
import io.github.phiscript.input.TapMod;

public final class TapSettings {
    private TapSettings(){}

    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        AppSettings prefs=new AppSettings(activity);
        LinearLayout form=new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int side=UiDesign.dp(activity,24),vertical=UiDesign.dp(activity,12);
        form.setPadding(side,vertical,side,vertical);

        Switch enabled=toggle(activity,form,words("调整触点位置与时机","Adjust touch positions and timing"),
                Boolean.parseBoolean(prefs.text("tapmod_enabled","true")));
        String[] keys={"tap_random_delay_ms","tap_screen_drift_permille","tap_vertical_permille","tap_jitter_permille","tap_noise_escape_permille"};
        String[] defaults={"6","3","35","6","2000"};
        int[] maximum={20,12,200,20,2000};
        String[] labels={
                words("单次手势最大随机延迟（0–20 毫秒）","Maximum random delay per gesture (0–20 ms)"),
                words("屏幕上下浮动（玩法高度 ±‰，0–12）","Vertical drift (±‰ of play area height, 0–12)"),
                tr("沿判定线法线偏移（屏幕高度 ‰，0–200）"),
                words("法线方向随机偏移（玩法高度 ±‰，0–20）","Random offset perpendicular to the judgement line (±‰ of play area height, 0–20)"),
                tr("噪域避让最大距离（屏幕高度 ‰，0–2000）")
        };
        EditText[] values=new EditText[keys.length];
        for(int i=0;i<4;i++)values[i]=number(activity,form,labels[i],prefs.text(keys[i],defaults[i]),i==0);
        note(activity,form,words("每项填 0 可单独关闭。同一手势内保持相同的上下偏移。","Set a value to 0 to turn that adjustment off. Vertical drift stays constant within a gesture."));

        Switch noise=toggle(activity,form,tr("避开谱面噪域"),Boolean.parseBoolean(prefs.text("tap_noise_avoid","true")));
        values[4]=number(activity,form,labels[4],prefs.text(keys[4],defaults[4]),false);
        TextView guardLabel=fieldLabel(activity,form,tr("噪域时钟误差保护"));
        Spinner guard=new Spinner(activity);
        guard.setId(View.generateViewId());guardLabel.setLabelFor(guard.getId());
        guard.setMinimumHeight(UiDesign.dp(activity,48));
        guard.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{
                tr("标准：提前 4 ms / 滞后 12 ms"),tr("保守：前后 16 ms"),tr("更保守：前后 24 ms")}));
        String clock=prefs.text("tap_noise_clock_guard_ms","0");
        guard.setSelection("16".equals(clock)?1:"24".equals(clock)?2:0);
        form.addView(guard);
        note(activity,form,words("时间保护会检查手势前后是否有噪域启闭。扩大保护时间可能缩短可连续触控的安全时段。",
                "The timing guard checks for noise just before and after each touch. A larger guard can shorten the window for a continuous gesture."));

        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        AlertDialog dialog=UiDesign.dialog(activity,tr("噪域与随机点击")).setView(scroll)
                .setPositiveButton(tr("保存"),null).setNegativeButton(tr("取消"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            if(RootPanel.blocked(activity))return;
            String[] inputs=new String[values.length];
            for(int i=0;i<values.length;i++){
                inputs[i]=values[i].getText().toString().trim();
                try{
                    float value=i==0?Integer.parseInt(inputs[i]):Float.parseFloat(inputs[i]);
                    if(!Float.isFinite(value)||value<0||value>maximum[i])throw new NumberFormatException();
                }catch(NumberFormatException invalid){
                    values[i].requestFocus();
                    values[i].setError(i==0?words("请输入 0 到 20 的整数","Enter a whole number from 0 to 20"):tr("请输入 0–")+maximum[i]);
                    return;
                }
            }
            prefs.save("tapmod_enabled",Boolean.toString(enabled.isChecked()));
            prefs.save("tap_noise_avoid",Boolean.toString(noise.isChecked()));
            prefs.save("tap_noise_clock_guard_ms",guard.getSelectedItemPosition()==1?"16":guard.getSelectedItemPosition()==2?"24":"0");
            for(int i=0;i<inputs.length;i++)prefs.save(keys[i],inputs[i]);
            TapMod.init(activity);dialog.dismiss();
        }));
        UiDesign.show(dialog);
    }

    private static Switch toggle(Activity a,LinearLayout form,String label,boolean checked){
        Switch toggle=new Switch(a);toggle.setText(label);toggle.setTextColor(UiDesign.INK);
        toggle.setMinHeight(UiDesign.dp(a,52));toggle.setChecked(checked);form.addView(toggle);return toggle;
    }
    private static TextView fieldLabel(Activity a,LinearLayout form,String title){
        TextView label=UiDesign.text(a,title,14,UiDesign.INK,true);form.addView(label,UiDesign.space(a,-1,-2,12));return label;
    }
    private static EditText number(Activity a,LinearLayout form,String title,String value,boolean integer){
        TextView label=fieldLabel(a,form,title);EditText edit=new EditText(a);edit.setId(View.generateViewId());
        label.setLabelFor(edit.getId());edit.setSingleLine(true);edit.setMinHeight(UiDesign.dp(a,48));
        edit.setInputType(InputType.TYPE_CLASS_NUMBER|(integer?0:InputType.TYPE_NUMBER_FLAG_DECIMAL));edit.setText(value);form.addView(edit);return edit;
    }
    private static void note(Activity a,LinearLayout form,String message){
        form.addView(UiDesign.text(a,message,13,UiDesign.MUTED,false),UiDesign.space(a,-1,-2,10));
    }
}
