package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.app.Activity;
import android.app.AlertDialog;
import io.github.phiscript.assets.ExternalCharts;
import io.github.phiscript.assets.PhiraEntry;
import io.github.phiscript.assets.PhiraLibraryDeletion;
import java.util.List;

/** Phigros local imports and downloaded charts share the library-management level. */
public final class PhigrosLibraryManagement {
    private PhigrosLibraryManagement(){}
    public static void open(Activity a){
        if(RootPanel.blocked(a))return;
        UiDesign.show(UiDesign.dialog(a,tr("管理 Phigros 本机谱面")).setItems(new String[]{tr("删除本地导入谱面"),tr("删除已下载谱面缓存")},(d,i)->{if(i==0)local(a);else ExternalCharts.cacheSettings(a);}).setNegativeButton(tr("关闭"),null).create());
    }
    private static void local(Activity a){
        if(RootPanel.blocked(a))return;
        try{
            List<PhiraEntry> entries=PhiraLibraryDeletion.list(a,true);
            if(entries.isEmpty()){RootPanel.message(a,tr("本地导入谱面"),tr("没有可删除的 Phigros 本地导入谱面。"));return;}
            String[] titles=new String[entries.size()];for(int i=0;i<titles.length;i++)titles[i]=entries.get(i).getTitle()+" / "+entries.get(i).getLevel();
            UiDesign.show(UiDesign.dialog(a,tr("删除 Phigros 本地导入谱面")).setItems(titles,(d,i)->confirm(a,entries.get(i))).setNegativeButton(tr("取消"),null).create());
        }catch(Exception e){RootPanel.message(a,tr("无法读取本地谱库"),String.valueOf(e.getMessage()));}
    }
    private static void confirm(Activity a,PhiraEntry entry){
        if(RootPanel.blocked(a))return;
        try{
            PhiraLibraryDeletion.Plan plan=PhiraLibraryDeletion.prepare(a,entry.getId(),true);
            UiDesign.show(UiDesign.dialog(a,tr("删除此 Phigros 本地谱面？")).setMessage(plan.title+" / "+plan.level+tr("\n\n移除本应用导入的副本。原始文件、Phira 谱库和游戏安装文件会保留。"))
                .setNegativeButton(tr("取消"),null).setPositiveButton(tr("删除"),(d,w)->{
                    if(RootPanel.blocked(a))return;
                    try{PhiraLibraryDeletion.Result result=PhiraLibraryDeletion.commit(a,plan,true);HomeScreen.refresh(a);RootPanel.message(a,tr("本地谱面已删除"),tr("剩余 ")+result.remaining+tr(" 个本地谱面。")+(result.fileDeleted?"":tr("部分副本文件清理失败，请重试。")));}
                    catch(Exception e){HomeScreen.refresh(a);RootPanel.message(a,tr("删除未完成"),String.valueOf(e.getMessage()));}
                }).create());
        }catch(Exception e){RootPanel.message(a,tr("无法删除谱面"),String.valueOf(e.getMessage()));}
    }
}
