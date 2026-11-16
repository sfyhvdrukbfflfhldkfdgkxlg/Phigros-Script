package io.github.phiscript.assets;
import io.github.phiscript.root.PhiraImportControl;
import java.nio.file.*;import java.io.*;import java.util.*;import java.util.zip.*;
import android.content.Context;import android.net.Uri;
public final class ImportLifecycleRegression {
 static int checks;static void ok(boolean c,String m){checks++;if(!c)throw new AssertionError(m);}
 public static void main(String[] a)throws Exception{
  Context.root=Files.createTempDirectory("phi-lifecycle").toFile();Context context=new Context();
  File file=new File(context.getCacheDir(),"lifecycle.pez");
  try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){
   out.putNextEntry(new ZipEntry("chart.json"));out.write(Files.readAllBytes(Paths.get("upstream/updates/data1.0.9/patch/tests/fixtures/pgr_v3_multihold.json")));out.closeEntry();
   out.putNextEntry(new ZipEntry("info.txt"));out.write("#\nName: Lifecycle Test\nLevel: IN 12\nChart: chart.json\n".getBytes("UTF-8"));out.closeEntry();
  }
  PhiraLibrary library;
  try(PhiraImportControl.Session s=PhiraImportControl.begin()){
   ok(PhiraImportControl.isBusy(),"import must own session");
   library=PhiraLibrary.Companion.importFiles(context,Collections.singletonList(Uri.fromFile(file)),message->kotlin.Unit.INSTANCE);
   ok(library.getEntries().size()==1,"chart saved before session closes");
  }
  ok(!PhiraImportControl.isBusy(),"successful import must close its session");
  ok(PhiraLibrary.Companion.restore(context).getEntries().size()==1,"saved chart remains available");
  PhiraLibrary one=io.github.phiscript.root.PlayEntry.importPhiraOne(context,Uri.fromFile(file));
  ok(one.getEntries().size()==1&&!PhiraImportControl.isBusy(),"single-file import route closes successfully");
  PhiraLibrary many=io.github.phiscript.root.PlayEntry.importPhira(context,Collections.singletonList(Uri.fromFile(file)));
  ok(many.getEntries().size()==1&&!PhiraImportControl.isBusy(),"multi-file import route closes successfully");
  for(int i=0;i<100;i++){
   try(PhiraImportControl.Session s=PhiraImportControl.begin()){ok(PhiraImportControl.isBusy(),"repeated begin");}
   ok(!PhiraImportControl.isBusy(),"repeated close");
  }
  try(PhiraImportControl.Session s=PhiraImportControl.begin()){PhiraImportControl.cancel();ok(PhiraImportControl.isCancelling(),"cancel is visible");}
  ok(!PhiraImportControl.isBusy()&&!Thread.currentThread().isInterrupted(),"cancel closes and clears interrupt");
  System.out.println("PASS import lifecycle "+checks+" assertions (save, close, restore, repeat, cancel)");
 }
}
