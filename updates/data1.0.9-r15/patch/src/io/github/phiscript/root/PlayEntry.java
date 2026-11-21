package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.app.Activity;
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
        UiDesign.show(UiDesign.dialog(a,tr("演奏模式")).setItems(io.github.phiscript.i18n.UiText.text(new String[]{
            tr("Phigros · 自动读谱"),tr("Phira · 谱面演奏"),tr("Phigros · 纯视觉"),tr("Phigros · 手选谱面"),tr("Phira Pro · 谱面演奏"),tr("Phigros · 课题连续校准（三首）")
        }),(dialog,index)->select(a,index)).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).create());
    }
    public static void select(Activity a,int index){
        if(index==1||index==4){GameLaunch.phira(a,index==4,false);return;}
        ModeRouting.select(new AppSettings(a),index);HomeScreen.refresh(a);
        if(index==5)RootPanel.message(a,tr("课题连续校准"),UiDesign.words(
            "请先在曲库准备三首课题谱面的触点。进入游戏课题选曲界面后启动打谱，等待通道就绪，再开始第一首。每首会重新识别曲名、难度并校准。",
            "Prepare touches for all three course charts in the library. Open the course selection screen, start playback in the app and wait for input to be ready before starting the first song. The app recognizes and calibrates each song separately."));
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
        if(ModeRouting.needsPhigros(prefs)&&RuntimeState.INSTANCE.getLibrary()==null){RootPanel.message(a,tr("缺少谱库"),tr("请先导入 Phigros 谱库再开启自动检测。"));return true;}
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
        if(library==null)throw new IllegalArgumentException(tr("没有可导入的 Phira 谱面"));
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
