package io.github.phiscript.assets;
import org.json.*;import java.nio.file.*;import java.util.concurrent.atomic.AtomicBoolean;import io.github.phiscript.engine.*;
public class SwordaltLiveDownload{
 public static void main(String[] args)throws Exception{
  android.content.Context.root=Files.createTempDirectory("swordalt-live").toFile();android.content.Context c=new android.content.Context();JSONObject catalog=SwordaltSource.saved(c);JSONObject entry=SwordaltSource.entry(catalog,"Message.くるぶっこちゃん.0/EZ.json");Chart chart=SwordaltSource.loadChart(c,catalog,entry,new AtomicBoolean());if(BlockAreaSet.count(chart)!=24)throw new AssertionError("noise missing");System.out.println("PASS live GitHub original download: "+entry.getLong("bytes")+" bytes, "+chart.getNotes().size()+" notes, "+BlockAreaSet.count(chart)+" noise areas; Git blob hash verified");
 }
}
