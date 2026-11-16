package io.github.phiscript.assets;
import android.content.Context;import android.net.Uri;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
import org.json.*;import io.github.phiscript.engine.*;import io.github.phiscript.root.PhiraImportControl;

/** Reproduce a successful >8 MiB import that used to fail on load and process restore. */
public final class LargeImportRegression {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 interface Job{void run()throws Exception;}
 static void rejected(Job j,String message)throws Exception{try{j.run();throw new AssertionError("must reject "+message);}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
 static String sha(byte[] b)throws Exception{StringBuilder out=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))out.append(String.format(Locale.ROOT,"%02x",x&255));return out.toString();}
 static void run(Context storage,File input,boolean legacy)throws Exception{
  PhiraLibrary saved;try(PhiraImportControl.Session ignored=PhiraImportControl.begin()){
   saved=PhiraLibrary.Companion.importFiles(storage,Collections.singletonList(Uri.fromFile(input)),s->kotlin.Unit.INSTANCE);
  }
  ok(!PhiraImportControl.isBusy(),"import session closed");PhiraEntry selected=saved.getEntries().get(saved.getEntries().size()-1);
  File index=new File(storage.getFilesDir(),"phira/index.json");JSONArray rows=new JSONObject(new String(Files.readAllBytes(index.toPath()),StandardCharsets.UTF_8)).getJSONArray("entries");JSONObject record=null;
  for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(selected.getId()))record=rows.getJSONObject(i);
  ok(record!=null,"selected metadata saved");final File chart=new File(storage.getFilesDir(),"phira/charts/"+selected.getId()+".json");final byte[] original=Files.readAllBytes(chart.toPath());
  ok(original.length>8*1024*1024,"actual normalized chart exceeds stale limit");ok(chart.isFile()&&original.length==record.getInt("bytes")&&sha(original).equals(record.getString("sha256")),"file exists and both size/hash match");
  if(legacy){rejected(()->saved.load(selected.getId(),selected.getLevel()),"legacy load bug");rejected(()->PhiraLibrary.Companion.restore(storage),"legacy restore bug");return;}
  Chart loaded=saved.load(selected.getId(),selected.getLevel());ok(!loaded.getNotes().isEmpty(),"large chart plays immediately after import");
  PhiraLibrary restarted=PhiraLibrary.Companion.restore(storage);ok(restarted!=null&&restarted.load(selected.getId(),selected.getLevel()).getNotes().size()==loaded.getNotes().size(),"large chart restores and plays after process restart");
  // Integrity checks must continue rejecting genuine loss, length changes and same-size tampering.
  Files.delete(chart.toPath());rejected(()->restarted.load(selected.getId()),"missing file");Files.write(chart.toPath(),Arrays.copyOf(original,original.length-1));rejected(()->restarted.load(selected.getId()),"truncated file");
  byte[] tampered=original.clone();tampered[tampered.length-2]^=1;Files.write(chart.toPath(),tampered);rejected(()->restarted.load(selected.getId()),"same-size corrupt file");
  Files.write(chart.toPath(),original);ok(!PhiraLibrary.Companion.restore(storage).load(selected.getId()).getNotes().isEmpty(),"restored valid file works");
 }
 public static void main(String[] args)throws Exception{
  boolean legacy=args.length>0&&args[0].equals("legacy");Context.root=Files.createTempDirectory("large-import").toFile();Context c=new Context();
  JSONObject source=new JSONObject(new String(Files.readAllBytes(Paths.get("upstream/updates/data1.0.9/patch/tests/fixtures/pgr_v3_multihold.json")),StandardCharsets.UTF_8));
  char[] padding=new char[9*1024*1024];Arrays.fill(padding,'x');source.put("largeImportRegressionPadding",new String(padding)).put("META",new JSONObject().put("name","Large import").put("level","IN 12"));
  File input=new File(c.getCacheDir(),"large.json");Files.write(input.toPath(),source.toString().getBytes(StandardCharsets.UTF_8));run(c,input,legacy);run(new ExternalCharts.Storage(c),input,legacy);
  if(!legacy){
   JSONObject cat=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/assets/phigros-swordalt-catalog-v1.json")),StandardCharsets.UTF_8));JSONArray rows=cat.getJSONArray("entries");JSONObject largest=rows.getJSONObject(0);
   for(int i=1;i<rows.length();i++)if(rows.getJSONObject(i).getLong("bytes")>largest.getLong("bytes"))largest=rows.getJSONObject(i);
   File actual=Paths.get("analysis/swordalt/raw-charts",largest.getString("path")).toFile();ok(actual.length()>8*1024*1024,"real source chart triggers old limit");run(c,actual,false);run(new ExternalCharts.Storage(c),actual,false);
  }
  System.out.println("PASS "+(legacy?"R4 bug reproduced":"large import repaired")+" "+checks+" assertions: both games, import/load/restore, real large chart and integrity checks");
 }
}
