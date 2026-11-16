package io.github.phiscript.assets;

import android.content.Context;
import android.os.SystemClock;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.vision.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-capture metadata cache; a missing/ambiguous result never becomes a chart. */
final class OnlinePhiraSession {
    private final Context context;private final AtomicBoolean stop;private final OnlinePhiraLibrary api=new OnlinePhiraLibrary();
    private final Map<String,OnlinePhiraLibrary.Search> searches=new LinkedHashMap<>();
    private final Map<String,OnlinePhiraLibrary.Entry> remote=new LinkedHashMap<>();
    private final Map<String,PhiraEntry> imported=new LinkedHashMap<>();
    private String pending;private int evidence,requests;private long nextRequest,lastLog;
    OnlinePhiraSession(Context context,AtomicBoolean stop){this.context=context.getApplicationContext();this.stop=stop;}
    List<PhiraEntry> entries(List<PhiraEntry> local){
        List<PhiraEntry> result=new ArrayList<>(local);
        for(OnlinePhiraLibrary.Entry e:remote.values()){
            PhiraEntry actual=imported.get(e.key());
            result.add(actual==null?e.metadata():new PhiraEntry(e.key(),e.title,e.level,Collections.emptyList(),actual.getAspectRatio(),actual.getForceAspectRatio()));
        }return result;
    }
    static Set<String> titles(List<String> lines){
        Set<String> out=new LinkedHashSet<>();
        for(int start=0;start<lines.size();start++){StringBuilder s=new StringBuilder();for(int count=0;count<3&&start+count<lines.size();count++){if(count>0)s.append(' ');s.append(lines.get(start+count));String value=SongMatcher.INSTANCE.normalize(s.toString());if(value.length()>=2&&value.length()<=200)out.add(value);}}
        return out;
    }
    static PhiraCandidate unique(OcrLabels labels,OnlinePhiraLibrary.Search search){
        if(!search.complete)return null;
        Set<String> titles=titles(labels.getTitleTexts());List<PhiraCandidate> exact=new ArrayList<>();
        for(OnlinePhiraLibrary.Entry e:search.entries)if(titles.contains(SongMatcher.INSTANCE.normalize(e.title)))exact.add(new PhiraCandidate(e.key(),e.title,e.level,Collections.emptyList()));
        return PhiraMatcher.INSTANCE.match(labels.getTitleTexts(),labels.getDifficultyTexts(),exact);
    }
    PhiraCandidate match(OcrLabels labels){
        if(labels.getResultScreen()||labels.getTitleTexts().isEmpty()||labels.getDifficultyTexts().isEmpty())return null;
        List<String> lines=new ArrayList<>(labels.getTitleTexts());lines.removeIf(s->s.trim().length()<2||s.length()>200);lines.sort((a,b)->Integer.compare(b.length(),a.length()));
        if(lines.isEmpty())return null;String title=lines.get(0).trim(),key=SongMatcher.INSTANCE.normalize(title);
        if(key.equals(pending))evidence++;else{pending=key;evidence=1;}
        if(evidence<2)return null;
        OnlinePhiraLibrary.Search found=searches.get(key);long now=SystemClock.uptimeMillis();
        if(found==null){
            // Identity verification runs alongside the input worker. A noisy
            // OCR frame must not trigger a slow HTTP request while fingers run.
            if(RuntimeState.INSTANCE.getActiveTouchStop().get()!=null)return null;
            if(now<nextRequest||requests>=12)return null;
            nextRequest=now+4000;requests++;
            try{
                RuntimeState.INSTANCE.log("Phira 在线谱库：按识别结果查询 "+title);
                found=api.search(title,stop);searches.put(key,found);
                for(OnlinePhiraLibrary.Entry e:found.entries)remote.put(e.key(),e);
            }catch(Exception e){if(stop.get()||Thread.currentThread().isInterrupted())return null;RuntimeState.INSTANCE.log("Phira 在线查询失败："+e.getMessage()+"；可按编号导入后重试");return null;}
        }
        PhiraCandidate result=unique(labels,found);
        if(result==null&&now-lastLog>5000){lastLog=now;RuntimeState.INSTANCE.log("Phira 在线候选不唯一或难度不符，未导入；请按谱面编号导入");}
        return result;
    }
    Chart load(String id,String level)throws Exception{
        OnlinePhiraLibrary.Entry entry=remote.get(id);if(entry==null||!entry.level.equals(level))throw new IllegalStateException("在线谱面标识已变化，请重新识别");
        PhiraEntry local=imported.get(id);
        if(local==null){RuntimeState.INSTANCE.log("下载并导入 Phira #"+entry.id+"："+entry.title+" / "+entry.level);local=api.importEntry(context,entry,stop);imported.put(id,local);}
        PhiraLibrary library=RuntimeState.INSTANCE.getPhiraLibrary();if(library==null)throw new IllegalStateException("Phira 导入谱库不可用");
        return library.load(local.getId(),local.getLevel());
    }
    boolean contains(String id){return remote.containsKey(id);}
}
