package io.github.phiscript.input;
import io.github.phiscript.engine.*;import java.util.*;
public final class GesturePlanRegression {
 static int checks;static final Viewport VIEW=new Viewport(0,0,1000,600);static final GestureProfile SMART=new GestureProfile(true,70,.12f);
 static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Note n(int id,int type,double at,double end,float x,boolean above,int line){return new Note(id,type,at,end,x,1,0,above,line);}
 static Chart chart(List<Note> notes){return Chart.Companion.fromNative$app_debug(notes,0,(n,t,v)->{if(t<0)throw new AssertionError("negative chart sample");return new Point((float)(v.getLeft()+v.getWidth()*.5f+n.getPositionX()*v.getHeight()+t*15),v.getTop()+v.getHeight()*(n.getLineIndex()==0?.5f:.7f));},(n,t,v)->new Point(0,-1));}
 static void near(Point a,Point b,String s){ok(Math.abs(a.getX()-b.getX())<.02&&Math.abs(a.getY()-b.getY())<.02,s+" "+a+" / "+b);}
 public static void main(String[] args){
  List<Note> run=new ArrayList<>();for(int i=0;i<15;i++)run.add(n(i,2,.1+i*.05,.1+i*.05,-.3f+i*.025f,true,0));
  Chart c=chart(run);GesturePlan p=GesturePlan.obtain(c,SMART);ok(p.connectedGroups==1&&p.connectedNotes==15&&p.events.size()==1,"drag run not assigned one independent contact");TouchEvent contact=p.events.get(0);
  for(Note note:run){long at=Math.round(note.getTimeSeconds()*1000);near(p.point(c,contact,at,VIEW,new int[]{1000,600},new boolean[1]),c.position(note,at/1000.0,VIEW,false),"drag hit anchor missed");}
  long mid=125;Point a=c.position(run.get(0),mid/1000.0,VIEW,false),b=c.position(run.get(1),mid/1000.0,VIEW,false);near(p.point(c,contact,mid,VIEW,new int[]{1000,600},new boolean[1]),new Point((a.getX()+b.getX())/2,(a.getY()+b.getY())/2),"connected path not interpolated");
  near(p.point(c,contact,-20,VIEW,new int[]{1000,600},new boolean[1]),c.position(run.get(0),0,VIEW,false),"negative lead-in not clamped");
  ok(GesturePlan.obtain(c,new GestureProfile(true,70,.12f))==p,"pre-clock plan not reused");
  GesturePlan legacy=GesturePlan.obtain(c,new GestureProfile(false,70,.12f));ok(legacy.connectedGroups==0&&legacy.events.size()==15,"disabled strategy changed legacy input");
  List<Note> mixed=new ArrayList<>(run);for(int i=0;i<8;i++)mixed.add(n(100+i,1,.15+i*.06,.15+i*.06,.4f,true,0));mixed.add(n(200,3,.05,.9f,.45f,true,0));mixed.add(n(201,4,.6,.6,-.45f,true,0));
  GesturePlan m=GesturePlan.obtain(chart(mixed),SMART);int taps=0,holds=0,flicks=0;for(TouchEvent event:m.events){if(event.getNote().getType()==1)taps++;if(event.getNote().getType()==3)holds++;if(event.getNote().getType()==4)flicks++;}ok(taps==8&&holds==1&&flicks==1,"fresh tap/hold/flick heads were merged");
  List<Note> lanes=new ArrayList<>();for(int i=0;i<10;i++){lanes.add(n(i*2,2,.1+i*.05,.1+i*.05,-.3f+i*.01f,true,0));lanes.add(n(i*2+1,2,.1+i*.05,.1+i*.05,.3f-i*.01f,true,0));}
  GesturePlan two=GesturePlan.obtain(chart(lanes),SMART);ok(two.connectedGroups==2&&two.events.size()==2,"simultaneous nearby runs mixed into one finger");
  for(List<Note> notes:Arrays.asList(Arrays.asList(n(1,2,.1,.1,0,true,0),n(2,2,.15,.15,0,true,1)),Arrays.asList(n(1,2,.1,.1,0,true,0),n(2,2,.15,.15,0,false,0)),Arrays.asList(n(1,2,.1,.1,-.4f,true,0),n(2,2,.15,.15,.4f,true,0)),Arrays.asList(n(1,2,.1,.1,-.05f,true,0),n(2,2,.12,.12,.05f,true,0)),Arrays.asList(n(1,2,.1,.1,0,true,0),n(2,2,.7,.7,0,true,0))))ok(GesturePlan.obtain(chart(notes),SMART).events.size()==2,"unrelated/distant/too-fast notes linked");
  List<Note> bridge=new ArrayList<>();bridge.add(n(1,2,.1,.1,0,true,0));bridge.add(n(2,2,.3,.3,.01f,true,0));for(int i=0;i<10;i++)bridge.add(n(10+i,3,.18,.24,i*.04f,true,0));GesturePlan safe=GesturePlan.obtain(chart(bridge),new GestureProfile(true,300,.12f));ok(safe.events.size()==12&&safe.connectedGroups==0,"gap bridge introduced artificial 11-finger chord");ChordPlanner.checkOverlap(safe.events,10);
  List<Note> dense=new ArrayList<>();for(int i=0;i<2000;i++)dense.add(n(i,2,.1+i*.001,.1+i*.001,i*.0001f,true,0));GesturePlan d=GesturePlan.obtain(chart(dense),SMART);ok(d.events.size()==8&&d.denseDragNotes==2000,"dense yellows were not covered by eight sustained contacts");
  for(int count:new int[]{11,21}){List<Note> over=new ArrayList<>();for(int i=0;i<count;i++)over.add(n(500+i,3,.1,.8,i*.04f,true,0));Chart overChart=chart(over);
   for(GestureProfile profile:Arrays.asList(SMART,new GestureProfile(false,70,.12f),new GestureProfile(true,70,.12f,true)))ok(GesturePlan.obtain(overChart,profile).events.size()==count,"estimated contact count rejected chart or deleted notes");
   GesturePlan.preflight(overChart);ok(true,"pre-clock planning remains available");
  }
  boolean invalid=false;try{new GestureProfile(true,0,.12f);}catch(IllegalArgumentException e){invalid=true;}ok(invalid,"invalid link gap accepted");invalid=false;try{new GestureProfile(true,70,Float.NaN);}catch(IllegalArgumentException e){invalid=true;}ok(invalid,"nonfinite distance accepted");
  System.out.println("PASS "+checks+" connected-gesture checks: moving hit anchors, interpolation, negative lead-in, independent lanes, tap/hold/flick heads, legacy switch, cache, capacity-safe bridging and 2000-note run.");
 }
}
