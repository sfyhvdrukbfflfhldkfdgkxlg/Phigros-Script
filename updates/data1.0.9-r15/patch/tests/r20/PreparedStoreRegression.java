package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.ExternalCharts;
import io.github.phiscript.engine.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exercises the actual disk store, real dense encoder/decoder and planner. */
public final class PreparedStoreRegression {
    private static int checks,calls;private static boolean forbid;private static AtomicBoolean cancelDuring;
    private static final Viewport V=new Viewport(0,0,1000,600);private static final TouchBounds B=new TouchBounds(0,0,999,599);
    private static final GestureProfile PROFILE=new GestureProfile(true,70,.12f,true,true,true);
    private static final class FilesContext extends Context {
        private final File base;FilesContext(File base){this.base=base;}
        @Override public File getFilesDir(){File f=new File(base,"files");f.mkdirs();return f;}
        @Override public File getCacheDir(){File f=new File(base,"cache");f.mkdirs();return f;}
    }
    private interface Work{void run()throws Exception;}
    private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private static Context context(String name)throws Exception{return new FilesContext(Files.createTempDirectory("r20-store-"+name).toFile());}
    private static Chart chart(){
        Note note=new Note(1,3,.02,.1,.5,1,0,true,0);
        return Chart.Companion.fromNative$app_debug(Collections.singletonList(note),0,(n,t,v)->{calls++;if(forbid)throw new AssertionError("disk cache hit evaluated geometry");if(cancelDuring!=null)cancelDuring.set(true);return new Point(v.getLeft()+v.getWidth()*.5f,v.getTop()+v.getHeight()*.5f);},(n,t,v)->new Point(0,1));
    }
    private static Chart bound(Context context,byte[] bytes){Chart c=chart();PreparedChartStore.bind(c,context,bytes);return c;}
    private static File directory(Context c){return new File(c.getFilesDir(),"prepared-touch-v1");}
    private static File[] caches(Context c){File[] f=directory(c).listFiles(x->x.getName().endsWith(".ptg"));return f==null?new File[0]:f;}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static void cancelled(Work work,String why)throws Exception{boolean rejected=false;try{work.run();}catch(IllegalStateException expected){rejected=true;}ok(rejected,why);}
    private static void settings(){AppSettings.values.clear();AppSettings.edges=AppSettings.inward=false;AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=true;AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new Context());forbid=false;cancelDuring=null;GesturePlan.clearPrepared();}
    private static void diskLifecycle()throws Exception{
        settings();Context context=context("lifecycle");byte[] source=bytes("small-source-chart-v1");File original=new File(context.getFilesDir(),"source.json");Files.write(original.toPath(),source);
        Chart first=bound(context,source);GesturePlan prepared=PreparedChartStore.ensure(context,first,PROFILE,V,B,null);long salt=TapMod.preparationSalt(first);
        ok(prepared.hasPreparedTrajectory()&&prepared.preparedSampleCount()>10,"real store did not prepare dense trajectory");ok(caches(context).length==1,"cache was not persisted");File target=caches(context)[0];byte[] valid=Files.readAllBytes(target.toPath());
        try(InputStream stream=new ByteArrayInputStream(valid)){PreparedTrajectory.inspectPrepared(stream,valid.length,PROFILE,V,B,PreparedChartStore.digest(source));}ok(true,"streaming inspection rejected real encoded cache");
        byte[] inspectionCorrupt=valid.clone();inspectionCorrupt[inspectionCorrupt.length-40]^=1;boolean inspectionRejected=false;try{PreparedTrajectory.inspectPrepared(new ByteArrayInputStream(inspectionCorrupt),inspectionCorrupt.length,PROFILE,V,B,PreparedChartStore.digest(source));}catch(IOException expected){inspectionRejected=true;}ok(inspectionRejected,"streaming inspection accepted corrupted sample/footer");
        inspectionRejected=false;try{PreparedTrajectory.inspectPrepared(new ByteArrayInputStream(valid),valid.length,PROFILE,new Viewport(0,0,1200,600),B,PreparedChartStore.digest(source));}catch(IOException expected){inspectionRejected=true;}ok(inspectionRejected,"streaming inspection ignored geometry key");
        GesturePlan.clearPrepared();Chart second=bound(context,source);forbid=true;int before=calls;GesturePlan restored;
        try{restored=PreparedChartStore.ensure(context,second,PROFILE,V,B,null);}finally{forbid=false;}
        ok(calls==before&&restored.preparedSampleCount()==prepared.preparedSampleCount(),"disk restore recalculated or changed samples");ok(TapMod.preparationSalt(second)==salt,"disk restore lost persisted random salt");
        byte[] corrupt=valid.clone();corrupt[corrupt.length/2]^=1;Files.write(target.toPath(),corrupt);GesturePlan.clearPrepared();Chart damaged=bound(context,source);before=calls;
        GesturePlan repaired=PreparedChartStore.ensure(context,damaged,PROFILE,V,B,null);ok(calls>before&&repaired.hasPreparedTrajectory(),"corrupt cache did not recompute");ok(!Arrays.equals(corrupt,Files.readAllBytes(target.toPath())),"corrupt cache was not replaced");
        GesturePlan decoded=GesturePlan.decodePrepared(damaged,PROFILE,V,B,Files.readAllBytes(target.toPath()),PreparedChartStore.digest(source));ok(decoded.preparedSampleCount()==repaired.preparedSampleCount(),"repaired cache failed real decoder");
        PreparedChartStore.ensure(context,bound(context,source),PROFILE,new Viewport(0,0,1200,600),new TouchBounds(0,0,1199,599),null);ok(caches(context).length==2,"viewport key reused physical coordinates");
        AppSettings.values.put("tap_noise_clock_guard_ms","24");PreparedChartStore.ensure(context,bound(context,source),PROFILE,V,B,null);ok(caches(context).length==3,"guard key reused stale paths");
        AppSettings.values.put("tap_noise_clock_guard_ms","0");AppSettings.inward=true;PreparedChartStore.ensure(context,bound(context,source),PROFILE,V,B,null);ok(caches(context).length==4,"inward flick key reused stale paths");AppSettings.inward=false;
        PreparedChartStore.ensure(context,bound(context,source),PROFILE,V,new TouchBounds(20,24,979,567),null);ok(caches(context).length==5,"bounds key reused edge coordinates");
        ok(Arrays.equals(source,Files.readAllBytes(original.toPath())),"cache lifecycle changed source chart");long[] stats=PreparedChartStore.statistics(context);ok(stats[0]==5&&stats[1]>valid.length,"store statistics did not reflect real encoded files");
        int count=caches(context).length;cancelled(()->PreparedChartStore.ensure(context,bound(context,bytes("cancel-before")),PROFILE,V,B,new AtomicBoolean(true)),"pre-cancelled preparation proceeded");
        AtomicBoolean flag=new AtomicBoolean();cancelDuring=flag;try{cancelled(()->PreparedChartStore.ensure(context,bound(context,bytes("cancel-during")),PROFILE,V,B,flag),"cancel during geometry committed cache");}finally{cancelDuring=null;}
        ok(caches(context).length==count,"cancelled preparation committed partial cache");ok(Arrays.equals(source,Files.readAllBytes(original.toPath())),"cancellation removed or changed source");
        File[] partial=directory(context).listFiles(f->f.getName().endsWith(".new"));ok(partial!=null&&partial.length==0,"cancelled preparation left a new generation");
        // prepareImported uses the native SessionSettings constructor in the mock.
        GesturePlan imported=PreparedChartStore.prepareImported(context,chart(),bytes("imported"),16.0/9,false,falseFlag());ok(imported.hasPreparedTrajectory(),"prepareImported did not use actual device viewport");
    }
    private static AtomicBoolean falseFlag(){return new AtomicBoolean();}
    private static void namespaces()throws Exception{
        settings();Context main=context("namespaces"),local=ExternalCharts.localStorage(main);byte[] source=bytes("same-public-chart");
        PreparedChartStore.ensure(main,bound(main,source),PROFILE,V,B,null);PreparedChartStore.ensure(local,bound(local,source),PROFILE,V,B,null);
        File mainFile=caches(main)[0],localFile=caches(local)[0];byte[] localBefore=Files.readAllBytes(localFile.toPath());
        ok(!mainFile.getCanonicalFile().equals(localFile.getCanonicalFile()),"Phira/Phigros shared a cache namespace");PreparedChartStore.clear(main);
        ok(caches(main).length==0&&localFile.isFile()&&Arrays.equals(localBefore,Files.readAllBytes(localFile.toPath())),"clearing Phira changed Phigros cache");
        forbid=true;try{ok(PreparedChartStore.ensure(local,bound(local,source),PROFILE,V,B,null).hasPreparedTrajectory(),"other namespace could not restore after clear");}finally{forbid=false;}
        PreparedChartStore.ensure(main,bound(main,source),PROFILE,V,B,null);File recreated=caches(main)[0];File backup=new File(recreated+".bak"),temp=new File(recreated+".new");
        Files.move(recreated.toPath(),backup.toPath());Files.write(temp.toPath(),new byte[]{1});PreparedChartStore.clear(main);
        ok(!backup.exists()&&!temp.exists(),"clear left orphan backup/new generations that can revive old preparation");ok(PreparedChartStore.statistics(main)[0]==0,"clear retained backup-only statistics");
    }
    private static void boundedLru()throws Exception{
        settings();Context context=context("lru");File first=null,last=null;
        for(int i=0;i<PreparedChartStore.MAX_FILES+2;i++){
            byte[] source=bytes("lru-chart-"+i);PreparedChartStore.ensure(context,bound(context,source),PROFILE,V,B,null);
            String prefix=PreparedChartStore.digest(source)+".";for(File f:caches(context))if(f.getName().startsWith(prefix)){last=f;f.setLastModified(1000000000000L+i*1000L);if(i==0)first=f;}
        }
        ok(caches(context).length==PreparedChartStore.MAX_FILES&&!first.exists()&&last.isFile(),"file LRU did not evict oldest cache only");
        Context sparse=context("bytes");directory(sparse).mkdirs();for(int i=0;i<8;i++){
            String name=String.format(Locale.ROOT,"%064x.%064x.ptg",i+1,100);File f=new File(directory(sparse),name);try(RandomAccessFile out=new RandomAccessFile(f,"rw")){out.setLength(32L*1024*1024);}f.setLastModified(1000000000000L+i*1000L);
        }
        File oldest=new File(directory(sparse),String.format(Locale.ROOT,"%064x.%064x.ptg",1,100));PreparedChartStore.ensure(sparse,bound(sparse,bytes("new-after-cap")),PROFILE,V,B,null);
        ok(!oldest.exists()&&PreparedChartStore.statistics(sparse)[1]<=PreparedChartStore.MAX_DISK_BYTES,"byte LRU exceeded bounded disk budget");
    }
    private static void symlinks()throws Exception{
        settings();Context context=context("symlinks");byte[] source=bytes("symlink-check");PreparedChartStore.ensure(context,bound(context,source),PROFILE,V,B,null);File base=caches(context)[0];String name=base.getName();PreparedChartStore.clear(context);
        File outside=File.createTempFile("r20-external-",".json");byte[] original=bytes("outside-must-survive");Files.write(outside.toPath(),original);
        Path probe=new File(directory(context),"symlink-probe").toPath();try{Files.createSymbolicLink(probe,outside.toPath());Files.delete(probe);}catch(UnsupportedOperationException|IOException|SecurityException unsupported){System.out.println("SKIP symbolic link fixture: host does not permit creating symbolic links");return;}
        for(String suffix:new String[]{"",".new",".bak"}){
            Path link=new File(directory(context),name+suffix).toPath();Files.createSymbolicLink(link,outside.toPath());GesturePlan.clearPrepared();
            try{
                Chart chart=bound(context,source);
                boolean rejected=false;
                try{PreparedChartStore.ensure(context,chart,PROFILE,V,B,null);}
                catch(IllegalStateException expected){rejected=expected.getCause() instanceof IOException;}
                ok(rejected&&GesturePlan.cachedPrepared(chart,PROFILE,V,B)==null,"Unsafe cache path reported successful preparation");
                ok(Arrays.equals(original,Files.readAllBytes(outside.toPath())),"AtomicFile"+suffix+" followed a link outside app cache");
            }finally{Files.deleteIfExists(link);}
        }
        File externalDirectory=Files.createTempDirectory("r20-external-dir").toFile();Context linked=context("directory-link");File dir=directory(linked);Files.createSymbolicLink(dir.toPath(),externalDirectory.toPath());
        try{
            boolean rejected=false;
            try{PreparedChartStore.ensure(linked,bound(linked,source),PROFILE,V,B,null);}
            catch(IllegalStateException expected){rejected=expected.getCause() instanceof IOException;}
            ok(rejected&&externalDirectory.list().length==0,"cache directory symlink was accepted or wrote outside app namespace");
        }finally{Files.delete(dir.toPath());}
    }
    public static void main(String[] args)throws Exception{diskLifecycle();namespaces();boundedLru();symlinks();System.out.println("PASS "+checks+" prepared-store checks: real disk encode/restore/corrupt rebuild, geometry/settings/bounds keys, cancellation source retention, namespace isolation, orphan AtomicFile cleanup, file/byte LRU and supported symlink containment.");}
}
