package io.github.phiscript.assets;

import android.content.Context;
import android.net.Uri;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.root.PhiraImportControl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** Uses the retained actual PhiraLibrary import, index, loader and restore. */
public final class PhiraDeletionRegression {
    private static int checks;private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private interface Attempt{void run()throws Exception;}
    private static void rejected(Attempt action,String why)throws Exception{boolean blocked=false;try{action.run();}catch(IllegalStateException|IllegalArgumentException expected){blocked=true;}ok(blocked,why);}
    private static File pack(Context context,String title)throws Exception{
        File file=File.createTempFile("delete-original-",".pez",context.getCacheDir());
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){
            zip.putNextEntry(new ZipEntry("chart.json"));zip.write(Files.readAllBytes(new File("patch/tests/r17/fixtures/pgr_v3_multihold.json").toPath()));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("info.txt"));zip.write(("#\nName: "+title+"\nLevel: IN Lv.13\nChart: chart.json\n").getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }return file;
    }
    private static PhiraLibrary imported(Context context,File... files)throws Exception{
        List<Uri> uris=new ArrayList<>();for(File file:files)uris.add(Uri.fromFile(file));
        PhiraLibrary library=PhiraLibrary.Companion.importFiles(context,uris,s->kotlin.Unit.INSTANCE);RuntimeState.INSTANCE.setPhiraLibrary(library);return library;
    }
    public static void main(String[] args)throws Exception{
        Context.root=Files.createTempDirectory("r18-deletion").toFile();Context context=new Context();RuntimeState.INSTANCE.getRunning().set(false);RuntimeState.INSTANCE.getActiveTouchStop().set(null);
        File first=pack(context,"Delete First"),second=pack(context,"Keep Second");byte[] firstOriginal=Files.readAllBytes(first.toPath()),secondOriginal=Files.readAllBytes(second.toPath());
        PhiraLibrary library=imported(context,first,second);PhiraEntry selected=library.getEntries().get(0),kept=library.getEntries().get(1);
        File index=new File(context.getFilesDir(),"phira/index.json"),selectedFile=new File(context.getFilesDir(),"phira/charts/"+selected.getId()+".json");
        byte[] before=Files.readAllBytes(index.toPath());
        ok(PhiraLibraryDeletion.list(context).size()==2,"management list did not reflect actual imported library");
        PhiraLibraryDeletion.Plan plan=PhiraLibraryDeletion.prepare(context,selected.getId());
        ok(!PhiraLibraryDeletion.commit(context,plan,false).deleted,"cancelled confirmation deleted a chart");
        ok(Arrays.equals(before,Files.readAllBytes(index.toPath()))&&selectedFile.isFile(),"cancel changed index or chart file");
        rejected(()->PhiraLibraryDeletion.prepare(context,"../index"),"path traversal identifier accepted");
        rejected(()->PhiraLibraryDeletion.prepare(context,String.join("",Collections.nCopies(64,"0"))),"unknown chart accepted");
        new AppSettings(context).save("phiraChartId",selected.getId());
        RuntimeState.INSTANCE.getRunning().set(true);
        try{rejected(()->PhiraLibraryDeletion.commit(context,plan,true),"running playback allowed deletion");}finally{RuntimeState.INSTANCE.getRunning().set(false);}
        RuntimeState.ActiveTouch active=new RuntimeState.ActiveTouch(new AtomicBoolean(),new AtomicBoolean());RuntimeState.INSTANCE.getActiveTouchStop().set(active);
        try{rejected(()->PhiraLibraryDeletion.commit(context,plan,true),"active touch ownership allowed deletion");}finally{RuntimeState.INSTANCE.getActiveTouchStop().set(null);}
        try(PhiraImportControl.Session importTask=PhiraImportControl.begin()){rejected(()->PhiraLibraryDeletion.commit(context,plan,true),"in-progress import allowed deletion");}
        ok(Arrays.equals(before,Files.readAllBytes(index.toPath()))&&selectedFile.isFile(),"blocked deletion mutated data");
        // A separate Phigros local-package storage uses the same index format in
        // its own directory. Phira deletion must leave it byte-for-byte alone.
        Context isolated=new ExternalCharts.Storage(context);PhiraLibrary independent=PhiraLibrary.Companion.importFiles(isolated,Collections.singletonList(Uri.fromFile(first)),s->kotlin.Unit.INSTANCE);
        File otherIndex=new File(isolated.getFilesDir(),"phira/index.json");byte[] otherBefore=Files.readAllBytes(otherIndex.toPath());
        PhiraLibraryDeletion.Result result=PhiraLibraryDeletion.commit(context,plan,true);
        ok(result.deleted&&result.fileDeleted&&result.remaining==1&&!selectedFile.exists(),"selected imported file not removed");
        ok(RuntimeState.INSTANCE.getPhiraLibrary().getEntries().size()==1&&!RuntimeState.INSTANCE.getPhiraLibrary().hasChart(selected.getId(),selected.getLevel()),"runtime still exposed deleted chart");
        ok(new AppSettings(context).text("phiraChartId","missing").isEmpty(),"selected chart id survived deletion");
        PhiraLibrary restored=PhiraLibrary.Companion.restore(context);ok(restored.getEntries().size()==1&&restored.load(kept.getId(),kept.getLevel()).getNotes().size()==7,"remaining chart failed real load/restore");
        ok(Arrays.equals(otherBefore,Files.readAllBytes(otherIndex.toPath()))&&independent.load(independent.getEntries().get(0).getId()).getNotes().size()==7,"Phigros isolated storage was modified");
        ok(Arrays.equals(firstOriginal,Files.readAllBytes(first.toPath()))&&Arrays.equals(secondOriginal,Files.readAllBytes(second.toPath())),"original selected PEZ changed");
        rejected(()->PhiraLibraryDeletion.commit(context,plan,true),"stale deletion plan removed a different chart");
        PhiraLibraryDeletion.Plan stale=PhiraLibraryDeletion.prepare(context,kept.getId());File third=pack(context,"Added Third");imported(context,third);
        rejected(()->PhiraLibraryDeletion.commit(context,stale,true),"concurrent import was silently overwritten");
        PhiraLibrary fresh=PhiraLibrary.Companion.restore(context);for(PhiraEntry e:new ArrayList<>(fresh.getEntries())){
            PhiraLibraryDeletion.Plan last=PhiraLibraryDeletion.prepare(context,e.getId());
            if(PhiraLibraryDeletion.list(context).size()==1){Files.write(new File(index+".bak").toPath(),Files.readAllBytes(index.toPath()));Files.write(new File(index+".new").toPath(),new byte[]{1});}
            PhiraLibraryDeletion.commit(context,last,true);
        }
        ok(PhiraLibrary.Companion.restore(context)==null&&RuntimeState.INSTANCE.getPhiraLibrary()==null,"last deletion resurrected after restore");
        ok(!index.exists()&&!new File(index+".bak").exists()&&!new File(index+".new").exists(),"empty AtomicFile generations remained");
        ok(PhiraLibraryDeletion.list(context).isEmpty(),"deleted last chart stayed in management list");
        System.out.println("PASS "+checks+" actual imported-library deletion checks: selected file/index only, cancellation, busy/unknown/stale guards, retained chart load/restore, original PEZ and Phigros-store isolation, final empty-library backup cleanup.");
    }
}
