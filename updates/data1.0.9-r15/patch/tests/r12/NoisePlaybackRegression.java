package io.github.phiscript.input;
import io.github.phiscript.engine.*;
import io.github.phiscript.AppSettings;
import io.github.phiscript.root.*;
import android.os.SystemClock;
import android.accessibilityservice.GestureDescription;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class NoisePlaybackRegression {
    static Note note(int id,int type,double t,double end,double x){return new Note(id,type,t,end,x,1,0,true,0);}
    static Chart build(JSONObject area,Note... notes)throws Exception {
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));
        return Chart.Companion.fromNative$app_debug(Arrays.asList(notes),0,(n,t,v)->new Point((float)(n.getPositionX()*v.getWidth()),v.getHeight()/2),(n,t,v)->new Point(0,1));
    }
    static JSONObject area(double start,double end)throws Exception {
        return CoreTest.area(.1,-1,.5,2).put("appearTime",start).put("enableTime",start).put("disableTime",end).put("disappearTime",end);
    }
    static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
    static float[] arc(List<float[]> points,double ratio){
        double total=0;for(int i=1;i<points.size();i++)total+=Math.hypot(points.get(i)[0]-points.get(i-1)[0],points.get(i)[1]-points.get(i-1)[1]);
        double left=total*ratio;for(int i=1;i<points.size();i++){float[] a=points.get(i-1),b=points.get(i);double n=Math.hypot(b[0]-a[0],b[1]-a[1]);if(n>0&&left<=n){double f=left/n;return new float[]{(float)(a[0]+(b[0]-a[0])*f),(float)(a[1]+(b[1]-a[1])*f)};}left-=n;}return points.get(points.size()-1);
    }
    static void clearPoint(Chart chart,float x,float y,long wall){require(!BlockAreaSet.covers(chart,x,y,0,0,1000,600,(wall-100000)/1000.0),"actual held/path position inside active noise at "+(wall-100000));}
    static void heldFixture(boolean root,String phase,boolean exact)throws Exception{
        SystemClock.now=100000;InputBackend.rootEnabled=root;RootClient.after=null;RootClient.failAt=-1;TouchService service=new TouchService();
        Chart held=build(area(.14,.27),note(1,3,.03,.48,.30),note(2,3,.03,.48,.78),note(3,1,.55,.55,.63));
        List<String> status=new ArrayList<>();
        Map<Long,float[]> previous=new HashMap<>();Map<Long,Long> heldSince=new HashMap<>();int[] cursor={0};
        if(root)RootClient.after=()->{
            long actual=SystemClock.now-1;List<float[]> samples=RootClient.last.samples;
            for(Map.Entry<Long,float[]> entry:previous.entrySet())for(long ms=heldSince.get(entry.getKey());ms<actual;ms++)clearPoint(held,entry.getValue()[0],entry.getValue()[1],ms);
            previous.clear();heldSince.clear();
            while(cursor[0]<samples.size()){float[] sample=samples.get(cursor[0]++);long key=(long)sample[1];clearPoint(held,sample[2],sample[3],actual);previous.put(key,new float[]{sample[2],sample[3]});heldSince.put(key,actual);}
        };
        else service.after=gesture->{
            long span=0;for(GestureDescription.StrokeDescription stroke:gesture.strokes)span=Math.max(span,stroke.start+stroke.duration);long base=SystemClock.now-span-2;
            for(Map.Entry<Long,float[]> entry:previous.entrySet())for(long ms=heldSince.get(entry.getKey());ms<base;ms++)clearPoint(held,entry.getValue()[0],entry.getValue()[1],ms);
            previous.clear();heldSince.clear();
            for(GestureDescription.StrokeDescription stroke:gesture.strokes){
                for(long ms=0;ms<=stroke.duration;ms++){float[] p=arc(stroke.path.points,ms/(double)stroke.duration);clearPoint(held,p[0],p[1],base+stroke.start+ms);}
                if(stroke.keep){previous.put((long)stroke.getId(),arc(stroke.path.points,1));heldSince.put((long)stroke.getId(),base+stroke.start+stroke.duration);}
            }
        };
        try{ChartMultiTouchPlayer.play(service,held,100000,new Viewport(0,0,1000,600),new AtomicBoolean(),m->{status.add(m);return kotlin.Unit.INSTANCE;},new AtomicBoolean(),new AtomicBoolean());}
        finally{RootClient.after=null;}
        List<long[]> downs=root?RootClient.last.heads:service.downs;int left=0,right=0,last=0;
        for(long[] v:downs){float x=Float.intBitsToFloat((int)v[2]);if(x<450)left++;else if(x>700)right++;else last++;}
        require(left>=2,"blocked hold should restart after release "+root+" "+left);
        require(right>=1,"safe parallel hold was never touched "+root);
        require(last==1,"post-zone tap missing "+root);
        require(root?RootClient.last.active.isEmpty():service.roots.isEmpty(),"physical pointers leaked "+root);
        if(exact)require(right==1,"steady safe parallel hold should stay pressed "+root+" "+right);
        System.out.println("noise phase="+phase+" root="+root+" blockedHoldDowns="+left+" safeHoldDowns="+right+" conservativeExtraDowns="+Math.max(0,right-1)+" actualUnsafe=0");
        if(right>1)for(String line:status)if(line.startsWith("噪域提交复核"))System.out.println(line);
    }
    public static void main(String[] args)throws Exception{
        TapMod.init(new android.content.Context());AppSettings.values.put("tapmod_enabled","false");TapMod.reload();
        Chart links=build(area(.70,.82),note(1,2,.31,.31,.3),note(2,2,.4,.4,.3),note(3,2,.75,.75,.3),note(4,2,1.05,1.05,.3),note(5,2,1.14,1.14,.3));
        GesturePlan plan=GesturePlan.obtain(links,new GestureProfile(true,70,.12f,true,true,true));
        require(plan.connectedGroups==2,"quiet chains must resume after noise");
        require(plan.events.size()==2 && plan.unavailableNotes==1,"two quiet groups and a fully obstructed judgement strip");
        for(TouchEvent e:plan.events)require(!(e.getStartMs()<700&&e.getEndMs()>820),"cross-window connection");
        // Fixed phases, never retry-until-pass. Cold input may conservatively
        // release during JIT/GC; safety and cleanup are mandatory in every run.
        for(boolean root:new boolean[]{true,false})heldFixture(root,"cold",false);
        for(int warm=0;warm<3;warm++)for(boolean root:new boolean[]{true,false})heldFixture(root,"warm-"+warm,false);
        for(boolean root:new boolean[]{true,false})heldFixture(root,"steady",true);
        System.out.println("PASS active-window separation and quiet linking; one held finger releases/restarts, parallel hold continues, later TAP dispatched on both backends.");
    }
}
