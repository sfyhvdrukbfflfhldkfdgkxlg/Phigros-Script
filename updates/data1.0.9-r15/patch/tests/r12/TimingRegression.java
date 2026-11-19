package io.github.phiscript;
import android.content.Context;
import io.github.phiscript.assets.ChartLibrary;
import io.github.phiscript.assets.ExternalCharts;
import io.github.phiscript.assets.PhiraLibrary;
import io.github.phiscript.assets.SessionChartSource;
import io.github.phiscript.capture.PhigrosRetryClock;
import io.github.phiscript.capture.PhiraRestartClock;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Note;
import io.github.phiscript.engine.Point;
import io.github.phiscript.root.ModeRouting;
import io.github.phiscript.root.PhiraChartRoots;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
public final class TimingRegression {
    static void clock(AppSettings prefs,int restart,int global,int touch){
        SessionSettings s=prefs.snapshot();
        if(s.getPhiraRestartDelayMs()!=restart||s.getPhiraGlobalOffsetMs()!=global||s.getTouchOffsetMs()!=touch)
            throw new AssertionError("Wrong game clock "+s.getPhiraRestartDelayMs()+"/"+s.getPhiraGlobalOffsetMs()+"/"+s.getTouchOffsetMs());
    }
    public static void main(String[] args)throws Exception {
        Context context=new Context();
        AppSettings p=new AppSettings(context);
        ModeRouting.select(p,0);clock(p,1700,0,0);
        if(PhigrosRetryClock.readDelay(p)!=1700)throw new AssertionError("Phigros retry clock has a different default");
        ModeRouting.select(p,1);clock(p,715,0,0);
        p.save("phiraRestartDelay","820");p.save("phiraGlobalOffset","-31");p.save("touchOffset","27");
        clock(p,820,-31,27);ModeRouting.select(p,0);clock(p,1700,0,0);
        p.save("phigrosRestartDelay","77");p.save("phigrosGlobalOffset","-42");p.save("phigrosTouchOffset","13");
        clock(p,77,-42,13);ModeRouting.select(p,3);clock(p,77,-42,13);ModeRouting.select(p,1);clock(p,820,-31,27);
        p.save("phiraRestartDelay","700");p.save("phigrosRestartDelay","0");
        clock(p,700,-31,27);ModeRouting.select(p,0);clock(p,0,-42,13);
        java.lang.reflect.Constructor<PhiraLibrary> localConstructor=PhiraLibrary.class.getDeclaredConstructor(Context.class,java.util.List.class);
        localConstructor.setAccessible(true);
        PhiraLibrary library=localConstructor.newInstance(context,Collections.emptyList());
        Chart chart=Chart.Companion.fromNative$app_debug(Collections.singletonList(new Note(1,1,.1,.1,0,1,0,true,0)),.25,
            (n,t,v)->new Point(10,10),(n,t,v)->new Point(0,-1));
        AtomicBoolean stop=new AtomicBoolean();
        SessionChartSource local=SessionChartSource.phira(context,library),online=SessionChartSource.phiraOnline(context,library,stop);
        if(local.restartChartOffset(chart)!=0||online.restartChartOffset(chart)!=0)throw new AssertionError("Phira defaults to applying chart offset");
        p.saveFlag(GameTiming.INCLUDE_PHIRA_CHART_OFFSET,true);
        if(local.restartChartOffset(chart)!=0||online.restartChartOffset(chart)!=0)throw new AssertionError("running Phira session changed timing");
        local=SessionChartSource.phira(context,library);online=SessionChartSource.phiraOnline(context,library,stop);
        if(local.restartChartOffset(chart)!=.25||online.restartChartOffset(chart)!=.25)throw new AssertionError("Phira opt-in missing in offline or online session");
        if(PhiraRestartClock.INSTANCE.epoch(10_000,715,local.restartChartOffset(chart),12,-2)!=10_975)
            throw new AssertionError("opt-in applied chart offset incorrectly");
        p.saveFlag(GameTiming.INCLUDE_PHIRA_CHART_OFFSET,false);
        JSONObject catalog=new JSONObject().put("share","timing").put("entries",new JSONArray().put(new JSONObject().put("id","1").put("name","Fixture - IN [12].json")));
        ChartLibrary source=ExternalCharts.fromCatalog(context,catalog);
        if(SessionChartSource.phigros(context,source,stop).restartChartOffset(chart)!=.25)
            throw new AssertionError("Phira setting changed Phigros clock");
        java.util.List<java.io.File> roots=PhiraChartRoots.roots(10);
        if(roots.size()!=24||roots.stream().filter(f->f.getPath().replace(java.io.File.separatorChar,'/').contains("/org.flos.phirapro/")).count()!=12)throw new AssertionError("Pro roots missing");
        System.out.println("PASS real timing settings: fresh defaults, saved values, local/online Phira offset selection, session stability and Phigros isolation.");
    }
}
