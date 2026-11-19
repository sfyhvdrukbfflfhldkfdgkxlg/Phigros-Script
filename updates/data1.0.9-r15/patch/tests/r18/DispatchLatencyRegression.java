package io.github.phiscript.input;

import android.os.SystemClock;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.InputBackend;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import org.json.*;

/** Geometry CPU time advances the same wall clock used by dispatch, after
 * preflight has completed. The observer checks actual physical pointer times. */
public final class DispatchLatencyRegression {
    static final Viewport V=new Viewport(0,0,1000,600);static boolean runtime;static int cost,checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static Note tap(int id,double time){return new Note(id,1,time,time,0,1,0,true,0);}
    static Chart chart(List<Note> notes){return Chart.Companion.fromNative$app_debug(notes,0,(note,t,v)->{
        if(runtime)SystemClock.now+=cost;
        return new Point(500+(float)(t-note.getTimeSeconds())*1200,300);
    },(note,t,v)->new Point(0,1));}
    static TouchService play(Chart chart,int cpu,List<String> status)throws Exception{
        cost=cpu;runtime=false;SystemClock.now=100000;InputBackend.rootEnabled=false;
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new android.content.Context());
        TouchService service=new TouchService();service.prepareChart(chart,V);runtime=true;
        try{ChartMultiTouchPlayer.play(service,chart,100000,V,new AtomicBoolean(),text->{status.add(text);return Unit.INSTANCE;},new AtomicBoolean(),new AtomicBoolean());}
        finally{runtime=false;}
        ok(service.roots.isEmpty()&&!service.frameworkFailed,"physical pointer ownership failed");return service;
    }
    static void solitary(int cpu)throws Exception{
        List<String> status=new ArrayList<>();TouchService service=play(chart(Arrays.asList(tap(1,.2),tap(2,.5),tap(3,.8))),cpu,status);
        ok(service.downs.size()==3&&service.ups.size()==3,"CPU cost lost a fresh TAP");
        Map<Long,Long> released=new HashMap<>();for(long[] up:service.ups)released.put(up[0],up[1]);
        long maximum=0;
        for(int i=0;i<3;i++){
            long[] down=service.downs.get(i);long duration=released.get(down[0])-down[1];
            ok(duration>=40&&duration<=50,"independent blue was compressed or delayed another event: "+duration);
            maximum=Math.max(maximum,down[1]-(100200+i*300));
        }
        String line=status.get(status.size()-1);java.util.regex.Matcher match=java.util.regex.Pattern.compile("最大按下调度延迟 (\\d+) ms").matcher(line);
        ok(match.find(),"dispatch statistics missing");long reported=Long.parseLong(match.group(1));
        ok(reported>=maximum-4&&reported<=maximum,"statistics omitted coordinate computation cost: "+reported+" vs "+maximum);
    }
    static void upcomingHead()throws Exception{
        TouchService service=play(chart(Arrays.asList(tap(1,.2),tap(2,.225))),0,new ArrayList<>());
        ok(service.downs.size()==2,"nearby blue head lost");ok(service.downs.get(1)[1]-100225<=12,"isolated-tap extension blocked the next blue");
    }
    static void noiseArrival()throws Exception{
        Chart chart=chart(Collections.singletonList(tap(1,.2)));
        JSONObject lower=new JSONObject().put("x",-1).put("y",-1),upper=new JSONObject().put("x",2).put("y",2);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(new JSONObject()
            .put("bottomLeftPercentage",lower).put("topRightPercentage",upper).put("appearTime",.23).put("enableTime",.23).put("disableTime",.8).put("disappearTime",.8))));BlockAreaSet.bind(chart);
        TouchService service=play(chart,0,new ArrayList<>());
        // Full-screen noise begins before a preserved 48 ms TAP would end.
        // The ordinary noise-aware path must retain the shorter release.
        for(long[] up:service.ups)ok(up[1]<100230,"fresh TAP was extended into noise");
    }
    public static void main(String[] args)throws Exception{
        for(int cpu:new int[]{0,2,6,12,24})solitary(cpu);
        upcomingHead();noiseArrival();
        System.out.println("PASS "+checks+" dispatch latency checks: real CPU wall-clock cost, preserved isolated TAP duration, actual DOWN statistics, nearby fresh heads and noise activation boundaries.");
    }
}
