package io.github.phiscript.assets;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import org.json.*;import io.github.phiscript.engine.*;
public class FullOfflinePackRegression{
 public static void main(String[] args)throws Exception{
  android.content.Context.root=Files.createTempDirectory("swordalt-full-pack").toFile();android.content.Context c=new android.content.Context();JSONObject catalog=SwordaltSource.importPack(c,Files.newInputStream(Paths.get("deliverables/Phigros_Swordalt_OriginalCharts_and_Noise.zip")),new AtomicBoolean());
  if(catalog.getJSONArray("entries").length()!=1057)throw new AssertionError("incomplete import");File[] cached=SwordaltSource.cacheDirectory(c).listFiles(f->f.getName().endsWith(".json"));if(cached.length!=1057)throw new AssertionError("missing files");long size=0;for(File f:cached)size+=f.length();if(size!=2696737181L)throw new AssertionError("wrong total size");
  JSONObject ametrine=SwordaltSource.entry(catalog,"Ametrine.GRYSCLMIssionary.0/IN.json");Chart chart=SwordaltSource.loadChart(c,catalog,ametrine,new AtomicBoolean());if(BlockAreaSet.count(chart)!=24)throw new AssertionError("noise not restored from offline pack");
  if(SwordaltSource.saved(c).getJSONArray("entries").length()!=1057)throw new AssertionError("catalog not saved");
  if(ExternalCharts.deleteCacheFiles(c,Arrays.asList(cached))!=0||SwordaltSource.cacheDirectory(c).listFiles().length!=0)throw new AssertionError("bulk delete failed");if(!new File(SwordaltSource.directory(c),"catalog.json").exists())throw new AssertionError("catalog deleted");
  System.out.println("PASS full offline pack import: 1057 original files, 2696737181 bytes, noise restored; all-cache deletion retains catalog");
 }
}
