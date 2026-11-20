package io.github.phiscript.input;

import android.content.Context;
import android.net.Uri;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.*;
import io.github.phiscript.engine.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Must run against the final hooked APK, not only replacement Java classes. */
public final class NativeImportPreparationRegression {
    private static int checks;private static final Viewport V=new Viewport(0,0,1000,600);private static final TouchBounds B=new TouchBounds(0,0,999,599);
    private static final class CancelPreparationContext extends Context {
        boolean cancel=true;
        @Override public Object getSystemService(String name){if(cancel&&WINDOW_SERVICE.equals(name))throw new CancellationException("cancel preparation after source commit");return super.getSystemService(name);}
    }
    private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private static void settings()throws Exception{
        Context.root=Files.createTempDirectory("r20-native-preparation").toFile();AppSettings.values.clear();AppSettings.edges=AppSettings.inward=false;AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=true;
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new Context());GesturePlan.clearPrepared();
    }
    private static File pack(Context c,String title)throws Exception{
        return pack(c,title,0);
    }
    private static File pack(Context c,String title,double offset)throws Exception{
        File f=File.createTempFile("r20-native-original-",".pez",c.getCacheDir());
        org.json.JSONObject chart=new org.json.JSONObject(new String(Files.readAllBytes(new File("patch/tests/r17/fixtures/pgr_v3_multihold.json").toPath()),StandardCharsets.UTF_8)).put("offset",offset);
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(f))){zip.putNextEntry(new ZipEntry("chart.json"));zip.write(chart.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry("info.txt"));zip.write(("#\nName: "+title+"\nLevel: IN Lv.13\nChart: chart.json\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();}return f;
    }
    private static File[] prepared(Context c){File[] f=new File(c.getFilesDir(),"prepared-touch-v1").listFiles(x->x.getName().endsWith(".ptg"));return f==null?new File[0]:f;}
    private static GesturePlan playback(Chart chart,Context c){return GesturePlan.forPlayback(chart,GestureProfile.read(c),V,B);}
    private static void playDetached(Chart chart)throws Exception{
        try{
            for(boolean root:new boolean[]{false,true}){
                io.github.phiscript.root.InputBackend.rootEnabled=root;
                android.os.SystemClock.now=100000;
                TouchService service=new TouchService();
                ChartMultiTouchPlayer.play(service,chart,100000,V,new java.util.concurrent.atomic.AtomicBoolean(),
                    status->kotlin.Unit.INSTANCE,new java.util.concurrent.atomic.AtomicBoolean(),new java.util.concurrent.atomic.AtomicBoolean());
                if(root)ok(!io.github.phiscript.root.RootClient.last.heads.isEmpty()&&io.github.phiscript.root.RootClient.last.active.isEmpty(),"standalone Root playback did not send and release its contacts");
                else ok(service.sends>0&&service.roots.isEmpty(),"standalone accessibility playback did not send and release its contacts");
            }
        }finally{io.github.phiscript.root.InputBackend.rootEnabled=false;TouchService.Companion.current=null;}
    }
    private static void requirePreparation(Chart chart,Context c,String label){
        try{playback(chart,c);throw new AssertionError(label+" computed coordinates during playback");}
        catch(IllegalStateException expected){
            ok(expected.getMessage().contains("预计算触点")||expected.getMessage().contains("Prepare touch points"),label+" did not explain how to prepare");
        }
        ok(GesturePlan.cachedPrepared(chart,GestureProfile.read(c),V,B)==null,label+" left a synthesized memory plan");
    }
    private static void phiraImportAndLoad()throws Exception{
        settings();Context c=new Context();File original=pack(c,"Native Import Preparation");byte[] originalBytes=Files.readAllBytes(original.toPath());
        PhiraLibrary library=PhiraLibrary.Companion.importFiles(c,Collections.singletonList(Uri.fromFile(original)),s->kotlin.Unit.INSTANCE);
        ok(library.getEntries().size()==1&&prepared(c).length==1,"actual native importFiles did not persist prepared trajectory");PhiraEntry entry=library.getEntries().get(0);File cache=prepared(c)[0];byte[] encoded=Files.readAllBytes(cache.toPath());
        File source=new File(c.getFilesDir(),"phira/charts/"+entry.getId()+".json");
        File saved=new File(c.getCacheDir(),"imported-chart.saved");
        Chart originalChart=library.load(entry.getId(),entry.getLevel());
        Files.move(source.toPath(),saved.toPath());
        GesturePlan.clearPrepared();RuntimeState.logs.clear();
        SessionChartSource session=SessionChartSource.phira(c,PhiraLibrary.Companion.restore(c));
        Chart loaded=session.load(entry.getId(),entry.getLevel());
        ok(playback(loaded,c).hasPreparedTrajectory(),"actual native Phira load did not install dense prepared plan");ok(playback(loaded,c).preparedSampleCount()>100,"native import lost sustained samples");long salt=TapMod.preparationSalt(loaded);
        ok(Arrays.equals(encoded,Files.readAllBytes(cache.toPath())),"native load rewrote valid disk cache instead of reuse");
        ok(loaded.getDurationSeconds()==originalChart.getDurationSeconds()&&loaded.getOffsetSeconds()==originalChart.getOffsetSeconds(),"standalone timing differs from imported chart");
        playDetached(loaded);
        ok(RuntimeState.logs.stream().noneMatch(s->s.contains("正在预计算")),"session started the planner");
        GesturePlan.clearPrepared();Chart again=session.load(entry.getId(),entry.getLevel());ok(playback(again,c).hasPreparedTrajectory()&&TapMod.preparationSalt(again)==salt,"reloaded chart did not recover prepared salt/path");
        byte[] corrupt=encoded.clone();corrupt[corrupt.length/2]^=1;Files.write(cache.toPath(),corrupt);GesturePlan.clearPrepared();
        try{session.load(entry.getId(),entry.getLevel());throw new AssertionError("corruption passed before retry");}
        catch(IOException expected){ok(expected.getMessage().contains("预计算触点"),"missing preparation hint");}
        Files.move(saved.toPath(),source.toPath());
        Chart damaged=PhiraLibrary.Companion.restore(c).load(entry.getId());
        requirePreparation(damaged,c,"corrupt Phira coordinate cache");
        ok(Arrays.equals(corrupt,Files.readAllBytes(cache.toPath())),"playback rewrote the damaged coordinate file");
        PreparedChartStore.preparePhira(c,library,entry,null);
        ok(playback(damaged,c).hasPreparedTrajectory()&&!Arrays.equals(corrupt,Files.readAllBytes(cache.toPath())),"explicit prepare failed to repair corrupted coordinates");
        AppSettings.values.put("tap_noise_clock_guard_ms","24");
        requirePreparation(damaged,c,"changed Phira settings");
        ok(prepared(c).length==1,"settings mismatch created another coordinate file during playback");
        AppSettings.values.remove("tap_noise_clock_guard_ms");
        ok(Arrays.equals(originalBytes,Files.readAllBytes(original.toPath())),"native preparation changed original PEZ");
        Context local=ExternalCharts.localStorage(c);PhiraLibrary localLibrary=PhiraLibrary.Companion.importFiles(local,Collections.singletonList(Uri.fromFile(original)),s->kotlin.Unit.INSTANCE);
        ok(prepared(local).length==1&&prepared(c).length==1,"native Phigros import did not use isolated persisted preparation");
        PhiraEntry localEntry=localLibrary.getEntries().get(0);
        Files.delete(new File(local.getFilesDir(),"phira/charts/"+localEntry.getId()+".json").toPath());
        Method adapt=ExternalCharts.class.getDeclaredMethod("fromLocal",Context.class,PhiraLibrary.class);adapt.setAccessible(true);
        ChartLibrary packages=(ChartLibrary)adapt.invoke(null,c,PhiraLibrary.Companion.restore(local));
        ChartLocation location=packages.getLocations().get(0);
        SessionChartSource phigros=SessionChartSource.phigros(c,packages,new java.util.concurrent.atomic.AtomicBoolean());
        Chart localChart=phigros.load(SessionChartSource.id(location),location.getDifficulty());
        ok(playback(localChart,local).hasPreparedTrajectory(),"Phigros package could not run from its standalone cache");
    }
    private static void committedSourceSurvivesCancelledPreparation()throws Exception{
        settings();CancelPreparationContext c=new CancelPreparationContext();File original=pack(c,"Native Cancelled Preparation");byte[] before=Files.readAllBytes(original.toPath());
        PhiraLibrary library=PhiraLibrary.Companion.importFiles(c,Collections.singletonList(Uri.fromFile(original)),s->kotlin.Unit.INSTANCE);
        ok(library.getEntries().size()==1&&PhiraLibrary.Companion.restore(c)!=null,"post-commit preparation cancellation removed imported source/index");ok(prepared(c).length==0,"cancelled native preparation committed partial trajectory");
        PhiraEntry entry=library.getEntries().get(0);ok(new File(c.getFilesDir(),"phira/charts/"+entry.getId()+".json").isFile()&&Arrays.equals(before,Files.readAllBytes(original.toPath())),"cancelled preparation changed chart source or original PEZ");
        c.cancel=false;
        Chart loaded=library.load(entry.getId());
        requirePreparation(loaded,c,"cancelled Phira preparation");
        ok(prepared(c).length==0,"loading a cancelled import wrote a coordinate cache");
        PreparedChartStore.preparePhira(c,library,entry,null);
        ok(playback(loaded,c).hasPreparedTrajectory()&&prepared(c).length==1,"explicit preparation failed to resume a cancelled import");
    }
    private static void nativeChartLibraryCache()throws Exception{
        settings();Context c=new Context();List<String> entries=Collections.singletonList("unused.bundle");PackageSnapshot snapshot=new PackageSnapshot("test.native.fixture","v1",1,0,entries);
        ChartSource source=new ChartSource(){public PackageSnapshot inspect(){return snapshot;}public List<String> entries(PackageSnapshot p){return entries;}public byte[] read(PackageSnapshot p,String entry,int max){throw new AssertionError("native cached load unexpectedly requested a package bundle");}};
        ChartLocation location=new ChartLocation("native.fixture","IN","unused","unused.bundle");Constructor<ChartLibrary> constructor=ChartLibrary.class.getDeclaredConstructor(Context.class,PackageSnapshot.class,List.class,ChartSource.class,String.class);constructor.setAccessible(true);
        ChartLibrary library=constructor.newInstance(c,snapshot,Collections.singletonList(location),source,"native fixture");Field directory=ChartLibrary.class.getDeclaredField("cache");directory.setAccessible(true);File chartCache=(File)directory.get(library);
        byte[] json=Files.readAllBytes(new File("patch/tests/r17/fixtures/pgr_v3_multihold.json").toPath());File cached=new File(chartCache,PreparedChartStore.digest("native.fixture/IN".getBytes(StandardCharsets.UTF_8))+".json");Files.write(cached.toPath(),json);
        Chart chart=library.load("native.fixture","IN");
        ok(chart.getNotes().size()==7,"native ChartLibrary cached parse lost fixture");
        requirePreparation(chart,c,"unprepared native Phigros chart");
        ok(prepared(c).length==0,"native ChartLibrary load prepared coordinates implicitly");
        rejectUnavailableDownload(library,location,"unprepared APK");
        ExternalCharts.importSelected(c,library,location.getSongId(),location.getDifficulty(),null);
        ok(prepared(c).length==1&&playback(chart,c).hasPreparedTrajectory(),"explicit preparation did not persist native Phigros coordinates");
        byte[] encoded=Files.readAllBytes(prepared(c)[0].toPath());GesturePlan.clearPrepared();
        ok(Arrays.equals(json,Files.readAllBytes(cached.toPath())),"preparation changed native source chart cache");
        Files.delete(cached.toPath());
        Chart again=ExternalCharts.loadSelected(library,"native.fixture","IN",null);
        ok(playback(again,c).hasPreparedTrajectory()&&Arrays.equals(encoded,Files.readAllBytes(prepared(c)[0].toPath())),"native APK did not reopen the prepared timeline independently");
    }
    private static void rejectUnavailableDownload(ChartLibrary library,ChartLocation where,String label)throws Exception{
        try{
            ExternalCharts.loadSelected(library,where.getSongId(),where.getDifficulty(),null);
            throw new AssertionError(label+" played a missing or damaged chart");
        }catch(IOException expected){
            ok(expected.getMessage().contains("预计算触点")||expected.getMessage().contains("Prepare touch points"),label+" omitted the preparation instructions");
        }
    }
    private static void onlineCatalogPlaybackIsOffline()throws Exception{
        URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url){throw new AssertionError("Playback contacted "+url);}
        }:null);
        settings();
        Context c=new Context();
        byte[] json=Files.readAllBytes(new File("patch/tests/r17/fixtures/pgr_v3_multihold.json").toPath());
        JSONObject cloudCatalog=new JSONObject().put("share","testShare").put("entries",new JSONArray().put(
            new JSONObject().put("id","17").put("name","Prepared fixture - IN [13].json")));
        ChartLibrary cloud=ExternalCharts.fromCatalog(c,cloudCatalog);
        ChartLocation location=cloud.getLocations().get(0);
        rejectUnavailableDownload(cloud,location,"Feijipan cache miss");
        File directory=new File(c.getFilesDir(),"phigros-cloud-v1/charts");
        File source=new File(directory,"testShare-17.json");
        Files.write(source.toPath(),json);
        JSONObject meta=new JSONObject().put("title","Prepared fixture").put("level","IN")
            .put("bytes",json.length).put("sha256",PreparedChartStore.digest(json));
        Files.write(new File(directory,"testShare-17.meta").toPath(),meta.toString().getBytes(StandardCharsets.UTF_8));
        rejectUnavailableDownload(cloud,location,"Feijipan source without coordinates");
        ExternalCharts.importSelected(c,cloud,location.getSongId(),location.getDifficulty(),null);
        Files.delete(source.toPath());GesturePlan.clearPrepared();
        Chart fromCloud=ExternalCharts.loadSelected(cloud,location.getSongId(),location.getDifficulty(),null);
        ok(playback(fromCloud,c).hasPreparedTrajectory(),"explicit Feijipan prepare failed to cache coordinates");
        File cloudMeta=new File(directory,"testShare-17.meta");
        Files.write(new File(cloudMeta+".bak").toPath(),Files.readAllBytes(cloudMeta.toPath()));
        Files.write(new File(cloudMeta+".new").toPath(),new byte[]{0});
        Method delete=ExternalCharts.class.getDeclaredMethod("deleteCacheFiles",Context.class,List.class);delete.setAccessible(true);
        ok((int)delete.invoke(null,c,Collections.singletonList(source))==0&&!new File(cloudMeta+".bak").exists()&&!new File(cloudMeta+".new").exists(),"deletion retained a playable chart binding");
        rejectUnavailableDownload(cloud,location,"deleted Feijipan entry");

        byte[] sha1=MessageDigest.getInstance("SHA-1").digest(concat(("blob "+json.length+'\0').getBytes(StandardCharsets.US_ASCII),json));
        StringBuilder hash=new StringBuilder();
        for(byte b:sha1)hash.append(String.format(Locale.ROOT,"%02x",b&255));
        String revision="0123456789abcdef0123456789abcdef01234567";
        JSONObject row=new JSONObject().put("title","Swordalt fixture").put("level","13").put("difficulty","IN")
            .put("path","Fixture/IN.json").put("bytes",json.length).put("sha1",hash.toString());
        JSONObject catalog=new JSONObject().put("phiscriptSwordaltCatalog",1).put("repository",SwordaltSource.REPOSITORY)
            .put("chartCommit",revision).put("entries",new JSONArray().put(row));
        ChartLibrary swordalt=ExternalCharts.fromSwordaltCatalog(c,catalog);
        ChartLocation swordaltLocation=swordalt.getLocations().get(0);
        rejectUnavailableDownload(swordalt,swordaltLocation,"Swordalt cache miss");
        String stem=PreparedChartStore.digest((revision+"/Fixture/IN.json").getBytes(StandardCharsets.UTF_8));
        File swordaltFile=new File(SwordaltSource.cacheDirectory(c),stem+".json");
        Files.write(swordaltFile.toPath(),json);
        rejectUnavailableDownload(swordalt,swordaltLocation,"unprepared Swordalt source");
        ExternalCharts.importSelected(c,swordalt,swordaltLocation.getSongId(),swordaltLocation.getDifficulty(),null);
        Files.delete(swordaltFile.toPath());GesturePlan.clearPrepared();
        Chart cached=ExternalCharts.loadSelected(swordalt,swordaltLocation.getSongId(),swordaltLocation.getDifficulty(),null);
        ok(cached.getNotes().size()==7&&playback(cached,c).hasPreparedTrajectory(),"Swordalt standalone cache failed to load");
        File metadata=new File(swordaltFile.getParentFile(),stem+".meta");
        JSONObject changed=new JSONObject(new String(Files.readAllBytes(metadata.toPath()),StandardCharsets.UTF_8));
        changed.put("sha1","ffffffffffffffffffffffffffffffffffffffff");Files.write(metadata.toPath(),changed.toString().getBytes(StandardCharsets.UTF_8));
        rejectUnavailableDownload(swordalt,swordaltLocation,"Swordalt edition mismatch");
    }
    private static byte[] concat(byte[] first,byte[] second)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream(first.length+second.length);
        out.write(first);
        out.write(second);
        return out.toByteArray();
    }
    private static void secondImportReuse()throws Exception{
        settings();
        Context c=new Context();
        Uri first=Uri.fromFile(pack(c,"Existing cached chart",0)), second=Uri.fromFile(pack(c,"New chart",.137));
        importBatch(c,first);
        ok(prepared(c).length==1,"first import fixture did not prepare");
        File existing=prepared(c)[0];
        byte[] original=Files.readAllBytes(existing.toPath());
        long marked=existing.lastModified()-60000;
        ok(existing.setLastModified(marked),"could not mark first cache");
        marked=existing.lastModified();
        PhiraLibrary library=importBatch(c,second);
        ok(library.getEntries().size()==2&&prepared(c).length==2,"second import did not prepare new chart");
        ok(existing.lastModified()==marked&&Arrays.equals(original,Files.readAllBytes(existing.toPath())),"unrelated import touched old cache");
        ok(RuntimeState.logs.stream().anyMatch(s->s.contains("复用 0 个，计算 1 个")),"import scanned outside its batch");
        byte[] damaged=original.clone();damaged[damaged.length/2]^=1;
        Files.write(existing.toPath(),damaged);
        GesturePlan.clearPrepared();
        importBatch(c,first,second);
        ok(RuntimeState.logs.stream().anyMatch(s->s.contains("复用 1 个，计算 1 个"))&&!Arrays.equals(damaged,Files.readAllBytes(existing.toPath())),"bad cached generation was silently reused during reimport");
        AppSettings.values.put("tap_noise_clock_guard_ms","24");
        importBatch(c,first,second);
        ok(RuntimeState.logs.stream().anyMatch(s->s.contains("复用 0 个，计算 2 个")),"changed settings reused an old geometry generation");
    }
    private static PhiraLibrary importBatch(Context c,Uri... sources){
        RuntimeState.logs.clear();
        return PhiraLibrary.Companion.importFiles(c,Arrays.asList(sources),s->kotlin.Unit.INSTANCE);
    }
    public static void main(String[] args)throws Exception{
        phiraImportAndLoad();
        committedSourceSurvivesCancelledPreparation();
        nativeChartLibraryCache();
        secondImportReuse();
        onlineCatalogPlaybackIsOffline();
        System.out.println("PASS "+checks+" final-native-hook checks: explicit preparation, disk-only reload, integrity, cancellation, settings invalidation and offline cloud playback.");
    }
}
