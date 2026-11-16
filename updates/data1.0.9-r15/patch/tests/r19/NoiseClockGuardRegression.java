package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import org.json.*;

/** Configurable uncertainty checks without changing nominal chart geometry. */
public final class NoiseClockGuardRegression {
    private static int checks;private static final Viewport VIEW=new Viewport(0,0,1000,600);private static final Point CENTER=new Point(500,300);
    private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private static JSONObject point(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    private static JSONObject rect(double left,double right,double on,double off)throws Exception{return new JSONObject().put("bottomLeftPercentage",point(left,.4)).put("topRightPercentage",point(right,.6)).put("appearTime",on).put("enableTime",on).put("disableTime",off).put("disappearTime",off);}
    private static Chart chart(JSONObject... areas)throws Exception{
        Note n=new Note(1,3,.1,2,0,1,0,true,0);Chart c=Chart.Companion.fromNative$app_debug(Collections.singletonList(n),0,(note,t,v)->CENTER,(note,t,v)->new Point(0,1));
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray(Arrays.asList(areas))));BlockAreaSet.bind(c);return c;
    }
    private static void configure(String value){AppSettings.values.put("tap_noise_clock_guard_ms",value);TapMod.reload();}
    public static void main(String[] args)throws Exception{
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.remove("tap_noise_clock_guard_ms");TapMod.init(new Context());
        ok(TapMod.noiseLookBackMs()==4&&TapMod.noiseLookAheadMs()==12,"default changed R18 guard");
        Chart late=chart(rect(.4,.6,.520,.521)),early=chart(rect(.4,.6,.479,.480));
        ok(TapMod.clearAt(late,VIEW,CENTER,.5)&&TapMod.clearAt(early,VIEW,CENTER,.5),"standard profile overreached its original window");
        configure("16");ok(TapMod.noiseLookBackMs()==16&&TapMod.noiseLookAheadMs()==16,"16ms profile getters");
        ok(TapMod.clearAt(late,VIEW,CENTER,.5)&&TapMod.clearAt(early,VIEW,CENTER,.5),"16ms profile included distant activation");
        configure("24");ok(TapMod.noiseLookBackMs()==24&&TapMod.noiseLookAheadMs()==24,"24ms profile getters");
        ok(!TapMod.clearAt(late,VIEW,CENTER,.5)&&!TapMod.clearAt(early,VIEW,CENTER,.5),"24ms uncertainty missed early/late flash");
        for(Chart c:new Chart[]{late,early}){boolean blocked=false;try{TapMod.segment(c,VIEW,CENTER,CENTER,500,501);}catch(TapMod.NoSafePoint expected){blocked=true;}ok(blocked,"segment broadphase kept the old fixed guard");}
        Chart shortFlash=chart(rect(.4,.6,.51825,.51875));ok(!TapMod.clearAt(shortFlash,VIEW,CENTER,.5),"sub-millisecond activation between 4ms probes missed");
        Chart narrow=chart(rect(.4,.6,.1,.494),rect(.4,.6,.514,2));configure("0");ok(TapMod.clearAt(narrow,VIEW,CENTER,.5),"standard narrow safe interval was removed");
        configure("16");ok(!TapMod.clearAt(narrow,VIEW,CENTER,.5),"conservative profile did not expose expected short-window tradeoff");
        JSONObject area=rect(.4,.6,0,2);Chart cancelled=chart(area,new JSONObject(area.toString()).put("isSubtract",true));
        for(String profile:new String[]{"0","16","24"}){configure(profile);ok(TapMod.clearAt(cancelled,VIEW,CENTER,.5),"clock protection changed XOR geometry");TapMod.segment(cancelled,VIEW,new Point(300,300),new Point(700,300),500,516);checks++;}
        for(String invalid:new String[]{"","-24","8","48","NaN","garbage"}){configure(invalid);ok(TapMod.noiseLookBackMs()==4&&TapMod.noiseLookAheadMs()==12,"invalid profile failed compatible fallback: "+invalid);}
        for(String profile:new String[]{"0","16","24"}){
            configure(profile);Chart split=chart(rect(.4,.6,0,2));Note n=split.getNotes().get(0);Point target=TapMod.withinBounds(split,n,.5,VIEW,CENTER,new TouchBounds(0,0,999,599));
            ok(TapMod.clearAt(split,VIEW,target,.5),"candidate search did not use selected guard profile");
        }
        // Public existing budget regression uses a costly real geometry lookup;
        // all profiles retain the same bounded runtime search and release path.
        for(String profile:new String[]{"0","16","24"}){configure(profile);NoiseSearchBudgetRegression.main(new String[0]);checks++;}
        configure("0");
        System.out.println("PASS "+checks+" clock-uncertainty profile checks: compatible default, 16/24ms getters, narrow activation/safe windows, XOR composition, candidate/segment wiring, invalid preferences and unchanged runtime search budget.");
    }
}
