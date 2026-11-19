package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.app.Activity;
import android.content.Intent;
import io.github.phiscript.AppSettings;
import io.github.phiscript.GamePackages;

public final class GameLaunch {
    private GameLaunch() {}
    static void phira(Activity activity,boolean pro,boolean launch){
        if(RootPanel.blocked(activity))return;
        String pkg=pro?GamePackages.PHIRA_PRO:GamePackages.PHIRA;
        Intent intent=activity.getPackageManager().getLaunchIntentForPackage(pkg);
        if(intent==null){RootPanel.message(activity,tr("游戏未安装"),pro?tr("找不到 Phira Pro 启动入口"):tr("找不到 Phira 启动入口"));return;}
        try{
            if(launch)activity.startActivity(intent);
            AppSettings prefs=new AppSettings(activity);
            prefs.save("phira_package",pkg);GamePackages.init(activity);ModeRouting.select(prefs,1);HomeScreen.refresh(activity);
        }catch(RuntimeException e){RootPanel.message(activity,tr("启动失败"),e.getMessage());}
    }
}
