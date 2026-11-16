package io.github.phiscript.assets;
import org.json.*;import io.github.phiscript.engine.*;
public final class RpeLayerRegression {
 public static void main(String[] args)throws Exception{
  JSONObject root=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("upstream/updates/data1.0.9/patch/tests/fixtures/rpe_empty_layers_bpm_parent.json")),"UTF-8"));
  JSONObject normalized=RpeNativeNormalizer.INSTANCE.normalize(root);JSONArray tracks=normalized.getJSONArray("lines").getJSONObject(0).getJSONObject("object").getJSONArray("x");
  if(tracks.length()!=2)throw new AssertionError("RPE conversion discarded valid layers after an empty layer: "+tracks.length());
  double sum=tracks.getJSONArray(0).getJSONObject(0).getDouble("value")+tracks.getJSONArray(1).getJSONObject(0).getDouble("value");if(Math.abs(sum-180.0/675)>1e-6)throw new AssertionError("RPE additive movement differs");
  JSONArray nativeLines=normalized.getJSONArray("lines");JSONObject object=nativeLines.getJSONObject(0).getJSONObject("object");
  JSONArray withGaps=new JSONArray().put(new JSONArray());for(int i=0;i<tracks.length();i++){withGaps.put(tracks.getJSONArray(i));withGaps.put(new JSONArray());}object.put("x",withGaps);
  Chart parsed=Chart.Companion.parse(normalized.toString());if(parsed.getNotes().size()!=6)throw new AssertionError("native layers/fake notes changed");
  System.out.println("PASS RPE conversion and native empty-layer compatibility: additive X, leading/intermediate empty tracks, six real notes");
 }
}
