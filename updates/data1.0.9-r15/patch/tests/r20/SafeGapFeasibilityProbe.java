package io.github.phiscript.input;

import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.SharedChartImport;
import io.github.phiscript.engine.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.Map;

/** Optional pinned AT/IN comparison; runs against either the previous or current
 * TapMod so selection changes can be separated from the dense cache change. */
public final class SafeGapFeasibilityProbe {
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
        AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=AppSettings.edges=AppSettings.inward=true;
        AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.init(new android.content.Context());
        Viewport view=new Viewport(0,0,1000,600);TouchBounds bounds=new TouchBounds(20,24,979,567);
        for(String path:args){
            Chart chart=SharedChartImport.parseJson(new String(Files.readAllBytes(Paths.get(path)),StandardCharsets.UTF_8));TapMod.begin(chart);
            Field salts=TapMod.class.getDeclaredField("salts");salts.setAccessible(true);((Map<Chart,Long>)salts.get(null)).put(chart,20261120L);
            long start=System.nanoTime();GesturePlan plan=ContactPlanner.build(chart,view,bounds,GestureProfile.read(new android.content.Context()));
            long planNanos=System.nanoTime()-start;int unavailable=0,moved=0,centered=0,lateralViolation=0;
            TapMod.planning.set(true);
            try{for(Note note:chart.getNotes()){
                double time=note.getTimeSeconds();Point raw=chart.position(note,time,view,false);if(raw==null)continue;boolean originallyClear=TapMod.clearAt(chart,view,raw,time);
                try{Point point=TapMod.withinBounds(chart,note,time,view,raw,bounds);if(!TapMod.clearAt(chart,view,point,time))throw new AssertionError("unsafe selected head");
                    if(Math.hypot(point.getX()-raw.getX(),point.getY()-raw.getY())>.1){moved++;if(originallyClear)centered++;}
                    Point normal=chart.judgeNormal(note,time,view);double m=Math.hypot(normal.getX(),normal.getY());
                    double lateral=Math.abs((point.getX()-raw.getX())*normal.getY()-(point.getY()-raw.getY())*normal.getX())/m;if(lateral>ContactPlanner.radius(view)+.05)lateralViolation++;
                }catch(TapMod.NoSafePoint expected){unavailable++;}
            }}finally{TapMod.planning.remove();}
            System.out.println("FEASIBILITY "+Paths.get(path).getFileName()+" notes="+chart.getNotes().size()+" planUnavailable="+plan.unavailableNotes+" contacts="+plan.events.size()+" headUnavailable="+unavailable+" moved="+moved+" alreadySafeCentered="+centered+" lateralViolation="+lateralViolation+" planMs="+planNanos/1000000+" totalMs="+(System.nanoTime()-start)/1000000);
            if(lateralViolation!=0)throw new AssertionError("selected head escaped legal judgement band");
        }
    }
}
