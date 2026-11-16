import io.github.phiscript.assets.SharedChartImport;
import io.github.phiscript.engine.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
public final class NativeNoiseProbe {
 public static void main(String[] args)throws Exception{
  Chart c=SharedChartImport.parseJson(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
  JSONArray samples=new JSONArray(new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8));
  double[] shifts={-.012,-.005,0,.005,.012};int[] blocked=new int[5],matches=new int[5];
  JSONArray output=new JSONArray();int mismatches=0;
  for(int i=0;i<samples.length();i++){
   JSONObject sample=samples.getJSONObject(i);double t=sample.getDouble("t");
   // v5: Y-down relative to half viewport width; native screen coordinates are Y-down.
   float height=(float)(1600/sample.optDouble("aspect",16.0/9));
   float x=(float)((sample.getDouble("x")+1)*800),y=(float)(height/2+sample.getDouble("y")*800);
   JSONArray flags=new JSONArray(),expected=sample.getJSONArray("reference");
   for(int j=0;j<shifts.length;j++){
    boolean b=BlockAreaSet.covers(c,x,y,0,0,1600,height,t+shifts[j]);flags.put(b);
    if(b)blocked[j]++;if(b==expected.getBoolean(j))matches[j]++;else mismatches++;
   }
   output.put(new JSONObject().put("t",t).put("serial",sample.getInt("serial")).put("production",flags).put("reference",expected));
  }
  Files.write(Paths.get(args[2]),output.toString(2).getBytes(StandardCharsets.UTF_8));
  System.out.println("areas="+BlockAreaSet.count(c)+" samples="+samples.length()+" shifts="+java.util.Arrays.toString(shifts)+" blocked="+java.util.Arrays.toString(blocked)+" matches="+java.util.Arrays.toString(matches)+" mismatches="+mismatches);
 }
}
