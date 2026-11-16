package io.github.phiscript.input;

import android.accessibilityservice.*;
import android.os.SystemClock;
import io.github.phiscript.engine.*;
import io.github.phiscript.assets.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.json.*;

public final class Regression {
    static int checks;
    static final Function1<String,Unit> STATUS = s -> Unit.INSTANCE;
    static void ok(boolean condition,String msg){checks++;if(!condition)throw new AssertionError(msg);}
    static void reset(){SystemClock.now=100000;GestureDescription.maxStrokes=20;}
    static Note n(int id,int kind,double start,double end){
        return new Note(id,kind,start,end,id,1,0,true,0);
    }
    static Chart chart(Note... notes){
        return Chart.Companion.fromNative$app_debug(Arrays.asList(notes),0,
            (n,t,v)->new Point(100+n.getId()*65+(float)(t*15),300),
            (n,t,v)->new Point(0,-1));
    }
    static TouchService play(Chart c,long elapsed) throws Exception {
        TouchService s=new TouchService();AtomicBoolean playing=new AtomicBoolean();
        ChartMultiTouchPlayer.play(s,c,SystemClock.now-elapsed,new Viewport(0,0,1000,600),
            new AtomicBoolean(),STATUS,playing,new AtomicBoolean());
        ok(!playing.get(),"playing flag leaked");ok(s.roots.isEmpty(),"held pointer leaked");return s;
    }
    static VisualTouch v(long id,VisualTouchKind kind,float x){
        return new VisualTouch(id,kind,x,.5f,0,-.05f);
    }
    static VisualTouchFrame frame(long at,VisualTouch... t){
        return new VisualTouchFrame(at,1000,600,Arrays.asList(t));
    }
    static void plannerAndPlayback()throws Exception{
        reset();TouchService s=play(chart(n(1,3,.02,.45),n(2,3,.02,.3),
            n(3,3,.08,.38),n(4,1,.1,.1),n(5,4,.2,.2),n(6,2,.25,.25)),0);
        ok(s.downs.size()==6,"every hold/shot must have exactly one DOWN");
        ok(s.ups.size()==6,"every hold/shot must have exactly one UP");
        ok(s.peak>=4,"simultaneous long holds and shots were serialized");
        ok(s.history.size()>8,"long holds were not continued");
        reset();s=play(chart(n(1,3,0,.45),n(2,1,.18,.18)),120);
        ok(s.downs.size()==2,"ongoing long hold was skipped at startup");
        reset();List<Note> ten=new ArrayList<>();for(int i=0;i<10;i++)ten.add(n(i,3,.02,.25));
        s=play(chart(ten.toArray(new Note[0])),0);
        ok(s.downs.size()==10&&s.peak==10,"10-hold chord failed");
        reset();List<Note> handoff=new ArrayList<>(ten);
        for(int i=0;i<10;i++)handoff.add(n(i,3,.25,.5));
        s=play(chart(handoff.toArray(new Note[0])),0);
        ok(s.downs.size()==20&&s.peak==10,"hold-tail padding caused artificial 20-finger overlap");
        reset();List<TouchEvent> timeline=new ArrayList<>();
        for(int i=0;i<8;i++)timeline.add(new TouchEvent(n(i,3,0,1),0,1000));
        for(int i=0;i<6;i++)timeline.add(new TouchEvent(n(8+i,1,0,0),1+i*5,3+i*5));
        ChordPlanner.checkOverlap(timeline,10);
        ok(ChordPlanner.endOfBatch(timeline,8,8,0,20)==32,"stroke total confused with simultaneous pointers");
        GestureDescription.maxStrokes=10;
        ok(ChordPlanner.endOfBatch(timeline,8,8,0,10)==11,"OEM stroke batch must split at first excluded event");
        reset();boolean rejected=false;TouchService capacityService=new TouchService();AtomicBoolean capacityPlaying=new AtomicBoolean();
        try{List<Note> eleven=new ArrayList<>(ten);eleven.add(n(10,3,.02,.25));ChartMultiTouchPlayer.play(capacityService,chart(eleven.toArray(new Note[0])),SystemClock.now,
            new Viewport(0,0,1000,600),new AtomicBoolean(),STATUS,capacityPlaying,new AtomicBoolean());}
        catch(IllegalStateException e){rejected=e.getMessage().contains("容量");}
        ok(rejected&&capacityService.peak<=10&&capacityService.roots.isEmpty()&&!capacityPlaying.get(),"runtime Android capacity must release input after startup precheck removal");
        reset();s=new TouchService();s.cancelAt=2;AtomicBoolean playing=new AtomicBoolean();
        boolean cancelled=false;try{ChartMultiTouchPlayer.play(s,chart(n(0,3,.01,.5)),SystemClock.now,
            new Viewport(0,0,1000,600),new AtomicBoolean(),STATUS,playing,new AtomicBoolean());}
        catch(IllegalStateException e){cancelled=e.getMessage().contains("取消");}
        ok(cancelled&&!playing.get(),"system cancellation was mistaken for completion");
        reset();s=new TouchService();s.rejectAt=2;playing=new AtomicBoolean();
        boolean rejectedDispatch=false;try{ChartMultiTouchPlayer.play(s,chart(n(0,3,.01,.5)),SystemClock.now,
            new Viewport(0,0,1000,600),new AtomicBoolean(),STATUS,playing,new AtomicBoolean());}
        catch(IllegalStateException e){rejectedDispatch=e.getMessage().contains("拒绝");}
        ok(rejectedDispatch&&!playing.get()&&s.roots.isEmpty(),"rejected dispatch did not release last accepted hold");
        reset();s=new TouchService();AtomicBoolean stop=new AtomicBoolean();s.after=g->stop.set(true);
        ChartMultiTouchPlayer.play(s,chart(n(0,3,.01,10)),SystemClock.now,new Viewport(0,0,1000,600),stop,STATUS,new AtomicBoolean(),new AtomicBoolean());
        ok(s.roots.isEmpty()&&s.ups.size()==1,"manual stop must release the long key");
        reset();s=play(Chart.Companion.fromNative$app_debug(Arrays.asList(n(0,3,.01,.1),n(1,1,.06,.06)),0,
            (n,t,v)->new Point(n.getId()==0?-500:200,300),(n,t,v)->new Point(0,-1)),0);
        ok(s.downs.size()==2,"offscreen note must not abort remaining song");
        System.out.println("PASS chart planner, 10 holds, startup catch-up, mixed chords, cancellation, release, edge fallback");
    }

