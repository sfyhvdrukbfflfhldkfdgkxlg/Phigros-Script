package io.github.phiscript.assets;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.json.*;import io.github.phiscript.engine.*;import io.github.phiscript.input.TapMod;

/** Offline export uses the same converter used by Android for missing-cache recovery. */
public final class ExportSwordaltScripts {
 static long checks,positions;static double maxVisibleError;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void geometry(Chart original,Chart script,Note a,Note b,double time,Viewport v){
  Point p=original.position(a,time,v,false),q=script.position(b,time,v,false);
  Point n=original.judgeNormal(a,time,v),m=script.judgeNormal(b,time,v);
  // Official and native players use opposite normal directions on the same axis.
  check(Math.min(Math.hypot(n.getX()-m.getX(),n.getY()-m.getY()),Math.hypot(n.getX()+m.getX(),n.getY()+m.getY()))<0.00001,"line rotation changed at "+time+" original="+n+" native="+m);
  check(p!=null&&q!=null,"missing position");
  double dx=p.getX()-q.getX(),dy=p.getY()-q.getY();double tolerance=0.025+Math.max(Math.abs(p.getX()),Math.abs(p.getY()))*0.000001;
  if(v.contains(p)){
   double error=Math.hypot(dx,dy);maxVisibleError=Math.max(maxVisibleError,error);
   double edge=Math.min(Math.min(p.getX()-v.getLeft(),v.getRight()-p.getX()),Math.min(p.getY()-v.getTop(),v.getBottom()-p.getY()));
   // Native playback clips floating-point edge hits 0.5 px inward along the unrestricted normal.
   boolean edgeClip=edge<0.001&&error<=1.0&&Math.abs(dx*n.getY()-dy*n.getX())<=tolerance;
   check(error<=tolerance||edgeClip,"visible position changed by "+error+" pixels at "+time+" original="+p+" native="+q);
  }else check(Math.abs(dx*n.getY()-dy*n.getX())<=tolerance,"offscreen position changed along judgement line at "+time);
  positions++;
 }
 public static void main(String[] args)throws Exception{
  android.content.Context.root=Files.createTempDirectory("script-export").toFile();android.content.Context context=new android.content.Context();new io.github.phiscript.AppSettings(context).save("tapmod_enabled","false");TapMod.init(context);
  check(new io.github.phiscript.AppSettings(context).text("tapmod_enabled","").equals("false"),"position comparison disables random offsets");
  JSONObject catalog=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/assets/phigros-swordalt-catalog-v1.json")),StandardCharsets.UTF_8));
  JSONArray entries=catalog.getJSONArray("entries");int charts=0,notes=0,noise=0,noiseCharts=0;long bytes=0;long maxBytes=0;int maxFrames=0;List<String> failures=new ArrayList<>();
  boolean repair=args.length>0&&args[0].equals("--repair");Set<String> selected=new HashSet<>();
  if(repair){JSONArray failed=new JSONObject(new String(Files.readAllBytes(Paths.get("analysis/r5-script-export-results.json")),StandardCharsets.UTF_8)).getJSONArray("failures");for(int i=0;i<failed.length();i++)selected.add(failed.getString(i).split(" :: ",2)[0]);}
  for(int i=0;i<(args.length>0&&!repair?3:entries.length());i++){
   JSONObject e=entries.getJSONObject(i);String path=e.getString("path");
   if(repair&&!selected.contains(path))continue;
   try{
    byte[] raw=Files.readAllBytes(Paths.get("analysis/swordalt/raw-charts",path));
    check(raw.length==e.getLong("bytes")&&SwordaltSource.blobHash(raw).equals(e.getString("sha1")),"raw source checksum");
    byte[] converted=PgrScriptConverter.convert(raw,e,catalog);JSONObject scriptJson=new JSONObject(new String(converted,StandardCharsets.UTF_8));
    check(scriptJson.getInt("phiscriptFormat")==1,"native script format");
    Chart original=Chart.Companion.parse(new String(raw,StandardCharsets.UTF_8)),script=Chart.Companion.parse(new String(converted,StandardCharsets.UTF_8));
    check(original.getNotes().size()==script.getNotes().size(),"note count changed");check(original.getOffsetSeconds()==script.getOffsetSeconds(),"offset changed");
    int areas=BlockAreaSet.count(script);check(areas==e.getInt("noiseCount"),"noise count changed");if(areas>0){noiseCharts++;noise+=areas;}
    JSONObject sourceJson=new JSONObject(new String(raw,StandardCharsets.UTF_8));
    if(sourceJson.has("blockAreaList"))check(PgrScriptConverter.canonical(sourceJson.getJSONArray("blockAreaList")).equals(PgrScriptConverter.canonical(scriptJson.getJSONArray("blockAreaList"))),"noise values changed");
    JSONArray lines=scriptJson.getJSONArray("lines");int frames=0;for(int k=0;k<lines.length();k++){
     JSONObject l=lines.getJSONObject(k).getJSONObject("object");for(String key:new String[]{"x","y","rotation"})frames+=l.getJSONArray(key).getJSONArray(0).length();
    }
    frames+=script.getNotes().size();maxFrames=Math.max(maxFrames,frames);check(frames<=1000000,"native frame budget");
    Viewport wide=new Viewport(17,31,2400,1080),normal=new Viewport(0,0,1920,1080);
    for(int k=0;k<original.getNotes().size();k++){
     Note a=original.getNotes().get(k),b=script.getNotes().get(k);
     check(a.getId()==b.getId()&&a.getType()==b.getType()&&a.getLineIndex()==b.getLineIndex()&&a.getAbove()==b.getAbove(),"note identity changed");
     check(Math.abs(a.getTimeSeconds()-b.getTimeSeconds())<1e-8&&Math.abs(a.getEndSeconds()-b.getEndSeconds())<1e-8,"note timing changed");
     check(Math.abs(a.getPositionX()-b.getPositionX())<1e-8,"local note x changed");
     geometry(original,script,a,b,a.getTimeSeconds(),normal);
     if(a.getType()==3){geometry(original,script,a,b,(a.getTimeSeconds()+a.getEndSeconds())/2,wide);geometry(original,script,a,b,a.getEndSeconds(),normal);}
     if(k==0||k==original.getNotes().size()-1)geometry(original,script,a,b,a.getTimeSeconds(),wide);
    }
    Path target=Paths.get("analysis/swordalt/scripts",path);Files.createDirectories(target.getParent());Files.write(target,converted);
    e.put("sourceSha1",e.getString("sha1")).put("sourceBytes",e.getLong("bytes")).put("sha1",SwordaltSource.blobHash(converted)).put("bytes",converted.length)
      .put("notes",script.getNotes().size()).put("frames",frames);
    charts++;notes+=script.getNotes().size();bytes+=converted.length;maxBytes=Math.max(maxBytes,converted.length);
   }catch(Throwable error){String failure=path+" :: "+error;failures.add(failure);System.out.println("FAIL "+failure);if(error instanceof OutOfMemoryError)System.gc();}
   if((i+1)%25==0){System.gc();System.out.println("converted "+(i+1)+" / "+entries.length()+"; failures="+failures.size());}
  }
  JSONObject result=new JSONObject().put("charts",charts).put("notes",notes).put("noiseCharts",noiseCharts).put("noiseAreas",noise).put("scriptBytes",bytes)
    .put("maximumScriptBytes",maxBytes).put("maximumFrames",maxFrames).put("positionComparisons",positions).put("maxVisiblePixelError",maxVisibleError).put("assertions",checks).put("failures",new JSONArray(failures));
  System.out.println(result.toString());if(args.length>0){if(repair)Files.write(Paths.get("analysis/r5-script-repair-results.json"),result.toString(2).getBytes(StandardCharsets.UTF_8));if(!failures.isEmpty())System.exit(1);return;}
  Files.write(Paths.get("analysis/r5-script-export-results.json"),result.toString(2).getBytes(StandardCharsets.UTF_8));
  if(!failures.isEmpty())System.exit(1);
  catalog.put("phiscriptSwordaltScripts",1).put("scriptFormat","phiscriptFormat 1").put("scriptConverter",1);
  Files.write(Paths.get("analysis/swordalt/script-catalog.json"),PgrScriptConverter.canonical(catalog).getBytes(StandardCharsets.UTF_8));
 }
}
