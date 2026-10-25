package io.github.phiscript.capture;
import io.github.phiscript.engine.*;import io.github.phiscript.vision.*;import java.util.*;import org.json.*;

public final class PhigrosClockRegression {
 static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final Viewport PHYSICAL=new Viewport(240,0,1920,1080);
 static final int W=960,H=432;
 static Chart chart()throws Exception {
  JSONObject line=new JSONObject().put("bpm",120);JSONArray notes=new JSONArray();
  for(int i=0;i<8;i++)notes.put(new JSONObject().put("type",new int[]{1,2,4,1,4,2,1,2}[i]).put("time",64+i*28).put("holdTime",0).put("speed",1).put("positionX",(i%3-1)*2).put("floorPosition",1+i*28/64.0));
  line.put("notesAbove",notes).put("notesBelow",new JSONArray()).put("judgeLineMoveEvents",new JSONArray().put(new JSONObject().put("startTime",0).put("endTime",100000).put("start",.5).put("end",.5).put("start2",.5).put("end2",.5)))
   .put("judgeLineRotateEvents",new JSONArray()).put("judgeLineDisappearEvents",new JSONArray()).put("speedEvents",new JSONArray().put(new JSONObject().put("startTime",0).put("endTime",100000).put("value",1).put("floorPosition",0)));
  return Chart.Companion.parse(new JSONObject().put("formatVersion",3).put("offset",.175).put("judgeLineList",new JSONArray().put(line)).toString());
 }
 static VisualScene scene(double time){
  List<VisualNoteBlob> notes=new ArrayList<>();for(int i=0;i<8;i++){double hit=1+i*28/64.0,x=96+768*.5+768*.05625*((i%3-1)*2),y=H*.5-H*.6*(hit-time);
   if(y>=0&&y<H)notes.add(new VisualNoteBlob(new int[]{1,2,4,1,4,2,1,2}[i],x,y,0,0,0,1));}
  return new VisualScene(W,H,Arrays.asList(new VisualJudgeLine(W*.5,H*.5,1,0,768,2)),notes,true);
 }
 public static void main(String[] args)throws Exception {
  Chart chart=chart();
  for(long delay:new long[]{0,700,2150,4300}){long zero=50000+delay,locked=-1;PhigrosChartClock clock=new PhigrosChartClock(chart,50000,2400,1080,PHYSICAL);
   for(int i=0;i<20&&locked<0;i++){long t=zero+220+i*24;locked=clock.observe(scene((t-zero)/1000.0),t,t);}
   ok(locked>=0&&Math.abs(locked-zero)<=12,"note clock followed HUD/offset instead of moving notes: "+delay+" -> "+locked);
  }
  PhigrosChartClock noisy=new PhigrosChartClock(chart,50000,2400,1080,PHYSICAL);long synchronizedAt=-1;
  for(int i=0;i<20&&synchronizedAt<0;i++){long at=52220+i*24;VisualScene clean=scene((at-52000)/1000.0);List<VisualNoteBlob> objects=new ArrayList<>(clean.getNotes());for(int j=0;j<12;j++)objects.add(new VisualNoteBlob(4,160+j*40,300+j,0,0,0,1));synchronizedAt=noisy.observe(new VisualScene(W,H,clean.getLines(),objects,true),at,at);}
  ok(synchronizedAt>=0&&Math.abs(synchronizedAt-52000)<12,"static background blobs prevented note alignment");
  PhigrosChartClock clock=new PhigrosChartClock(chart,50000,2400,1080,PHYSICAL);VisualScene still=scene(.3);for(long t=50300;t<50700;t+=20)ok(clock.observe(still,t,t)<0,"static screenshot established a music clock");
  clock=new PhigrosChartClock(chart,50000,2400,1080,PHYSICAL);for(int i=0;i<20;i++)ok(clock.observe(scene(.3),50300,50300)<0,"duplicate image voted");
  ok(clock.observe(scene(.5),50500,50800)<0,"stale screenshot voted");
  ok(new PhigrosChartClock(chart,50000,2400,1080,PHYSICAL).observe(new VisualScene(W,H,Collections.emptyList(),Collections.emptyList(),false),50300,50300)<0,"HUD-only image started input");
  PhigrosChartClock tracked=new PhigrosChartClock(chart,50000,2400,1080,PHYSICAL);long lock=-1;
  for(int i=0;i<24&&lock<0;i++){long t=52200+i*24;lock=tracked.observe(scene((t-52000)/1000.0),t,t+15);}
  ok(lock>=0&&Math.abs(lock-52000)<12,"multi-frame startup failed with capture delivery jitter");
  long changed=-1;for(long t=52900;t<=54500;t+=40){long update=tracked.observe(scene((t-52024)/1000.0),t,t+(t%3)*12);if(update>=0)changed=update;}
  ok(changed>=0&&Math.abs(changed-52024)<14,"locked clock did not follow small phase drift: "+changed);
  VisualScene frozen=scene(2.5);tracked.observe(frozen,54700,54700);for(long t=54740;t<=55200;t+=40)ok(tracked.observe(frozen,t,t)<0,"cached lock was emitted for a frozen scene");
  PhigrosChartClock insufficient=new PhigrosChartClock(chart,50000,2400,1080,PHYSICAL);
  for(int i=0;i<3;i++){long t=52200+i*24;ok(insufficient.observe(scene((t-52000)/1000.0),t,t)<0,"three fresh frames alone bypassed joint-fit consensus");}
  System.out.println("PASS "+checks+" actual PGR visual-projection clock checks: variable HUD-to-music delays, physical pillarbox scaling, chart offset not double-applied, static/duplicate/stale/unreliable rejection, cached multi-frame likelihoods, ongoing phase drift and frozen-after-lock rejection. Synthetic moving scenes; no real audio/device timing test.");
 }
}
