package io.github.phiscript.input;
import android.os.SystemClock;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import org.json.*;

/** A costly judge-normal lookup consumes real dispatch wall time. Offline
 * planning may finish; runtime must yield to per-contact UP handling. */
public final class NoiseSearchBudgetRegression {
    static int checks;
    static void ok(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");
        TapMod.init(new android.content.Context());SystemClock.now=100000;
        Note note=new Note(1,3,.1,2,0,1,0,true,0);
        Chart chart=Chart.Companion.fromNative$app_debug(Collections.singletonList(note),0,
            (n,t,v)->new Point(500,300),(n,t,v)->{try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}SystemClock.now+=20;return new Point(0,1);});
        JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",.4).put("y",.4))
            .put("topRightPercentage",new JSONObject().put("x",.6).put("y",.6))
            .put("appearTime",0).put("enableTime",0).put("disableTime",3).put("disappearTime",3);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(chart);
        Viewport view=new Viewport(0,0,1000,600);TouchBounds bounds=new TouchBounds(0,0,999,599);
        TapMod.begin(chart);TapMod.planning.set(true);Point offline;
        try{offline=TapMod.withinBounds(chart,note,.5,view,new Point(500,300),bounds);}
        finally{TapMod.planning.remove();}
        ok(TapMod.clearAt(chart,view,offline,.5),"offline search lost its safe point");
        ok(TapMod.searchTimeouts()==0,"offline planning was constrained by runtime budget");
        boolean yielded=false;
        try{TapMod.runtimeWithinBounds(chart,note,.5,view,new Point(500,300),bounds,null,0);}
        catch(TapMod.NoSafePoint expected){yielded=expected.getMessage().contains("超时");}
        ok(yielded&&TapMod.searchTimeouts()==1,"expensive runtime search held the dispatch loop");
        yielded=false;
        try{TapMod.runtimeWithinBounds(chart,note,.5,view,new Point(100,100),bounds,null,0);}
        catch(TapMod.NoSafePoint expected){yielded=expected.getMessage().contains("超时");}
        ok(yielded&&TapMod.searchTimeouts()==2,"active-noise centering bypassed budget for a nominally clear point");
        Point safe=TapMod.runtimeWithinBounds(chart,note,4,view,new Point(100,100),bounds,null,0);
        ok(safe.getX()==100&&safe.getY()==100,"inactive-noise safe contact was altered");
        ok(TapMod.searchTimeouts()==2,"inactive-noise fast path consumed search budget");
        System.out.println("PASS "+checks+" runtime-noise search budget checks: costly blocked/clear active-noise searches yield for release, unrestricted preflight and inactive-noise fast path.");
    }
}
