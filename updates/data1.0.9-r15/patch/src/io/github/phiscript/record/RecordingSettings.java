package io.github.phiscript.record;

import static io.github.phiscript.root.UiDesign.words;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.Toast;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.i18n.UiText;
import io.github.phiscript.root.HomeScreen;
import io.github.phiscript.root.RootPanel;
import io.github.phiscript.root.UiDesign;

public final class RecordingSettings {
    private static final int AUDIO_REQUEST=0x4a71;
    private RecordingSettings(){}

    private static boolean granted(Context context){
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
    }
    public static String audioLabel(Context context){
        if(Build.VERSION.SDK_INT<29 || !RecordingPreferences.gameAudioEnabled(context))
            return words("静音","Silent");
        return granted(context)?words("游戏声音","Game audio"):words("游戏声音需授权","Game audio permission needed");
    }
    public static void show(Activity activity){
        if(blocked(activity))return;
        RecordingPolicy.Profile current=RecordingPreferences.load(activity);
        ScrollView scroll=new ScrollView(activity);
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad=UiDesign.dp(activity,20);
        content.setPadding(pad,UiDesign.dp(activity,8),pad,UiDesign.dp(activity,12));
        scroll.addView(content);
        content.addView(UiDesign.text(activity,words("画质","Video quality"),14,UiDesign.MUTED,true));
        RadioGroup profiles=new RadioGroup(activity);
        content.addView(profiles);
        for(int edge:new int[]{720,1080})for(int fps:new int[]{30,60}){
            RadioButton option=new RadioButton(activity);
            option.setId(View.generateViewId());
            option.setText(edge+"p  ·  "+fps+" fps");
            option.setTextColor(UiDesign.INK);
            option.setMinHeight(UiDesign.dp(activity,48));
            option.setTag(RecordingPolicy.profile(edge,fps));
            profiles.addView(option);
            if(edge==current.shortEdge && fps==current.fps)profiles.check(option.getId());
        }
        Switch sound=new Switch(activity);
        sound.setText(words("录制游戏声音","Record game audio"));
        sound.setTextSize(16);
        sound.setTextColor(UiDesign.INK);
        sound.setMinHeight(UiDesign.dp(activity,52));
        sound.setEnabled(Build.VERSION.SDK_INT>=29);
        sound.setChecked(sound.isEnabled() && RecordingPreferences.gameAudioEnabled(activity));
        content.addView(sound);
        String help=Build.VERSION.SDK_INT<29
                ?words("此设备只能录制静音视频。游戏内录需要 Android 10 或更高版本。",
                       "This device records silent video. Game audio requires Android 10 or later.")
                :words("Android 会请求录音权限。视频可收录游戏允许捕获的声音，麦克风保持关闭。",
                       "Android requests audio permission. The video includes audio the game allows to be captured. The microphone stays off.");
        content.addView(UiDesign.text(activity,help,13,UiDesign.MUTED,false));
        UiDesign.show(UiDesign.dialog(activity,words("录屏设置","Recording settings"))
                .setView(scroll).setNegativeButton(UiText.text("取消"),null)
                .setPositiveButton(UiText.text("保存"),(dialog,which)->{
                    if(blocked(activity))return;
                    RadioButton selected=profiles.findViewById(profiles.getCheckedRadioButtonId());
                    RecordingPreferences.save(activity,(RecordingPolicy.Profile)selected.getTag());
                    boolean enable=sound.isChecked();
                    RecordingPreferences.setGameAudio(activity,enable);
                    HomeScreen.refresh(activity);
                    if(enable && !granted(activity)){
                        activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_REQUEST);
                    }else saved(activity);
                }).create());
    }
    public static boolean onPermissionResult(Activity activity,int code){
        if(code!=AUDIO_REQUEST)return false;
        HomeScreen.refresh(activity);
        if(granted(activity))saved(activity);
        else if(!activity.isFinishing()&&!activity.isDestroyed()){
            UiDesign.show(UiDesign.dialog(activity,words("游戏声音未授权","Game audio permission needed"))
                    .setMessage(words("本次可以继续录制静音视频。要录下游戏声音，请在系统设置中允许录音。",
                            "You can still record silent video. To include game audio, allow audio recording in the app’s system settings."))
                    .setNegativeButton(words("关闭","Close"),null)
                    .setPositiveButton(words("系统设置","System settings"),(dialog,which)->activity.startActivity(
                            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.getPackageName())))).create());
        }
        return true;
    }
    private static void saved(Activity activity){
        Toast.makeText(activity,RecordingPreferences.load(activity).label()+" · "+audioLabel(activity),Toast.LENGTH_SHORT).show();
    }
    private static boolean blocked(Activity activity){
        if(!RuntimeState.INSTANCE.getRunning().get()&&!RootPanel.isBusy()&&!ManualRecordingService.ready())return false;
        Toast.makeText(activity,UiText.text("请先停止当前演奏或录屏，再修改录屏档位"),Toast.LENGTH_LONG).show();return true;
    }
}
