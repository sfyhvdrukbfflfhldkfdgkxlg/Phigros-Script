package io.github.phiscript.record;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.i18n.UiText;
import io.github.phiscript.root.RootPanel;
import io.github.phiscript.root.UiDesign;

public final class RecordingSettings {
    private RecordingSettings(){}
    public static void show(Activity activity){
        if(blocked(activity))return;
        RecordingPolicy.Profile current=RecordingPreferences.load(activity);
        int selected=(current.shortEdge==1080?2:0)+(current.fps==60?1:0);
        int[] choice={selected};
        UiDesign.show(UiDesign.dialog(activity,tr("录屏分辨率与帧率"))
            .setSingleChoiceItems(UiText.text(new String[]{tr("720p / 30 fps · 省资源"),tr("720p / 60 fps · 流畅"),tr("1080p / 30 fps · 清晰"),tr("1080p / 60 fps · 清晰流畅")}),selected,(dialog,index)->choice[0]=index)
            .setNegativeButton(UiText.text("取消"),null)
            .setPositiveButton(UiText.text("保存"),(dialog,which)->{
                if(blocked(activity))return;
                RecordingPolicy.Profile profile=RecordingPolicy.profile(choice[0]>=2?1080:720,choice[0]%2==1?60:30);
                RecordingPreferences.save(activity,profile);
                io.github.phiscript.root.HomeScreen.refresh(activity);
                Toast.makeText(activity,UiText.text(tr("下次录屏使用 ")+profile.label()+tr("；自动与手动录屏均生效")),Toast.LENGTH_LONG).show();
            }).create());
    }
    private static boolean blocked(Activity activity){
        if(!RuntimeState.INSTANCE.getRunning().get()&&!RootPanel.isBusy()&&!ManualRecordingService.ready())return false;
        Toast.makeText(activity,UiText.text("请先停止当前演奏或录屏，再修改录屏档位"),Toast.LENGTH_LONG).show();return true;
    }
}
