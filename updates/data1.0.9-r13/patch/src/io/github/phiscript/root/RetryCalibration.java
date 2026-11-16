package io.github.phiscript.root;
import android.app.*;import android.widget.*;import android.text.InputType;
import io.github.phiscript.AppSettings;import io.github.phiscript.capture.PhigrosRetryClock;
public final class RetryCalibration {
    private RetryCalibration(){}
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        new AlertDialog.Builder(activity).setTitle("重试校准").setItems(new String[]{"Phigros","Phira / Phira Pro"},(d,i)->edit(activity,i==0)).setNegativeButton("取消",null).show();
    }
    private static void edit(Activity activity,boolean phigros){
        AppSettings prefs=new AppSettings(activity);LinearLayout form=new LinearLayout(activity);form.setOrientation(1);
        int pad=Math.round(20*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);
        TextView label=new TextView(activity);label.setText("重试起始延迟（毫秒，0–5000）");form.addView(label);
        EditText delay=new EditText(activity);delay.setSingleLine(true);delay.setInputType(InputType.TYPE_CLASS_NUMBER);
        delay.setText(io.github.phiscript.GameTiming.read(prefs,phigros,"phiraRestartDelay","700"));form.addView(delay);
        TextView help=new TextView(activity);help.setText("点击偏早时增大，偏晚时减小。各游戏分别保存。");form.addView(help);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(phigros?"Phigros · 重试校准":"Phira / Pro · 重试校准").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(RootPanel.blocked(activity))return;
            try{int value=Integer.parseInt(delay.getText().toString().trim());if(value<0||value>5000)throw new NumberFormatException();prefs.save(io.github.phiscript.GameTiming.key(phigros,"phiraRestartDelay"),Integer.toString(value));dialog.dismiss();}
            catch(NumberFormatException error){delay.setError("请输入 0–5000 毫秒");}
        }));dialog.show();
    }
}
