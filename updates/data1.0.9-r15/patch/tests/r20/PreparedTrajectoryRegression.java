package io.github.phiscript.input;

import io.github.phiscript.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.InputBackend;
import android.os.SystemClock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import kotlin.Unit;
import org.json.*;

/** Uses the real planner/decoder and fails if a prepared hit evaluates geometry. */
public final class PreparedTrajectoryRegression {
    static final Viewport V=new Viewport(0,0,1000,600);static final TouchBounds B=new TouchBounds(0,0,999,599);
    static int checks,calls;static boolean forbid;
    static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    static float[] arc(List<float[]> points,double f){double total=0;for(int i=1;i<points.size();i++)total+=Math.hypot(points.get(i)[0]-points.get(i-1)[0],points.get(i)[1]-points.get(i-1)[1]);double left=total*f;for(int i=1;i<points.size();i++){float[] a=points.get(i-1),b=points.get(i);double distance=Math.hypot(b[0]-a[0],b[1]-a[1]);if(distance>0&&left<=distance)return new float[]{(float)(a[0]+(b[0]-a[0])*left/distance),(float)(a[1]+(b[1]-a[1])*left/distance)};left-=distance;}return points.get(points.size()-1);}
    static Note n(int id,int type,double at,double end,double x){return new Note(id,type,at,end,x,1,0,true,0);}
    static Chart chart(List<Note> notes){return Chart.Companion.fromNative$app_debug(notes,0,(n,t,v)->{calls++;if(forbid)throw new AssertionError("cache hit evaluated chart.position");return new Point((float)(v.getLeft()+v.getWidth()*n.getPositionX()),(float)(v.getTop()+v.getHeight()*(.5+.05*Math.sin(t))));},(n,t,v)->{if(forbid)throw new AssertionError("cache hit evaluated judgeNormal");return new Point(0,1);});}
    static GestureProfile profile(){return new GestureProfile(true,70,.12f,true,true,true);}
    static void settings(){AppSettings.values.clear();AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=true;AppSettings.edges=AppSettings.inward=false;AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new android.content.Context());}
    static void preparedPlayback(boolean root)throws Exception{
        settings();forbid=false;calls=0;
        Chart c=chart(Arrays.asList(n(1,3,.03,.65,.25),n(2,1,.15,.15,.6),n(3,4,.35,.35,.7),n(4,2,.5,.5,.65)));
        TouchService service=new TouchService();GesturePlan plan=GesturePlan.prepareForImport(c,profile(),V,B,new AtomicBoolean());
        ok(plan.hasPreparedTrajectory()&&plan.preparedSampleCount()>150,"dense hold/red trajectory missing");byte[] encoded=plan.encodePrepared("fixture-mixed-v1");
        long salt=TapMod.preparationSalt(c);TapMod.preparationSalt(c,salt+1);
        GesturePlan restored=GesturePlan.decodePrepared(c,profile(),V,B,encoded,"fixture-mixed-v1");ok(TapMod.preparationSalt(c)==salt,"persisted random salt not restored");
        forbid=true;int before=calls;SystemClock.now=100000;InputBackend.rootEnabled=root;
        List<float[]> actual=new ArrayList<>();if(!root)service.after=g->{long duration=0;for(android.accessibilityservice.GestureDescription.StrokeDescription stroke:g.strokes)duration=Math.max(duration,stroke.start+stroke.duration);long base=SystemClock.now-duration-2;for(android.accessibilityservice.GestureDescription.StrokeDescription stroke:g.strokes)for(long ms=0;ms<=stroke.duration;ms++){float[] p=arc(stroke.path.points,ms/(double)stroke.duration);actual.add(new float[]{base+stroke.start+ms,p[0],p[1]});}};
        // Exact same physical geometry as the import. Test mocks disable edges.
        ChartMultiTouchPlayer.play(service,c,100000,V,new AtomicBoolean(),s->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
        ok(calls==before,"prepared playback recalculated chart coordinates");ok(root?io.github.phiscript.root.RootClient.last.active.isEmpty():service.roots.isEmpty(),"prepared playback leaked contacts");
        List<long[]> heads=root?io.github.phiscript.root.RootClient.last.heads:service.downs;ok(heads.size()==plan.events.size(),"prepared playback lost or duplicated a planned fresh head");
        if(root)for(float[] sample:io.github.phiscript.root.RootClient.last.samples)actual.add(new float[]{sample[0],sample[2],sample[3]});
        for(Note note:c.getNotes()){boolean hit=false;if(note.getType()==1||note.getType()==3){for(long[] head:heads)if(Math.abs(head[1]-100000-Math.round(note.getTimeSeconds()*1000))<=100&&Math.abs(Float.intBitsToFloat((int)head[2])-note.getPositionX()*1000)<2)hit=true;}else for(float[] sample:actual)if(Math.abs(sample[0]-100000-note.getTimeSeconds()*1000)<=50&&Math.abs(sample[1]-note.getPositionX()*1000)<98)hit=true;ok(hit,"prepared playback lost TAP/HOLD head or DRAG/FLICK contact coverage for note "+note.getId());}
        float low=Float.POSITIVE_INFINITY,high=Float.NEGATIVE_INFINITY;for(float[] sample:actual)if(sample[0]-100000>=320&&sample[0]-100000<=390){low=Math.min(low,sample[2]);high=Math.max(high,sample[2]);}ok(high-low>=20,"prepared red trajectory collapsed into a stationary touch");
        ok(restored.preparedSampleCount()==plan.preparedSampleCount(),"encoded dense samples changed");forbid=false;
        for(int i=0;i<plan.events.size();i++){TouchEvent a=plan.events.get(i),b=restored.events.get(i);for(long t=a.getStartMs();t<=a.getEndMs();t+=7){Point x=plan.preparedPoint(a,t,null,t),y=restored.preparedPoint(b,t,null,t);ok(Float.floatToIntBits(x.getX())==Float.floatToIntBits(y.getX())&&Float.floatToIntBits(x.getY())==Float.floatToIntBits(y.getY()),"roundtrip changed physical interpolation");}}
        encoded[encoded.length/2]^=1;boolean rejected=false;try{GesturePlan.decodePrepared(c,profile(),V,B,encoded,"fixture-mixed-v1");}catch(IOException expected){rejected=true;}ok(rejected,"corrupt cache accepted");
    }
    static void invalidation()throws Exception{
        settings();forbid=false;Chart c=chart(Collections.singletonList(n(1,3,.1,.4,.5)));GesturePlan p=GesturePlan.prepareForImport(c,profile(),V,B,null);byte[] bytes=p.encodePrepared("same-chart");
        boolean rejected=false;try{GesturePlan.decodePrepared(c,profile(),new Viewport(0,0,1200,600),B,bytes,"same-chart");}catch(IOException expected){rejected=true;}ok(rejected,"changed viewport reused physical coordinates");
        rejected=false;try{GesturePlan.decodePrepared(c,profile(),V,B,bytes,"different-chart");}catch(IOException expected){rejected=true;}ok(rejected,"different chart reused cache");
        AppSettings.values.put("tap_noise_clock_guard_ms","24");rejected=false;try{GesturePlan.decodePrepared(c,profile(),V,B,bytes,"same-chart");}catch(IOException expected){rejected=true;}ok(rejected,"changed noise guard reused escape trajectory");
        AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.reload();rejected=false;
        AppSettings.inward=true;try{GesturePlan.decodePrepared(c,profile(),V,B,bytes,"same-chart");}catch(IOException expected){rejected=true;}ok(rejected,"changed inward flick setting reused trajectory");AppSettings.inward=false;
        Chart cancelled=chart(Collections.singletonList(n(2,3,.1,.8,.5)));try{GesturePlan.prepareForImport(cancelled,profile(),V,B,new AtomicBoolean(true));}catch(IllegalStateException expected){rejected=true;}ok(rejected,"cancelled import still prepared trajectories");
    }
    static void gaps()throws Exception{
        settings();forbid=false;Chart c=chart(Collections.singletonList(n(1,3,.05,.65,.5)));
        JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",-1).put("y",-1)).put("topRightPercentage",new JSONObject().put("x",2).put("y",2)).put("appearTime",.25).put("enableTime",.25).put("disableTime",.4).put("disappearTime",.4);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(c);
        GesturePlan source=GesturePlan.prepareForImport(c,profile(),V,B,null);GesturePlan plan=GesturePlan.decodePrepared(c,profile(),V,B,source.encodePrepared("noise-gap"),"noise-gap");TouchEvent event=plan.events.get(0);TouchSafety safety=TouchSafety.forPreparation(B);
        forbid=true;boolean blocked=false;try{safety.point(plan,c,event,300,V,new int[]{1000,600},new boolean[1]);}catch(TapMod.NoSafePoint expected){blocked=true;}ok(blocked,"cached blocked sample returned a point");
        Point before=safety.point(plan,c,event,150,V,new int[]{1000,600},new boolean[1]);blocked=false;try{safety.point(plan,c,event,500,V,new int[]{1000,600},new boolean[1],before,150);}catch(TapMod.NoSafePoint expected){blocked=true;}ok(blocked,"interpolated continuous finger across a noise gap");
        ok(safety.point(plan,c,event,500,V,new int[]{1000,600},new boolean[1])!=null,"fresh lifetime after noise could not resume");forbid=false;
    }
    static void primitiveMergeAndInactiveSpan()throws Exception{
        settings();forbid=false;BlockAreaSet.clear();Note note=n(1,3,.01,.019,.5);Chart c=chart(Collections.singletonList(note));ContactPlanner.Route route=new ContactPlanner.Route(note,new ContactPlanner.Anchor(note),10,19);route.keys.put(11L,new ContactPlanner.Anchor(note));route.keys.put(13L,new ContactPlanner.Anchor(note));
        GesturePlan plan=GesturePlan.routed(Collections.singletonList(route),0,0,0,0);PreparedTrajectory prepared=PreparedTrajectory.prepare(c,plan,profile(),V,B,null);Object track=prepared.tracks.get(plan.events.get(0));java.lang.reflect.Field field=track.getClass().getDeclaredField("time");field.setAccessible(true);ok(Arrays.equals((int[])field.get(track),new int[]{10,11,13,14,18,19}),"primitive merge dropped a non-grid route boundary");
        Note held=n(2,3,.1,.5,.5);Chart gap=chart(Collections.singletonList(held));JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",-1).put("y",-1)).put("topRightPercentage",new JSONObject().put("x",2).put("y",2)).put("appearTime",.203).put("enableTime",.203).put("disableTime",.204).put("disappearTime",.204);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(gap);GesturePlan raw=ContactPlanner.build(gap,V,B,profile());TouchEvent e=raw.events.get(0);TouchSafety safety=TouchSafety.forPreparation(B);Point start=safety.preparePoint(raw,gap,e,190,V,new int[]{1000,600},new boolean[1],null,190);
        ok(TapMod.clearAt(gap,V,start,.190)&&TapMod.clearAt(gap,V,start,.220),"gap oracle endpoints are not clear");boolean rejected=false;try{safety.preparePoint(raw,gap,e,220,V,new int[]{1000,600},new boolean[1],start,190);}catch(TapMod.NoSafePoint expected){rejected=true;}ok(rejected,"inactive-endpoint shortcut skipped a region active between samples");ok(safety.preparePoint(raw,gap,e,220,V,new int[]{1000,600},new boolean[1],null,220)!=null,"a fresh lifetime could not recover after the short activation");
    }
    static void residentPeek(){
        settings();forbid=false;Chart c=chart(Collections.singletonList(n(1,3,.1,.3,.5)));GesturePlan p=GesturePlan.prepareForImport(c,profile(),V,B,null);int before=calls;forbid=true;ok(GesturePlan.cachedPrepared(c,profile(),V,B)==p,"resident lookup did not preserve the prepared object");ok(calls==before,"resident lookup evaluated geometry");ok(GesturePlan.cachedPrepared(c,profile(),new Viewport(0,0,999,600),B)==null,"resident lookup ignored viewport");long salt=TapMod.preparationSalt(c);TapMod.preparationSalt(c,salt+1);ok(GesturePlan.cachedPrepared(c,profile(),V,B)==null,"resident lookup ignored random salt");TapMod.preparationSalt(c,salt);GesturePlan.discardPrepared(c);ok(GesturePlan.cachedPrepared(c,profile(),V,B)==null,"manual refresh kept a stale resident cache");forbid=false;
    }
    public static void main(String[] args)throws Exception{for(boolean root:new boolean[]{false,true})preparedPlayback(root);invalidation();gaps();primitiveMergeAndInactiveSpan();residentPeek();InputBackend.rootEnabled=false;System.out.println("PASS "+checks+" dense trajectory checks: cache-hit zero geometry calls, native Root/AOSP playback, digest corruption, settings/viewport/fingerprint invalidation, random salt, cancellation and explicit noise gaps.");}
}
