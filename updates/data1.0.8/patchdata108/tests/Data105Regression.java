package io.github.phiscript.input;

import io.github.phiscript.*;import io.github.phiscript.engine.*;import io.github.phiscript.root.*;import io.github.phiscript.update.*;
import android.os.SystemClock;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import kotlin.Unit;import org.json.*;

public final class Data105Regression {
 static int checks;static final Viewport V=new Viewport(0,0,1000,600);static final int[] SCREEN={1000,600};
 static final GestureProfile SMART=new GestureProfile(true,70,.12f,true,true,true);
 static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static Note n(int id,int type,double at,float x){return GesturePlanRegression.n(id,type,at,at,x,true,0);}
 static Chart chart(Note... notes){return Data104Regression.chart(Arrays.asList(notes));}
 static Point point(GesturePlan p,Chart c,long time){return p.point(c,p.events.get(0),time,V,SCREEN,new boolean[1]);}
 static VisualTouch t(long id,VisualTouchKind k,float x){return new VisualTouch(id,k,x,.5f,0,-.1f);}
 static VisualTouchDemand frame(VisualChordState state,long at,VisualTouch... touches){return state.update(new VisualTouchFrame(at,1000,600,Arrays.asList(touches)),at);}
 static JSONObject release(String tag,boolean draft,String url)throws Exception {
  return new JSONObject().put("tag_name",tag).put("draft",draft).put("prerelease",true).put("html_url",UpdateCatalog.RELEASES+"/tag/"+tag)
   .put("assets",new JSONArray().put(new JSONObject().put("browser_download_url",url).put("size",48000000).put("state","uploaded")));
 }
 static void updates()throws Exception {
  String root=UpdateCatalog.RELEASES+"/download/";
  JSONArray a=new JSONArray().put(release("data1.0.4",false,root+"data1.0.4/a.apk")).put(release("data1.0.8",false,root+"data1.0.8/a.apk"))
    .put(release("data1.0.8",true,root+"data1.0.8/a.apk")).put(release("data1.0.9",false,"https://example.com/a.apk"));
  UpdateCatalog.Release newest=UpdateCatalog.latest(a.toString(),"data1.0.5");ok(newest!=null&&newest.version.equals("data1.0.8"),"published prerelease with APK missed or untrusted/draft accepted");
  ok(UpdateCatalog.latest(a.toString(),"data1.0.8")==null,"same version announced as new");
  ok(UpdateCatalog.compare("data1.0.10","v1.0.9")>0&&UpdateCatalog.compare("data1.0.5","v1.0.5")==0,"lexicographic/tag comparison");
  for(String url:Arrays.asList("http://github.com/"+UpdateCatalog.REPO+"/releases/download/x/a.apk",root+"../a.apk",root+"x/a.apk?x=1","https://github.com.attacker.test/"+UpdateCatalog.REPO+"/releases/download/x/a.apk",root+"x/a.zip"))
   ok(UpdateCatalog.latest(new JSONArray().put(release("data1.0.99",false,url)).toString(),"data1.0.5")==null,"untrusted update URL accepted: "+url);
  ok(UpdateCatalog.latest("[{\"tag_name\":\"data1.0.8\"}]","data1.0.5")==null,"missing APK accepted");
  boolean malformed=false;try{UpdateCatalog.latest("not JSON","data1.0.5");}catch(Exception expected){malformed=true;}ok(malformed,"malformed JSON accepted");
 }
 static void chartGestures()throws Exception {
  Chart reds=chart(n(1,4,.2,0),n(2,4,.4,0),n(3,4,.6,0));GesturePlan plan=GesturePlan.obtain(reds,SMART);
  ok(plan.events.size()==1&&plan.hasFlickPath(plan.events.get(0)),"red run sends fresh DOWNs");
  float prior=0;for(long hit:new long[]{200,400,600}){Point a=point(plan,reds,hit-12),b=point(plan,reds,hit+12);float dy=b.getY()-a.getY();ok(Math.abs(dy)>20,"red movement too short");if(prior!=0)ok(prior*dy<0,"consecutive red directions did not reverse");prior=dy;}
  Chart blueRed=chart(n(1,1,.2,0),n(2,4,.2,0),n(3,4,.4,0));plan=GesturePlan.obtain(blueRed,SMART);ok(plan.events.size()==1,"simultaneous blue/red did not share DOWN then MOVE");TouchEvent contact=plan.events.get(0);Point down=point(plan,blueRed,contact.getStartMs());ok(Math.abs(down.getX()-500)<.01&&Math.abs(down.getY()-300)<.01,"blue DOWN moved to red endpoint");ok(plan.isContinuous(contact),"blue-led red trace cannot catch up");
  Chart yellow=chart(n(1,1,.2,-.02f),n(2,2,.25,0),n(3,2,.55,.02f));plan=GesturePlan.obtain(yellow,SMART);ok(plan.events.size()==1,"blue/yellow did not share DOWN");for(long hit:new long[]{250,550}){Point p=point(plan,yellow,hit);ok(Math.abs(p.getY()-300)<.01,"yellow hit left judge line");}
  for(Chart separate:Arrays.asList(chart(n(1,4,.2,0),n(2,1,.25,0)),chart(n(1,1,.2,-.4f),n(2,4,.25,.4f)),chart(n(1,1,.2,0),n(2,1,.3,0),n(3,4,.35,0)))){
   plan=GesturePlan.obtain(separate,SMART);ok(plan.events.size()==2,"blue head swallowed or distant red combined");}
  Chart lanes=chart(n(1,4,.2,-.3f),n(2,4,.2,.3f),n(3,4,.4,-.3f),n(4,4,.4,.3f));ok(GesturePlan.obtain(lanes,SMART).events.size()==2,"simultaneous red lanes share pointer");
  Chart mixed=chart(n(1,1,.2,0),n(2,4,.22,0),n(3,4,.42,0),n(4,4,.62,0));
  AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=AppSettings.edges=AppSettings.inward=true;
  for(boolean root:new boolean[]{true,false}){InputBackend.rootEnabled=root;SystemClock.now=100000;RootClient.after=null;RootClient.failAt=-1;TouchService service=new TouchService();ChartMultiTouchPlayer.play(service,mixed,SystemClock.now,V,new AtomicBoolean(),s->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());ok(root?RootClient.last.heads.size()==1&&RootClient.last.active.isEmpty():service.downs.size()==1&&service.roots.isEmpty(),"backend did not retain combined contact or release at end");}
  AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=AppSettings.edges=AppSettings.inward=false;InputBackend.rootEnabled=false;
 }
 static void visual(){
  TouchBounds bounds=new TouchBounds(20,24,979,567);VisualChordState state=new VisualChordState(SMART,bounds);
  VisualTouchDemand d=frame(state,1000,t(1,VisualTouchKind.TAP,.5f));ok(d.getHolds().size()==1&&d.getShots().isEmpty(),"visual blue seed absent");long id=d.getHolds().get(0).getId();
  d=frame(state,1060,t(2,VisualTouchKind.FLICK,.5f));ok(d.getHolds().size()==1&&d.getHolds().get(0).getId()==id,"visual blue/red re-clicked");
  d=frame(state,1120);float first=d.getHolds().get(0).getY();ok(first<.44,"first visual red did not move inward");
  d=frame(state,1260,t(3,VisualTouchKind.FLICK,.5f));ok(d.getHolds().size()==1&&d.getHolds().get(0).getId()==id,"visual red chain re-clicked");d=frame(state,1320);ok(d.getHolds().get(0).getY()>first+.05,"visual red did not reverse");
  d=frame(state,1700);ok(d.getHolds().isEmpty(),"visual red contact leaked past grace");
  state=new VisualChordState(SMART,bounds);d=frame(state,2000,t(10,VisualTouchKind.TAP,.3f));id=d.getHolds().get(0).getId();d=frame(state,2080,t(11,VisualTouchKind.DRAG,.32f));ok(d.getHolds().size()==1&&d.getHolds().get(0).getId()==id,"visual blue/yellow re-clicked");d=frame(state,2300,t(12,VisualTouchKind.DRAG,.34f));ok(d.getHolds().size()==1&&d.getHolds().get(0).getId()==id,"visual yellow tracking churn lost contact");
  state=new VisualChordState(SMART,bounds);d=frame(state,3000,t(20,VisualTouchKind.TAP,.3f),t(21,VisualTouchKind.TAP,.3f));ok(d.getHolds().size()==2,"two blue heads shared a DOWN");
 }
 public static void main(String[] args)throws Exception {updates();chartGestures();visual();System.out.println("PASS "+checks+" data1.0.5 checks: trusted release/version parsing, sustained alternating red paths, blue/red and blue/yellow DOWN reuse, preserved fresh blue heads and independent lanes, both chart backends and visual composition. Host models only.");}
}
