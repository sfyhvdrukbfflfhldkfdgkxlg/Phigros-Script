package io.github.phiscript.root;
import android.app.Activity;import android.app.AlertDialog;import android.widget.EditText;
import io.github.phiscript.assets.FirstNoteCatalog;import io.github.phiscript.i18n.UiText;
import java.util.*;

public final class FirstNoteBrowser {
    private FirstNoteBrowser(){}
    private static String ms(long us){return String.format(Locale.ROOT,"%.3f",us/1000.0);}
    public static void open(Activity activity){
        if(RootPanel.blocked(activity))return;
        try{
            FirstNoteCatalog catalog=FirstNoteCatalog.get(activity);EditText query=new EditText(activity);query.setSingleLine(true);query.setHint(UiText.text("歌曲 ID 或难度，留空列出全部"));
            new AlertDialog.Builder(activity).setTitle(UiText.text("内置首键时间基准")).setView(query)
                .setNegativeButton(UiText.text("取消"),null).setPositiveButton(UiText.text("查找"),(d,w)->{
                    String filter=query.getText().toString().trim().toLowerCase(Locale.ROOT);List<FirstNoteCatalog.Entry> rows=new ArrayList<>();
                    for(FirstNoteCatalog.Entry row:catalog.entries())if((row.songId+" "+row.difficulty).toLowerCase(Locale.ROOT).contains(filter))rows.add(row);
                    if(rows.isEmpty()){RootPanel.message(activity,"没有匹配谱面","请换一个歌曲 ID 或清空筛选。");return;}
                    String[] names=new String[rows.size()];for(int i=0;i<names.length;i++){FirstNoteCatalog.Entry row=rows.get(i);names[i]=row.songId+" / "+row.difficulty+" · "+ms(row.firstMusicUs)+" ms";}
                    new AlertDialog.Builder(activity).setTitle(UiText.text("选择首键时间记录")).setItems(names,(dialog,index)->{
                        FirstNoteCatalog.Entry row=rows.get(index);
                        String text="歌曲 ID："+row.songId+"\n难度："+row.difficulty+"\n首键（谱面零点）："+ms(row.firstChartUs)+" ms\n谱面 offset："+ms(row.offsetUs)+" ms\n首键（音乐零点）："+ms(row.firstMusicUs)+" ms\n音符数量："+row.notes+"\n网络快照版本："+catalog.gameVersion+"\n\n"+
                            "首键时间以音乐零点为参照。导入时按实际谱面生成每个按键的时间表，版本有差异时使用导入数据。开局零点和设备延迟仍需校准。";
                        RootPanel.message(activity,"首键时间记录",text);
                    }).setNegativeButton(UiText.text("关闭"),null).show();
                }).show();
        }catch(Exception e){RootPanel.message(activity,"首键基准未读取",String.valueOf(e.getMessage()));}
    }
}
