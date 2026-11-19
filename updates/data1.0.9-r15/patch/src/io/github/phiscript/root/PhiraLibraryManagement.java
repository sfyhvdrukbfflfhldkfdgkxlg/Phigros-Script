package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.app.Activity;
import android.app.AlertDialog;
import io.github.phiscript.assets.PhiraEntry;
import io.github.phiscript.assets.PhiraLibraryDeletion;
import java.util.List;

public final class PhiraLibraryManagement {
    private PhiraLibraryManagement(){}
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        try{
            List<PhiraEntry> entries=PhiraLibraryDeletion.list(activity);
            if(entries.isEmpty()){RootPanel.message(activity,tr("本机导入谱面"),tr("当前没有可删除的 Phira / Phira Pro 本机导入谱面。"));return;}
            String[] labels=new String[entries.size()];for(int i=0;i<labels.length;i++)labels[i]=entries.get(i).getTitle()+" / "+entries.get(i).getLevel();
            UiDesign.show(UiDesign.dialog(activity,UiDesign.words("删除本机导入谱面（Phira / Pro）","Delete imported Phira / Pro charts")).setItems(labels,(dialog,index)->confirm(activity,entries.get(index))).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).create());
        }catch(Exception error){RootPanel.message(activity,tr("无法读取本机谱库"),String.valueOf(error.getMessage()));}
    }
    private static void confirm(Activity activity,PhiraEntry entry){
        if(RootPanel.blocked(activity))return;
        try{
            PhiraLibraryDeletion.Plan plan=PhiraLibraryDeletion.prepare(activity,entry.getId());
            AlertDialog confirmation=UiDesign.show(UiDesign.dialog(activity,UiDesign.words("删除此本机导入谱面？","Delete this imported chart?")).setMessage(plan.title+" / "+plan.level+"\n\n"+UiDesign.words("将移除本应用保存的谱面和选中状态。你选择过的原始 PEZ / ZIP、Phira 和 Phira Pro 游戏内的谱面会保留。","Removes this app's saved chart and selection. Your original PEZ / ZIP and charts inside Phira / Phira Pro are kept."))
                .setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).setPositiveButton(io.github.phiscript.i18n.UiText.text("删除"),(dialog,which)->{
                    if(RootPanel.blocked(activity))return;
                    try{
                        PhiraLibraryDeletion.Result result=PhiraLibraryDeletion.commit(activity,plan,true);HomeScreen.refresh(activity);
                        RootPanel.message(activity,tr("本机谱面已删除"),plan.title+" / "+plan.level+tr("\n剩余 ")+result.remaining+tr(" 个本机谱面。")+(result.fileDeleted?"":tr("\n索引已移除，但文件清理失败，请检查应用存储权限。")));
                    }catch(Exception error){HomeScreen.refresh(activity);RootPanel.message(activity,tr("删除未完成"),String.valueOf(error.getMessage()));}
                }).create());
            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(UiDesign.DANGER);
        }catch(Exception error){RootPanel.message(activity,tr("无法删除谱面"),String.valueOf(error.getMessage()));}
    }
}
