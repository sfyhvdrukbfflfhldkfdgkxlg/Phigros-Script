package io.github.phiscript.assets;
import android.app.*;import android.content.*;import android.net.Uri;import android.widget.*;
import io.github.phiscript.RuntimeState;import io.github.phiscript.root.RootPanel;
import org.json.*;import java.io.*;import java.util.*;

/** Separate original-chart source with explicit single-chart and offline-package imports. */
public final class SwordaltLibraryUi {
    private static final int PICK_PACK=42372;
    private SwordaltLibraryUi(){}
    public static void open(Activity a){
        if(RootPanel.blocked(a))return;
        try{JSONObject catalog=SwordaltSource.saved(a);JSONArray entries=catalog.getJSONArray("entries");int noise=0,visible=0;for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(e.optInt("noiseCount",0)>0)noise++;if(e.optBoolean("siteVisible",true))visible++;}
            String kind=SwordaltSource.scripts(catalog)?"脚本":"原谱";
            TextView heading=new TextView(a);heading.setText("Swordalt "+kind+"与噪域\n版本 "+catalog.optString("version")+" · "+entries.length()+" 张\n网站列出 "+visible+" 张 · 已知含噪域 "+noise+" 张");heading.setTextSize(16);int pad=Math.round(20*a.getResources().getDisplayMetrics().density);heading.setPadding(pad,pad,pad,pad);
            new AlertDialog.Builder(a).setCustomTitle(heading)
                .setItems(new String[]{"启用"+kind+"库 · 识别后按曲获取","选择谱面 · 下载导入","更新联网目录（原谱）","导入原谱 / 脚本离线 ZIP"},(d,index)->{
                    if(index==0)ExternalCharts.useSwordalt(a);
                    else if(index==1)search(a,catalog);
                    else if(index==2)ExternalCharts.swordaltWork(a,"更新 Swordalt 目录",cancel->ExternalCharts.fromSwordaltCatalog(a,SwordaltSource.refresh(a,cancel)));
                    else{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);a.startActivityForResult(pick,PICK_PACK);}
                }).setNegativeButton("关闭",null).show();
        }catch(Exception e){ExternalCharts.message(a,"原谱目录不可用",e.getMessage());}
    }
    private static void search(Activity a,JSONObject catalog){
        EditText input=new EditText(a);input.setSingleLine(true);input.setHint("曲名 / 曲师；留空显示全部");
        new AlertDialog.Builder(a).setTitle("查找要下载的谱面").setView(input).setPositiveButton("查找",(d,w)->choose(a,catalog,input.getText().toString().trim())).setNegativeButton("取消",null).show();
    }
    private static void choose(Activity a,JSONObject catalog,String query){
        try{List<JSONObject> matches=new ArrayList<>();List<String> labels=new ArrayList<>();JSONArray list=catalog.getJSONArray("entries");String needle=query.toLowerCase(Locale.ROOT);
            for(int i=0;i<list.length();i++){JSONObject e=list.getJSONObject(i);String text=e.getString("title")+" / "+e.getString("difficulty")+" ["+e.optString("level","?")+"] · "+e.optString("composer");
                if(!needle.isEmpty()&&!text.toLowerCase(Locale.ROOT).contains(needle)&&!e.getString("path").toLowerCase(Locale.ROOT).contains(needle))continue;
                int count=e.optInt("noiseCount",-1);text+=count>0?" · 噪域 "+count:count==0?"":" · 噪域待读取";if(!e.optBoolean("siteVisible",true))text+=" · 额外变体";matches.add(e);labels.add(text);
            }
            if(matches.isEmpty()){ExternalCharts.message(a,"没有匹配曲目","请使用部分曲名或曲师重新查找。");return;}
            new AlertDialog.Builder(a).setTitle("选择谱面（"+matches.size()+"）").setItems(labels.toArray(new String[0]),(d,index)->{
                JSONObject selected=matches.get(index);ExternalCharts.swordaltWork(a,"下载并导入原谱",cancel->{SwordaltSource.loadChart(a,catalog,selected,cancel);SwordaltSource.saveCatalog(a,catalog);return ExternalCharts.fromSwordaltCatalog(a,catalog);});
            }).setNegativeButton("取消",null).show();
        }catch(Exception e){ExternalCharts.message(a,"无法选取谱面",e.getMessage());}
    }
    public static boolean result(Activity a,int request,int result,Intent data){
        if(request!=PICK_PACK)return false;if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;Uri uri=data.getData();
        ExternalCharts.swordaltWork(a,"导入 Swordalt 离线谱库",cancel->{InputStream stream=a.getContentResolver().openInputStream(uri);if(stream==null)throw new IOException("无法读取离线包");return ExternalCharts.fromSwordaltCatalog(a,SwordaltSource.importPack(a,stream,cancel));});return true;
    }
}
