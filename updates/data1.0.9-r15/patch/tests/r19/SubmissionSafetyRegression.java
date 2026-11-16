package io.github.phiscript.input;
import android.os.SystemClock;
import android.accessibilityservice.GestureDescription;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import org.json.*;

/** Repro: geometry is safe when computed, then activates before actual send. */
public final class SubmissionSafetyRegression {
    static final Viewport V=new Viewport(0,0,1000,600);static boolean runtime,diagnostics=true;static int cpu,checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static float[] arc(List<float[]> points,double fraction){double total=0;for(int i=1;i<points.size();i++)total+=Math.hypot(points.get(i)[0]-points.get(i-1)[0],points.get(i)[1]-points.get(i-1)[1]);double left=total*fraction;for(int i=1;i<points.size();i++){float[] a=points.get(i-1),b=points.get(i);double length=Math.hypot(b[0]-a[0],b[1]-a[1]);if(length>0&&left<=length){double f=left/length;return new float[]{(float)(a[0]+(b[0]-a[0])*f),(float)(a[1]+(b[1]-a[1])*f)};}left-=length;}return points.get(points.size()-1);}
    static void observePaths(TouchService service,Chart chart,int[] violations){service.after=gesture->{long duration=0;for(GestureDescription.StrokeDescription stroke:gesture.strokes)duration=Math.max(duration,stroke.start+stroke.duration);long base=SystemClock.now-duration-2-100000;for(GestureDescription.StrokeDescription stroke:gesture.strokes)for(long ms=0;ms<=stroke.duration;ms++){float[] p=arc(stroke.path.points,ms/(double)stroke.duration);if(BlockAreaSet.covers(chart,p[0],p[1],0,0,1000,600,(base+stroke.start+ms)/1000.0))violations[0]++;}};}
    static Chart chart()throws Exception{
        List<Note> notes=Arrays.asList(new Note(1,1,.2,.2,-.2,1,0,true,0),new Note(2,1,.2,.2,.2,1,0,true,0),new Note(3,1,.6,.6,0,1,0,true,0));
        Chart chart=Chart.Companion.fromNative$app_debug(notes,0,(note,t,v)->{if(runtime)SystemClock.now+=cpu;return new Point(500+(float)note.getPositionX()*500,300);},(note,t,v)->new Point(0,1));
        JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",-1).put("y",-1))
            .put("topRightPercentage",new JSONObject().put("x",2).put("y",2)).put("appearTime",.24).put("enableTime",.24).put("disableTime",.5).put("disappearTime",.5);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(chart);return chart;
    }
    static void replay(boolean root,int cost)throws Exception{
        runtime=false;cpu=cost;SystemClock.now=100000;InputBackend.rootEnabled=root;RootClient.after=null;RootClient.failAt=-1;
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new android.content.Context());
        Chart chart=chart();TouchService service=new TouchService();GesturePlan.preflight(chart,service,V);
        int[] actualPathViolations={0};if(!root)observePaths(service,chart,actualPathViolations);
        List<String> status=new ArrayList<>();runtime=true;
        try{ChartMultiTouchPlayer.play(service,chart,100000,V,new AtomicBoolean(),text->{status.add(text);return Unit.INSTANCE;},new AtomicBoolean(),new AtomicBoolean());}
        finally{runtime=false;InputBackend.rootEnabled=false;}
        List<long[]> heads=root?RootClient.last.heads:service.downs;int bad=0;
        for(long[] head:heads){float x=Float.intBitsToFloat((int)head[2]),y=Float.intBitsToFloat((int)head[3]);if(BlockAreaSet.covers(chart,x,y,0,0,1000,600,(head[1]-100000)/1000.0))bad++;}
        if(root){for(float[] sample:RootClient.last.samples)if(BlockAreaSet.covers(chart,sample[2],sample[3],0,0,1000,600,(sample[0]-100000)/1000.0))bad++;}
        else bad+=actualPathViolations[0];
        System.out.println("submission root="+root+" cpu="+cost+" actualHeads="+heads.size()+" unsafe="+bad);
        ok(bad==0,"old safe geometry submitted after noise activated; root="+root+" cpu="+cost);
        boolean later=false;for(long[] head:heads)if(head[1]-100000>=580)later=true;
        ok(later,"unsafe chord rejection blocked the later safe tap");
        ok(root?RootClient.last.active.isEmpty():service.roots.isEmpty(),"touch leaked after rejected submission");
        if(diagnostics)ok(status.stream().anyMatch(s->s.startsWith("噪域提交复核")),"submission rejection diagnostics missing");
    }
    static void selectiveHolds(boolean root,String phase,boolean exact)throws Exception{
        List<Note> notes=Arrays.asList(new Note(1,3,.10,.80,.25,1,0,true,0),new Note(2,3,.10,.80,.75,1,0,true,0),new Note(3,1,.18,.18,.50,1,0,true,0),new Note(4,1,.65,.65,.50,1,0,true,0));
        Chart chart=Chart.Companion.fromNative$app_debug(notes,0,(n,t,v)->new Point((float)n.getPositionX()*1000,300),(n,t,v)->new Point(0,1));
        JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",.01).put("y",-1)).put("topRightPercentage",new JSONObject().put("x",.49).put("y",2)).put("appearTime",.27).put("enableTime",.27).put("disableTime",.49).put("disappearTime",.49);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(chart);
        runtime=false;cpu=0;SystemClock.now=100000;InputBackend.rootEnabled=root;RootClient.after=null;RootClient.failAt=-1;
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.init(new android.content.Context());
        TouchService service=new TouchService();GesturePlan.preflight(chart,service,V);int[] bad={0};if(!root)observePaths(service,chart,bad);
        List<String> status=new ArrayList<>();ChartMultiTouchPlayer.play(service,chart,100000,V,new AtomicBoolean(),s->{status.add(s);return Unit.INSTANCE;},new AtomicBoolean(),new AtomicBoolean());
        List<long[]> downs=root?RootClient.last.heads:service.downs;int left=0,right=0;for(long[] d:downs){float x=Float.intBitsToFloat((int)d[2]);if(Math.abs(x-250)<2)left++;if(Math.abs(x-750)<2)right++;}
        if(root)for(float[] s:RootClient.last.samples)if(BlockAreaSet.covers(chart,s[2],s[3],0,0,1000,600,(s[0]-100000)/1000.0))bad[0]++;
        ok(right>=1,"safe parallel HOLD never received its head");if(exact)ok(right==1,"steady safe parallel HOLD was dropped/restarted during selective release");ok(left>=2,"blocked HOLD did not lift and resume after noise");ok(bad[0]==0,"actual held-pointer path entered noise during selective retirement");ok(root?RootClient.last.active.isEmpty():service.roots.isEmpty(),"selective holds leaked after completion");
        System.out.println("selective phase="+phase+" root="+root+" safeHOLDdowns="+right+" blockedHOLDdowns="+left+" conservativeExtraDowns="+Math.max(0,right-1)+" unsafe="+bad[0]);
        if(right>1)for(String line:status)if(line.startsWith("噪域提交复核"))System.out.println(line);
    }
    static void arcLengthTiming()throws Exception{
        runtime=false;Chart chart=chart();
        JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",.39).put("y",.48)).put("topRightPercentage",new JSONObject().put("x",.41).put("y",.52))
            .put("appearTime",.29).put("enableTime",.29).put("disableTime",.32).put("disappearTime",.32);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(chart);
        List<Point> path=Arrays.asList(new Point(300,300),new Point(400,300),new Point(400,500));
        ChartPlaybackClock.Slice timing=new ChartPlaybackClock(100000).snapshot(100200);
        java.lang.reflect.Method check=ChartMultiTouchPlayer.class.getDeclaredMethod("checkSubmittedPath",Chart.class,Viewport.class,List.class,ChartPlaybackClock.Slice.class,long.class,long.class,long.class);check.setAccessible(true);
        boolean blocked=false;try{check.invoke(null,chart,V,path,timing,100200L,100500L,Long.MAX_VALUE);}
        catch(java.lang.reflect.InvocationTargetException failure){if(failure.getCause() instanceof TapMod.NoSafePoint)blocked=true;else throw failure;}
        // 100px then 200px: Android reaches the bend at .300, while an incorrect
        // per-vertex time split would reach it at .350 after the noise vanished.
        ok(blocked,"actual constant-arc-length traversal timing was not checked");
    }
    public static void main(String[] args)throws Exception{
        diagnostics=args.length==0;
        for(boolean root:new boolean[]{false,true})for(int cpu:new int[]{0,6,12,24})replay(root,cpu);
        // Fixed phases, not retry-until-pass: JIT/GC may consume the real 8ms
        // safety budget, but every cold path must still be safe and complete.
        for(boolean root:new boolean[]{false,true})selectiveHolds(root,"cold",false);
        for(int warm=0;warm<3;warm++)for(boolean root:new boolean[]{false,true})selectiveHolds(root,"warm-"+warm,false);
        for(boolean root:new boolean[]{false,true})selectiveHolds(root,"steady",true);
        arcLengthTiming();
        System.out.println("PASS "+checks+" actual-wall-clock submission safety checks: expensive geometry, activation after planning, per-contact rejection, subsequent safe input and release.");
    }
}
