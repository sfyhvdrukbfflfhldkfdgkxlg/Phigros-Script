package io.github.phiscript.input;
import io.github.phiscript.*;import io.github.phiscript.engine.*;import io.github.phiscript.root.*;import android.os.SystemClock;
import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import kotlin.Unit;

public final class Data106Regression {
 static int checks;static final Viewport V=new Viewport(0,0,1000,600);static final GestureProfile P=new GestureProfile(true,70,.12f,true,true,true);
 static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Note n(int id,int type,double at,float x){return new Note(id,type,at,at,x,1,0,true,0);}
 static Chart c(Note... notes){return Data104Regression.chart(Arrays.asList(notes));}
 static GesturePlan plan(Chart c){return GesturePlan.obtain(c,P);}
 static Point p(GesturePlan plan,Chart chart,TouchEvent e,long t){return plan.point(chart,e,t,V,new int[]{1000,600},new boolean[1]);}
 static VisualTouch v(long id,VisualTouchKind kind,float x){return new VisualTouch(id,kind,x,.5f,0,-.1f);}
 static VisualTouchDemand f(VisualChordState state,long now,VisualTouch... touches){return state.update(new VisualTouchFrame(now,1000,600,Arrays.asList(touches)),now);}
 static void patterns(){
  Chart mixed=c(n(1,1,.2,0),n(2,2,.26,.02f),n(3,2,.4,.03f),n(4,4,.55,.04f),n(5,2,.8,.05f));GesturePlan plan=plan(mixed);
  ok(plan.events.size()==1,"blue-yellow-yellow-red-yellow split into repeated DOWNs");TouchEvent e=plan.events.get(0);ok(e.getStartMs()==200,"blue pressed early");
  ok(Math.abs(p(plan,mixed,e,200).getX()-500)<.01,"blue initial position changed");
  for(long t:new long[]{260,400,800})ok(Math.abs(p(plan,mixed,e,t).getY()-300)<.01,"yellow hit point missed");
  Point last=null;for(long t=200;t<840;t+=4){Point next=p(plan,mixed,e,t);if(last!=null)ok(Math.hypot(next.getX()-last.getX(),next.getY()-last.getY())<12,"mixed path teleported");last=next;}
  Chart reverse=c(n(1,4,.2,0),n(2,4,.4,0),n(3,4,.6,0),n(4,4,.8,0));plan=plan(reverse);ok(plan.events.size()==1,"red run not sustained");float previous=0;
  for(int t=200;t<=800;t+=200){Point a=p(plan,reverse,plan.events.get(0),t-10),b=p(plan,reverse,plan.events.get(0),t+10);float delta=b.getY()-a.getY();ok(Math.abs(delta)>15,"red action too small");if(previous!=0)ok(delta*previous<0,"red did not reset direction");previous=delta;}
  for(int count:new int[]{2,3,4})for(int kind:new int[]{1,2,4}){
   List<Note> notes=new ArrayList<>();for(int i=0;i<count;i++){notes.add(n(i,kind,.3,(i-(count-1)/2f)*.18f));if(kind!=1)notes.add(n(10+i,kind,.5,(i-(count-1)/2f)*.18f));}
   plan=plan(Data104Regression.chart(notes));ok(plan.events.size()==count,"parallel chord lost independent contacts: "+count+" / "+kind);long start=plan.events.get(0).getStartMs();for(TouchEvent chord:plan.events)ok(chord.getStartMs()==start,"simultaneous heads serialized");
  }
  Chart ongoing=c(n(1,2,.2,0),n(2,2,.4,0),n(3,2,.6,0),n(4,1,.4,0));plan=plan(ongoing);ok(plan.events.size()==2,"interleaved blue lacks fresh DOWN");ok(plan.connectedNotes==3,"interleaved blue stole the ongoing yellow lane");
  for(Chart separate:Arrays.asList(c(n(1,1,.2,-.4f),n(2,2,.22,.4f)),c(n(1,1,.2,-.4f),n(2,4,.22,.4f)),c(n(1,4,.2,0),n(2,1,.25,0))))ok(plan(separate).events.size()==2,"unsafe reuse or red-to-blue swallowed new head");
  Chart pair=c(n(1,1,.2,-.2f),n(2,1,.2,.2f),n(3,4,.3,-.2f),n(4,4,.3,.2f));plan=plan(pair);ok(plan.events.size()==2&&plan.events.stream().allMatch(x->x.getStartMs()==200),"two blue-to-red lanes not simultaneous point-flicks");
 }
 static void realCharts()throws Exception {
  for(int fixture=1;fixture<=3;fixture++){
   String json=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("analysis/realcharts/chart_"+fixture+".json")),"UTF-8");
   Chart chart=io.github.phiscript.assets.PhiraChartParser.INSTANCE.parse(json,0);GesturePlan plan=plan(chart);
   ok(plan.events.size()+plan.connectedNotes-plan.connectedGroups+plan.coveredDrags==chart.getNotes().size(),"original chart notes lost by mixed planning: "+fixture);
   Map<Integer,TouchEvent> heads=new HashMap<>();for(TouchEvent e:plan.events)heads.put(e.getNote().getId(),e);
   for(Note n:chart.getNotes())if(n.getType()==1||n.getType()==3){TouchEvent e=heads.get(n.getId());ok(e!=null&&e.getStartMs()==Math.round(n.getTimeSeconds()*1000),"original chart blue/hold head swallowed or moved early: "+fixture+" / "+n.getId());}
  }
 }
 static void sustainedBackends()throws Exception {
  List<Note> notes=new ArrayList<>();notes.add(new Note(0,3,.05,6,-.3,1,0,true,0));notes.add(new Note(1,3,.05,6.2,.3,1,0,true,0));for(int i=0;i<100;i++)notes.add(n(10+i,1,.2+i*.05f,0));Chart chart=Data104Regression.chart(notes);
  AppSettings.smart=AppSettings.catchDrags=AppSettings.combos=AppSettings.flicks=true;
  for(boolean root:new boolean[]{true,false}){InputBackend.rootEnabled=root;SystemClock.now=100000;RootClient.after=null;RootClient.failAt=-1;TouchService service=new TouchService();ChartMultiTouchPlayer.play(service,chart,SystemClock.now,V,new AtomicBoolean(),s->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
   List<long[]> heads=root?RootClient.last.heads:service.downs;ok(heads.size()==102,"long holds restarted or fresh blue heads lost on "+(root?"Root":"AOSP"));
   int left=0,right=0,blue=0;for(long[] h:heads){float x=Float.intBitsToFloat((int)h[2]);if(Math.abs(x-320)<1)left++;else if(Math.abs(x-680)<1)right++;else if(Math.abs(x-500)<1)blue++;}
   ok(left==1&&right==1&&blue==100,"new blue touches interrupted sustained holds");
   List<Integer> physical=root?RootClient.last.physicalHeads:service.audit.physicalHeads;for(int i=3;i<physical.size();i++)ok(!physical.get(i).equals(physical.get(i-1)),"fresh blue immediately reused the last physical ID");
   if(root)for(long[] head:heads)if(Math.abs(Float.intBitsToFloat((int)head[2])-500)<1)ok(RootClient.last.releases.get(head[0])-head[1]>=33,"blue touch too brief for a 30 Hz game frame");ok(root?RootClient.last.active.isEmpty():service.roots.isEmpty(),"touches leaked at end");
  }
  AppSettings.smart=AppSettings.catchDrags=AppSettings.combos=AppSettings.flicks=false;InputBackend.rootEnabled=false;
 }
 static void visual(){
  VisualChordState state=new VisualChordState(P,new TouchBounds(20,24,979,567));VisualTouchDemand d=f(state,1000,v(1,VisualTouchKind.DRAG,.5f));ok(d.getHolds().size()==1,"yellow not sustained");long id=d.getHolds().get(0).getId();
  d=f(state,1100,v(2,VisualTouchKind.FLICK,.5f));ok(d.getHolds().size()==1&&d.getHolds().get(0).getId()==id,"yellow-to-red recreated contact");float at=d.getHolds().get(0).getY();ok(state.preview(1132).get(id).getY()<at-.05,"red movement postponed by one accessibility batch");
  d=f(state,1200,v(3,VisualTouchKind.DRAG,.5f));ok(d.getHolds().size()==1&&d.getHolds().get(0).getId()==id,"red-to-yellow did not reuse safe contact");
  d=f(state,1240,v(3,VisualTouchKind.DRAG,.5f));ok(Math.abs(d.getHolds().get(0).getY()-.5f)<.01,"red/yellow return did not settle");
  state=new VisualChordState(P,new TouchBounds(20,24,979,567));d=f(state,2000,v(10,VisualTouchKind.DRAG,.3f),v(11,VisualTouchKind.DRAG,.7f));ok(d.getHolds().size()==2,"visual yellow chord collapsed");
  long a=d.getHolds().get(0).getId(),b=d.getHolds().get(1).getId();d=f(state,2100,v(20,VisualTouchKind.DRAG,.31f),v(21,VisualTouchKind.DRAG,.69f));ok(d.getHolds().size()==2&&d.getHolds().get(0).getId()==a&&d.getHolds().get(1).getId()==b,"parallel yellow ID assignment unstable");
  for(int i=0;i<10;i++){long now=2200+i*16;d=f(state,now,v(20,VisualTouchKind.DRAG,.31f),v(21,VisualTouchKind.DRAG,.69f));Set<Long> ids=new HashSet<>();for(VisualTouch t:d.getHolds())ok(ids.add(t.getId()),"duplicate live visual ID");}
 }
 public static void main(String[] args)throws Exception {patterns();realCharts();sustainedBackends();visual();System.out.println("PASS "+checks+" data1.0.9 checks: mixed-role continuous paths, fresh blue heads, independent 2/3/4-note chords, alternating red actions, no teleports, two holds plus 100 blue heads on actual Root state/AOSP injector models, visual role changes and same-batch red forecasts. No device run.");}
}
