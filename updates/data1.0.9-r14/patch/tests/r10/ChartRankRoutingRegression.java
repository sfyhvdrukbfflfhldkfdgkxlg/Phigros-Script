package io.github.phiscript.assets;

import android.content.Context;
import io.github.phiscript.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.input.*;
import io.github.phiscript.root.ModeRouting;
import io.github.phiscript.vision.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import org.json.*;

/** Load each difficulty through the production selection adapter, one cache entry at a time. */
public final class ChartRankRoutingRegression {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static OcrLabels labels(String title,String rank){return new OcrLabels(Collections.singletonList(title),Collections.singletonList(rank),Collections.emptyList(),false,false,null);}
    public static void main(String[] args)throws Exception{
        Context.root=Files.createTempDirectory("r10-rank-routing").toFile();Context context=new Context();
        AppSettings prefs=new AppSettings(context);ModeRouting.select(prefs,0);prefs.save("tapmod_enabled","false");prefs.save("tap_noise_avoid","false");TapMod.init(context);
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url){throw new AssertionError("Wrong chart selected; unexpected download: "+url.getPath());}}:null);
        int charts=0,notes=0,areas=0,matched=0,positions=0;List<String> ambiguous=new ArrayList<>();
        try(ZipFile zip=new ZipFile("count-current/Phigros_Swordalt_All_Scripts.zip")){
            JSONObject catalog=new JSONObject(new String(ExternalCharts.read(zip.getInputStream(zip.getEntry("swordalt-catalog.json")),4*1024*1024),StandardCharsets.UTF_8));
            ChartLibrary library=ExternalCharts.fromSwordaltCatalog(context,catalog);
            SessionChartSource source=SessionChartSource.phigros(context,library,new AtomicBoolean());
            List<PhiraCandidate> candidates=new ArrayList<>();Map<String,PhiraEntry> entries=new HashMap<>();
            for(PhiraEntry e:source.getEntries()){candidates.add(new PhiraCandidate(e.getId(),e.getTitle(),e.getLevel(),e.getAliases()));entries.put(e.getId(),e);}
            Map<String,ChartLocation> choices=new HashMap<>();for(ChartLocation c:library.getLocations())choices.put(c.getSongId()+"\n"+c.getDifficulty(),c);
            JSONArray list=catalog.getJSONArray("entries");
            for(int i=0;i<list.length();i++){
                JSONObject row=list.getJSONObject(i);String title=row.getString("title"),rank=row.getString("difficulty"),path=row.getString("path");
                ChartLocation location=choices.get("swordalt:"+ExternalCharts.hash(title)+"\n"+rank);ok(location!=null,"catalog rank must have its own selection: "+path);
                String id=SessionChartSource.id(location);PhiraEntry entry=entries.get(id);
                ok(entry!=null&&entry.getTitle().equals(title)&&entry.getLevel().equals(rank),"source identity must retain difficulty");
                if(row.optBoolean("siteVisible",true)&&!rank.equals("SP")){
                    PhiraCandidate match=source.match(labels(title,rank+" Lv.12"),candidates);
                    if(match==null)ambiguous.add(path);else{ok(match.getId().equals(id)&&match.getLevel().equals(rank),"OCR must not substitute a different difficulty: "+path);matched++;}
                }
                byte[] data=ExternalCharts.read(zip.getInputStream(zip.getEntry("charts/"+path)),256*1024*1024);
                File cache=new File(SwordaltSource.cacheDirectory(context),SwordaltSource.key(catalog,row)+".json");Files.write(cache.toPath(),data);
                Chart chart;
                try{chart=source.load(id,rank);}finally{Files.deleteIfExists(cache.toPath());}
                ok(chart.getNotes().size()==row.getInt("notes")&&BlockAreaSet.count(chart)==row.getInt("noiseCount"),"selected file must be the catalog's exact chart: "+path);
                for(Viewport viewport:new Viewport[]{new Viewport(0,0,1920,1080),new Viewport(13,17,2400,1080),new Viewport(0,0,2048,1536)})for(int at:new int[]{0,chart.getNotes().size()/2,chart.getNotes().size()-1}){
                    Note note=chart.getNotes().get(at);Point point=chart.position(note,note.getTimeSeconds(),viewport,false),normal=chart.judgeNormal(note,note.getTimeSeconds(),viewport);
                    ok(point!=null&&Float.isFinite(point.getX())&&Float.isFinite(point.getY())&&Float.isFinite(normal.getX())&&Float.isFinite(normal.getY()),"native position must remain finite");positions++;
                }
                try{source.load(id,rank.equals("EZ")?"HD":"EZ");throw new AssertionError("wrong rank accepted");}catch(IllegalStateException expected){checks++;}
                charts++;notes+=chart.getNotes().size();areas+=BlockAreaSet.count(chart);
                if(charts%50==0){System.gc();System.out.println("loaded exact rank "+charts+" / "+list.length());}
            }
        }
        ok(charts==1057&&notes==677156&&areas==17782,"complete script inventory");
        ok(matched==1037&&ambiguous.isEmpty(),"every visible non-SP formal title must resolve without a wrong-rank fallback");
        JSONObject result=new JSONObject().put("chartsLoadedByExactRank",charts).put("notes",notes).put("noiseAreas",areas).put("nativePositionSamples",positions).put("ocrExactMatches",matched).put("ambiguousOcrRequiresManual",new JSONArray(ambiguous)).put("wrongRankSubstitutions",0).put("assertions",checks);
        Files.write(Paths.get("r10/rank-routing-results.json"),result.toString(2).getBytes(StandardCharsets.UTF_8));System.out.println("PASS exact rank routing "+result);
    }
}
