package io.github.phiscript.assets;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import org.json.*;import io.github.phiscript.engine.*;
public final class ScriptGeometryRegression {
 static JSONObject event(double start,double end,double a,double b,double ay,double by)throws Exception{return new JSONObject().put("startTime",start).put("endTime",end).put("start",a).put("end",b).put("start2",ay).put("end2",by);}
 public static void main(String[] args)throws Exception{
  android.content.Context.root=Files.createTempDirectory("geometry-unit").toFile();android.content.Context c=new android.content.Context();new io.github.phiscript.AppSettings(c).save("tapmod_enabled","false");io.github.phiscript.input.TapMod.init(c);
  JSONObject base=new JSONObject(new String(Files.readAllBytes(Paths.get("upstream/updates/data1.0.9/patch/tests/fixtures/pgr_v3_multihold.json")),StandardCharsets.UTF_8));
  JSONObject entry=new JSONObject().put("title","Synthetic").put("difficulty","IN").put("level","12").put("path","Synthetic/IN.json").put("sha1","0000000000000000000000000000000000000000");JSONObject catalog=new JSONObject().put("repository",SwordaltSource.REPOSITORY).put("chartCommit","0000000000000000000000000000000000000000");
  for(int version:new int[]{1,3}){
   JSONObject raw=new JSONObject(base.toString()).put("formatVersion",version);JSONArray source=raw.getJSONArray("judgeLineList");
   for(int i=0;i<source.length();i++){
    JSONArray moves=new JSONArray().put(event(10,10,.2,.8,.3,.7)).put(event(20,30,.8,.4,.7,.5)).put(event(20,26,.9,.6,.5,.4)).put(event(22,25,.1,.3,.2,.4)).put(event(45,40,.9,.2,.5,.5));
    if(version==1)for(int j=0;j<moves.length();j++){
     JSONObject e=moves.getJSONObject(j);e.put("start",Math.round(e.getDouble("start")*880)*1000+Math.round(e.getDouble("start2")*520)).put("end",Math.round(e.getDouble("end")*880)*1000+Math.round(e.getDouble("end2")*520));
    }
    source.getJSONObject(i).put("judgeLineMoveEvents",moves).put("judgeLineRotateEvents",new JSONArray().put(event(5,5,0,90,0,0)).put(event(15,25,90,180,0,0)).put(event(19,18,180,0,0,0)));
   }
   byte[] original=raw.toString().getBytes(StandardCharsets.UTF_8);Chart before=Chart.Companion.parse(new String(original,StandardCharsets.UTF_8));Chart after=Chart.Companion.parse(new String(PgrScriptConverter.convert(original,entry,catalog),StandardCharsets.UTF_8));
   for(int n=0;n<before.getNotes().size();n++){
    Note a=before.getNotes().get(n),b=after.getNotes().get(n);double tick=1.875/source.getJSONObject(a.getLineIndex()).getDouble("bpm");
    for(double t:new double[]{0,4.99,5,5.01,9.99,10,10.01,15,18.99,19,20,20.01,21.99,22,22.01,25,26,29,30,40,50}){
     double time=t*tick;for(double sample:new double[]{Math.nextDown(time),time,Math.nextUp(time)})ExportSwordaltScripts.geometry(before,after,a,b,sample,new Viewport(0,0,1920,1080));
    }
   }
  }
  System.out.println("PASS script geometry "+ExportSwordaltScripts.checks+" assertions: PGR 1/3, constant gaps, overlap, duplicate starts, instantaneous jumps, reversed scenery and adjacent floating-point times");
 }
}
