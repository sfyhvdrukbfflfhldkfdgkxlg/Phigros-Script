package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.SharedChartImport;
import io.github.phiscript.engine.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Optional bounded real-chart geometry workload for source/final cache profiling. */
public final class GeometryCacheProfile {
    public static void main(String[] args)throws Exception{
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tap_noise_clock_guard_ms",args.length>1?args[1]:"24");TapMod.init(new Context());
        Chart c=SharedChartImport.parseJson(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));Viewport v=new Viewport(0,0,1000,600);
        Point[] points={new Point(100,100),new Point(500,300),new Point(900,500),new Point(500,100)};long start=System.nanoTime();int queries=0,safe=0;
        for(int i=0;i<1600;i++){
            double t=i*.093;for(Point p:points){if(TapMod.clearAt(c,v,p,t))safe++;queries++;}
        }
        System.out.println("PROFILE queries="+queries+" safe="+safe+" ms="+(System.nanoTime()-start)/1000000);
        for(String name:new String[]{"gCalls","gRecent","gScanned","gMiss","gProbes"})try{Field f=BlockAreaSet.class.getDeclaredField(name);f.setAccessible(true);System.out.println(name+"="+f.getLong(null));}catch(NoSuchFieldException absent){}
    }
}
