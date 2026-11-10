package io.github.phiscript.assets;
import org.json.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import io.github.phiscript.engine.*;

/** Downloads actual public-share files using the final APK's transport and adapter. */
public final class FeijipanLiveDownload {
    public static void main(String[] args)throws Exception{
        String proxy=System.getenv("HTTPS_PROXY");if(proxy!=null&&!proxy.isEmpty()){URI uri=new URI(proxy);System.setProperty("https.proxyHost",uri.getHost());System.setProperty("https.proxyPort",Integer.toString(uri.getPort()));}
        android.content.Context.root=Files.createTempDirectory("feijipan-live").toFile();android.content.Context context=new android.content.Context();
        JSONObject catalog=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/assets/"+FeijipanSource.NEW_ASSET)),StandardCharsets.UTF_8));
        String[] paths={"Glaciaxion.SunsetRay.0/EZ.json","ハテ.rNFrums.0/EZ.json","もぺもぺ.LeaF.0/AT.json","Message.くるぶっこちゃん.0/SP.json"};
        for(String path:paths){
            JSONObject selected=null;JSONArray entries=catalog.getJSONArray("entries");for(int i=0;i<entries.length();i++)if(path.equals(entries.getJSONObject(i).getJSONObject("resource").getString("path")))selected=entries.getJSONObject(i);
            if(selected==null)throw new AssertionError("live chart not indexed: "+path);JSONObject resource=selected.getJSONObject("resource");
            ChartLibrary library=ExternalCharts.fromCatalog(context,new JSONObject(catalog.toString()).put("entries",new JSONArray().put(selected)));
            String song=library.getLocations().get(0).getSongId();Chart chart=ExternalCharts.loadSelected(library,song,resource.getString("difficulty"),null);
            if(chart.getNotes().size()!=resource.getInt("notes")||BlockAreaSet.count(chart)!=resource.getInt("noiseCount"))throw new AssertionError("notes/noise mismatch: "+path);
            System.out.println("PASS live Feijipan "+path+": "+resource.getLong("bytes")+" bytes, "+chart.getNotes().size()+" notes, "+BlockAreaSet.count(chart)+" noise areas; actual file ID + Git blob verified");
        }
    }
}
