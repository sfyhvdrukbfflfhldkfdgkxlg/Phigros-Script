package io.github.phiscript.assets;
import android.app.*;import android.content.*;import android.net.Uri;import android.widget.*;
import io.github.phiscript.RuntimeState;import io.github.phiscript.root.RootPanel;import io.github.phiscript.root.UiDesign;
import org.json.*;import java.io.*;import java.util.*;

/** Separate original-chart source with explicit single-chart and offline-package imports. */
public final class SwordaltLibraryUi {
    private static final int PICK_PACK=42372;
    private static final int PICK_FEIJIPAN_PACK=42373;
    private static final int PICK_OFFLINE_TOUCHES=42374;
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
                    else pickPack(a,PICK_PACK);
                }).setNegativeButton(UiDesign.words("关闭","Close"),null).create());
        }catch(Exception e){ExternalCharts.message(a,UiDesign.words("原谱目录不可用","Chart catalog unavailable"),e.getMessage());}
    }

    public static void openFeijipan(Activity a){
        if(RootPanel.blocked(a))return;
        UiDesign.show(UiDesign.dialog(a,UiDesign.words("小飞机网盘 · 离线谱库","Feijipan · Offline chart library"))
                .setItems(new String[]{UiDesign.words("预计算触点包 · 离线演奏（推荐）","Prepared touch pack · Offline playback (recommended)"),
                        UiDesign.words("原谱 ZIP · 按曲准备触点","Original chart ZIP · Prepare touches per song")},
                        (dialog,choice)->openFeijipanPack(a,choice==0))
                .setNegativeButton(UiDesign.words("关闭","Close"),null).create());
    }

    private static void openFeijipanPack(Activity a,boolean touches){
        String shareUrl=touches?OfflineCloudPlans.SHARE_URL:FeijipanOfflinePack.SHARE_URL;
        String name=touches?OfflineCloudPlans.NAME:FeijipanOfflinePack.NAME;
        String instructions=touches?UiDesign.words(
                "打开分享页下载预计算触点 ZIP（约 413 MiB）。网盘可能要求使用官方客户端。回到此处选择 ZIP，校验安装后即可离线识别曲目、读取时间轴和触点。",
                "Open the share page to download the prepared touch ZIP (about 413 MiB). You may need the official Feijipan app. Return here to select, verify and install it. Song recognition and touch playback then work offline."):UiDesign.words(
                "打开分享页，通过小飞机网盘下载完整 ZIP（约 348 MiB）。网盘可能要求使用官方客户端。下载完成后回到此处，选择 ZIP 校验并导入。解压后约占 2.6 GiB，触点可按曲准备。",
                "Open the share page and download the complete ZIP (about 348 MiB) from Feijipan. You may need the official Feijipan app. Return here to select, verify and import the ZIP. The unpacked charts use about 2.6 GiB. Prepare touch coordinates per song.");
        AlertDialog dialog=UiDesign.dialog(a,touches?UiDesign.words("预计算触点包","Prepared touch pack"):UiDesign.words("原谱 ZIP","Original chart ZIP"))
                .setMessage(instructions+"\n\n"+name)
                .setPositiveButton(UiDesign.words("打开分享页","Open share page"),null)
                .setNeutralButton(UiDesign.words("选择已下载 ZIP","Choose downloaded ZIP"),(d,w)->pickPack(a,touches?PICK_OFFLINE_TOUCHES:PICK_FEIJIPAN_PACK))
                .setNegativeButton(UiDesign.words("关闭","Close"),null).create();
        UiDesign.show(dialog);
        // Keep both steps available when the browser returns to this activity.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(shareUrl)).addCategory(Intent.CATEGORY_BROWSABLE));}
            catch(ActivityNotFoundException|SecurityException failure){
                UiDesign.show(UiDesign.dialog(a,UiDesign.words("无法打开分享页","Could not open the share page"))
                        .setMessage(shareUrl).setPositiveButton(UiDesign.words("复制链接","Copy link"),(d,w)->{
                            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
                            if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Feijipan",shareUrl));
                        }).setNegativeButton(UiDesign.words("关闭","Close"),null).create());
            }
        });
    }

    static boolean openPublishedFeijipan(Activity a,java.net.URI link){
        String address=publishedFeijipanShare(link);
        if(address==null)return false;
        openFeijipanPack(a,OfflineCloudPlans.SHARE_URL.equals(address));
        return true;
    }

    static String publishedFeijipanShare(java.net.URI link){
        if(!"https".equalsIgnoreCase(link.getScheme())||!"share.feijipan.com".equalsIgnoreCase(link.getHost())
                ||link.getUserInfo()!=null||(link.getPort()!=-1&&link.getPort()!=443))return null;
        String path=link.getPath();
        if(path.endsWith("/"))path=path.substring(0,path.length()-1);
        if(java.net.URI.create(OfflineCloudPlans.SHARE_URL).getPath().equals(path))return OfflineCloudPlans.SHARE_URL;
        if(java.net.URI.create(FeijipanOfflinePack.SHARE_URL).getPath().equals(path))return FeijipanOfflinePack.SHARE_URL;
        return null;
    }

    private static void pickPack(Activity a,int request){
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        try{a.startActivityForResult(pick,request);}
        catch(ActivityNotFoundException|SecurityException failure){
            ExternalCharts.message(a,UiDesign.words("无法选择 ZIP","Could not select a ZIP"),
                    UiDesign.words("请启用系统文件选择器后重试。","Enable the system file picker and try again."));
        }
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
        if(request!=PICK_PACK&&request!=PICK_FEIJIPAN_PACK&&request!=PICK_OFFLINE_TOUCHES)return false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;Uri uri=data.getData();
        if(request==PICK_OFFLINE_TOUCHES){ExternalCharts.installOfflineTouches(a,uri);return true;}
        if(request==PICK_FEIJIPAN_PACK){
            ExternalCharts.swordaltWork(a,UiDesign.words("校验并导入离线谱库","Verify and import offline charts"),cancel->{
                RuntimeState.INSTANCE.log(UiDesign.words("正在校验整个 ZIP 的 SHA-256…","Checking the SHA-256 of the complete ZIP…"));
                JSONObject catalog=FeijipanOfflinePack.install(a,uri,cancel);
                RuntimeState.INSTANCE.log(UiDesign.words("离线谱面已保存。请在预计算触点中选择要演奏的曲目。","Offline charts saved. Use Prepare touches for the songs you plan to play."));
                return ExternalCharts.fromSwordaltCatalog(a,catalog);
            });
            return true;
        }
        ExternalCharts.swordaltWork(a,UiDesign.words("导入 Swordalt 离线谱库","Import Swordalt offline library"),cancel->{InputStream stream=a.getContentResolver().openInputStream(uri);if(stream==null)throw new IOException(UiDesign.words("无法读取离线包","Unable to read the offline ZIP"));ChartLibrary library=ExternalCharts.fromSwordaltCatalog(a,SwordaltSource.importPack(a,stream,cancel));RuntimeState.INSTANCE.log(UiDesign.words("离线谱面已保存。请在预计算触点中选择要演奏的曲目。","Offline charts saved. Use Prepare touches for the songs you plan to play."));return library;});return true;
    }
}
