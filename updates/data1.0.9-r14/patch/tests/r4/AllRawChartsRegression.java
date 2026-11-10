import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.json.*;import io.github.phiscript.engine.*;import io.github.phiscript.input.TapMod;
/** Exercise raw PGR parsing, noise capture and original positions across the entire extracted snapshot. */
public class AllRawChartsRegression{
 public static void main(String[] args)throws Exception{
  JSONObject catalog=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/assets/phigros-swordalt-catalog-v1.json")),StandardCharsets.UTF_8));JSONArray entries=catalog.getJSONArray("entries");int charts=0,noiseCharts=0,areas=0,notes=0;List<String> failed=new ArrayList<>();TapMod.init(new android.content.Context());
  for(int i=0;i<entries.length();i++){
   JSONObject e=entries.getJSONObject(i);try{
    String text=new String(Files.readAllBytes(Paths.get("analysis/swordalt/raw-charts",e.getString("path"))),StandardCharsets.UTF_8);Chart chart=Chart.Companion.parse(text);
    int count=BlockAreaSet.count(chart);if(count!=e.getInt("noiseCount"))throw new AssertionError("noise data count mismatch "+count+" / "+e.getInt("noiseCount"));
    if(count>0){noiseCharts++;areas+=count;}
    notes+=chart.getNotes().size();Viewport v=new Viewport(0,0,1920,1080);
    for(int sample:new int[]{0,chart.getNotes().size()/2,chart.getNotes().size()-1})if(sample>=0){Note n=chart.getNotes().get(sample);Point p=chart.position(n,n.getTimeSeconds(),v,true);if(p!=null&&(!Float.isFinite(p.getX())||!Float.isFinite(p.getY())))throw new AssertionError("nonfinite original position");}
    charts++;
   }catch(Throwable error){if(error instanceof OutOfMemoryError)System.gc();String failure=e.getString("path")+" :: "+error.toString();failed.add(failure);System.out.println("FAIL "+failure);}
   if((i+1)%50==0){System.gc();System.out.println("checked "+(i+1)+" / "+entries.length()+"; failures="+failed.size());}
  }
  JSONObject result=new JSONObject().put("parsedCharts",charts).put("noiseCharts",noiseCharts).put("noiseAreas",areas).put("notes",notes).put("failures",new JSONArray(failed));Files.write(Paths.get("analysis/r4-all-raw-results.json"),result.toString(2).getBytes(StandardCharsets.UTF_8));System.out.println(result.toString());if(!failed.isEmpty())System.exit(1);
 }
}
