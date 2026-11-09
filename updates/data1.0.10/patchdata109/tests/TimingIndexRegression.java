package io.github.phiscript.assets;
import android.content.Context;import android.util.AtomicFile;
import io.github.phiscript.RuntimeState;import io.github.phiscript.engine.*;import io.github.phiscript.root.PhiraImportControl;
import java.io.*;import java.nio.file.*;import java.lang.reflect.*;import java.security.*;import java.util.*;

public final class TimingIndexRegression {
 static int checks;static Context context;static String json;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Chart chart(double offset){return Chart.Companion.parse(json.replaceFirst("\"offset\"\\s*:\\s*0","\"offset\":"+offset));}
 static void reject(String text)throws Exception{boolean failed=false;try{FirstNoteCatalog.read(new ByteArrayInputStream(text.getBytes("UTF-8")));}catch(IOException e){failed=true;}ok(failed,"malformed reference accepted");}
 static File record(String source)throws Exception{String key=hash(source);try(java.util.stream.Stream<Path> files=Files.walk(context.getFilesDir().toPath())){return files.filter(p->p.getFileName().toString().endsWith(".cti")&&p.toString().contains(key)).findFirst().get().toFile();}}
 static String hash(String s)throws Exception{StringBuilder b=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")))b.append(String.format("%02x",v&255));return b.toString();}
 static void persistence()throws Exception{
  Chart chart=chart(.125);ChartTimingIndex.Summary first=ChartTimingIndex.ensure(context,"unit-a","unit.song.0","IN",chart);
  ok(first.count==chart.getNotes().size(),"note count changed while saving");
  ok(first.firstMusicUs==first.firstChartUs+125000,"offset omitted or counted twice");
  ChartTimingIndex.Summary read=ChartTimingIndex.readStored(context,"unit-a","unit.song.0","IN");ok(read.signature.equals(first.signature),"disk restore differs from completed chart");
  File file=record("unit-a");byte[] before=Files.readAllBytes(file.toPath());long modified=file.lastModified();
  ChartTimingIndex.ensure(context,"unit-a","unit.song.0","IN",chart);ok(file.lastModified()==modified,"unchanged timing table rewritten");
  AtomicFile.failNext=true;boolean failed=false;try{ChartTimingIndex.ensure(context,"unit-a","unit.song.0","IN",chart(.25));}catch(IOException e){failed=true;}
  ok(failed&&Arrays.equals(before,Files.readAllBytes(file.toPath())),"failed commit damaged completed chart");
  try(PhiraImportControl.Session session=PhiraImportControl.begin()){
   PhiraImportControl.cancel();failed=false;try{ChartTimingIndex.ensure(context,"unit-a","unit.song.0","IN",chart(.5));}catch(java.util.concurrent.CancellationException e){failed=true;}
   ok(failed&&Arrays.equals(before,Files.readAllBytes(file.toPath())),"cancelled save replaced completed times");
  }
  byte[] corrupt=before.clone();corrupt[corrupt.length-40]^=1;Files.write(file.toPath(),corrupt);
  failed=false;try{ChartTimingIndex.readStored(context,"unit-a","unit.song.0","IN");}catch(IOException e){failed=true;}ok(failed,"corrupt timing table accepted");
  ChartTimingIndex.ensure(context,"unit-a","unit.song.0","IN",chart);ok(Arrays.equals(before,Files.readAllBytes(file.toPath())),"corrupt table did not rebuild deterministically");
  ChartTimingIndex.Summary changed=ChartTimingIndex.ensure(context,"unit-a","unit.song.0","IN",chart(.25));ok(!changed.signature.equals(first.signature)&&changed.firstMusicUs-first.firstMusicUs==125000,"changed data reused old times");
  ChartTimingIndex.ensure(context,"unit-b","unit.song.0","IN",chart);ok(record("unit-a").exists()&&record("unit-b").exists(),"package versions share timing records");
 }
 static void reference()throws Exception{
  FirstNoteCatalog table=FirstNoteCatalog.get(context);ok(table.entries().size()==1057&&table.gameVersion.equals("4.0.1.2"),"wrong reference snapshot");
  ok(table.find("Chronomia.Lime","HD")!=null&&table.find("Chronomia.Lime.0","HD")==table.find("Chronomia.Lime","HD"),"official suffix not matched");
  ok(table.find("Chronomia.Lime.1","HD")==null,"different chart suffix silently matched");
  FirstNoteCatalog.Entry entry=table.find("Chronomia.Lime","HD");ok(entry.firstMusicUs==entry.firstChartUs+entry.offsetUs,"reference clock offset inconsistent");
  String valid="# phigros-first-notes-v1\n# game_version=test\nunit\tIN\t100000\t25000\t125000\t2\t200000\t"+String.join("",Collections.nCopies(40,"a"))+"\n";
  ok(FirstNoteCatalog.read(new ByteArrayInputStream(valid.getBytes("UTF-8"))).find("unit","IN").firstMusicUs==125000,"valid reference not read");
  reject(valid+valid.substring(valid.indexOf("unit\t")));reject(valid.replace("125000","125500"));reject(valid.replace("# phigros-first-notes-v1","# unknown"));reject(valid.replace("\t2\t","\t0\t"));
 }
 static ChartLibrary library(String name,List<ChartLocation> locations)throws Exception{
  PackageSnapshot snapshot=new PackageSnapshot("unit.game",name,1,1,Collections.emptyList());
  ChartSource source=new ChartSource(){public PackageSnapshot inspect(){return snapshot;}public List<String> entries(PackageSnapshot s){return Collections.emptyList();}public byte[] read(PackageSnapshot s,String entry,int max){throw new IllegalStateException("fixture not cached");}};
  Constructor<ChartLibrary> constructor=ChartLibrary.class.getDeclaredConstructor(Context.class,PackageSnapshot.class,List.class,ChartSource.class,String.class);constructor.setAccessible(true);
  ChartLibrary library=constructor.newInstance(context,snapshot,locations,source,"fixture");Field cache=ChartLibrary.class.getDeclaredField("cache");cache.setAccessible(true);File dir=(File)cache.get(library);
  for(ChartLocation row:locations)if(!row.getSongId().equals("bad"))Files.write(new File(dir,hash(row.getSongId()+"/"+row.getDifficulty())+".json").toPath(),json.getBytes("UTF-8"));
  return library;
 }
 static void importResume()throws Exception{
  List<ChartLocation> locations=Arrays.asList(new ChartLocation("first","IN","fixture","fixture"),new ChartLocation("bad","IN","fixture","fixture"),new ChartLocation("third","IN","fixture","fixture"));
  ChartLibrary library=library("import",locations);String key=hash(library.getSnapshot().toString());ChartTimingIndex.importLibrary(context,library);
  ok(ChartTimingIndex.readStored(context,key,"first","IN").count>0&&ChartTimingIndex.readStored(context,key,"third","IN").count>0,"invalid chart discarded other completed records");
  ok(!ChartTimingIndex.isBusy()&&!PhiraImportControl.isBusy(),"import ownership leaked");
  ChartLibrary next=library("cancel-import",Arrays.asList(new ChartLocation("first","IN","fixture","fixture"),new ChartLocation("second","IN","fixture","fixture")));
  RuntimeState.onLog=text->{if(text.startsWith("已保存按键时间："))PhiraImportControl.cancel();};
  try{ChartTimingIndex.importLibrary(context,next);}finally{RuntimeState.onLog=null;Thread.interrupted();}
  String nextKey=hash(next.getSnapshot().toString());ChartTimingIndex.readStored(context,nextKey,"first","IN");boolean missing=false;try{ChartTimingIndex.readStored(context,nextKey,"second","IN");}catch(FileNotFoundException e){missing=true;}ok(missing,"cancelled import continued to next chart");
  ChartTimingIndex.importLibrary(context,next);ok(ChartTimingIndex.readStored(context,nextKey,"second","IN").count>0,"partial import did not resume");
 }
 static void backwardsEvents()throws Exception{
  org.json.JSONObject source=new org.json.JSONObject(json);org.json.JSONObject line=source.getJSONArray("judgeLineList").getJSONObject(0);
  for(String key:new String[]{"judgeLineMoveEvents","judgeLineRotateEvents","judgeLineDisappearEvents"}){
   org.json.JSONObject event=new org.json.JSONObject();event.put("startTime",912);event.put("endTime",896);event.put("start",0);event.put("end",0);event.put("start2",0);event.put("end2",0);
   line.getJSONArray(key).put(event);
  }
  Chart original=Chart.Companion.parse(json),compatible=Chart.Companion.parse(source.toString());
  ok(compatible.getNotes().size()==original.getNotes().size(),"backwards line events discarded note timings");
  for(int i=0;i<original.getNotes().size();i++)ok(original.getNotes().get(i).getTimeSeconds()==compatible.getNotes().get(i).getTimeSeconds(),"line event normalization changed a note time");
 }
 static void legacyMoves()throws Exception{
  org.json.JSONObject legacy=new org.json.JSONObject(json);legacy.put("formatVersion",1);
  org.json.JSONArray lines=legacy.getJSONArray("judgeLineList");
  for(int i=0;i<lines.length();i++){
   org.json.JSONArray events=lines.getJSONObject(i).getJSONArray("judgeLineMoveEvents");
   for(int j=0;j<events.length();j++)for(String key:new String[]{"start","end"}){
    org.json.JSONObject e=events.getJSONObject(j);e.put(key,e.getDouble(key)*880000+e.getDouble(key+"2")*520);e.remove(key+"2");
   }
  }
  Chart original=Chart.Companion.parse(json),converted=Chart.Companion.parse(legacy.toString());Viewport viewport=new Viewport(0,0,1000,600);
  ok(converted.getNotes().size()==original.getNotes().size(),"official v1 note count differs");
  for(int i=0;i<original.getNotes().size();i++){
   Note a=original.getNotes().get(i),b=converted.getNotes().get(i);Point pa=original.position(a,a.getTimeSeconds(),viewport,false),pb=converted.position(b,b.getTimeSeconds(),viewport,false);
   ok(a.getTimeSeconds()==b.getTimeSeconds()&&Math.abs(pa.getX()-pb.getX())<.01&&Math.abs(pa.getY()-pb.getY())<.01,"legacy packed coordinates or note time differs");
  }
 }
 public static void main(String[] args)throws Exception{
  Context.root=Files.createTempDirectory("phi-timing-index-").toFile();context=new Context();json=new String(Files.readAllBytes(Paths.get("patch/tests/fixtures/pgr_v3_multihold.json")),"UTF-8");
  persistence();reference();importResume();backwardsEvents();legacyMoves();System.out.println("PASS "+checks+" timing index checks: every head/tail saved atomically, offset once, deterministic restore/reuse/rebuild, failed/cancelled commit isolation, source separation, first-note reference validation, suffix matching, partial import resume, official v1 packed moves, backwards line-event compatibility and ownership cleanup. Host file/asset transport models only.");
 }
}
