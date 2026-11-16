package io.github.phiscript.input;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
public final class PlanningCancellationRegression {
 static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final Viewport V=new Viewport(0,0,1000,600);static final TouchBounds B=new TouchBounds(0,0,999,599);static final GestureProfile P=new GestureProfile(true,70,.12f,true,true,true);
 static Chart chart(AtomicBoolean flip){Note note=new Note(1,4,.2,.2,.5,1,0,true,0);return Chart.Companion.fromNative$app_debug(Collections.singletonList(note),0,(n,t,v)->new Point(500,100),(n,t,v)->{if(flip!=null)flip.set(true);return new Point(0,1);});}
 public static void main(String[] args)throws Exception{
  AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new android.content.Context());AtomicBoolean cancelled=new AtomicBoolean(true);TapMod.preparationStop.set(cancelled);boolean stopped=false;try{ContactPlanner.build(chart(null),V,B,P);}catch(CancellationException expected){stopped=true;}finally{TapMod.preparationStop.remove();}ok(stopped,"already cancelled planner ran candidates");ok(TapMod.planning.get()==null,"cancelled planner leaked planning state");
  AtomicBoolean during=new AtomicBoolean();Chart c=chart(during);stopped=false;try{GesturePlan.prepareForImport(c,P,V,B,during);}catch(CancellationException expected){stopped=true;}ok(stopped&&during.get(),"cancellation inside red preparation became an unavailable note");ok(TapMod.preparationStop.get()==null&&TapMod.planning.get()==null,"preparation cancellation leaked its stop token");ok(GesturePlan.cachedPrepared(c,P,V,B)==null,"cancelled candidate search published partial coordinates");
  Chart segmentChart=chart(null);JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",.8).put("y",.7)).put("topRightPercentage",new JSONObject().put("x",.9).put("y",.8)).put("appearTime",-1).put("enableTime",-1).put("disableTime",2000).put("disappearTime",2000);
  JSONArray areas=new JSONArray();for(int i=0;i<512;i++)areas.put(new JSONObject(area.toString()));BlockAreaSet.capture(new JSONObject().put("blockAreaList",areas));BlockAreaSet.bind(segmentChart);
  Point safe=new Point(10,100);TapMod.segment(segmentChart,V,safe,safe,0,1);
  AtomicBoolean stop=new AtomicBoolean();CountDownLatch begun=new CountDownLatch(1);Throwable[] failure={null};long[] queries=new long[2];
  // A long stationary segment skips the unrelated analytic cut phase. Its safe
  // sample loop still runs the real geometry; many areas make that observable.
  Thread worker=new Thread(()->{TapMod.preparationStop.set(stop);TapMod.planningQueryCounts.set(queries);begun.countDown();try{TapMod.segment(segmentChart,V,safe,safe,0,1000000);}catch(Throwable error){failure[0]=error;}finally{TapMod.preparationStop.remove();TapMod.planningQueryCounts.remove();}},"planning-cancellation-geometry");worker.setDaemon(true);worker.start();
  boolean observed=false;StackTraceElement[] lastStack=new StackTraceElement[0];long signalled;
  try{
   ok(begun.await(2,TimeUnit.SECONDS),"partial segment worker did not start");
   long waitUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
   while(worker.isAlive()&&System.nanoTime()<waitUntil){
    lastStack=worker.getStackTrace();boolean inClearAt=false,inGeometry=false;
    for(StackTraceElement frame:lastStack){
     if(frame.getClassName().equals(TapMod.class.getName())&&frame.getMethodName().equals("clearAt"))inClearAt=true;
     if(frame.getClassName().equals(BlockAreaSet.class.getName())||frame.getClassName().startsWith(BlockAreaSet.class.getName()+"$"))inGeometry=true;
    }
    if(inClearAt&&inGeometry){observed=true;break;}Thread.sleep(1);
   }
   ok(observed,"partial segment never entered BlockAreaSet from clearAt before cancellation: "+Arrays.toString(lastStack));
  }finally{signalled=System.nanoTime();stop.set(true);worker.join(1000);}
  ok(!worker.isAlive(),"partial segment did not yield within one second");ok(failure[0] instanceof CancellationException,"partial segment cancellation was swallowed or treated as blocked");ok(queries[1]==1&&queries[0]>0,"partial segment fixture never reached actual geometry");System.out.println("PASS "+checks+" planning cancellation checks; observedGeometry="+observed+" partial clearAt="+queries[0]+" responseMs="+(System.nanoTime()-signalled)/1000000);
 }
}
