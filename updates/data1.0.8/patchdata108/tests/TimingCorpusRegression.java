package io.github.phiscript.assets;
import android.content.Context;import io.github.phiscript.engine.*;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.json.*;

public final class TimingCorpusRegression {
 static long notes,checks;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static byte[] read(InputStream stream)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=stream.read(b))!=-1)bytes.write(b,0,n);return bytes.toByteArray();}
 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]);JSONArray index=new JSONArray(new String(Files.readAllBytes(root.resolve("derived/corpus-index.json")),"UTF-8"));
  Context.root=Files.createTempDirectory("phi-timing-corpus-").toFile();Context context=new Context();FirstNoteCatalog table=FirstNoteCatalog.get(context);
  long began=System.nanoTime();
  try(ZipFile archive=new ZipFile(root.resolve("reference-charts.zip").toFile())){
   for(int i=0;i<index.length();i++){
    JSONObject row=index.getJSONObject(i);String id=row.getString("id"),difficulty=row.getString("difficulty");Chart chart;
    try(InputStream input=archive.getInputStream(archive.getEntry(row.getString("source")))){chart=Chart.Companion.parse(new String(read(input),"UTF-8"));}
    catch(RuntimeException e){throw new IllegalStateException("Native parser rejected "+id+" / "+difficulty,e);}
    try(DataInputStream expected=new DataInputStream(Files.newInputStream(root.resolve("derived/expected").resolve(row.getString("expected"))))){
     ok(expected.readInt()==0x43544531,"expected fixture schema");int count=expected.readInt();double offset=expected.readDouble();
     ok(chart.getNotes().size()==count,"native parser lost notes: "+id+"/"+difficulty);ok(Math.abs(chart.getOffsetSeconds()-offset)<.000001,"chart offset differs");
     Map<Integer,Note> actual=new HashMap<>();for(Note note:chart.getNotes())actual.put(note.getId(),note);
     for(int n=0;n<count;n++){
      int key=expected.readInt(),type=expected.readInt();double head=expected.readDouble(),tail=expected.readDouble();expected.readInt();expected.readBoolean();Note note=actual.get(key);
      ok(note!=null&&note.getType()==type&&Math.abs(note.getTimeSeconds()-head)<.000001&&Math.abs(note.getEndSeconds()-tail)<.000001,"native per-note time differs: "+id+"/"+difficulty+"/"+key);
     }
     notes+=count;ok(expected.read()==-1,"expected fixture has trailing rows");
    }
    ChartTimingIndex.Summary saved=ChartTimingIndex.ensure(context,"network-reference-4.0.1.2",id,difficulty,chart);
    ChartTimingIndex.Summary restored=ChartTimingIndex.readStored(context,"network-reference-4.0.1.2",id,difficulty);
    ok(saved.signature.equals(restored.signature)&&saved.reference(context).equals("一致"),"first-note reference or persisted time mismatch: "+id+"/"+difficulty);
    FirstNoteCatalog.Entry reference=table.find(id,difficulty);ok(reference!=null&&Math.abs(saved.firstMusicUs-reference.firstMusicUs)<=1,"first note calculation differs");
    if((i+1)%100==0)System.err.println("native_chart_timing_verified "+(i+1)+" notes "+notes);
   }
  }
  ok(index.length()==1057&&notes==677156,"reference snapshot coverage differs");
  System.out.println("PASS "+checks+" checks on "+index.length()+" pinned public charts and "+notes+" note heads/tails: independent Decimal conversion vs actual native parser, built-in first-note references, atomic table restore and checksums. Elapsed "+((System.nanoTime()-began)/1000000000L)+" seconds. Snapshot 4.0.1.2; no Android device/music-start validation.");
 }
}