    static void visual()throws Exception{
        reset();VisualChordState state=new VisualChordState();
        VisualTouch a=v(1,VisualTouchKind.HOLD,.2f), b=v(2,VisualTouchKind.HOLD,.8f);
        ok(state.update(frame(100,a,b),100).getHolds().size()==2,"two holds lost");
        ok(state.update(frame(120,a),120).getHolds().size()==2,"one missed frame released a hold");
        ok(state.update(frame(220,a),220).getHolds().size()==1,"missing hold retained forever");
        VisualTouch tap=v(3,VisualTouchKind.TAP,.5f);
        ok(state.update(frame(230,a,tap),230).getShots().size()==1,"tap missing");
        ok(state.update(frame(230,a,tap),240).getShots().isEmpty(),"same frame repeated a tap");
        ok(state.update(frame(250,v(1,VisualTouchKind.TAP,.2f)),250).getShots().size()==1,"hold-to-tap reclassification crashed");
        reset();TouchService service=new TouchService();AtomicBoolean stop=new AtomicBoolean();
        List<VisualTouch> old=new ArrayList<>(), fresh=new ArrayList<>();
        for(int i=0;i<10;i++){old.add(v(i,VisualTouchKind.HOLD,.05f+i*.09f));fresh.add(v(20+i,VisualTouchKind.HOLD,.05f+i*.09f));}
        service.after=g->{if(service.sends>=4)stop.set(true);};
        VisualMultiTouchPlayer.play(service,1000,600,()->new VisualTouchFrame(SystemClock.now,1000,600,
            service.sends==0?old:fresh),stop,()->true,STATUS);
        ok(service.downs.size()==20,"10-to-10 hold handoff lost a chord");
        ok(service.roots.isEmpty()&&service.peak==10,"handoff exceeded fingers or failed cleanup");
        reset();TouchService s=new TouchService();AtomicBoolean finish=new AtomicBoolean();
        List<VisualTouch> taps=new ArrayList<>();for(int i=0;i<14;i++)taps.add(v(i,VisualTouchKind.TAP,.05f+i*.06f));
        s.after=g->{if(s.downs.size()>=14)finish.set(true);};
        VisualMultiTouchPlayer.play(s,1000,600,()->new VisualTouchFrame(SystemClock.now,1000,600,taps),finish,()->true,STATUS);
        ok(s.downs.size()==14,"visual shots above batch capacity were lost");
        System.out.println("PASS visual hold dropout, ID reclassification, duplicate frame, 10-to-10 handoff, queued shots");
    }

