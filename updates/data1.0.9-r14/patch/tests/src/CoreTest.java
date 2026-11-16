package io.github.phiscript.input;
import io.github.phiscript.engine.*;import io.github.phiscript.*;import io.github.phiscript.root.*;import org.json.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import android.os.SystemClock;
public final class CoreTest{
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static JSONObject area(double x1,double y1,double x2,double y2)throws Exception{return new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",x1).put("y",y1)).put("topRightPercentage",new JSONObject().put("x",x2).put("y",y2)).put("appearTime",0).put("enableTime",1).put("disableTime",3).put("disappearTime",4);}
 static Object region(JSONObject... b)throws Exception{Object k=new Object();BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray(Arrays.asList(b))));BlockAreaSet.bind(k);return k;}
 static boolean covers(Object k,float x,float y,double t){return BlockAreaSet.covers(k,x,y,0,0,2000,1000,t);}
 static Note n(int id,int type,double start,double end){return new Note(id,type,start,end,0,1,0,true,0);}
 static Chart chart(double offset,Note... notes){return Chart.Companion.fromNative$app_debug(Arrays.asList(notes),offset,(n,t,v)->new Point(v.getWidth()*.5f,v.getHeight()*.5f),(n,t,v)->new Point(0,1));}
 static Chart blockedChart(JSONObject b,double offset,Note...notes)throws Exception{BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(b)));return chart(offset,notes);}
 public static void main(String[]args)throws Exception{
  TapMod.init(new android.content.Context());JSONObject b=area(.4,.4,.6,.6);Object k=region(b);ok(covers(k,1000,500,2),"center rectangle");ok(!covers(k,20,20,2),"no origin ghost");ok(!covers(k,1000,500,.5),"preactive");ok(!covers(k,1000,500,3),"disabled boundary");ok(covers(k,1000,500,1),"enable boundary");
  JSONObject rotated=area(.4,.45,.6,.55).put("rotateEvents",new JSONArray().put(new JSONObject().put("time",0).put("rotation",90)));Object r=region(rotated);ok(covers(r,1000,680,2),"pixel aspect rotation");ok(!covers(r,1080,500,2),"rotated narrow x");
  JSONObject moved=area(.4,.4,.6,.6).put("moveEvents",new JSONArray().put(new JSONObject().put("time",0).put("endPosition",new JSONObject().put("x",.5).put("y",.5))).put(new JSONObject().put("time",2).put("easeTypeX",1).put("endPosition",new JSONObject().put("x",.9).put("y",.5))));Object move=region(moved);ok(covers(move,1200,500,1),"destination keyframe easing");ok(!covers(move,1550,500,1),"not linear interpolation");
  Object hole=region(area(.2,.2,.8,.8),area(.45,.45,.55,.55).put("isSubtract",true));ok(!covers(hole,1000,500,2)&&covers(hole,700,500,2),"subtractive hole");
  Chart offset=blockedChart(b,1,n(0,1,0,0));ok(covers(offset,1000,500,0),"music time offset");
  CountDownLatch a=new CountDownLatch(1),c=new CountDownLatch(1);Object ka=new Object(),kb=new Object();Thread t=new Thread(()->{try{BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area(.1,.1,.2,.2))));a.countDown();c.await();BlockAreaSet.bind(ka);}catch(Exception e){throw new RuntimeException(e);}});t.start();a.await();BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(b)));BlockAreaSet.bind(kb);c.countDown();t.join();ok(covers(ka,300,850,2)&&!covers(ka,1000,500,2)&&covers(kb,1000,500,2),"thread-local pending binding");
  try{Chart.Companion.parse("{\"blockAreaList\":["+b+"],\"formatVersion\":3}");}catch(Exception expected){}Chart clean=chart(0,n(0,1,0,0));ok(BlockAreaSet.count(clean)==0,"failed parse clears pending");
  Viewport v=new Viewport(0,0,2000,1000);Note note=n(0,3,1,3);Chart chart=blockedChart(b,0,note);TapMod.begin(chart);Point pos=chart.position(note,2,v,false);ok(!covers(chart,pos.getX(),pos.getY(),2),"final jitter avoids noise native hook");ok(chart.position(note,2,v,true).equals(new Point(1000,500)),"visual path unchanged");
  AppSettings.values.put("tap_vertical_permille","0");AppSettings.values.put("tap_jitter_permille","6");TapMod.reload();Chart ordinary=chart(0,note);TapMod.begin(ordinary);Point p=ordinary.position(note,2,v,false);ok(p.equals(ordinary.position(note,2.2,v,false)),"hold random stable");TapMod.begin(ordinary);ok(!p.equals(ordinary.position(note,2,v,false)),"new performance seed");
  double sumX=0,sumY=0;for(int i=0;i<10000;i++){TapMod.begin(ordinary);Point q=ordinary.position(note,2,v,false);double dx=q.getX()-1000,dy=q.getY()-500;ok(Math.abs(dx)<=6.001&&Math.abs(dy)<=6.001,"jitter bounded");sumX+=dx;sumY+=dy;}ok(Math.abs(sumX/10000)<.2&&Math.abs(sumY/10000)<.2,"jitter unbiased away from edges");
  for(int i=0;i<1000;i++){TapMod.begin(chart);Point q=chart.position(note,2,v,false);ok(!covers(chart,q.getX(),q.getY(),2),"all randomized destinations outside");}
  boolean stopped=false;try{TapMod.segment(chart,v,new Point(500,500),new Point(1500,500),2000,2100);}catch(IllegalStateException e){stopped=true;}ok(stopped,"continuous path crossing rejected");
  Chart full=blockedChart(area(-1,-1,2,2),0,note);stopped=false;try{full.position(note,2,v,false);}catch(IllegalStateException e){stopped=true;}ok(stopped,"no candidate fails closed");
  AppSettings.values.put("tapmod_enabled","false");TapMod.reload();InputBackend.rootEnabled=false;Regression.plannerAndPlayback();checks+=Regression.checks;
  AppSettings.values.put("tapmod_enabled","true");TapMod.reload();for(boolean root:new boolean[]{false,true}){
   SystemClock.now=100000;InputBackend.rootEnabled=root;RootClient.failAt=-1;RootClient.after=null;TouchService service=new TouchService();AtomicBoolean playing=new AtomicBoolean();Chart c1=chart(0,n(0,3,.02,.4),n(1,1,.1,.1),n(2,4,.2,.2));
   ChartMultiTouchPlayer.play(service,c1,SystemClock.now,new Viewport(0,0,1000,600),new AtomicBoolean(),s->kotlin.Unit.INSTANCE,playing,new AtomicBoolean());ok(!playing.get(),"ownership released");ok(root?RootClient.last.closed&&RootClient.last.active.isEmpty():service.roots.isEmpty(),"fingers released");
   SystemClock.now=100000;Chart blocked=blockedChart(area(-1,-1,2,2).put("enableTime",0),0,n(0,3,.02,.4));stopped=false;try{ChartMultiTouchPlayer.play(service,blocked,SystemClock.now,new Viewport(0,0,1000,600),new AtomicBoolean(),s->kotlin.Unit.INSTANCE,playing,new AtomicBoolean());}catch(IllegalStateException expected){stopped=true;}ok(!stopped&&!playing.get(),"blocked contact lifts without aborting the song");
  }
  boolean malformed=false;try{BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(new JSONObject())));}catch(IllegalArgumentException expected){malformed=true;}ok(malformed,"malformed noise is rejected");
  Chart cleanAfter=chart(0,n(0,1,0,0));ok(BlockAreaSet.count(cleanAfter)==0,"malformed metadata does not leak");
  System.out.println("PASS core/planner/playback "+checks+" assertions");
 }
}
