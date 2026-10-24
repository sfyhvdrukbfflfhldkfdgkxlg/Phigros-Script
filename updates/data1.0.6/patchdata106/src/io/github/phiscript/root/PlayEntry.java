package io.github.phiscript.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.PhiraLibrary;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;

/** Automatic Phigros chart route alongside the original visual and Phira routes. */
public final class PlayEntry {
    private PlayEntry(){}
    public static void choose(Activity a){
        if(RootPanel.blocked(a))return;
        new AlertDialog.Builder(a).setTitle(io.github.phiscript.i18n.UiText.text("演奏模式")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{
            "Phigros · 自动读谱","Phira · 谱面演奏","Phigros · 纯视觉","Phigros · 手动同步（备用）"
        }),(dialog,index)->select(a,index)).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).show();
    }
    public static void select(Activity a,int index){
        if(index<0||index>3)throw new IllegalArgumentException("演奏模式无效");
        AppSettings prefs=new AppSettings(a);prefs.save("mode",index==1?"PHIRA":"VISUAL");
        prefs.saveFlag("phigros_auto_mode",index==0);prefs.saveFlag("phigros_manual_mode",index==3);HomeScreen.refresh(a);
    }
    public static String label(Activity a){
        AppSettings prefs=new AppSettings(a);
        if("PHIRA".equals(prefs.text("mode","VISUAL")))return "Phira 谱面演奏";
        if(prefs.flag("phigros_manual_mode",false))return "Phigros 手动同步";
        return prefs.flag("phigros_auto_mode",false)?"Phigros 自动读谱":"Phigros 纯视觉";
    }
    public static void start(Activity a,boolean preview){
        if(RootPanel.blocked(a))return;
        AppSettings prefs=new AppSettings(a);
        if(!"PHIRA".equals(prefs.text("mode","VISUAL"))&&prefs.flag("phigros_auto_mode",false)){AutoEntry.start(a,preview);return;}
        if(!preview&&prefs.flag("phigros_manual_mode",false)&&!"PHIRA".equals(prefs.text("mode","VISUAL"))){RootPanel.choose(a);return;}
        AutoEntry.launch(a,preview);
    }
    public static void phigros(Activity a){AutoEntry.start(a);}
    public static boolean intercept(Activity a,boolean preview){
        AppSettings prefs=new AppSettings(a);
        if(prefs.flag("phigros_auto_mode",false)&&!"PHIRA".equals(prefs.text("mode","VISUAL"))&&RuntimeState.INSTANCE.getLibrary()==null){RootPanel.message(a,"缺少谱库","请先导入 Phigros 谱库再开启自动检测。");return true;}
        if(!preview&&prefs.flag("phigros_manual_mode",false)&&!"PHIRA".equals(prefs.text("mode","VISUAL"))){RootPanel.choose(a);return true;}
        return false;
    }
    public static PhiraLibrary importPhira(Context c,List<Uri> files){
        try(PhiraImportControl.Session session=PhiraImportControl.begin()){
        PhiraLibrary library=null;
        for(int i=0;i<files.size();i+=16){
            PhiraImportControl.check();
            library=PhiraLibrary.Companion.importFiles(c,new ArrayList<Uri>(files.subList(i,Math.min(i+16,files.size()))),text->{RuntimeState.INSTANCE.log(text);return Unit.INSTANCE;});
            PhiraImportControl.check();
        }
        if(library==null)throw new IllegalArgumentException("没有可导入的 Phira 谱面");
        return library;
        }
    }
}
