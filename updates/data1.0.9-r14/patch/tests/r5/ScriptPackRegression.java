package io.github.phiscript.assets;
import java.io.*;import java.nio.file.*;import java.util.*;import org.json.*;import io.github.phiscript.engine.*;

public final class ScriptPackRegression {
 public static void main(String[] args)throws Exception{
  android.content.Context.root=Files.createTempDirectory("scripts-full-pack").toFile();android.content.Context c=new android.content.Context();
  JSONObject catalog=SwordaltSource.importPack(c,Files.newInputStream(Paths.get("deliverables/Phigros_Swordalt_All_Scripts.zip")),null);JSONArray rows=catalog.getJSONArray("entries");
  if(!SwordaltSource.scripts(catalog)||rows.length()!=1057)throw new AssertionError("script pack incomplete");long bytes=0;int notes=0,areas=0,charts=0;
  // Use the application's actual cache/parse route for every exported script.
  for(int i=0;i<rows.length();i++){
   JSONObject row=rows.getJSONObject(i);Chart chart=SwordaltSource.loadChart(c,catalog,row,null);
   if(chart.getNotes().size()!=row.getInt("notes")||BlockAreaSet.count(chart)!=row.getInt("noiseCount"))throw new AssertionError("script data changed: "+row.getString("path"));
   bytes+=row.getLong("bytes");notes+=chart.getNotes().size();areas+=BlockAreaSet.count(chart);charts++;
   if(charts%50==0){System.gc();System.out.println("loaded offline "+charts+" / "+rows.length());}
  }
  if(notes!=677156||areas!=17782)throw new AssertionError("incomplete notes/noise");
  File[] cached=SwordaltSource.cacheDirectory(c).listFiles(f->f.getName().endsWith(".json"));if(cached.length!=1057)throw new AssertionError("wrong cache count");long cachedBytes=0;for(File f:cached)cachedBytes+=f.length();if(cachedBytes!=bytes)throw new AssertionError("wrong cache size");
  if(!SwordaltSource.scripts(SwordaltSource.saved(c)))throw new AssertionError("script selection not restored");
  if(ExternalCharts.deleteCacheFiles(c,Arrays.asList(cached))!=0||SwordaltSource.cacheDirectory(c).listFiles().length!=0||!SwordaltSource.scripts(SwordaltSource.saved(c)))throw new AssertionError("delete must retain catalog");
  System.out.println("PASS full script pack: "+charts+" scripts, "+notes+" notes, "+areas+" noise areas, "+bytes+" bytes; actual import, offline load and deletion");
 }
}
