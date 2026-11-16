package io.github.phiscript.input;
import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.SharedChartImport;
import io.github.phiscript.engine.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class PlannerPerformanceProbe {
 public static void main(String[] args)throws Exception{
  AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=AppSettings.edges=AppSettings.inward=true;AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tap_noise_clock_guard_ms",args[1]);TapMod.init(new android.content.Context());
  Chart chart=SharedChartImport.parseJson(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));Viewport v=new Viewport(0,0,1000,600);TouchBounds b=TouchSafety.read(new android.content.Context(),1000,600,v).bounds;TapMod.preparationSalt(chart,1234567);AtomicBoolean stop=new AtomicBoolean();TapMod.preparationStop.set(stop);long[] queries=new long[2];TapMod.planningQueryCounts.set(queries);long limit=Long.parseLong(args[2]);Thread timer=new Thread(()->{try{Thread.sleep(limit);}catch(InterruptedException ignored){}stop.set(true);});timer.setDaemon(true);timer.start();long begin=System.nanoTime();String outcome="finished";int events=-1,miss=-1;
  try{GesturePlan plan=ContactPlanner.build(chart,v,b,GestureProfile.read(new android.content.Context()));events=plan.events.size();miss=plan.unavailableNotes;}catch(java.util.concurrent.CancellationException cancelled){outcome="cancelled";}
  finally{TapMod.preparationStop.remove();TapMod.planningQueryCounts.remove();timer.interrupt();}
  long[] stats={};try{java.lang.reflect.Method method=ContactPlanner.class.getDeclaredMethod("lastStats");method.setAccessible(true);stats=(long[])method.invoke(null);}catch(NoSuchMethodException old){}
  System.out.println("PLANNER guard="+args[1]+" outcome="+outcome+" elapsedMs="+(System.nanoTime()-begin)/1000000+" contacts="+events+" unavailable="+miss+" clearAt="+queries[0]+" segment="+queries[1]+" stats="+Arrays.toString(stats));
 }
}
