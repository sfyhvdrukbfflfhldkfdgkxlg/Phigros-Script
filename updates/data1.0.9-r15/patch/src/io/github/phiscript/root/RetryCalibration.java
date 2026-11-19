package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;
import android.app.*;import android.widget.*;import android.text.InputType;
import io.github.phiscript.AppSettings;
import io.github.phiscript.GameTiming;
public final class RetryCalibration {
    private RetryCalibration(){}
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        UiDesign.show(UiDesign.dialog(activity,tr("重试校准")).setItems(new String[]{"Phigros","Phira / Phira Pro"},(d,i)->edit(activity,i==0)).setNegativeButton(tr("取消"),null).create());
    }
    private static void edit(Activity activity,boolean phigros){
        AppSettings prefs=new AppSettings(activity);LinearLayout form=new LinearLayout(activity);form.setOrientation(1);
        int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);
        TextView label=new TextView(activity);label.setText(tr("重试起始延迟（毫秒，0–5000）"));form.addView(label);
        EditText delay=new EditText(activity);delay.setSingleLine(true);delay.setInputType(InputType.TYPE_CLASS_NUMBER);
        delay.setId(android.view.View.generateViewId());label.setLabelFor(delay.getId());
        String defaultDelay=Integer.toString(phigros?GameTiming.PHIGROS_DELAY_MS:GameTiming.PHIRA_DELAY_MS);
        delay.setText(GameTiming.read(prefs,phigros,"phiraRestartDelay",defaultDelay));form.addView(delay);
        CheckBox includeOffset=new CheckBox(activity);
        if(!phigros){
            includeOffset.setText(UiDesign.words("计入谱面自带偏移","Apply the chart’s timing offset"));
            includeOffset.setChecked(prefs.flag(GameTiming.INCLUDE_PHIRA_CHART_OFFSET,false));
            form.addView(includeOffset);
        }
        TextView help=new TextView(activity);help.setText(tr("点击偏早时增大，偏晚时减小。各游戏分别保存。"));form.addView(help);
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        AlertDialog dialog=UiDesign.dialog(activity,phigros?tr("Phigros · 重试校准"):tr("Phira / Pro · 重试校准")).setView(scroll)
                .setNegativeButton(tr("取消"),null).setPositiveButton(tr("保存"),null)
                .setNeutralButton(UiDesign.words("恢复默认","Reset"),null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                delay.setText(defaultDelay);
                delay.setError(null);
                includeOffset.setChecked(false);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(RootPanel.blocked(activity))return;
            try{
                int value=Integer.parseInt(delay.getText().toString().trim());
                if(value<0||value>5000)throw new NumberFormatException();
                prefs.save(GameTiming.key(phigros,"phiraRestartDelay"),Integer.toString(value));
                if(!phigros)prefs.saveFlag(GameTiming.INCLUDE_PHIRA_CHART_OFFSET,includeOffset.isChecked());
                dialog.dismiss();
            }
            catch(NumberFormatException error){delay.setError(tr("请输入 0–5000 毫秒"));}
        });});UiDesign.show(dialog);
    }
}
