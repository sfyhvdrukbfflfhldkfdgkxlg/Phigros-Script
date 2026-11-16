package io.github.phiscript.assets;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.zip.*;import org.json.*;import io.github.phiscript.engine.*;
public final class SharedImportCorpus {
 public static void main(String[] args)throws Exception{
  android.content.Context.root=Files.createTempDirectory("r6-corpus").toFile();android.content.Context c=new android.content.Context();new io.github.phiscript.AppSettings(c).save("tapmod_enabled","false");io.github.phiscript.input.TapMod.init(c);
  int charts=0,notes=0,areas=0,positions=0;
  try(ZipFile zip=new ZipFile("count-current/Phigros_Swordalt_All_Scripts.zip")){
   JSONObject catalog=new JSONObject(new String(ExternalCharts.read(zip.getInputStream(zip.getEntry("swordalt-catalog.json")),4*1024*1024),StandardCharsets.UTF_8));JSONArray entries=catalog.getJSONArray("entries");
   for(int i=0;i<entries.length();i++){
    JSONObject row=entries.getJSONObject(i);byte[] data=ExternalCharts.read(zip.getInputStream(zip.getEntry("charts/"+row.getString("path"))),256*1024*1024);Chart chart=SharedChartImport.parseJson(new String(data,StandardCharsets.UTF_8));
    if(chart.getNotes().size()!=row.getInt("notes")||BlockAreaSet.count(chart)!=row.getInt("noiseCount"))throw new AssertionError("script contents changed: "+row.getString("path"));
    for(Viewport view:new Viewport[]{new Viewport(0,0,1920,1080),new Viewport(13,17,2400,1080),new Viewport(0,0,2048,1536)})for(int at:new int[]{0,chart.getNotes().size()/2,chart.getNotes().size()-1}){
     Note n=chart.getNotes().get(at);Point point=chart.position(n,n.getTimeSeconds(),view,true),normal=chart.judgeNormal(n,n.getTimeSeconds(),view);
     if(point==null||!Float.isFinite(point.getX())||!Float.isFinite(point.getY())||!Float.isFinite(normal.getX())||!Float.isFinite(normal.getY()))throw new AssertionError("invalid native coordinate");positions++;
    }
    charts++;notes+=chart.getNotes().size();areas+=BlockAreaSet.count(chart);if(charts%50==0){System.gc();System.out.println("checked scripts "+charts+" / "+entries.length());}
   }
  }
  if(charts!=1057||notes!=677156||areas!=17782)throw new AssertionError("corpus incomplete");
  JSONObject result=new JSONObject().put("charts",charts).put("notes",notes).put("noiseAreas",areas).put("positionSamples",positions).put("failures",0);Files.write(Paths.get("r12/corpus-results.json"),result.toString(2).getBytes(StandardCharsets.UTF_8));System.out.println("PASS R12 shared reader, entire script corpus "+result);
 }
}
