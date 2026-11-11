package io.github.phiscript.assets;

import android.content.Context;
import io.github.phiscript.*;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.input.TouchService;
import io.github.phiscript.root.ModeRouting;
import io.github.phiscript.vision.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Chart identity and storage boundary for the unchanged Phira session state machine. */
public final class SessionChartSource {
    public static final String MANUAL_ID="phigros_session_chart_id";
    private final PhiraLibrary phira;
    private final ChartLibrary phigros;
    private final Context context;
    private final AtomicBoolean stop;
    private final String selected;
    private final List<PhiraEntry> entries;
    private final List<SongCandidate> songs;
    private final Map<String,ChartLocation> locations=new LinkedHashMap<>();
    private final Map<String,String> identities=new HashMap<>();

    private SessionChartSource(PhiraLibrary library){
        if(library==null)throw new IllegalArgumentException("Phira 谱库未就绪");
        phira=library;phigros=null;context=null;stop=null;selected="";entries=null;songs=null;
    }
    private SessionChartSource(Context c,ChartLibrary library,AtomicBoolean cancelled,boolean manual)throws Exception{
        if(library==null)throw new IllegalStateException("请先选择 Phigros 谱库");
        phira=null;phigros=library;context=c.getApplicationContext();stop=cancelled;songs=library.songs();
        List<PhiraEntry> metadata=new ArrayList<>();Map<String,SongCandidate> names=new HashMap<>();for(SongCandidate song:songs)names.put(song.getId(),song);
        for(ChartLocation location:library.getLocations()){
            String id=id(location);if(locations.put(id,location)!=null)throw new IllegalStateException("Phigros 谱面标识重复");
            if(identities.put(location.getSongId()+"\n"+location.getDifficulty(),id)!=null)throw new IllegalStateException("Phigros 曲名和难度不能唯一匹配");
            metadata.add(ExternalCharts.sessionEntry(library,location,id,names.get(location.getSongId())));
        }
        entries=Collections.unmodifiableList(metadata);selected=manual?new AppSettings(c).text(MANUAL_ID,""):"";
        if(!selected.isEmpty()&&!locations.containsKey(selected))throw new IllegalStateException("手选 Phigros 谱面已变化，请重新选择");
    }
    public static SessionChartSource phira(PhiraLibrary library){return new SessionChartSource(library);}
    public static SessionChartSource phigros(Context c,ChartLibrary library,AtomicBoolean stop)throws Exception{
        return new SessionChartSource(c,library,stop,ModeRouting.kind(new AppSettings(c))==ModeRouting.Kind.PHIGROS_MANUAL);
    }
    public List<PhiraEntry> getEntries(){return phira!=null?phira.getEntries():entries;}
    public boolean isPhigros(){return phigros!=null;}
    public String selectionId(SessionSettings settings){return phira!=null?settings.getPhiraChartId():selected;}
    public boolean isForeground(){
        TouchService service=TouchService.Companion.getCurrent();String pkg=service==null?null:service.getForegroundPackage();
        return phira!=null?service!=null&&service.getGameForeground()&&GamePackages.isPhira(pkg):TouchService.GAME_PACKAGE.equals(pkg);
    }
    public PhiraCandidate match(OcrLabels labels,List<PhiraCandidate> candidates){
        if(phira!=null)return PhiraMatcher.INSTANCE.match(labels.getTitleTexts(),labels.getDifficultyTexts(),candidates);
        Identity identity=PhigrosChartMatcher.match(labels.getTitleTexts(),labels.getDifficultyTexts(),songs);
        if(identity==null)return null;String id=identities.get(identity.getSongId()+"\n"+identity.getDifficulty());
        if(id==null)return null;for(PhiraCandidate candidate:candidates)if(candidate.getId().equals(id))return candidate;return null;
    }
    public Chart load(String id,String level)throws Exception{
        if(phira!=null)return phira.load(id,level);
        ChartDownload.check(stop);ChartLocation location=locations.get(id);
        if(location==null||!location.getDifficulty().equals(level))throw new IllegalStateException("Phigros 谱面已变化，请重新识别或选择");
        Chart chart=ExternalCharts.loadSelected(phigros,location.getSongId(),location.getDifficulty(),stop);ChartDownload.check(stop);
        return chart;
    }
    public static String id(ChartLocation location)throws Exception{return "phigros:"+ExternalCharts.hash(location.getSongId()+"\n"+location.getDifficulty());}
    public static boolean hasManual(Context c,ChartLibrary library){
        if(library==null)return false;String choice=new AppSettings(c).text(MANUAL_ID,"");if(choice.isEmpty())return false;
        try{for(ChartLocation location:library.getLocations())if(id(location).equals(choice))return true;}catch(Exception ignored){}return false;
    }
    public static void selectManual(Context c,ChartLibrary library,ChartLocation choice)throws Exception{
        boolean found=false;for(ChartLocation location:library.getLocations())if(location.getSongId().equals(choice.getSongId())&&location.getDifficulty().equals(choice.getDifficulty())){found=true;break;}
        if(!found)throw new IllegalStateException("所选 Phigros 谱面已变化");
        AppSettings prefs=new AppSettings(c);prefs.save(MANUAL_ID,id(choice));ModeRouting.select(prefs,3);
    }
}
