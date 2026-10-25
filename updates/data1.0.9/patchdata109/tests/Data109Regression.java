package io.github.phiscript.input;

import io.github.phiscript.*;import io.github.phiscript.engine.*;import io.github.phiscript.root.*;
import android.os.SystemClock;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import kotlin.Unit;

public final class Data109Regression {
 static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final Viewport V=new Viewport(0,0,1000,600);
 static final GestureProfile P=new GestureProfile(true,70,.12f,true,true,true);
 static List<Note> rain(){List<Note> n=new ArrayList<>();for(int row=0;row<50;row++)for(int lane=0;lane<32;lane++)n.add(new Note(row*32+lane,2,1+row*.03,1+row*.03,-.7+1.4*lane/31,1,0,true,0));return n;}
 static Chart chart(List<Note> n){return Chart.Companion.fromNative$app_debug(n,0,(note,t,v)->new Point(v.getLeft()+v.getWidth()*.5f+(float)note.getPositionX()*v.getHeight(),v.getTop()+v.getHeight()*.5f),(note,t,v)->new Point(0,-1));}
 static void planning(){
  List<Note> notes=rain();Chart c=chart(notes);GesturePlan p=GesturePlan.obtain(c,P);
  ok(p.events.size()==8&&p.denseDragSections==1&&p.denseDragNotes==1600,"rain was not assigned exactly eight sustained fingers");
  ChordPlanner.checkOverlap(p.events,10);
  for(Note note:notes){long t=Math.round(note.getTimeSeconds()*1000);Point target=c.position(note,t/1000.0,V,false);boolean caught=false;
   for(TouchEvent e:p.events){ok(e.getStartMs()<t&&e.getEndMs()>t,"leased contact did not span yellow hit");Point a=p.point(c,e,t,V,new int[]{1000,600},new boolean[1]);if(Math.abs(a.getX()-target.getX())<V.getHeight()*.12)caught=true;}
   ok(caught,"yellow is outside every conservative coverage strip");
  }
  List<Note> mixed=rain();mixed.add(new Note(9000,3,.05,3,-.4,1,0,true,0));for(int i=0;i<10;i++)mixed.add(new Note(9100+i,1,1+i*.1,1+i*.1,.4,1,0,true,0));
  GesturePlan m=GesturePlan.obtain(chart(mixed),P);ok(m.denseDragNotes==1600&&m.events.size()==19,"eight yellow fingers consumed blue/hold heads");ChordPlanner.checkOverlap(m.events,10);
  List<Note> crowded=rain();for(int i=0;i<3;i++)crowded.add(new Note(9000+i,3,.05,3,-.4+i*.3,1,0,true,0));
  GesturePlan q=GesturePlan.obtain(chart(crowded),P);ok(q.denseDragSections==0,"eight-finger lease displaced three ongoing holds");ok(q.events.size()-q.connectedGroups+q.connectedNotes+q.coveredDrags==1603,"capacity fallback discarded notes");
  List<Note> single=Arrays.asList(new Note(1,2,1,1,0,1,0,true,0));
  for(GestureProfile profile:Arrays.asList(P,new GestureProfile(false,70,.12f))){GesturePlan s=GesturePlan.obtain(chart(single),profile);TouchEvent e=s.events.get(0);ok(s.isContinuous(e)&&e.getStartMs()<1000&&e.getEndMs()>1000,"yellow became a tap");}
  // Ordinary separated chords remain independent outside the dense policy.
  for(int count:new int[]{2,3,4}){List<Note> n=new ArrayList<>();for(int i=0;i<count;i++)n.add(new Note(i,2,1,1,-.4+i*.25,1,0,true,0));ok(GesturePlan.obtain(chart(n),P).events.size()==count,"ordinary chord collapsed");}
 }
 static void play()throws Exception{
  AppSettings.smart=AppSettings.catchDrags=AppSettings.combos=AppSettings.flicks=true;
  try{for(boolean root:new boolean[]{true,false}){
   List<Note> notes=rain();notes.add(new Note(9000,3,.05,3,-.4,1,0,true,0));for(int i=0;i<10;i++)notes.add(new Note(9100+i,1,1+i*.1,1+i*.1,.4,1,0,true,0));
   InputBackend.rootEnabled=root;SystemClock.now=100000;RootClient.after=null;RootClient.failAt=-1;TouchService service=new TouchService();
   ChartMultiTouchPlayer.play(service,chart(notes),100000,V,new AtomicBoolean(),s->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
   List<long[]> heads=root?RootClient.last.heads:service.downs;ok(heads.size()==19,"rain re-created pointers or lost other heads");ok((root?RootClient.last.peak:service.peak)==10,"did not execute real eight yellow plus hold plus blue multitouch");
   ok(root?RootClient.last.active.isEmpty():service.roots.isEmpty(),"pointer leak after rain");
   if(root){int sustained=0;for(long[] head:heads)if(RootClient.last.releases.get(head[0])-head[1]>1700)sustained++;ok(sustained==9,"yellow bank or original hold was interrupted");}
  }}finally{AppSettings.smart=AppSettings.catchDrags=AppSettings.combos=AppSettings.flicks=false;InputBackend.rootEnabled=false;}
 }
 static void refine(){
  ChartPlaybackClock clock=new ChartPlaybackClock(100000);ok(!clock.refine(100010,100100),"refined before initial lock");clock.calibrate(100000);clock.snapshot(100500);
  ok(clock.refine(100060,101000),"small stable drift was ignored");ChartPlaybackClock.Slice old=clock.snapshot(101000);ok(old.chartAt(101000)==1000,"refinement jumped the cursor");
  ok(!clock.refine(100061,101200)&&!clock.refine(101000,102000),"rate limit or beat-jump guard failed");
  long previous=1000;for(long wall=101001;wall<=103000;wall++){long t=old.chartAt(wall);ok(t>=previous&&t-previous<=2,"refinement rewound/skipped time");previous=t;}
  ok(old.chartAt(103000)==2976,"refinement exceeded 24 ms step");ok(clock.refine(100060,103000),"later drift correction refused");
  ChartPlaybackClock.Slice newer=clock.snapshot(103000);ok(newer.chartAt(103000)==old.chartAt(103000),"new slice discontinuity");ok(old.chartAt(104000)==3976,"submitted accessibility batch mutated");
 }
 public static void main(String[] args)throws Exception{planning();play();refine();System.out.println("PASS "+checks+" data109 checks: 1600-yellow eight-contact coverage, real Root/AOSP 10-pointer event models with hold and blue heads, capacity fallback, yellow sustained contact, bounded ongoing clock corrections. No device run.");}
}
