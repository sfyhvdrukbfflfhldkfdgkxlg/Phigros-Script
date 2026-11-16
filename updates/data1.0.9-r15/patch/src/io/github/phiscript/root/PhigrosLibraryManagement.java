package io.github.phiscript.root;

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
        UiDesign.show(UiDesign.dialog(a,"管理 Phigros 本机谱面").setItems(new String[]{"删除本地导入谱面","删除已下载谱面缓存"},(d,i)->{if(i==0)local(a);else ExternalCharts.cacheSettings(a);}).setNegativeButton("关闭",null).create());
    }
    private static void local(Activity a){
        if(RootPanel.blocked(a))return;
        try{
            List<PhiraEntry> entries=PhiraLibraryDeletion.list(a,true);
            if(entries.isEmpty()){RootPanel.message(a,"本地导入谱面","没有可删除的 Phigros 本地导入谱面。");return;}
            String[] titles=new String[entries.size()];for(int i=0;i<titles.length;i++)titles[i]=entries.get(i).getTitle()+" / "+entries.get(i).getLevel();
            UiDesign.show(UiDesign.dialog(a,"删除 Phigros 本地导入谱面").setItems(titles,(d,i)->confirm(a,entries.get(i))).setNegativeButton("取消",null).create());
        }catch(Exception e){RootPanel.message(a,"无法读取本地谱库",String.valueOf(e.getMessage()));}
    }
    private static void confirm(Activity a,PhiraEntry entry){
        if(RootPanel.blocked(a))return;
        try{
            PhiraLibraryDeletion.Plan plan=PhiraLibraryDeletion.prepare(a,entry.getId(),true);
            UiDesign.show(UiDesign.dialog(a,"删除此 Phigros 本地谱面？").setMessage(plan.title+" / "+plan.level+"\n\n移除本应用导入的副本。原始文件、Phira 谱库和游戏安装文件会保留。")
                .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
                    if(RootPanel.blocked(a))return;
                    try{PhiraLibraryDeletion.Result result=PhiraLibraryDeletion.commit(a,plan,true);HomeScreen.refresh(a);RootPanel.message(a,"本地谱面已删除","剩余 "+result.remaining+" 个本地谱面。"+(result.fileDeleted?"":"部分副本文件清理失败，请重试。"));}
                    catch(Exception e){HomeScreen.refresh(a);RootPanel.message(a,"删除未完成",String.valueOf(e.getMessage()));}
                }).create());
        }catch(Exception e){RootPanel.message(a,"无法删除谱面",String.valueOf(e.getMessage()));}
    }
}