    static Chart parse(String name)throws Exception{
        String text=new String(Files.readAllBytes(Paths.get("patch/tests/fixtures/"+name)),"UTF-8");
        if(name.endsWith(".pec"))text=PhiraPecDecoder.INSTANCE.toJson(text);
        return PhiraChartParser.INSTANCE.parse(text,0);
    }
    static void formats()throws Exception{
        for(String f:new String[]{"pgr_v1_multihold.json","pgr_v3_multihold.json",
                "rpe_empty_layers_bpm_parent.json","pec_multihold.pec"}){
            Chart c=parse(f);ok(c.getNotes().size()>=5,"parsed note count "+f);
            long holds=c.getNotes().stream().filter(n->n.getType()==3).count();
            ok(holds>=2,"long note type mapping "+f);
            for(Note n:c.getNotes()){
                Point p=c.position(n,n.getTimeSeconds(),new Viewport(0,0,1000,600),false);
                ok(p!=null&&Float.isFinite(p.getX())&&Float.isFinite(p.getY()),"invalid transformed position "+f);
            }
            reset();TouchService playback=play(c,Math.max(0,Math.round(c.getFirstNoteSeconds()*1000)-40));
            ok(playback.downs.size()==c.getNotes().size(),"parser-to-input lost notes "+f);
            ok(playback.peak>=2,"parser-to-input lost multihold "+f);
            System.out.println("PASS parser "+f+": "+c.getNotes().size()+" notes, "+holds+" holds");
        }
        Chart c=parse("rpe_empty_layers_bpm_parent.json");
        ok(c.getNotes().size()==6,"fake note was not ignored");
        Note h=c.getNotes().stream().filter(n->n.getType()==3&&n.getLineIndex()==0).findFirst().get();
        ok(Math.abs(h.getEndSeconds()-3)<.001,"BPM-change hold tail is wrong");
        // First layer is empty. The two later X layers must sum rather than be discarded.
        JSONObject root=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/tests/fixtures/rpe_empty_layers_bpm_parent.json")),"UTF-8"));
        JSONArray layers=root.getJSONArray("judgeLineList").getJSONObject(0).getJSONArray("eventLayers");
        JSONObject normalized=RpeNativeNormalizer.INSTANCE.normalize(root);
        JSONArray tracks=normalized.getJSONArray("lines").getJSONObject(0).getJSONObject("object").getJSONArray("x");
        ok(tracks.length()==2,"empty layer discarded valid later layers");
        double sum=tracks.getJSONArray(0).getJSONObject(0).getDouble("value")+tracks.getJSONArray(1).getJSONObject(0).getDouble("value");
        ok(Math.abs(sum-180.0/675)<1e-6,"additive X transform");
        // Repeat after putting a nonempty first layer and an empty second layer.
        layers.put(0,layers.getJSONObject(1));layers.put(1,new JSONObject());
        c=PhiraChartParser.INSTANCE.parse(root.toString(),0);
        ok(c.getNotes().size()==6,"non-first empty RPE layer rejected");
        System.out.println("PASS layered animations, empty-layer gaps, fake notes, parent lines, BPM-changing holds");
    }
    public static void main(String[] args)throws Exception{
        plannerAndPlayback();visual();formats();System.out.println("ALL "+checks+" CHECKS PASSED (host simulation; no device run)");
    }
}
