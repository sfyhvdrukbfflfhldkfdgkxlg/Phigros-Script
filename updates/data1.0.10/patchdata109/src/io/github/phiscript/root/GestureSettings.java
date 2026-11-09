package io.github.phiscript.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import io.github.phiscript.AppSettings;
import io.github.phiscript.input.GestureProfile;

public final class GestureSettings {
    private GestureSettings(){}
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;AppSettings prefs=new AppSettings(activity);
        LinearLayout form=new LinearLayout(activity);form.setOrientation(1);int pad=Math.round(activity.getResources().getDisplayMetrics().density*20);form.setPadding(pad,0,pad,0);
        TextView info=new TextView(activity);info.setText(io.github.phiscript.i18n.UiText.text("所有黄键均使用持续接触或滑动，关闭连接选项也不会改成短点按。连续黄键沿实际落点连接。极密集黄键段会尝试 8 指持续占位，先核对判定范围与触点容量；无法覆盖时保留逐轨滑动，不会丢弃音符。\n\n蓝键和长按头保留新的按下，连续红键保持往返滑动。8 指占位只用于谱面演奏；视觉模式仍根据已识别位置持续接住黄键。Root 与无障碍共用规划。"));form.addView(info);
        CheckBox enabled=new CheckBox(activity);enabled.setText(io.github.phiscript.i18n.UiText.text("为连续拖键单独规划连接手势"));enabled.setChecked(prefs.flag("gesture_connect_drags",true));form.addView(enabled);
        CheckBox catches=new CheckBox(activity);catches.setText(io.github.phiscript.i18n.UiText.text("黄键持续接住，优先复用已有长键触点"));catches.setChecked(prefs.flag("gesture_catch_drags",true));form.addView(catches);
        CheckBox flicks=new CheckBox(activity);flicks.setText(io.github.phiscript.i18n.UiText.text("连续红键保持触点，来回滑动"));flicks.setChecked(prefs.flag("gesture_connect_flicks",true));form.addView(flicks);
        CheckBox combos=new CheckBox(activity);combos.setText(io.github.phiscript.i18n.UiText.text("蓝键后接红键或黄键：点按后滑动"));combos.setChecked(prefs.flag("gesture_combine_shots",true));form.addView(combos);
        CheckBox inward=new CheckBox(activity);inward.setText(io.github.phiscript.i18n.UiText.text("红键朝屏幕内侧滑动"));inward.setChecked(prefs.flag("gesture_flick_inward",true));form.addView(inward);
        EditText gap=field(activity,form,"相邻音符最大间隔（20～300 ms）",prefs.text("gesture_link_gap_ms","70"),InputType.TYPE_CLASS_NUMBER);
        EditText distance=field(activity,form,"最大距离 / 玩法高度（0.01～0.50）",prefs.text("gesture_link_distance","0.12"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox edges=new CheckBox(activity);edges.setText(io.github.phiscript.i18n.UiText.text("避开通知栏、导航条与返回手势区域"));edges.setChecked(prefs.flag("gesture_edge_guard",true));form.addView(edges);
        TextView edgeInfo=new TextView(activity);edgeInfo.setText(io.github.phiscript.i18n.UiText.text("按系统边缘范围和以下最小留边共同避让，划键朝可用区域移动。适用于 Root / 无障碍、谱面 / 视觉演奏。边缘落点移入后可能影响判定；暂停和重试按钮仍按原位置点击。"));form.addView(edgeInfo);
        EditText side=field(activity,form,"左右最小留边（0～96 dp）",prefs.text("gesture_edge_side_dp","20"),InputType.TYPE_CLASS_NUMBER);
        EditText top=field(activity,form,"顶部最小留边（0～96 dp）",prefs.text("gesture_edge_top_dp","24"),InputType.TYPE_CLASS_NUMBER);
        EditText bottom=field(activity,form,"底部最小留边（0～96 dp）",prefs.text("gesture_edge_bottom_dp","32"),InputType.TYPE_CLASS_NUMBER);
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        new AlertDialog.Builder(activity).setTitle(io.github.phiscript.i18n.UiText.text("连键手势与边缘避让")).setView(scroll).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).setPositiveButton(io.github.phiscript.i18n.UiText.text("保存"),(d,w)->{
            try{GestureProfile profile=new GestureProfile(enabled.isChecked(),Integer.parseInt(gap.getText().toString().trim()),Float.parseFloat(distance.getText().toString().trim()),catches.isChecked(),flicks.isChecked(),combos.isChecked());int sideValue=margin(side),topValue=margin(top),bottomValue=margin(bottom);prefs.saveFlag("gesture_connect_flicks",profile.connectFlicks);prefs.saveFlag("gesture_combine_shots",profile.combineShots);prefs.saveFlag("gesture_connect_drags",profile.connectDrags);prefs.saveFlag("gesture_catch_drags",profile.catchDrags);prefs.saveFlag("gesture_flick_inward",inward.isChecked());prefs.save("gesture_link_gap_ms",Integer.toString(profile.gapMs));prefs.save("gesture_link_distance",Float.toString(profile.distance));prefs.saveFlag("gesture_edge_guard",edges.isChecked());prefs.save("gesture_edge_side_dp",Integer.toString(sideValue));prefs.save("gesture_edge_top_dp",Integer.toString(topValue));prefs.save("gesture_edge_bottom_dp",Integer.toString(bottomValue));}
            catch(Exception e){RootPanel.message(activity,"参数无效",e.getMessage());}
        }).show();
    }
    private static int margin(EditText edit){int value=Integer.parseInt(edit.getText().toString().trim());if(value<0||value>96)throw new IllegalArgumentException("留边应为 0～96 dp");return value;}
    private static EditText field(Activity a,LinearLayout form,String title,String value,int type){TextView label=new TextView(a);label.setText(io.github.phiscript.i18n.UiText.text(title));form.addView(label);EditText edit=new EditText(a);edit.setSingleLine(true);edit.setInputType(type);edit.setText(io.github.phiscript.i18n.UiText.text(value));form.addView(edit);return edit;}
}
