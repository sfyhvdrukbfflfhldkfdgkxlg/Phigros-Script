package io.github.phiscript.assets;

import android.content.Context;
import io.github.phiscript.*;
import io.github.phiscript.root.ModeRouting;
import io.github.phiscript.vision.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Uses the real catalog and source boundary, without downloading chart contents. */
public final class RankMatchingRegression {
    static int checks;
    static SessionChartSource source;
    static List<PhiraCandidate> candidates;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static PhiraCandidate match(List<String> titles,List<String> ranks){return source.match(new OcrLabels(titles,ranks,Collections.emptyList(),false,false,null),candidates);}
    static void expect(String title,String rank,String expected){PhiraCandidate result=match(Collections.singletonList(title),Collections.singletonList(rank));ok(result!=null&&result.getTitle().equals(expected)&&result.getLevel().equals(rank),"exact title/rank: "+title+" / "+rank+" -> "+(result==null?"null":result.getTitle()+" / "+result.getLevel()));}
    public static void main(String[] args)throws Exception{
        boolean baseline=args.length>0&&args[0].equals("--baseline");Context.root=Files.createTempDirectory("r11-identity").toFile();Context context=new Context();ModeRouting.select(new AppSettings(context),0);
        JSONObject catalog=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/assets/phigros-swordalt-catalog-v1.json")),StandardCharsets.UTF_8));
        ChartLibrary library=ExternalCharts.fromSwordaltCatalog(context,catalog);source=SessionChartSource.phigros(context,library,new AtomicBoolean());candidates=new ArrayList<>();
        for(PhiraEntry entry:source.getEntries())candidates.add(new PhiraCandidate(entry.getId(),entry.getTitle(),entry.getLevel(),entry.getAliases()));
        int tested=0,matched=0;List<String> ambiguous=new ArrayList<>();JSONArray rows=catalog.getJSONArray("entries");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String rank=row.getString("difficulty");if(!row.optBoolean("siteVisible",true)||rank.equals("SP"))continue;
            String title=row.getString("title");PhiraCandidate result=match(Collections.singletonList(title),Collections.singletonList(rank+" Lv.12"));tested++;
            if(result==null)ambiguous.add(row.getString("path"));else{ok(result.getTitle().equals(title)&&result.getLevel().equals(rank),"OCR must not substitute song or rank: "+row.getString("path"));matched++;}
        }
        if(baseline){ok(ambiguous.size()==7&&matched==1030,"reproduce seven exact-title alias collisions");System.out.println("PASS R10 reproduction: "+ambiguous);return;}
        ok(tested==1037&&matched==tested&&ambiguous.isEmpty(),"every visible non-SP difficulty must match its formal title: "+ambiguous);
        for(String rank:new String[]{"EZ","HD","IN","AT"})expect("Ark",rank,"Ark");
        for(String rank:new String[]{"EZ","HD","IN"}){expect("Rebirth",rank,"Re：birth");expect("重生",rank,"重生");expect("望影の方舟Six",rank,"望影の方舟Six");}
        for(String title:new String[]{"ARK","Ａｒｋ","IN Lv.12 Ark","Ark IN Lv.12"})expect(title,"IN","Ark");
        PhiraCandidate split=match(Arrays.asList("Re：","birth"),Collections.singletonList("IN"));ok(split!=null&&split.getTitle().equals("Re：birth"),"wrapped formal title");
        for(List<String> ranks:Arrays.asList(Collections.<String>emptyList(),Arrays.asList("IN","HD"),Collections.singletonList("IN HD"),Collections.singletonList("unknown")))ok(match(Collections.singletonList("Ark"),ranks)==null,"missing/conflicting difficulty must remain unresolved");
        ok(match(Arrays.asList("Ark","望影の方舟Six"),Collections.singletonList("IN"))==null,"two exact formal titles remain ambiguous");
        ok(match(Collections.singletonList("Ark IN Lv.12"),Collections.singletonList("HD"))==null,"title difficulty must agree with dedicated difficulty region");
        ok(match(Arrays.asList("IN Ark","HD Ark"),Collections.singletonList("IN"))==null,"contradictory title difficulties must remain unresolved");
        ok(match(Collections.singletonList("Rebirth"),Collections.singletonList("AT"))==null,"unavailable difficulty must not fall back");
        ok(match(Collections.singletonList("there is no such chart"),Collections.singletonList("IN"))==null,"unknown chart remains unmatched");
        ok(match(Collections.singletonList("Another Me"),Collections.singletonList("IN"))==null,"shared alias without unique formal title remains ambiguous");
        JSONObject result=new JSONObject().put("visibleRanks",tested).put("exactMatches",matched).put("unresolvedFormalTitles",new JSONArray(ambiguous)).put("wrongSongOrRankSubstitutions",0).put("assertions",checks);
        Files.write(Paths.get("r11/rank-matching-results.json"),result.toString(2).getBytes(StandardCharsets.UTF_8));System.out.println("PASS R11 rank matching "+result);
    }
}
