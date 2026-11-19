package io.github.phiscript.root;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import io.github.phiscript.*;
import io.github.phiscript.assets.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OnlinePhiraLibraryUi {
    private static final AtomicBoolean busy=new AtomicBoolean();
    private interface Work {void run()throws Exception;}
    private OnlinePhiraLibraryUi(){}
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        boolean enabled=new AppSettings(activity).flag("phira_online_library",false);
        UiDesign.show(UiDesign.dialog(activity,UiDesign.words("Phira 在线谱库","Phira online library")).setItems(new String[]{UiDesign.words("按谱面编号导入（如 8825）","Import by chart ID (e.g. 8825)"),UiDesign.words("按曲名搜索并选择导入","Search by song title"),enabled?UiDesign.words("关闭按识别结果联网导入","Disable recognition-based imports"):UiDesign.words("启用按识别结果联网导入","Enable recognition-based imports")},(d,index)->{
            if(index==0)input(activity,true);else if(index==1)input(activity,false);else{
                AppSettings prefs=new AppSettings(activity);prefs.saveFlag("phira_online_library",!enabled);
                if(!enabled){prefs.save("phiraChartId","");prefs.save("mode","PHIRA");}
                HomeScreen.refresh(activity);RootPanel.message(activity,tr("Phira 在线识别"),enabled?tr("已关闭联网识别导入，现有本地谱面仍可演奏。"):tr("已启用。切到 Phira 或 Phira Pro 后开始打谱，识别到唯一曲名和难度后，会下载并保存谱面，再进入原确认与重试演奏流程。\n\n有同名同难度谱面时不会猜测，请使用编号导入。在线服务不可用时可先导入本地 PEZ。"));
            }
        }).setNegativeButton(tr("取消"),null).create());
    }
    private static void input(Activity activity,boolean byId){
        EditText field=new EditText(activity);field.setSingleLine();field.setHint(byId?"8825":UiDesign.words("曲名","Song title"));if(byId)field.setInputType(InputType.TYPE_CLASS_NUMBER);
        UiDesign.show(UiDesign.dialog(activity,byId?UiDesign.words("按 Phira 编号导入","Import by Phira ID"):UiDesign.words("搜索 Phira 在线谱库","Search Phira charts")).setView(UiDesign.inputBox(activity,byId?UiDesign.words("输入游戏中显示的谱面编号。","Enter the chart ID shown in the game."):UiDesign.words("输入部分曲名，再从结果中选择。","Enter part of a title, then choose from the results."),field)).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).setPositiveButton(io.github.phiscript.i18n.UiText.text("查询"),(d,w)->{
            String query=field.getText().toString();background(activity,()->{
                OnlinePhiraLibrary api=new OnlinePhiraLibrary();List<OnlinePhiraLibrary.Entry> entries;
                if(byId)entries=Collections.singletonList(api.fetch(OnlinePhiraLibrary.id(query),new AtomicBoolean()));else entries=api.search(query,new AtomicBoolean()).entries;
                activity.runOnUiThread(()->choose(activity,entries));
            });
        }).create());
    }
    private static void choose(Activity activity,List<OnlinePhiraLibrary.Entry> entries){
        if(!RootPanel.alive(activity))return;if(entries.isEmpty()){RootPanel.message(activity,tr("未找到谱面"),tr("请核对曲名，或使用 Phira 中显示的谱面编号。"));return;}
        String[] labels=new String[entries.size()];for(int i=0;i<labels.length;i++)labels[i]=entries.get(i).display();
        UiDesign.show(UiDesign.dialog(activity, "").setTitle(tr("选择要导入的谱面")).setItems(labels,(d,index)->{
            if(RootPanel.blocked(activity))return;OnlinePhiraLibrary.Entry chosen=entries.get(index);
            background(activity,()->{
                PhiraEntry entry=new OnlinePhiraLibrary().importEntry(activity,chosen,new AtomicBoolean());
                // Existing manual-selection flow can immediately play this exact imported chart.
                AppSettings prefs=new AppSettings(activity);prefs.save("mode","PHIRA");prefs.save("phiraChartId",entry.getId());prefs.saveFlag("phira_online_library",false);
                activity.runOnUiThread(()->{if(RootPanel.alive(activity)){HomeScreen.refresh(activity);RootPanel.message(activity,tr("Phira 谱面已导入"),chosen.display()+tr("\n\n已选中本地谱面。请在 Phira 打开同一编号，再开始打谱。可随时启用按识别结果联网导入。"));}});
            });
        }).setNegativeButton(tr("取消"),null).create());
    }
    private static void background(Activity activity,Work work){
        if(!busy.compareAndSet(false,true)){RootPanel.message(activity,tr("正在处理"),tr("请等待当前在线查询或导入结束。"));return;}
        RuntimeState.INSTANCE.log(tr("正在查询或导入 Phira 在线谱库…"));
        new Thread(()->{try{work.run();}catch(Exception e){RuntimeState.INSTANCE.log(tr("Phira 在线谱库：")+e.getMessage());activity.runOnUiThread(()->{if(RootPanel.alive(activity))RootPanel.message(activity,tr("Phira 在线谱库"),String.valueOf(e.getMessage()));});}finally{busy.set(false);}},"phi-online-library").start();
    }
}
