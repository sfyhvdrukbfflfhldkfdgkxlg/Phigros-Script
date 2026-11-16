package io.github.phiscript.input;

import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import org.json.*;

/** A stationary path formerly entered the entire sample loop before its caller
 * could observe timeout. Verifies cooperative yield without claiming that a
 * single geometry primitive can be preempted. */
public final class NoiseDeadlineRegression {
    private static int checks;private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private interface Work{void run();}
    private static void expired(Work work,String why){boolean failed=false;try{work.run();}catch(TapMod.NoSafePoint expected){failed=expected.getMessage().contains("超时");}ok(failed,why);}
    private static JSONObject point(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    public static void main(String[] args)throws Exception{
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tap_noise_clock_guard_ms","24");TapMod.init(new android.content.Context());
        Note note=new Note(1,3,0,1000,0,1,0,true,0);Chart chart=Chart.Companion.fromNative$app_debug(Collections.singletonList(note),0,(n,t,v)->new Point(700,300),(n,t,v)->new Point(0,1));
        JSONArray areas=new JSONArray();for(int i=0;i<8;i++)areas.put(new JSONObject().put("bottomLeftPercentage",point(.01+i*.01,.1)).put("topRightPercentage",point(.015+i*.01,.2)).put("appearTime",0).put("enableTime",0).put("disableTime",1000).put("disappearTime",1000));
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",areas));BlockAreaSet.bind(chart);Viewport v=new Viewport(0,0,1000,600);Point safe=new Point(700,300);
        expired(()->TapMod.segment(chart,v,safe,safe,0,60000,System.nanoTime()-1),"expired segment deadline was ignored");
        expired(()->TapMod.clearAt(chart,v,safe,.5,System.nanoTime()-1),"expired point deadline was ignored");
        TapMod.segment(chart,v,safe,safe,500,512);ok(TapMod.clearAt(chart,v,safe,.5),"original unlimited API changed safe geometry");
        for(int i=0;i<3;i++){
            long start=System.nanoTime(),deadline=start+5000000L;boolean yielded=false;
            try{TapMod.segment(chart,v,safe,safe,0,60000,deadline);}catch(TapMod.NoSafePoint expected){yielded=expected.getMessage().contains("超时");}
            ok(yielded,"future deadline did not stop inside long stationary segment");ok(System.nanoTime()>=deadline,"segment timed out before requested deadline");
        }
        AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.reload();
        System.out.println("PASS "+checks+" cooperative noise deadline checks: expired point/path, future-deadline stationary-loop yield and preserved unlimited entry; individual geometry primitive remains non-preemptible.");
    }
}
