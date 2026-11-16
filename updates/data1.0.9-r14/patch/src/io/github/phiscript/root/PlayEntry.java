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
            "Phigros · 自动读谱","Phira · 谱面演奏","Phigros · 纯视觉","Phigros · 手选谱面","Phira Pro · 谱面演奏"
        }),(dialog,index)->select(a,index)).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).show();
    }
    public static void select(Activity a,int index){
        if(index==1||index==4){GameLaunch.phira(a,index==4,false);return;}
        ModeRouting.select(new AppSettings(a),index);HomeScreen.refresh(a);
    }
    public static String label(Activity a){return ModeRouting.label(new AppSettings(a));}

    public static void start(Activity a,boolean preview){
        if(!io.github.phiscript.assets.ExternalCharts.ensureSelected(a))return;
        if(RootPanel.blocked(a))return;
        AppSettings prefs=new AppSettings(a);
        if(ModeRouting.kind(prefs)==ModeRouting.Kind.PHIGROS_AUTO){AutoEntry.start(a,preview);return;}
        if(!preview&&ModeRouting.kind(prefs)==ModeRouting.Kind.PHIGROS_MANUAL){RootPanel.choose(a);return;}
        AutoEntry.launch(a,preview);
    }
    public static void phigros(Activity a){AutoEntry.start(a);}
    public static boolean intercept(Activity a,boolean preview){
        if(!io.github.phiscript.assets.ExternalCharts.ensureSelected(a))return true;
        AppSettings prefs=new AppSettings(a);
        if(ModeRouting.needsPhigros(prefs)&&RuntimeState.INSTANCE.getLibrary()==null){RootPanel.message(a,"缺少谱库","请先导入 Phigros 谱库再开启自动检测。");return true;}
        if(!preview&&ModeRouting.kind(prefs)==ModeRouting.Kind.PHIGROS_MANUAL&&!io.github.phiscript.assets.SessionChartSource.hasManual(a,RuntimeState.INSTANCE.getLibrary())){RootPanel.choose(a);return true;}
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
    public static PhiraLibrary importPhiraOne(Context c,Uri file){
        try(PhiraImportControl.Session session=PhiraImportControl.begin()){
            PhiraImportControl.check();
            // A cancellation after the atomic commit must not hide this saved chart
            // from the caller. The next record checks cancellation before decoding.
            return PhiraLibrary.Companion.importFiles(c,java.util.Collections.singletonList(file),
                text->{RuntimeState.INSTANCE.log(text);return Unit.INSTANCE;});
        }
    }
}
