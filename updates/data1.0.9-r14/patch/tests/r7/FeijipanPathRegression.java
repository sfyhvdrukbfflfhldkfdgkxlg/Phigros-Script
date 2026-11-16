package io.github.phiscript.assets;

import android.content.Context;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.engine.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.json.*;

/** Exercises production catalog traversal and loading, including numeric web API IDs. */
public final class FeijipanPathRegression {
    static int checks,requests;
    static String profile="normal",downloadId;
    static byte[] chartBody;
    static JSONObject manifest;
    static final Map<String,List<JSONObject>> directories=new LinkedHashMap<>();
    static final Map<String,String> songFolders=new LinkedHashMap<>();
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Task{void run()throws Exception;}
    static void fails(Task task,String message)throws Exception{boolean failed=false;try{task.run();}catch(Exception expected){failed=true;}ok(failed,message);}
    static JSONObject copy(JSONObject value){return new JSONObject(value.toString());}
    static JSONObject file(long id,String name){return new JSONObject().put("fileType",1).put("fileId",id).put("name",name).put("fileName",name);}
    static JSONObject folder(long id,String name){return new JSONObject().put("fileType",2).put("folderId",id).put("name",name).put("folderName","文件夹");}
    static void add(String folder,JSONObject row){directories.computeIfAbsent(folder,k->new ArrayList<>()).add(row);}
    static JSONObject page(List<JSONObject> all,int number){JSONArray rows=new JSONArray();int start=(number-1)*110;for(int i=start;i<Math.min(all.size(),start+110);i++)rows.put(all.get(i));return new JSONObject().put("code",200).put("total",all.size()).put("totalPage",Math.max(1,(all.size()+109)/110)).put("list",rows);}
    static Map<String,String> query(URL url)throws Exception{Map<String,String> q=new HashMap<>();for(String part:url.getQuery().split("&")){String[] pair=part.split("=",2);q.put(URLDecoder.decode(pair[0],"UTF-8"),URLDecoder.decode(pair[1],"UTF-8"));}return q;}
    static byte[] serve(URL url)throws Exception{
        Map<String,String> q=query(url);ok(FeijipanSource.NEW_SHARE.equals(q.get("shareId")),"new share ID retained in API requests");
        if(url.getPath().equals("/ws/file/redirect")){
            if(FeijipanSource.encode("42|").equals(q.get("downloadId")))return manifest.toString().getBytes(StandardCharsets.UTF_8);
            downloadId=q.get("downloadId");if(chartBody==null)throw new AssertionError("unexpected chart download during indexing");return chartBody;
        }
        JSONObject response;
        if(url.getPath().equals("/ws/recommend/list"))response=new JSONObject().put("code",200).put("status",0).put("list",new JSONArray().put(new JSONObject().put("fileList",new JSONArray().put(folder(10,"Phigros")))));
        else{
            String id=q.get("folderId");int offset=Integer.parseInt(q.get("offset"));List<JSONObject> rows=new ArrayList<>(directories.get(id));
            if(profile.equals("missing")&&id.equals("1000"))rows.remove(0);
            if(profile.equals("loop")&&id.equals("10"))rows.add(folder(10,"循环目录"));
            response=page(rows,offset);
            if(profile.equals("badPage")&&id.equals("11")&&offset==2)response.put("list",new JSONArray());
            if(profile.equals("changedPage")&&id.equals("11")&&offset==2)response.put("total",333);
            if(profile.equals("duplicateManifest")&&id.equals("12"))response=page(Arrays.asList(file(43,"swordalt-catalog.json")),offset);
        }
        return response.toString().getBytes(StandardCharsets.UTF_8);
    }
    static void mockTransport(){URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url){requests++;return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}public long getContentLengthLong(){return -1;}public String getContentType(){return "application/json";}public InputStream getInputStream()throws IOException{try{return new ByteArrayInputStream(serve(url));}catch(Exception error){throw new IOException(error);}}public OutputStream getOutputStream(){return new ByteArrayOutputStream();}};}}:null);}
    static JSONObject find(JSONObject catalog,String path){JSONArray rows=catalog.getJSONArray("entries");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.getJSONObject("resource").getString("path").equals(path))return row;}throw new AssertionError(path);}
    static ChartLibrary one(Context c,JSONObject catalog,JSONObject row)throws Exception{return ExternalCharts.fromCatalog(c,copy(catalog).put("entries",new JSONArray().put(row)));}
    static byte[] script(String path)throws Exception{try(ZipFile z=new ZipFile("count-current/Phigros_Swordalt_All_Scripts.zip");InputStream in=z.getInputStream(z.getEntry("charts/"+path));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    static void buildAsset(Context c)throws Exception{
        JSONArray files=new JSONArray(new String(Files.readAllBytes(Paths.get("patch/data-tools/snapshot/feijipan-feyajI8g/files.json")),StandardCharsets.UTF_8));
        JSONObject catalog=FeijipanSource.resolve(FeijipanSource.NEW_SHARE,files,manifest,"Phigros/swordalt-catalog.json");ChartLibrary lib=ExternalCharts.fromCatalog(c,catalog);
        ok(catalog.getJSONArray("missing").length()==5&&lib.getLocations().size()==1052,"actual remote availability and missing paths preserved");
        Files.write(Paths.get("patch/assets/"+FeijipanSource.NEW_ASSET),catalog.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS built actual Feijipan catalog: "+lib.getLocations().size()+" charts, "+files.length()+" total remote files; hash metadata retained");
    }
    public static void main(String[] args)throws Exception{
        Context.root=Files.createTempDirectory("feijipan-paths").toFile();Context c=new Context();manifest=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/data-tools/snapshot/feijipan-feyajI8g/manifest.json")),StandardCharsets.UTF_8));
        if(args.length>0&&args[0].equals("--catalog")){buildAsset(c);return;}
        add("10",folder(11,"charts"));add("10",folder(12,"noise"));add("10",file(42,"swordalt-catalog.json"));add("10",file(44,"source-catalog.json"));add("10",file(45,"noise-index.json"));
        add("12",file(46,"EZ.json"));add("12",file(47,"IN.json"));
        JSONArray rows=manifest.getJSONArray("entries");for(int i=0;i<rows.length();i++){
            JSONObject resource=rows.getJSONObject(i);String[] parts=resource.getString("path").split("/");String id=songFolders.get(parts[0]);
            if(id==null){id=Integer.toString(1000+songFolders.size());songFolders.put(parts[0],id);add("11",folder(Long.parseLong(id),parts[0]));}
            add(id,file(90000000000L+i,parts[1]));
        }
        ok(songFolders.size()==334,"fixture covers all 334 song directories");mockTransport();
        JSONObject catalog=FeijipanSource.catalog(FeijipanSource.NEW_SHARE,new AtomicBoolean());
        ok(catalog.getJSONArray("entries").length()==1057,"traversal exceeds old 128 folder cap and includes all 1057 charts");ok(catalog.getJSONArray("missing").length()==0,"all pages joined");
        Set<String> actualPaths=new HashSet<>();int noise=0,hidden=0;for(int i=0;i<1057;i++){
            JSONObject row=catalog.getJSONArray("entries").getJSONObject(i),resource=row.getJSONObject("resource");
            ok(row.getString("path").equals("Phigros/charts/"+resource.getString("path")),"name preferred over generic folderName; exact full path");ok(actualPaths.add(row.getString("path")),"unique path");
            if(resource.getInt("noiseCount")>0)noise++;if(!resource.optBoolean("siteVisible",true))hidden++;
        }
        ok(noise==25&&hidden==19,"noise and tutorial/variant metadata retained");
        ChartLibrary library=ExternalCharts.fromCatalog(c,catalog);ok(library.getLocations().size()==1057,"all charts manually selectable");ok(library.songs().size()==327,"only visible songs participate in OCR");
        profile="missing";JSONObject missing=FeijipanSource.catalog(FeijipanSource.NEW_SHARE,null);ok(missing.getJSONArray("entries").length()==1056&&missing.getJSONArray("missing").length()==1,"incomplete upload indexes only present charts");
        profile="badPage";fails(()->FeijipanSource.catalog(FeijipanSource.NEW_SHARE,null),"empty middle page rejected");profile="changedPage";fails(()->FeijipanSource.catalog(FeijipanSource.NEW_SHARE,null),"changing pagination rejected");profile="loop";fails(()->FeijipanSource.catalog(FeijipanSource.NEW_SHARE,null),"folder loop rejected");profile="duplicateManifest";fails(()->FeijipanSource.catalog(FeijipanSource.NEW_SHARE,null),"multiple manifests rejected");profile="normal";
        int before=requests;fails(()->FeijipanSource.catalog(FeijipanSource.NEW_SHARE,new AtomicBoolean(true)),"cancellation before catalog request");ok(requests==before,"cancelled scan does not contact server");
        JSONObject legacy=FeijipanSource.resolve("eTyQ30l7",new JSONArray().put(new JSONObject().put("id","123").put("name","Legacy Song - IN [12].json")).put(new JSONObject().put("id","124").put("name","noise-index.json")),null,null);ok(ExternalCharts.fromCatalog(c,legacy).getLocations().size()==1,"legacy named layout works and metadata excluded");
        JSONArray wrongPlace=new JSONArray().put(new JSONObject().put("id","555").put("path","Other/charts/Glaciaxion.SunsetRay.0/EZ.json").put("name","EZ.json"));fails(()->FeijipanSource.resolve(FeijipanSource.NEW_SHARE,wrongPlace,manifest,"Phigros/swordalt-catalog.json"),"another root cannot satisfy chart path");
        JSONObject selected=find(catalog,"Message.くるぶっこちゃん.0/EZ.json");ChartLibrary playable=one(c,catalog,selected);String song=playable.songs().get(0).getId();chartBody=script(selected.getJSONObject("resource").getString("path"));byte[] good=chartBody.clone();
        before=requests;Chart first=ExternalCharts.loadSelected(playable,song,"EZ",null),second=ExternalCharts.loadSelected(playable,song,"EZ",null);ok(first!=second&&requests==before+2,"each performance downloads and verifies fresh data");ok(FeijipanSource.encode(selected.getString("id")+"|").equals(downloadId),"selected chart uses its actual numeric file ID");ok(first.getNotes().size()==100&&BlockAreaSet.count(first)==24,"native chart notes and noise survive download/parse");
        File cached=new File(c.getFilesDir(),"phigros-cloud-v1/charts/"+FeijipanSource.NEW_SHARE+"-"+selected.getString("id")+".json");ok(Arrays.equals(Files.readAllBytes(cached.toPath()),good),"exact native script cached");
        chartBody=good.clone();chartBody[chartBody.length-2]^=1;fails(()->ExternalCharts.loadSelected(playable,song,"EZ",null),"same-length changed content rejected by hash");ok(Arrays.equals(good,Files.readAllBytes(cached.toPath())),"failed verification retains valid cache and never plays fallback");chartBody=Arrays.copyOf(good,good.length-1);fails(()->ExternalCharts.loadSelected(playable,song,"EZ",null),"truncated content rejected");chartBody=good;
        JSONObject bad=copy(selected);bad.getJSONObject("resource").put("noiseCount",23);ChartLibrary badNoise=one(c,catalog,bad);fails(()->ExternalCharts.loadSelected(badNoise,song,"EZ",null),"noise metadata mismatch stops playback");
        bad=copy(selected).put("path","Other/charts/Message.くるぶっこちゃん.0/EZ.json");final JSONObject wrongRoot=bad;fails(()->one(c,catalog,wrongRoot),"saved catalog cannot mix roots");
        bad=copy(selected).put("path","Phigros/../charts/Message.くるぶっこちゃん.0/EZ.json");final JSONObject traversal=bad;fails(()->one(c,catalog,traversal),"unsafe saved path rejected");
        bad=copy(selected).put("name","Wrong Song - EZ [1].json");final JSONObject wrongTitle=bad;fails(()->one(c,catalog,wrongTitle),"identity mismatch rejected");
        bad=copy(selected);bad.remove("resource");final JSONObject noIntegrity=bad;fails(()->one(c,catalog,noIntegrity),"new format cannot omit verification data");
        AtomicBoolean cancelled=new AtomicBoolean(true);before=requests;fails(()->ExternalCharts.loadSelected(playable,song,"EZ",cancelled),"cancelled performance rejected");ok(requests==before,"no network on cancelled performance");
        ExternalCharts.commitPhigros(c,playable,"cloud");ok(RuntimeState.INSTANCE.getLibrary()==playable&&RuntimeState.INSTANCE.getPhiraLibrary()==null,"cloud activates only Phigros library");
        ok(ExternalCharts.deleteCacheFiles(c,Collections.singletonList(cached))==0&&!cached.exists(),"new share cache can be deleted in settings");
        JSONObject bundled=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/assets/"+FeijipanSource.NEW_ASSET)),StandardCharsets.UTF_8));ChartLibrary bundledLib=ExternalCharts.fromCatalog(c,bundled);ok(bundledLib.getLocations().size()==1052,"bundled real share map validates");
        java.lang.reflect.Method restore=ExternalCharts.class.getDeclaredMethod("restoreCloud",Context.class);restore.setAccessible(true);io.github.phiscript.AppSettings prefs=new io.github.phiscript.AppSettings(c);File saved=new File(c.getFilesDir(),"phigros-cloud-v1/catalog.json");
        prefs.save("phigros_library_source","cloud");ChartLibrary migrated=(ChartLibrary)restore.invoke(null,c);ok(migrated.getLocations().size()==1022,"R6 bundled-only selection survives upgrade");ok(new JSONObject(new String(Files.readAllBytes(saved.toPath()),StandardCharsets.UTF_8)).getString("share").equals("eTyQ30l7"),"migrated legacy catalog persisted");
        ok(saved.delete(),"remove test catalog");prefs.save("phigros_library_source","");ChartLibrary fresh=(ChartLibrary)restore.invoke(null,c);ok(fresh.getLocations().size()==1052,"fresh install defaults to new share");prefs.save("phigros_library_source","cloud");ChartLibrary restarted=(ChartLibrary)restore.invoke(null,c);ok(restarted.getLocations().size()==1052,"new default stays selected after restart");
        System.out.println("PASS Feijipan paths: "+checks+" assertions; 334 directories, 1057 charts, 25 noise charts, 19 hidden variants; pagination, integrity, cancellation, isolated mode and cache deletion");
    }
}
