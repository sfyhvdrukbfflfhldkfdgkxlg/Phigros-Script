package io.github.phiscript.root;
import android.app.*;import android.widget.*;import android.text.InputType;import io.github.phiscript.AppSettings;import io.github.phiscript.input.TapMod;
public final class TapSettings {
    public static void open(Activity a){if(RootPanel.blocked(a))return;AppSettings prefs=new AppSettings(a);LinearLayout box=new LinearLayout(a);box.setOrientation(1);box.setPadding(28,12,28,12);
        Switch enabled=new Switch(a);enabled.setText("启用点击位置调整");enabled.setChecked(Boolean.parseBoolean(prefs.text("tapmod_enabled","true")));box.addView(enabled);
        Switch noise=new Switch(a);noise.setText("避开谱面噪域");noise.setChecked(Boolean.parseBoolean(prefs.text("tap_noise_avoid","true")));box.addView(noise);
        String[] labels={"沿判定线法线偏移（屏幕高度 ‰，0–200）","随机偏移范围（屏幕高度 ±‰，0–20）","噪域避让最大距离（屏幕高度 ‰，0–2000）"};String[] keys={"tap_vertical_permille","tap_jitter_permille","tap_noise_escape_permille"},defaults={"35","6","2000"};int[] maximum={200,20,2000};EditText[] values=new EditText[3];
        for(int i=0;i<3;i++){TextView label=new TextView(a);label.setText(labels[i]);box.addView(label);EditText edit=new EditText(a);edit.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);edit.setText(prefs.text(keys[i],defaults[i]));values[i]=edit;box.addView(edit);}
        TextView help=new TextView(a);help.setText("每次演奏重新随机，同一长按保持偏移稳定。先沿判定线法线避让，再尝试同判定带两侧的安全位置。仅能处理谱面文件保留的噪域数据。");box.addView(help);ScrollView scroll=new ScrollView(a);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("噪域与随机点击").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(RootPanel.blocked(a))return;for(int i=0;i<3;i++){try{float n=Float.parseFloat(values[i].getText().toString());if(!Float.isFinite(n)||n<0||n>maximum[i])throw new NumberFormatException();}catch(Exception e){values[i].setError("请输入 0–"+maximum[i]);return;}}
            prefs.save("tapmod_enabled",Boolean.toString(enabled.isChecked()));prefs.save("tap_noise_avoid",Boolean.toString(noise.isChecked()));for(int i=0;i<3;i++)prefs.save(keys[i],values[i].getText().toString());TapMod.init(a);dialog.dismiss();}));dialog.show();
    }
}
