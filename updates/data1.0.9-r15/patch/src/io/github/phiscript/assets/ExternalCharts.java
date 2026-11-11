package io.github.phiscript.assets;
import android.app.*;import android.content.*;import android.net.Uri;import android.widget.*;import android.util.AtomicFile;
import io.github.phiscript.*;import io.github.phiscript.engine.Chart;import io.github.phiscript.vision.SongCandidate;import io.github.phiscript.root.*;
import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.lang.reflect.*;import java.security.MessageDigest;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.regex.*;import kotlin.Unit;
/** Adapts cloud metadata and local PEZ/ZIP libraries to the existing OCR/playback pipeline. */
public final class ExternalCharts {
    private static final String PREF="phigros_library_source",DEFAULT_SHARE=FeijipanSource.NEW_SHARE;
    private static final int PICK=42371;
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static final Map<ChartLibrary,Adapter> adapters=Collections.synchronizedMap(new WeakHashMap<ChartLibrary,Adapter>());
    private static ChartLibrary cloud,local,previous,swordalt;
    private static final Pattern NAME=Pattern.compile("^(.+) - (EZ|HD|IN|AT|SP) \\[[^\\]]+\\]\\.(?:json|pez|zip)$",Pattern.CASE_INSENSITIVE);
    private static final class Entry{String id,title,level,name,display;PhiraEntry local;JSONObject resource;List<String> aliases=new ArrayList<>();boolean auto=true;Entry(String i,String t,String l,String n,String d){id=i;title=t;level=l;name=n;display=d;}}
    private static final class Adapter{Context context;String share;int expectedCharts;JSONObject swordaltCatalog;PhiraLibrary imported;List<Entry> entries;List<SongCandidate> songs;Map<String,Entry> choices=new LinkedHashMap<>();}
    static final class Storage extends ContextWrapper{
        Storage(Context c){super(c.getApplicationContext());}
        @Override public Context getApplicationContext(){return this;}
        @Override public File getFilesDir(){File d=new File(super.getFilesDir(),"phigros-packages-v1");d.mkdirs();return d;}
    }
    public static boolean isBusy(){return busy.get();}
    static void message(Activity a,String title,String msg){if(!a.isFinishing()&&!a.isDestroyed())new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("确定",null).show();}
    static String hash(String s)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();}
    static byte[] read(InputStream in,int limit)throws IOException{try(InputStream src=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n;while((n=src.read(buf))!=-1){if(out.size()+(long)n>limit)throw new IOException("文件过大");out.write(buf,0,n);}return out.toByteArray();}}
    private static File base(Context c){File d=new File(c.getFilesDir(),"phigros-cloud-v1");d.mkdirs();return d;}
    private static File cache(Context c){File d=new File(base(c),"charts");d.mkdirs();return d;}
    private static void atomic(File target,byte[] bytes)throws IOException{AtomicFile f=new AtomicFile(target);FileOutputStream out=f.startWrite();try{out.write(bytes);f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}}
    private static ChartLibrary wrap(Adapter a)throws Exception{
        Map<String,List<Entry>> grouped=new TreeMap<>();for(Entry e:a.entries)grouped.computeIfAbsent(e.title,k->new ArrayList<>()).add(e);
        List<ChartLocation> locations=new ArrayList<>();List<SongCandidate> songs=new ArrayList<>();List<String> paths=new ArrayList<>();
        for(Map.Entry<String,List<Entry>> group:grouped.entrySet()){
            String id=(a.swordaltCatalog!=null?"swordalt:":a.share==null?"package:":"cloud:")+hash(group.getKey());Map<String,Integer> counts=new HashMap<>();for(Entry e:group.getValue())counts.put(e.level,counts.getOrDefault(e.level,0)+1);
            Set<String> aliases=new LinkedHashSet<>();String userAlias=new AppSettings(a.context).alias(id);if(!userAlias.isEmpty())aliases.add(userAlias);
            for(Entry e:group.getValue()){
                String level=counts.get(e.level)>1?e.level+" #"+e.id.substring(Math.max(0,e.id.length()-6)):e.level;
                String key=id+"\n"+level;if(a.choices.put(key,e)!=null)throw new IllegalStateException("曲名/难度重复，无法唯一选择");
                locations.add(new ChartLocation(id,level,e.title,e.id));paths.add(e.id);
                Matcher m=NAME.matcher(e.display);if(m.matches()&&!m.group(1).equals(e.title))aliases.add(m.group(1));
                if(e.local!=null)aliases.addAll(e.local.getAliases());aliases.addAll(e.aliases);
            }
            boolean automatic=false;for(Entry e:group.getValue())automatic|=e.auto;if(automatic)songs.add(new SongCandidate(id,group.getKey(),new ArrayList<>(aliases)));
        }
        a.songs=songs;final PackageSnapshot snapshot=new PackageSnapshot("io.github.phiscript.external",a.swordaltCatalog!=null?a.swordaltCatalog.getString("chartCommit"):a.share==null?"PEZ/ZIP":a.share,1L,0L,paths);
        ChartSource source=new ChartSource(){public PackageSnapshot inspect(){return snapshot;}public List<String> entries(PackageSnapshot s){return paths;}public byte[] read(PackageSnapshot s,String e,int max){throw new IllegalStateException("外部谱库只能按选中歌曲读取");}};
        Constructor<ChartLibrary> ctor=ChartLibrary.class.getDeclaredConstructor(Context.class,PackageSnapshot.class,List.class,ChartSource.class,String.class);ctor.setAccessible(true);
        ChartLibrary result=ctor.newInstance(a.context,snapshot,locations,source,a.swordaltCatalog!=null?(SwordaltSource.scripts(a.swordaltCatalog)?"Swordalt · 脚本与噪域":"Swordalt · 原谱与噪域"):a.share==null?"Phigros · PEZ/ZIP":"小飞机 · 每次识别后下载");adapters.put(result,a);return result;
    }
    public static ChartLibrary fromCatalog(Context c,JSONObject j)throws Exception{
        Adapter a=new Adapter();a.context=c.getApplicationContext();a.share=j.getString("share");if(!a.share.matches("[A-Za-z0-9]{4,32}"))throw new IllegalArgumentException("分享编号无效");a.entries=new ArrayList<>();JSONArray array=j.getJSONArray("entries");Set<String> ids=new HashSet<>(),paths=new HashSet<>();a.expectedCharts=j.optInt("expectedCharts",array.length());
        if(array.length()<1||array.length()>10000)throw new IllegalArgumentException("目录条目数量无效");
        if(a.expectedCharts<array.length()||a.expectedCharts>10000)throw new IllegalArgumentException("目录预期数量无效");
        for(int i=0;i<array.length();i++){JSONObject item=array.getJSONObject(i);String name=item.getString("name"),id=item.getString("id");Matcher m=NAME.matcher(name);
            if(!m.matches())throw new IllegalStateException("目录文件名缺少曲名和难度："+name+"。应为 曲名 - IN [等级].json");
            if(!id.matches("[0-9]{1,24}")||!ids.add(id))throw new IllegalStateException("目录文件编号无效或重复");
            Entry e=new Entry(id,m.group(1),m.group(2).toUpperCase(Locale.ROOT),name,item.optString("display",name));
            if(item.has("resource")){
                e.resource=FeijipanSource.validateResource(item);if(!paths.add(item.getString("path"))||!e.title.equals(e.resource.getString("title"))||!e.level.equals(e.resource.getString("difficulty")))throw new IllegalStateException("目录曲名、难度或位置不一致");
                if(j.optInt("phiscriptFeijipanCatalog")==1){String manifest=j.getString("manifestPath");if(!manifest.endsWith("swordalt-catalog.json")||!item.getString("path").equals(manifest.substring(0,manifest.length()-"swordalt-catalog.json".length())+"charts/"+e.resource.getString("path")))throw new IllegalStateException("谱面与清单不在同一谱库目录");}
                e.auto=e.resource.optBoolean("siteVisible",true);JSONArray aliases=e.resource.optJSONArray("aliases");if(aliases!=null)for(int n=0;n<aliases.length();n++)if(!aliases.optString(n).trim().isEmpty())e.aliases.add(aliases.getString(n));
            }else if(j.optInt("phiscriptFeijipanCatalog")==1)throw new IllegalStateException("目录缺少谱面校验信息");
            a.entries.add(e);}
        return wrap(a);
    }
    public static ChartLibrary fromSwordaltCatalog(Context context,JSONObject catalog)throws Exception{
        SwordaltSource.validate(catalog);Adapter a=new Adapter();a.context=context.getApplicationContext();a.share="swordalt";a.swordaltCatalog=catalog;a.entries=new ArrayList<>();
        JSONArray rows=catalog.getJSONArray("entries");for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String title=row.getString("title"),rank=row.getString("difficulty");
            Entry e=new Entry(hash(row.getString("path")),title,rank,title+" - "+rank+" ["+row.optString("level","?")+"].json",title);e.resource=row;e.auto=row.optBoolean("siteVisible",true);
            JSONArray names=row.optJSONArray("aliases");if(names!=null)for(int j=0;j<names.length();j++)if(!names.optString(j).trim().isEmpty())e.aliases.add(names.getString(j));a.entries.add(e);
        }
        return wrap(a);
    }
    private static ChartLibrary fromLocal(Context c,PhiraLibrary imported)throws Exception{
        Adapter a=new Adapter();a.context=c.getApplicationContext();a.imported=imported;a.entries=new ArrayList<>();
        for(PhiraEntry p:imported.getEntries()){String title=p.getTitle(),level=p.getLevel();Matcher fn=NAME.matcher(title+".json");if(fn.matches()){title=fn.group(1);level=fn.group(2);}else{Matcher m=Pattern.compile("(?i)(?:^|[^A-Z])(EZ|HD|IN|AT|SP)(?=$|[^A-Z])").matcher(level);if(m.find())level=m.group(1).toUpperCase(Locale.ROOT);}
            Entry e=new Entry(p.getId(),title,level,title,title);e.local=p;a.entries.add(e);}
        return wrap(a);
    }
    public static List<SongCandidate> songs(ChartLibrary lib){Adapter a=adapters.get(lib);return a==null?null:a.songs;}
    public static PhiraEntry sessionEntry(ChartLibrary library,ChartLocation location,String id,SongCandidate song){
        Adapter adapter=adapters.get(library);Entry entry=adapter==null?null:adapter.choices.get(location.getSongId()+"\n"+location.getDifficulty());
        String title=entry!=null?entry.title:song!=null?song.getTitle():location.getSongId();Set<String> aliases=new LinkedHashSet<>();
        if(song!=null)aliases.addAll(song.getAliases());if(entry!=null)aliases.addAll(entry.aliases);
        PhiraEntry imported=entry==null?null:entry.local;if(imported!=null)aliases.addAll(imported.getAliases());
        return new PhiraEntry(id,title,location.getDifficulty(),new ArrayList<>(aliases),imported==null?16.0/9:imported.getAspectRatio(),imported!=null&&imported.getForceAspectRatio());
    }
    public static Chart load(ChartLibrary lib,String song,String level){try{return load(lib,song,level,null);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e.getMessage(),e);}}
    public static Chart load(ChartLibrary lib,String song,String level,AtomicBoolean cancel)throws Exception{
        Adapter a=adapters.get(lib);if(a==null)return null;Entry e=a.choices.get(song+"\n"+level);if(e==null)throw new IllegalStateException("未找到唯一的曲名/难度，请手动选择同名谱面");
        if(a.imported!=null){try{return a.imported.load(e.local.getId(),e.local.getLevel());}catch(Exception error){throw new IllegalStateException(phigrosMessage(error.getMessage()),error);}}
        if(a.swordaltCatalog!=null)return SwordaltSource.loadChart(a.context,a.swordaltCatalog,e.resource,cancel);
        RuntimeState.INSTANCE.log("已识别 "+e.title+" / "+e.level+"，开始下载对应谱面…");final long[] logged={0};
        byte[] data=FeijipanSource.download(a.share,e.id,cancel,(done,total)->{if(System.currentTimeMillis()-logged[0]>1000){logged[0]=System.currentTimeMillis();RuntimeState.INSTANCE.log("正在下载 "+e.title+"："+(done/1024)+" KiB"+(total>0?" / "+(total/1024)+" KiB":""));}});
        if(e.resource!=null)FeijipanSource.verify(data,e.resource);
        ChartDownload.check(cancel);String json;
        if(e.name.toLowerCase(Locale.ROOT).endsWith(".json")){json=new String(data,StandardCharsets.UTF_8);if(json.startsWith("\uFEFF"))json=json.substring(1);}
        else json=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(data),e.name).getNormalizedJson();
        Chart chart=SharedChartImport.parseJson(json);ChartDownload.check(cancel);
        if(e.resource!=null&&e.resource.optInt("noiseCount",-1)>=0&&io.github.phiscript.engine.BlockAreaSet.count(chart)!=e.resource.getInt("noiseCount"))throw new IllegalStateException("谱面噪域数量与清单不一致，未启用该文件");
        if(!io.github.phiscript.engine.BlockAreaSet.hasMetadata(chart))RuntimeState.INSTANCE.log("提示：该文件未包含噪域数据；如果游戏中有噪域，请导入保留 blockAreaList 的谱面。");
        if(chart.getNotes().isEmpty())throw new IllegalStateException("下载的谱面没有可演奏音符");
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);String stem=a.share+"-"+e.id;
        File destination=new File(cache(a.context),stem+".json");long size=0;File[] files=cache(a.context).listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".json")&&!f.equals(destination))size+=f.length();
        if(size+bytes.length>512L*1024*1024)throw new IllegalStateException("谱面缓存超过 512 MiB，请在设置中删除已下载谱面");
        atomic(destination,bytes);
        JSONObject meta=new JSONObject().put("source","小飞机 "+a.share).put("title",e.title).put("level",e.level).put("bytes",bytes.length).put("sha256",hash(json));if(e.resource!=null)meta.put("path",e.resource.getString("path")).put("sha1",e.resource.getString("sha1"));atomic(new File(cache(a.context),stem+".meta"),meta.toString().getBytes(StandardCharsets.UTF_8));
        RuntimeState.INSTANCE.log("下载并校验完成："+e.title+" / "+e.level+"；噪域 "+io.github.phiscript.engine.BlockAreaSet.count(chart)+" 个");return chart;
    }
    public static Chart loadSelected(ChartLibrary lib,String song,String level,AtomicBoolean cancel)throws Exception{Chart c=load(lib,song,level,cancel);return c==null?lib.load(song,level):c;}
    public static boolean isCloud(ChartLibrary lib){Adapter a=adapters.get(lib);return a!=null&&a.share!=null;}
    private static void activate(Activity a,ChartLibrary lib,String mode){ChartLibrary old=RuntimeState.INSTANCE.getLibrary();if(old!=null&&!adapters.containsKey(old))previous=old;RuntimeState.INSTANCE.setLibrary(lib);new AppSettings(a).save(PREF,mode);HomeScreen.refresh(a);}
    public static void commitPhigros(Context c,ChartLibrary library,String source){
        if(library==null)throw new IllegalArgumentException("Phigros 谱库未就绪");ChartLibrary old=RuntimeState.INSTANCE.getLibrary();if(old!=null&&!adapters.containsKey(old))previous=old;
        RuntimeState.INSTANCE.setLibrary(library);AppSettings prefs=new AppSettings(c);prefs.save(PREF,source);ModeRouting.select(prefs,0);
    }
    public static void onApkLibrary(Activity a){ChartLibrary library=RuntimeState.INSTANCE.getLibrary();if(library!=null&&!adapters.containsKey(library)){previous=library;commitPhigros(a,library,"apk");HomeScreen.refresh(a);}}
    private static String phigrosMessage(String text){return text==null?"Phigros 谱面操作未完成":text.replace("Phira","Phigros 本地");}
    public static void attach(Activity a){try{ensureSelected(a);}catch(Exception e){RuntimeState.INSTANCE.log("外部谱库恢复失败："+e.getMessage());}}
    public static boolean ensureSelected(Activity a){if(!ModeRouting.needsPhigros(new AppSettings(a)))return true;return ensurePhigrosSelected(a);}
    private static ChartLibrary restoreCloud(Context context)throws Exception{
        File saved=new File(base(context),"catalog.json");boolean exists=saved.exists();
        // R6 could select its bundled share without writing a catalog. Preserve that choice on upgrade.
        String asset="cloud".equals(new AppSettings(context).text(PREF,""))?"phigros-cloud-catalog-v1.json":FeijipanSource.NEW_ASSET;
        byte[] bytes=exists?read(new AtomicFile(saved).openRead(),4*1024*1024):read(context.getAssets().open(asset),4*1024*1024);
        JSONObject catalog=new JSONObject(new String(bytes,StandardCharsets.UTF_8));ChartLibrary library=fromCatalog(context,catalog);
        if(!exists)atomic(saved,catalog.toString().getBytes(StandardCharsets.UTF_8));return library;
    }
    public static boolean ensurePhigrosSelected(Activity a){
        try{AppSettings prefs=new AppSettings(a);String source=prefs.text(PREF,"cloud");if("apk".equals(source))return true;
            if("swordalt".equals(source)){if(swordalt==null)swordalt=fromSwordaltCatalog(a,SwordaltSource.saved(a));activate(a,swordalt,"swordalt");}
            else if("cloud".equals(source)){if(cloud==null)cloud=restoreCloud(a);activate(a,cloud,"cloud");}
            else if("local".equals(source)){if(local==null){PhiraLibrary saved=PhiraLibrary.Companion.restore(new Storage(a));if(saved==null)throw new IllegalStateException("没有已导入的 PEZ/ZIP 谱面");local=fromLocal(a,saved);}activate(a,local,"local");}
            return true;
        }catch(Exception e){message(a,"Phigros 谱库未就绪",phigrosMessage(e.getMessage()));return false;}
    }
    public static void useSwordalt(Activity a){if(RootPanel.blocked(a))return;try{if(swordalt==null)swordalt=fromSwordaltCatalog(a,SwordaltSource.saved(a));commitPhigros(a,swordalt,"swordalt");HomeScreen.refresh(a);message(a,"Swordalt 谱库已启用",swordalt.getLocations().size()+" 张谱面。已导入的文件校验后直接读取；缺少的文件按曲下载，脚本目录会自动转换。噪域数据保留。可在本入口导入原谱或脚本离线 ZIP。");}catch(Exception e){message(a,"Swordalt 目录未就绪",e.getMessage());}}
    static void swordaltWork(Activity a,String title,Job job){work(a,title,job,"swordalt");}
    private static String availability(ChartLibrary lib){Adapter a=adapters.get(lib);return a==null||a.share==null?lib.getLocations().size()+" 张谱面":"小飞机 "+a.share+"：可用 "+lib.getLocations().size()+" / "+a.expectedCharts+" 张谱面"+(a.expectedCharts>lib.getLocations().size()?"。缺少的文件未加入演奏目录，请补齐上传后更新目录。":"。");}
    public static void useCloud(Activity a){if(RootPanel.blocked(a))return;try{if(cloud==null)cloud=restoreCloud(a);commitPhigros(a,cloud,"cloud");HomeScreen.refresh(a);message(a,"联网谱库已启用",availability(cloud)+"\n每次识别曲名和难度后下载对应谱面，下载完成才开始。噪域数据随谱面保留。同名同难度请手动确认。");}catch(Exception e){message(a,"联网谱库未就绪",e.getMessage());}}
    public static void useNewShare(Activity a){
        if(RootPanel.blocked(a))return;try{
            File saved=new File(base(a),"catalog.json");JSONObject catalog=null;
            if(saved.exists()){try{JSONObject old=new JSONObject(new String(read(new AtomicFile(saved).openRead(),4*1024*1024),StandardCharsets.UTF_8));if(FeijipanSource.NEW_SHARE.equals(old.optString("share")))catalog=old;}catch(Exception error){RuntimeState.INSTANCE.log("上次网盘目录无法读取，使用内置新谱库目录");}}
            if(catalog==null)catalog=new JSONObject(new String(read(a.getAssets().open(FeijipanSource.NEW_ASSET),4*1024*1024),StandardCharsets.UTF_8));
            ChartLibrary library=fromCatalog(a,catalog);atomic(saved,catalog.toString().getBytes(StandardCharsets.UTF_8));cloud=library;commitPhigros(a,library,"cloud");HomeScreen.refresh(a);
            message(a,"新小飞机谱库已启用",availability(library)+"\n按 charts/歌曲/难度.json 匹配。识别后下载并校验，噪域随脚本读取。文件移动或重新上传后，请使用“联网链接 / 更新目录”。");
        }catch(Exception e){message(a,"新小飞机谱库未就绪",e.getMessage());}
    }
    public static void useLocal(Activity a){if(RootPanel.blocked(a))return;try{if(local==null){PhiraLibrary saved=PhiraLibrary.Companion.restore(new Storage(a));if(saved==null)throw new IllegalStateException("没有已导入的 Phigros 本地谱面");local=fromLocal(a,saved);}commitPhigros(a,local,"local");HomeScreen.refresh(a);}catch(Exception e){message(a,"Phigros 本地谱库未就绪",phigrosMessage(e.getMessage()));}}
    public static void useApk(Activity a){if(RootPanel.blocked(a))return;if(previous!=null){commitPhigros(a,previous,"apk");HomeScreen.refresh(a);}}
    public static void choose(Activity a){if(RootPanel.blocked(a))return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);a.startActivityForResult(i,PICK);}
    public static boolean result(Activity a,int request,int result,Intent data){
        if(SwordaltLibraryUi.result(a,request,result,data))return true;
        if(request!=PICK)return false;if(result!=Activity.RESULT_OK||data==null)return true;List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return true;if(uris.size()>16){message(a,"文件过多","一次最多选择 16 个 PEZ / ZIP / JSON 文件");return true;}
        work(a,"导入 Phigros 谱面",cancel->{PhiraLibrary imported;try(PhiraImportControl.Session ignored=PhiraImportControl.begin()){imported=PhiraLibrary.Companion.importFiles(new Storage(a),uris,s->{RuntimeState.INSTANCE.log(phigrosMessage(s));return Unit.INSTANCE;});}return fromLocal(a,imported);},"local");return true;
    }
    interface Job{ChartLibrary run(AtomicBoolean cancel)throws Exception;}
    private static void work(Activity a,String title,Job job,String mode){
        if(RuntimeState.INSTANCE.getRunning().get()||!busy.compareAndSet(false,true)){message(a,"任务进行中","请先停止当前识别或演奏");return;}
        AtomicBoolean cancel=new AtomicBoolean();ProgressDialog dialog=new ProgressDialog(a);dialog.setTitle(title);dialog.setMessage("正在处理，可从运行记录查看进度…");dialog.setIndeterminate(true);dialog.setCancelable(false);dialog.setButton(DialogInterface.BUTTON_NEGATIVE,"取消",(d,w)->{cancel.set(true);PhiraImportControl.cancel();});dialog.show();HomeScreen.refresh(a);
        new Thread(()->{ChartLibrary done=null;String error=null;try{done=job.run(cancel);}catch(OutOfMemoryError e){error="设备可用内存不足，请关闭其他应用后分批导入。";}catch(Exception e){error=phigrosMessage(e.getMessage()==null?e.toString():e.getMessage());}final ChartLibrary result=done;final String failure=error;
            a.runOnUiThread(()->{busy.set(false);if(!a.isDestroyed())dialog.dismiss();if(result!=null){if("cloud".equals(mode))cloud=result;else if("swordalt".equals(mode))swordalt=result;else local=result;commitPhigros(a,result,mode);HomeScreen.refresh(a);message(a,title,"cloud".equals(mode)?"已保存并启用。"+availability(result):"已保存并启用 "+result.getLocations().size()+" 张谱面");}else message(a,title,failure);HomeScreen.refresh(a);});},"phi-external-import").start();
    }
    public static void network(Activity a){if(RootPanel.blocked(a))return;EditText field=new EditText(a);field.setSingleLine(true);field.setText("https://share.feijipan.com/s/"+DEFAULT_SHARE);
        new AlertDialog.Builder(a).setTitle("联网谱库 / 下载谱面").setMessage("小飞机分享链接只更新目录；JSON / PEZ / ZIP 直链下载后导入本地。").setView(field).setPositiveButton("连接",(d,w)->{String link=field.getText().toString().trim();try{java.net.URI uri=new java.net.URI(link);if(!"https".equalsIgnoreCase(uri.getScheme()))throw new IllegalArgumentException("请使用 HTTPS 链接");
            if("swordalt.github.io".equalsIgnoreCase(uri.getHost())&&uri.getPath().startsWith("/phigros-chart-downloader")){SwordaltLibraryUi.open(a);}
            else if("share.feijipan.com".equalsIgnoreCase(uri.getHost())){String path=uri.getPath();if(!path.matches("/s/[A-Za-z0-9]{4,32}/?"))throw new IllegalArgumentException("分享链接格式不正确");String id=path.split("/")[2];work(a,"连接联网谱库",cancel->{JSONObject j=FeijipanSource.catalog(id,cancel);ChartLibrary lib=fromCatalog(a,j);ChartDownload.check(cancel);atomic(new File(base(a),"catalog.json"),j.toString().getBytes(StandardCharsets.UTF_8));return lib;},"cloud");}
            else work(a,"下载并导入谱面",cancel->{byte[] bytes=ChartDownload.get(link,64*1024*1024,cancel,null);String name=uri.getPath()==null?"chart.pez":new File(uri.getPath()).getName();File file=File.createTempFile("download-","-"+name.replaceAll("[^A-Za-z0-9._-]","_"),a.getCacheDir());try{try(FileOutputStream out=new FileOutputStream(file)){out.write(namedPackage(bytes,name));}ChartDownload.check(cancel);PhiraLibrary imported;try(PhiraImportControl.Session ignored=PhiraImportControl.begin()){imported=PhiraLibrary.Companion.importFiles(new Storage(a),Collections.singletonList(Uri.fromFile(file)),s->{RuntimeState.INSTANCE.log(phigrosMessage(s));return Unit.INSTANCE;});}return fromLocal(a,imported);}finally{file.delete();}},"local");
        }catch(Exception e){message(a,"链接无效",e.getMessage());}}).setNegativeButton("取消",null).show();
    }
    static byte[] namedPackage(byte[] source,String name)throws Exception{
        PhiraImportData data=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(source),name);
        String title=data.getTitle(),level=data.getLevel();Matcher match=NAME.matcher(name);
        if(match.matches()){title=match.group(1);level=match.group(2).toUpperCase(Locale.ROOT);}
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(out)){
            zip.putNextEntry(new java.util.zip.ZipEntry("chart.json"));zip.write(data.getNormalizedJson().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("info.txt"));zip.write(("#\nName: "+title+"\nLevel: "+level+"\nChart: chart.json\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return out.toByteArray();
    }
    static int deleteCacheFiles(Context context,List<File> selected)throws IOException{
        Set<String> parents=new HashSet<>(Arrays.asList(cache(context).getCanonicalPath(),SwordaltSource.cacheDirectory(context).getCanonicalPath()));int failed=0;
        for(File f:selected){if(!parents.contains(f.getCanonicalFile().getParent())||!f.getName().endsWith(".json"))throw new IOException("无效缓存文件");
            if(f.exists()&&!f.delete()){failed++;continue;}
            File meta=new File(f.getParentFile(),f.getName().replace(".json",".meta"));if(meta.exists()&&!meta.delete())failed++;
        }
        return failed;
    }
    public static void cacheSettings(Activity a){
        if(RootPanel.blocked(a))return;File[] arr=cache(a).listFiles(f->f.getName().endsWith(".json"));List<File> files=arr==null?new ArrayList<>():new ArrayList<>(Arrays.asList(arr));File[] extra=SwordaltSource.cacheDirectory(a).listFiles(f->f.getName().endsWith(".json"));if(extra!=null)files.addAll(Arrays.asList(extra));files.sort(Comparator.comparing(File::getName));long total=0;for(File f:files)total+=f.length();
        String[] labels=new String[files.size()+1];labels[0]="删除全部已下载谱面（"+files.size()+" 张，"+String.format(Locale.ROOT,"%.1f",total/1048576.0)+" MiB）";
        for(int i=0;i<files.size();i++){File f=files.get(i);try{JSONObject m=new JSONObject(new String(read(new FileInputStream(new File(f.getParentFile(),f.getName().replace(".json",".meta"))),65536),StandardCharsets.UTF_8));labels[i+1]="["+m.optString("source","小飞机")+"] "+m.getString("title")+" / "+m.getString("level");}catch(Exception e){labels[i+1]=f.getName();}}
        new AlertDialog.Builder(a).setTitle("已下载谱面缓存").setItems(labels,(d,index)->new AlertDialog.Builder(a).setTitle("删除谱面缓存").setMessage("曲目目录会保留，下次识别后重新下载。"+(index==0?"\n删除全部缓存？":"\n"+labels[index])).setPositiveButton("删除",(x,w)->{
            if(RootPanel.blocked(a))return;List<File> selected=index==0?files:Collections.singletonList(files.get(index-1));int failed;try{failed=deleteCacheFiles(a,selected);}catch(Exception e){message(a,"删除未完成",e.getMessage());return;}
            message(a,"删除完成",failed==0?"缓存已删除，下次演奏会重新下载。":"有 "+failed+" 个文件删除失败，请重试。");}).setNegativeButton("取消",null).show()).setNegativeButton("关闭",null).show();
    }
}
