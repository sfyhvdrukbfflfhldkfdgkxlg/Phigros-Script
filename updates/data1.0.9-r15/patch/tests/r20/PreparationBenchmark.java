package io.github.phiscript.input;

import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.SharedChartImport;
import io.github.phiscript.engine.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

/** Optional pinned-chart benchmark. The frozen pre-optimization preparer is
 * compared in one warmed JVM, with exact coordinate/lifetime equivalence. */
public final class PreparationBenchmark {
    static final Viewport V=new Viewport(0,0,1000,600);
    static long positions,normals,checks;
    static Object value(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static long allocated(){try{com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();return bean.getThreadAllocatedBytes(Thread.currentThread().getId());}catch(Throwable unsupported){return 0;}}
    static long compare(GesturePlan plan,ReferencePreparation before,PreparedTrajectory after)throws Exception{
        long connections=0;for(TouchEvent event:plan.events){Object a=before.tracks.get(event),b=after.tracks.get(event);
            for(String field:new String[]{"time","broken","critical"})ok(Arrays.equals((int[])value(a,field),(int[])value(b,field)),field+" changed");
            for(String field:new String[]{"x","y"}){float[] x=(float[])value(a,field),y=(float[])value(b,field);ok(x.length==y.length,field+" length");for(int i=0;i<x.length;i++){checks++;if(Float.floatToIntBits(x[i])!=Float.floatToIntBits(y[i]))throw new AssertionError(field+" changed at "+i);}}
            byte[] connected=(byte[])value(a,"connected");ok(Arrays.equals(connected,(byte[])value(b,"connected")),"contact lifetime changed");for(byte c:connected)connections+=c;
            ok(value(a,"continuous").equals(value(b,"continuous")),"continuous flag changed");ok(value(a,"flick").equals(value(b,"flick")),"flick flag changed");
        }return connections;
    }
    static void run(String filename)throws Exception{
        JSONObject raw=new JSONObject(new String(Files.readAllBytes(Paths.get(filename)),StandardCharsets.UTF_8));
        Chart source=SharedChartImport.parseJson(raw.toString());
        Chart chart=Chart.Companion.fromNative$app_debug(source.getNotes(),source.getOffsetSeconds(),(note,t,v)->{positions++;return source.position(note,t,v,false);},(note,t,v)->{normals++;return source.judgeNormal(note,t,v);});
        BlockAreaSet.capture(raw);BlockAreaSet.bind(chart);TapMod.preparationSalt(source,1234567L);TapMod.preparationSalt(chart,1234567L);
        GestureProfile profile=GestureProfile.read(new android.content.Context());TouchBounds bounds=TouchSafety.read(new android.content.Context(),1000,600,V).bounds;
        long begin=System.nanoTime();GesturePlan plan=ContactPlanner.build(chart,V,bounds,profile);long planNs=System.nanoTime()-begin;
        ReferencePreparation.prepare(chart,plan,profile,V,bounds,null);PreparedTrajectory.prepare(chart,plan,profile,V,bounds,null);
        long oldNs=0,newNs=0,oldAlloc=0,newAlloc=0,connections=0,oldCalls=0,newCalls=0,oldNormals=0,newNormals=0;int rounds=2,samples=0;
        for(int round=0;round<rounds;round++){
            ReferencePreparation a;PreparedTrajectory b;long base,memory;
            if(round%2==0){
                positions=normals=0;memory=allocated();base=System.nanoTime();a=ReferencePreparation.prepare(chart,plan,profile,V,bounds,null);oldNs+=System.nanoTime()-base;oldAlloc+=allocated()-memory;oldCalls+=positions;oldNormals+=normals;
                positions=normals=0;memory=allocated();base=System.nanoTime();b=PreparedTrajectory.prepare(chart,plan,profile,V,bounds,null);newNs+=System.nanoTime()-base;newAlloc+=allocated()-memory;newCalls+=positions;newNormals+=normals;
            }else{
                positions=normals=0;memory=allocated();base=System.nanoTime();b=PreparedTrajectory.prepare(chart,plan,profile,V,bounds,null);newNs+=System.nanoTime()-base;newAlloc+=allocated()-memory;newCalls+=positions;newNormals+=normals;
                positions=normals=0;memory=allocated();base=System.nanoTime();a=ReferencePreparation.prepare(chart,plan,profile,V,bounds,null);oldNs+=System.nanoTime()-base;oldAlloc+=allocated()-memory;oldCalls+=positions;oldNormals+=normals;
            }
            connections=compare(plan,a,b);samples=b.sampleCount();ok(oldCalls==newCalls&&oldNormals==newNormals,"geometry call counts changed");
        }
        System.out.printf(Locale.ROOT,"PREPARE %s contacts=%d samples=%d plannerMs=%.1f beforeMs=%.1f afterMs=%.1f speedup=%.2fx beforeAllocatedMiB=%.1f afterAllocatedMiB=%.1f chartPositionCalls=%d judgeNormalCalls=%d removedExactSegmentCalls=%d equivalent=true%n",Paths.get(filename).getFileName(),plan.events.size(),samples,planNs/1e6,oldNs/1e6/rounds,newNs/1e6/rounds,oldNs/(double)newNs,oldAlloc/1048576.0/rounds,newAlloc/1048576.0/rounds,oldCalls/rounds,oldNormals/rounds,connections);
    }
    public static void main(String[] args)throws Exception{
        if(args.length==0)throw new IllegalArgumentException("Supply pinned original AT/IN JSON paths");
        AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=AppSettings.edges=AppSettings.inward=true;AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.init(new android.content.Context());
        for(String filename:args)run(filename);System.out.println("PASS "+checks+" exact prepared-coordinate and contact-lifetime comparisons");
    }
}
