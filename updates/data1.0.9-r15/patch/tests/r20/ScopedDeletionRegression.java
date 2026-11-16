package io.github.phiscript.assets;

import android.content.Context;
import android.net.Uri;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.input.PreparedChartStore;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Same chart IDs in two real native library indexes must remain isolated. */
public final class ScopedDeletionRegression {
    private static int checks;private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private interface Work{void run()throws Exception;}
    private static void rejected(Work work,String why)throws Exception{boolean blocked=false;try{work.run();}catch(IllegalStateException expected){blocked=true;}ok(blocked,why);}
    private static File pack(Context c,String title)throws Exception{
        File f=File.createTempFile("r20-original-",".pez",c.getCacheDir());
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(f))){zip.putNextEntry(new ZipEntry("chart.json"));zip.write(Files.readAllBytes(new File("patch/tests/r17/fixtures/pgr_v3_multihold.json").toPath()));zip.closeEntry();zip.putNextEntry(new ZipEntry("info.txt"));zip.write(("#\nName: "+title+"\nLevel: IN Lv.13\nChart: chart.json\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();}return f;
    }
    private static PhiraLibrary imported(Context c,File... files)throws Exception{List<Uri> uris=new ArrayList<>();for(File f:files)uris.add(Uri.fromFile(f));return PhiraLibrary.Companion.importFiles(c,uris,s->kotlin.Unit.INSTANCE);}
    private static File index(Context c){return new File(c.getFilesDir(),"phira/index.json");}
    private static File cache(Context c)throws Exception{File dir=new File(c.getFilesDir(),"prepared-touch-v1");dir.mkdirs();File f=new File(dir,String.format(Locale.ROOT,"%064x.%064x.ptg",1,2));Files.write(f.toPath(),new byte[]{1,2,3});return f;}
    private static Map<String,String> cacheSnapshot(Context c)throws Exception{
        Map<String,String> snapshot=new TreeMap<>();File[] files=new File(c.getFilesDir(),"prepared-touch-v1").listFiles();
        if(files!=null)for(File file:files)if(file.isFile())snapshot.put(file.getName(),PreparedChartStore.digest(Files.readAllBytes(file.toPath())));return snapshot;
    }
    public static void main(String[] args)throws Exception{
        Context.root=Files.createTempDirectory("r20-scoped-delete").toFile();Context main=new Context(),local=ExternalCharts.localStorage(main);
        RuntimeState.INSTANCE.getRunning().set(false);RuntimeState.INSTANCE.getActiveTouchStop().set(null);AppSettings.values.clear();
        File first=pack(main,"Scoped First"),second=pack(main,"Scoped Kept");byte[] original=Files.readAllBytes(first.toPath());
        PhiraLibrary phira=imported(main,first,second),phigros=imported(local,first,second);RuntimeState.INSTANCE.setPhiraLibrary(phira);
        new AppSettings(main).save("phigros_library_source","local");ExternalCharts.refreshLocalAfterDeletion(main,phigros);
        PhiraEntry selected=phigros.getEntries().get(0),kept=phigros.getEntries().get(1);ok(phira.hasChart(selected.getId(),selected.getLevel()),"fixture did not use same ID in both namespaces");
        ChartLocation chosen=null;for(ChartLocation e:RuntimeState.INSTANCE.getLibrary().getLocations())if(e.getBundleEntry().equals(selected.getId()))chosen=e;
        if(chosen==null)throw new AssertionError("native local adapter lost selected chart");new AppSettings(main).save(SessionChartSource.MANUAL_ID,SessionChartSource.id(chosen));new AppSettings(main).save("phiraChartId",selected.getId());
        byte[] mainIndex=Files.readAllBytes(index(main).toPath()),localIndex=Files.readAllBytes(index(local).toPath());File mainCache=cache(main),localCache=cache(local);
        Map<String,String> mainCacheBefore=cacheSnapshot(main);long mainCacheCountBefore=PreparedChartStore.statistics(main)[0];
        ok(PhiraLibraryDeletion.list(main,true).size()==2&&PhiraLibraryDeletion.list(main,false).size()==2,"scoped lists did not restore separate native indexes");
        PhiraLibraryDeletion.Plan plan=PhiraLibraryDeletion.prepare(main,selected.getId(),true);ok(!PhiraLibraryDeletion.commit(main,plan,false).deleted,"cancelled scoped deletion removed chart");
        ok(Arrays.equals(localIndex,Files.readAllBytes(index(local).toPath())),"cancel changed Phigros index");RuntimeState.INSTANCE.getRunning().set(true);
        try{rejected(()->PhiraLibraryDeletion.commit(main,plan,true),"running task allowed scoped deletion");}finally{RuntimeState.INSTANCE.getRunning().set(false);}
        PhiraLibraryDeletion.Result deleted=PhiraLibraryDeletion.commit(main,plan,true);ok(deleted.deleted&&deleted.fileDeleted&&deleted.remaining==1,"selected Phigros local entry did not delete");
        ok(Arrays.equals(mainIndex,Files.readAllBytes(index(main).toPath()))&&RuntimeState.INSTANCE.getPhiraLibrary()==phira,"Phigros deletion changed Phira index/runtime");
        ok(new AppSettings(main).text("phiraChartId","").equals(selected.getId()),"Phigros deletion cleared Phira selected ID");ok(new AppSettings(main).text(SessionChartSource.MANUAL_ID,"missing").isEmpty(),"deleted Phigros manual selection remained active");
        ok(RuntimeState.INSTANCE.getLibrary()!=null&&RuntimeState.INSTANCE.getLibrary().getLocations().size()==1,"Phigros RuntimeState did not refresh remaining chart");
        PhiraLibrary restored=PhiraLibrary.Companion.restore(local);ok(restored.getEntries().size()==1&&restored.load(kept.getId(),kept.getLevel()).getNotes().size()==7,"remaining Phigros chart failed native restore/load");
        ok(new File(main.getFilesDir(),"phira/charts/"+selected.getId()+".json").isFile()&&!new File(local.getFilesDir(),"phira/charts/"+selected.getId()+".json").exists(),"deletion crossed chart-file namespace");
        ok(mainCache.isFile()&&!localCache.exists(),"deletion cleared another namespace's prepared cache");ok(Arrays.equals(original,Files.readAllBytes(first.toPath())),"scoped deletion changed original user PEZ");
        rejected(()->PhiraLibraryDeletion.prepare(main,selected.getId(),true),"deleted Phigros ID accepted from matching Phira library");
        PhiraLibraryDeletion.Plan last=PhiraLibraryDeletion.prepare(main,kept.getId(),true);Files.write(new File(index(local)+".bak").toPath(),Files.readAllBytes(index(local).toPath()));Files.write(new File(index(local)+".new").toPath(),new byte[]{1});
        PhiraLibraryDeletion.commit(main,last,true);ok(PhiraLibrary.Companion.restore(local)==null&&RuntimeState.INSTANCE.getLibrary()==null,"last scoped entry revived or RuntimeState retained it");
        ok(!index(local).exists()&&!new File(index(local)+".bak").exists()&&!new File(index(local)+".new").exists(),"last scoped deletion left AtomicFile generations");
        ok(PhiraLibraryDeletion.list(main,false).size()==2&&RuntimeState.INSTANCE.getPhiraLibrary()==phira&&Arrays.equals(mainIndex,Files.readAllBytes(index(main).toPath())),"empty Phigros cleanup modified Phira library");
        ok(PreparedChartStore.statistics(main)[0]==mainCacheCountBefore&&mainCacheBefore.equals(cacheSnapshot(main)),"Phira prepared cache count/content changed after last Phigros deletion");
        System.out.println("PASS "+checks+" scoped native-library deletion checks: identical IDs, separate indexes/files/cache, cancellation/running/unknown guards, selected IDs, RuntimeState and final backup-safe restore.");
    }
}
