package io.github.phiscript.input;

import android.content.Context;
import android.net.Uri;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.*;
import io.github.phiscript.engine.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.*;

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
    private static void phiraImportAndLoad()throws Exception{
        settings();Context c=new Context();File original=pack(c,"Native Import Preparation");byte[] originalBytes=Files.readAllBytes(original.toPath());
        PhiraLibrary library=PhiraLibrary.Companion.importFiles(c,Collections.singletonList(Uri.fromFile(original)),s->kotlin.Unit.INSTANCE);
        ok(library.getEntries().size()==1&&prepared(c).length==1,"actual native importFiles did not persist prepared trajectory");PhiraEntry entry=library.getEntries().get(0);File cache=prepared(c)[0];byte[] encoded=Files.readAllBytes(cache.toPath());
        GesturePlan.clearPrepared();Chart loaded=PhiraLibrary.Companion.restore(c).load(entry.getId(),entry.getLevel());
        ok(playback(loaded,c).hasPreparedTrajectory(),"actual native Phira load did not install dense prepared plan");ok(playback(loaded,c).preparedSampleCount()>100,"native import lost sustained samples");long salt=TapMod.preparationSalt(loaded);
        ok(Arrays.equals(encoded,Files.readAllBytes(cache.toPath())),"native load rewrote valid disk cache instead of reuse");
        GesturePlan.clearPrepared();Chart again=PhiraLibrary.Companion.restore(c).load(entry.getId());ok(playback(again,c).hasPreparedTrajectory()&&TapMod.preparationSalt(again)==salt,"native reloaded chart did not recover prepared salt/path");
        byte[] corrupt=encoded.clone();corrupt[corrupt.length/2]^=1;Files.write(cache.toPath(),corrupt);GesturePlan.clearPrepared();
        Chart repaired=PhiraLibrary.Companion.restore(c).load(entry.getId());ok(playback(repaired,c).hasPreparedTrajectory()&&!Arrays.equals(corrupt,Files.readAllBytes(cache.toPath())),"native load failed to rebuild corrupted preparation");
        ok(Arrays.equals(originalBytes,Files.readAllBytes(original.toPath())),"native preparation changed original PEZ");
        Context local=ExternalCharts.localStorage(c);PhiraLibrary localLibrary=PhiraLibrary.Companion.importFiles(local,Collections.singletonList(Uri.fromFile(original)),s->kotlin.Unit.INSTANCE);
        ok(prepared(local).length==1&&prepared(c).length==1,"native Phigros import did not use isolated persisted preparation");Chart localChart=localLibrary.load(localLibrary.getEntries().get(0).getId());ok(playback(localChart,local).hasPreparedTrajectory(),"native local Phigros load lacked prepared plan");
    }
    private static void committedSourceSurvivesCancelledPreparation()throws Exception{
        settings();CancelPreparationContext c=new CancelPreparationContext();File original=pack(c,"Native Cancelled Preparation");byte[] before=Files.readAllBytes(original.toPath());
        PhiraLibrary library=PhiraLibrary.Companion.importFiles(c,Collections.singletonList(Uri.fromFile(original)),s->kotlin.Unit.INSTANCE);
        ok(library.getEntries().size()==1&&PhiraLibrary.Companion.restore(c)!=null,"post-commit preparation cancellation removed imported source/index");ok(prepared(c).length==0,"cancelled native preparation committed partial trajectory");
        PhiraEntry entry=library.getEntries().get(0);ok(new File(c.getFilesDir(),"phira/charts/"+entry.getId()+".json").isFile()&&Arrays.equals(before,Files.readAllBytes(original.toPath())),"cancelled preparation changed chart source or original PEZ");
        c.cancel=false;Chart loaded=library.load(entry.getId());ok(playback(loaded,c).hasPreparedTrajectory()&&prepared(c).length==1,"cancelled preparation could not resume on next actual load");
    }
    private static void nativeChartLibraryCache()throws Exception{
        settings();Context c=new Context();List<String> entries=Collections.singletonList("unused.bundle");PackageSnapshot snapshot=new PackageSnapshot("test.native.fixture","v1",1,0,entries);
        ChartSource source=new ChartSource(){public PackageSnapshot inspect(){return snapshot;}public List<String> entries(PackageSnapshot p){return entries;}public byte[] read(PackageSnapshot p,String entry,int max){throw new AssertionError("native cached load unexpectedly requested a package bundle");}};
        ChartLocation location=new ChartLocation("native.fixture","IN","unused","unused.bundle");Constructor<ChartLibrary> constructor=ChartLibrary.class.getDeclaredConstructor(Context.class,PackageSnapshot.class,List.class,ChartSource.class,String.class);constructor.setAccessible(true);
        ChartLibrary library=constructor.newInstance(c,snapshot,Collections.singletonList(location),source,"native fixture");Field directory=ChartLibrary.class.getDeclaredField("cache");directory.setAccessible(true);File chartCache=(File)directory.get(library);
        byte[] json=Files.readAllBytes(new File("patch/tests/r17/fixtures/pgr_v3_multihold.json").toPath());File cached=new File(chartCache,PreparedChartStore.digest("native.fixture/IN".getBytes(StandardCharsets.UTF_8))+".json");Files.write(cached.toPath(),json);
        Chart chart=library.load("native.fixture","IN");ok(chart.getNotes().size()==7,"native ChartLibrary cached parse lost fixture");ok(prepared(c).length==1&&playback(chart,c).hasPreparedTrajectory(),"actual cached ChartLibrary.load did not invoke preparation hook");
        byte[] encoded=Files.readAllBytes(prepared(c)[0].toPath());GesturePlan.clearPrepared();Chart again=library.load("native.fixture","IN");ok(playback(again,c).hasPreparedTrajectory()&&Arrays.equals(encoded,Files.readAllBytes(prepared(c)[0].toPath())),"native ChartLibrary cached reload did not reuse encoded trajectory");ok(Arrays.equals(json,Files.readAllBytes(cached.toPath())),"preparation changed native source chart cache");
    }
    private static void secondImportReuse()throws Exception{
        settings();Context c=new Context();PhiraLibrary.Companion.importFiles(c,Collections.singletonList(Uri.fromFile(pack(c,"Existing cached chart",0))),s->kotlin.Unit.INSTANCE);ok(prepared(c).length==1,"first import fixture did not prepare");File existing=prepared(c)[0];byte[] original=Files.readAllBytes(existing.toPath());RuntimeState.logs.clear();
        PhiraLibrary library=PhiraLibrary.Companion.importFiles(c,Collections.singletonList(Uri.fromFile(pack(c,"New chart",.137))),s->kotlin.Unit.INSTANCE);
        ok(library.getEntries().size()==2&&prepared(c).length==2,"second import did not calculate the genuinely new chart");ok(RuntimeState.logs.stream().anyMatch(s->s.contains("复用 1 个，计算 1 个")),"second import reparsed existing charts instead of verified reuse");ok(Arrays.equals(original,Files.readAllBytes(existing.toPath())),"second import rewrote a verified existing cache");
        byte[] damaged=original.clone();damaged[damaged.length/2]^=1;Files.write(existing.toPath(),damaged);GesturePlan.clearPrepared();RuntimeState.logs.clear();PreparedChartStore.afterPhiraImport(c,PhiraLibrary.Companion.restore(c));
        ok(RuntimeState.logs.stream().anyMatch(s->s.contains("复用 1 个，计算 1 个"))&&!Arrays.equals(damaged,Files.readAllBytes(existing.toPath())),"bad cached generation was silently reused during import");
        AppSettings.values.put("tap_noise_clock_guard_ms","24");RuntimeState.logs.clear();PreparedChartStore.afterPhiraImport(c,PhiraLibrary.Companion.restore(c));ok(RuntimeState.logs.stream().anyMatch(s->s.contains("复用 0 个，计算 2 个")),"changed settings reused an old geometry generation");
    }
    public static void main(String[] args)throws Exception{phiraImportAndLoad();committedSourceSurvivesCancelledPreparation();nativeChartLibraryCache();secondImportReuse();System.out.println("PASS "+checks+" final-native-hook checks: actual Phira/Phigros import persistence, actual load cache reuse/corrupt rebuild, committed-source cancellation retention, native ChartLibrary hooks and verified second-import reuse.");}
}
