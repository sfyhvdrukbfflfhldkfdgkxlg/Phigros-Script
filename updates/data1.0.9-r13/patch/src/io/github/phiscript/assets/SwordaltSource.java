package io.github.phiscript.assets;

import android.content.Context;
import android.util.AtomicFile;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.BlockAreaSet;
import org.json.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Original PGR JSON from the public resource repository used by Swordalt's downloader. */
public final class SwordaltSource {
    public static final String ASSET="phigros-swordalt-catalog-v1.json";
    public static final String REPOSITORY="7aGiven/Phigros_Resource";
    private static final int MAX_JSON=32*1024*1024,MAX_CATALOG=4*1024*1024;
    private static final long MAX_LIBRARY=4L*1024*1024*1024;
    private static final long MAX_CACHE=8L*1024*1024*1024;
    private SwordaltSource(){}
    public static boolean scripts(JSONObject catalog){return catalog.optInt("phiscriptSwordaltScripts",0)==1;}
    private static int limit(JSONObject catalog){return scripts(catalog)?256*1024*1024:MAX_JSON;}
    private static String label(JSONObject catalog){return scripts(catalog)?"Swordalt 脚本":"Swordalt 原谱";}
    public static File directory(Context c){File f=new File(c.getFilesDir(),"phigros-swordalt-v1");f.mkdirs();return f;}
    public static File cacheDirectory(Context c){File f=new File(directory(c),"charts");f.mkdirs();return f;}
    private static void check(AtomicBoolean cancel)throws IOException{ChartDownload.check(cancel);}
    private static byte[] read(InputStream in,int max,AtomicBoolean cancel)throws IOException{
        try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[16384];int n;while((n=input.read(b))!=-1){check(cancel);if(out.size()+(long)n>max)throw new IOException("谱面数据超过大小限制");out.write(b,0,n);}check(cancel);return out.toByteArray();
        }
    }
    private static void atomic(File file,byte[] bytes)throws IOException{AtomicFile a=new AtomicFile(file);FileOutputStream out=a.startWrite();try{out.write(bytes);a.finishWrite(out);}catch(IOException error){a.failWrite(out);throw error;}}
    public static JSONObject validate(JSONObject catalog)throws Exception{
        if(catalog.optInt("phiscriptSwordaltCatalog")!=1||!REPOSITORY.equals(catalog.optString("repository")))throw new IOException("不是支持的 Swordalt 原谱目录");
        if(!catalog.getString("chartCommit").matches("[0-9a-f]{40}"))throw new IOException("谱面版本编号无效");
        if(catalog.has("phiscriptSwordaltScripts")&&!scripts(catalog))throw new IOException("脚本目录版本不支持");
        JSONArray entries=catalog.getJSONArray("entries");if(entries.length()<1||entries.length()>10000)throw new IOException("目录数量无效");
        Set<String> paths=new HashSet<>();long sum=0;
        for(int i=0;i<entries.length();i++){
            JSONObject e=entries.getJSONObject(i);String path=e.getString("path");String[] pieces=path.split("/",-1);
            if(pieces.length!=2||pieces[0].isEmpty()||pieces[0].equals(".")||pieces[0].equals("..")||path.contains("\\")||!pieces[1].matches("(EZ|HD|IN|AT|SP)\\.json")||!paths.add(path))throw new IOException("谱面路径无效或重复");
            for(int j=0;j<path.length();j++)if(Character.isISOControl(path.charAt(j)))throw new IOException("谱面路径包含控制字符");
            if(!e.getString("sha1").matches("[0-9a-f]{40}")||!e.getString("difficulty").concat(".json").equals(pieces[1]))throw new IOException("谱面校验信息无效");
            long size=e.getLong("bytes");if(size<1||size>limit(catalog))throw new IOException("谱面大小超出支持范围");sum+=size;
            if(scripts(catalog)&&(!e.getString("sourceSha1").matches("[0-9a-f]{40}")||e.getLong("sourceBytes")<1||e.getLong("sourceBytes")>MAX_JSON))throw new IOException("脚本原始数据校验信息无效");
            String title=e.getString("title");if(title.trim().isEmpty()||title.length()>300)throw new IOException("曲名无效");
        }
        if(sum>MAX_LIBRARY)throw new IOException("谱库原始数据超过 4 GiB");return catalog;
    }
    public static JSONObject saved(Context c)throws Exception{
        File f=new File(directory(c),"catalog.json");
        byte[] bytes=f.exists()?read(new AtomicFile(f).openRead(),MAX_CATALOG,null):read(c.getAssets().open(ASSET),MAX_CATALOG,null);
        return validate(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));
    }
    public static void saveCatalog(Context c,JSONObject catalog)throws Exception{validate(catalog);atomic(new File(directory(c),"catalog.json"),catalog.toString().getBytes(StandardCharsets.UTF_8));}
    static String blobHash(byte[] bytes)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-1");hash.update(("blob "+bytes.length+"\u0000").getBytes(StandardCharsets.US_ASCII));hash.update(bytes);StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    static String key(JSONObject catalog,JSONObject entry)throws Exception{return ExternalCharts.hash((scripts(catalog)?"scripts-v1/":"")+catalog.getString("chartCommit")+"/"+entry.getString("path"));}
    private static String rawUrl(String revision,String path)throws Exception{
        StringBuilder out=new StringBuilder("https://raw.githubusercontent.com/").append(REPOSITORY).append('/').append(revision).append('/');
        String[] parts=path.split("/");for(int i=0;i<parts.length;i++){if(i>0)out.append('/');out.append(URLEncoder.encode(parts[i],"UTF-8").replace("+","%20"));}return out.toString();
    }
    private static boolean verified(byte[] bytes,JSONObject entry)throws Exception{return bytes.length==entry.getLong("bytes")&&blobHash(bytes).equals(entry.getString("sha1"));}
    private static long cacheBytes(Context c){long total=0;File[] list=cacheDirectory(c).listFiles();if(list!=null)for(File file:list)if(file.getName().endsWith(".json"))total+=file.length();return total;}
    private static void store(Context c,JSONObject catalog,JSONObject entry,byte[] bytes)throws Exception{
        File f=new File(cacheDirectory(c),key(catalog,entry)+".json");long size=cacheBytes(c)-(f.exists()?f.length():0)+bytes.length;
        if(size>MAX_CACHE)throw new IOException("Swordalt 缓存超过 8 GiB，请在设置中删除旧谱面");
        atomic(f,bytes);JSONObject meta=new JSONObject().put("source",label(catalog)).put("title",entry.getString("title")).put("level",entry.getString("difficulty")).put("bytes",bytes.length).put("sha1",entry.getString("sha1")).put("path",entry.getString("path")).put("noiseCount",entry.optInt("noiseCount",-1));
        atomic(new File(cacheDirectory(c),key(catalog,entry)+".meta"),meta.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static byte[] data(Context c,JSONObject catalog,JSONObject entry,AtomicBoolean cancel)throws Exception{
        check(cancel);File f=new File(cacheDirectory(c),key(catalog,entry)+".json");
        if(f.isFile()){
            byte[] bytes=read(new FileInputStream(f),limit(catalog),cancel);
            if(verified(bytes,entry)){RuntimeState.INSTANCE.log("读取已导入的 "+label(catalog)+"："+entry.getString("title")+" / "+entry.getString("difficulty"));return bytes;}
            RuntimeState.INSTANCE.log("Swordalt 谱面缓存校验未通过，重新下载");
        }
        RuntimeState.INSTANCE.log("下载 Swordalt 原谱与噪域："+entry.getString("title")+" / "+entry.getString("difficulty"));final long[] logged={0};
        byte[] bytes=ChartDownload.get(rawUrl(catalog.getString("chartCommit"),entry.getString("path")),MAX_JSON,cancel,(done,total)->{
            if(System.currentTimeMillis()-logged[0]>1000){logged[0]=System.currentTimeMillis();RuntimeState.INSTANCE.log("下载原谱 "+entry.optString("title")+"："+(done/1024)+" KiB");}
        });
        check(cancel);
        if(scripts(catalog)){
            if(bytes.length!=entry.getLong("sourceBytes")||!blobHash(bytes).equals(entry.getString("sourceSha1")))throw new IOException("脚本来源长度或 Git 数据校验失败");
            bytes=PgrScriptConverter.convert(bytes,entry,catalog);check(cancel);
        }
        if(!verified(bytes,entry))throw new IOException("谱面长度或 Git 数据校验失败，未导入");
        // Verify the JSON and noise metadata before committing the exact downloaded bytes.
        JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(scripts(catalog)?json.optInt("phiscriptFormat")!=1:!json.has("judgeLineList")||!json.has("formatVersion"))throw new IOException("下载内容不是支持的谱面格式");
        if(json.has("blockAreaList")&&json.optJSONArray("blockAreaList")==null)throw new IOException("噪域列表格式无效");
        store(c,catalog,entry,bytes);return bytes;
    }
    public static Chart loadChart(Context c,JSONObject catalog,JSONObject entry,AtomicBoolean cancel)throws Exception{
        byte[] bytes=data(c,catalog,entry,cancel);check(cancel);Chart chart=SharedChartImport.parseJson(new String(bytes,StandardCharsets.UTF_8));check(cancel);
        RuntimeState.INSTANCE.log(label(catalog)+"就绪："+entry.getString("title")+" / "+entry.getString("difficulty")+"；噪域 "+BlockAreaSet.count(chart)+" 个");return chart;
    }
    static JSONObject entry(JSONObject catalog,String path)throws Exception{JSONArray a=catalog.getJSONArray("entries");for(int i=0;i<a.length();i++)if(path.equals(a.getJSONObject(i).getString("path")))return a.getJSONObject(i);throw new IOException("未找到唯一的谱面路径");}
    private static JSONObject getJson(String url,AtomicBoolean cancel)throws Exception{return new JSONObject(new String(ChartDownload.get(url,MAX_CATALOG,cancel,null),StandardCharsets.UTF_8));}
    private static String getText(String revision,String path,AtomicBoolean cancel)throws Exception{return new String(ChartDownload.get(rawUrl(revision,path),MAX_CATALOG,cancel,null),StandardCharsets.UTF_8);}
    private static Map<String,String[]> rows(String text){Map<String,String[]> result=new LinkedHashMap<>();for(String line:text.split("\\r?\\n")){String[] cols=line.split("\\t",-1);if(cols.length>1)result.put(cols[0].trim(),cols);}return result;}
    public static JSONObject refresh(Context c,AtomicBoolean cancel)throws Exception{
        String api="https://api.github.com/repos/"+REPOSITORY+"/";
        String chart=getJson(api+"branches/chart",cancel).getJSONObject("commit").getString("sha");String info=getJson(api+"branches/info",cancel).getJSONObject("commit").getString("sha");
        if(!chart.matches("[0-9a-f]{40}")||!info.matches("[0-9a-f]{40}"))throw new IOException("资源版本编号无效");
        JSONObject tree=getJson(api+"git/trees/"+chart+"?recursive=1",cancel);if(tree.optBoolean("truncated",false))throw new IOException("资源目录未完整返回");
        Map<String,String[]> songs=rows(getText(info,"info.tsv",cancel)),difficulties=rows(getText(info,"difficulty.tsv",cancel));String version=getText(info,"version.txt",cancel).trim();
        Map<String,JSONObject> old=new HashMap<>();try{JSONArray prior=saved(c).getJSONArray("entries");for(int i=0;i<prior.length();i++)old.put(prior.getJSONObject(i).getString("path"),prior.getJSONObject(i));}catch(Exception ignored){}
        JSONArray entries=new JSONArray(),list=tree.getJSONArray("tree");String[] ranks={"EZ","HD","IN","AT"};
        for(int i=0;i<list.length();i++){
            JSONObject f=list.getJSONObject(i);if(!"blob".equals(f.optString("type")))continue;String path=f.getString("path");if(!path.matches("[^/]+/(EZ|HD|IN|AT|SP)\\.json"))continue;
            String id=path.substring(0,path.indexOf('/')),rank=path.substring(path.indexOf('/')+1,path.length()-5),base=id;int variant=0;int dot=id.lastIndexOf('.');
            if(dot>=0&&id.substring(dot+1).matches("[0-9]+")){variant=Integer.parseInt(id.substring(dot+1));base=id.substring(0,dot);}
            String[] song=songs.get(base),diff=difficulties.get(base);String title=song==null?id:song[1],composer=song!=null&&song.length>2?song[2]:"",level="?";JSONArray aliases=new JSONArray();
            if(base.equals("AnotherMe.NeutralMoon")){title="Another Me (Neutral Moon)";aliases.put("Another Me");}if(base.equals("AnotherMe.DAAN")){title="Another Me (DAAN)";aliases.put("Another Me");}
            if(variant!=0)title+="（变体 "+variant+"）";
            for(int r=0;r<ranks.length;r++)if(ranks[r].equals(rank)&&diff!=null&&diff.length>r+1&&!diff[r+1].isEmpty())level=diff[r+1];
            boolean visible=song!=null&&variant==0&&(!level.equals("?")||base.equals("Message.くるぶっこちゃん")&&rank.equals("SP"));
            JSONObject e=new JSONObject().put("path",path).put("id",id).put("title",title).put("composer",composer).put("difficulty",rank).put("level",level).put("aliases",aliases).put("sha1",f.getString("sha")).put("bytes",f.getLong("size")).put("noiseCount",-1).put("siteVisible",visible);
            JSONObject previous=old.get(path);if(previous!=null&&e.getString("sha1").equals(previous.optString("sha1"))){e.put("noiseCount",previous.optInt("noiseCount",-1));if(previous.has("noiseDeclared"))e.put("noiseDeclared",previous.getBoolean("noiseDeclared"));}
            entries.put(e);
        }
        JSONObject result=validate(new JSONObject().put("phiscriptSwordaltCatalog",1).put("provider","swordalt").put("repository",REPOSITORY).put("website","https://swordalt.github.io/phigros-chart-downloader/").put("chartCommit",chart).put("infoCommit",info).put("version",version).put("entries",entries));
        check(cancel);saveCatalog(c,result);return result;
    }
    public static JSONObject importPack(Context c,InputStream source,AtomicBoolean cancel)throws Exception{
        JSONObject catalog=null;Map<String,JSONObject> entries=new HashMap<>();Set<String> seenNames=new HashSet<>(),imported=new HashSet<>();long inflated=0;int count=0;
        try(ZipInputStream zip=new ZipInputStream(source)){
            ZipEntry item;while((item=zip.getNextEntry())!=null){check(cancel);String name=item.getName();
                if(++count>10000||name.startsWith("/")||name.contains("\\"))throw new IOException("离线包路径或数量无效");
                for(String p:name.split("/"))if(p.equals(".")||p.equals(".."))throw new IOException("离线包包含不安全路径");
                if(!seenNames.add(name))throw new IOException("离线包包含重复路径");
                boolean metadata=name.equals("swordalt-catalog.json"),chart=name.startsWith("charts/")&&name.endsWith(".json");int limit=metadata?MAX_CATALOG:catalog!=null?limit(catalog):MAX_JSON;
                ByteArrayOutputStream out=metadata||chart?new ByteArrayOutputStream():null;long entryBytes=0;byte[] buffer=new byte[16384];int n;
                while((n=zip.read(buffer))!=-1){check(cancel);entryBytes+=n;inflated+=n;if(entryBytes>limit||inflated>MAX_LIBRARY)throw new IOException("离线包解压数据超过限制");if(out!=null)out.write(buffer,0,n);}
                if(metadata){if(catalog!=null||!imported.isEmpty())throw new IOException("离线包目录顺序无效");catalog=validate(new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)));JSONArray list=catalog.getJSONArray("entries");for(int i=0;i<list.length();i++){JSONObject e=list.getJSONObject(i);entries.put(e.getString("path"),e);}}
                else if(chart){if(catalog==null)throw new IOException("离线包缺少前置目录");String path=name.substring(7);JSONObject e=entries.get(path);if(e==null||!imported.add(path))throw new IOException("离线包中有未登记或重复谱面");byte[] bytes=out.toByteArray();if(!verified(bytes,e))throw new IOException("离线谱面校验失败："+path);store(c,catalog,e,bytes);if(imported.size()%20==0)RuntimeState.INSTANCE.log("导入 Swordalt 原谱："+imported.size()+" / "+entries.size());}
                zip.closeEntry();
            }
        }
        check(cancel);if(catalog==null||imported.size()!=entries.size())throw new IOException("离线包谱面不完整，未启用新目录");saveCatalog(c,catalog);return catalog;
    }
}
