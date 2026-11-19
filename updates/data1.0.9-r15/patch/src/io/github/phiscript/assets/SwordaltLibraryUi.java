package io.github.phiscript.assets;
import android.app.*;import android.content.*;import android.net.Uri;import android.widget.*;
import io.github.phiscript.RuntimeState;import io.github.phiscript.root.RootPanel;import io.github.phiscript.root.UiDesign;
import org.json.*;import java.io.*;import java.util.*;

/** Separate original-chart source with explicit single-chart and offline-package imports. */
public final class SwordaltLibraryUi {
    private static final int PICK_PACK=42372;
    private SwordaltLibraryUi(){}
    public static void open(Activity a){
        if(RootPanel.blocked(a))return;
        try{JSONObject catalog=SwordaltSource.saved(a);JSONArray entries=catalog.getJSONArray("entries");int noise=0,visible=0;for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(e.optInt("noiseCount",0)>0)noise++;if(e.optBoolean("siteVisible",true))visible++;}
            String headingText=String.format(Locale.ROOT,UiDesign.words(
                "Swordalt 谱库与噪域\n版本 %s · %d 张（网站列出 %d 张）\n已知含噪域 %d 张",
                "Swordalt charts and noise areas\nVersion %s · %d charts (%d listed online)\n%d charts with known noise areas"),catalog.optString("version"),entries.length(),visible,noise);
            TextView heading=UiDesign.text(a,headingText,16,UiDesign.INK,true);int pad=UiDesign.dp(a,24);heading.setPadding(pad,pad,pad,UiDesign.dp(a,12));
            UiDesign.show(UiDesign.dialog(a, "").setCustomTitle(heading)
                .setItems(new String[]{
                    UiDesign.words("启用 Swordalt 谱库","Use the Swordalt library"),
                    UiDesign.words("选择谱面 · 下载并准备触点","Choose, download and prepare a chart"),
                    UiDesign.words("更新联网目录（原谱）","Update online chart catalog"),
                    UiDesign.words("导入离线 ZIP · 按曲准备触点","Import an offline ZIP and prepare charts individually")
                },(d,index)->{
                    if(index==0)ExternalCharts.useSwordalt(a);
                    else if(index==1)search(a,catalog);
                    else if(index==2)ExternalCharts.swordaltWork(a,UiDesign.words("更新 Swordalt 目录","Update Swordalt catalog"),cancel->ExternalCharts.fromSwordaltCatalog(a,SwordaltSource.refresh(a,cancel)));
                    else{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);a.startActivityForResult(pick,PICK_PACK);}
                }).setNegativeButton(UiDesign.words("关闭","Close"),null).create());
        }catch(Exception e){ExternalCharts.message(a,UiDesign.words("原谱目录不可用","Chart catalog unavailable"),e.getMessage());}
    }
    private static void search(Activity a,JSONObject catalog){
        EditText input=new EditText(a);input.setSingleLine(true);input.setHint(UiDesign.words("曲名 / 曲师","Song title / Composer"));
        UiDesign.show(UiDesign.dialog(a,UiDesign.words("查找要下载的谱面","Find charts to download")).setView(UiDesign.inputBox(a,UiDesign.words("输入关键词，或留空查看全部。","Enter a keyword, or leave empty to show all charts."),input)).setPositiveButton(io.github.phiscript.i18n.UiText.text("查找"),(d,w)->choose(a,catalog,input.getText().toString().trim())).setNegativeButton(io.github.phiscript.i18n.UiText.text("取消"),null).create());
    }
    private static void choose(Activity a,JSONObject catalog,String query){
        try{List<JSONObject> matches=new ArrayList<>();List<String> labels=new ArrayList<>();JSONArray list=catalog.getJSONArray("entries");String needle=query.toLowerCase(Locale.ROOT);
            for(int i=0;i<list.length();i++){JSONObject e=list.getJSONObject(i);String text=e.getString("title")+" / "+e.getString("difficulty")+" ["+e.optString("level","?")+"] · "+e.optString("composer");
                if(!needle.isEmpty()&&!text.toLowerCase(Locale.ROOT).contains(needle)&&!e.getString("path").toLowerCase(Locale.ROOT).contains(needle))continue;
                int count=e.optInt("noiseCount",-1);text+=count>0?UiDesign.words(" · 噪域 "," · Noise areas: ")+count:count==0?"":UiDesign.words(" · 噪域待读取"," · Noise areas unknown");if(!e.optBoolean("siteVisible",true))text+=UiDesign.words(" · 额外变体"," · Alternate chart");matches.add(e);labels.add(text);
            }
            if(matches.isEmpty()){ExternalCharts.message(a,UiDesign.words("没有匹配曲目","No matching charts"),UiDesign.words("请使用部分曲名或曲师重新查找。","Try part of the song title or composer name."));return;}
            String title=String.format(Locale.ROOT,UiDesign.words("选择谱面（%d）","Choose a chart (%d)"),matches.size());
            UiDesign.show(UiDesign.dialog(a,title).setItems(labels.toArray(new String[0]),(d,index)->{
                JSONObject selected=matches.get(index);ExternalCharts.swordaltWork(a,UiDesign.words("下载并准备谱面","Download and prepare chart"),cancel->{SwordaltSource.loadChart(a,catalog,selected,cancel);SwordaltSource.saveCatalog(a,catalog);return ExternalCharts.fromSwordaltCatalog(a,catalog);});
            }).setNegativeButton(UiDesign.words("取消","Cancel"),null).create());
        }catch(Exception e){ExternalCharts.message(a,UiDesign.words("无法选取谱面","Unable to select chart"),e.getMessage());}
    }
    public static boolean result(Activity a,int request,int result,Intent data){
        if(request!=PICK_PACK)return false;if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;Uri uri=data.getData();
        ExternalCharts.swordaltWork(a,UiDesign.words("导入 Swordalt 离线谱库","Import Swordalt offline library"),cancel->{InputStream stream=a.getContentResolver().openInputStream(uri);if(stream==null)throw new IOException(UiDesign.words("无法读取离线包","Unable to read the offline ZIP"));ChartLibrary library=ExternalCharts.fromSwordaltCatalog(a,SwordaltSource.importPack(a,stream,cancel));RuntimeState.INSTANCE.log(UiDesign.words("离线谱面已保存。请在预计算触点中选择要演奏的曲目。","Offline charts saved. Use Prepare touches for the songs you plan to play."));return library;});return true;
    }
}
