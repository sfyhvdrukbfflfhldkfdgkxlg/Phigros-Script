package io.github.phiscript.input;

import android.accessibilityservice.GestureDescription;
import android.os.SystemClock;
import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.PhiraImportDecoder;
import io.github.phiscript.assets.SharedChartImport;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.InputBackend;
import io.github.phiscript.root.RootClient;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;

/** Observe emitted pointer lifetimes, including contacts held before the observed window. */
public final class PointerReplayRegression {
    private static final long EPOCH = 100000;
    private static final Viewport VIEW = new Viewport(0, 19, 1000, 581);

    private static final class Sample {
        final long time;
        final float x, y;
        Sample(long t, float x, float y) { time=t; this.x=x; this.y=y; }
    }

    private static final class Trace {
        final long start, end;
        final List<Sample> samples = new ArrayList<>();
        final List<long[]> changes = new ArrayList<>();
        int newDowns;
        Trace(long start,long end) { this.start=start; this.end=end; }
        void sample(long t,float x,float y) {
            if(t>=start && t<=end)samples.add(new Sample(t,x,y));
        }
        int peak() {
            changes.sort((a,b)->a[0]==b[0]?Long.compare(a[1],b[1]):Long.compare(a[0],b[0]));
            int active=0,peak=0;
            for(long[] e:changes){
                if(e[0]>end)break;
                if(e[0]>start)peak=Math.max(peak,active);
                active+=(int)e[1];
            }
            return Math.max(peak,active);
        }
    }

    private static float[] position(List<float[]> points,double fraction) {
        double total=0;
        for(int i=1;i<points.size();i++)total+=distance(points.get(i-1), points.get(i));
        double left=total*fraction;
        for(int i=1;i<points.size();i++){
            float[] a=points.get(i-1),b=points.get(i);
            double length=distance(a,b);
            if(length>0 && left<=length){
                double f=left/length;
                return new float[]{(float)(a[0]+(b[0]-a[0])*f),(float)(a[1]+(b[1]-a[1])*f)};
            }
            left-=length;
        }
        return points.get(points.size()-1);
    }

    private static double distance(float[] a,float[] b){return Math.hypot(a[0]-b[0],a[1]-b[1]);}

    private static Trace play(Chart chart,boolean root,long start,long end) throws Exception {
        Trace trace=new Trace(start,end);
        SystemClock.now=EPOCH;
        TouchService service=new TouchService();
        InputBackend.rootEnabled=root;
        int[] cursor={0},lastCount={0};
        if(root)RootClient.after=()->{
            List<float[]> sent=RootClient.last.samples;
            int size=sent.size()-cursor[0];
            long t=SystemClock.now-EPOCH;
            if(size!=lastCount[0])trace.changes.add(new long[]{t,size-lastCount[0]});
            lastCount[0]=size;
            while(cursor[0]<sent.size()){
                float[] p=sent.get(cursor[0]++);
                trace.sample((long)p[0]-EPOCH,p[2],p[3]);
            }
        };
        else service.after=g->{
            long duration=0;
            for(GestureDescription.StrokeDescription s:g.strokes)duration=Math.max(duration,s.start+s.duration);
            long base=SystemClock.now-EPOCH-duration-2;
            for(GestureDescription.StrokeDescription s:g.strokes){
                long first=Math.max(0,start-(base+s.start)),last=Math.min(s.duration,end-(base+s.start));
                for(long ms=first;ms<=last;ms++){
                    float[] p=position(s.path.points,ms/(double)s.duration);
                    trace.sample(base+s.start+ms,p[0],p[1]);
                }
            }
        };
        try {
            GesturePlan.preflight(chart,service,VIEW);
            ChartMultiTouchPlayer.play(service,chart,EPOCH,VIEW,new AtomicBoolean(),m->kotlin.Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
            if(!root){
                for(long[] p:service.downs)trace.changes.add(new long[]{p[1]-EPOCH,1});
                for(long[] p:service.ups)trace.changes.add(new long[]{p[1]-EPOCH,-1});
            }
            for(long[] down:root?RootClient.last.heads:service.downs){
                long t=down[1]-EPOCH;
                if(t>=start && t<=end)trace.newDowns++;
            }
        } finally {RootClient.after=null;}
        if(service.frameworkFailed || !service.roots.isEmpty() || root && !RootClient.last.active.isEmpty())
            throw new AssertionError("Incomplete physical release");
        return trace;
    }

    static void verifyRun(Chart chart,boolean root,long start,long end,int expected) throws Exception {
        Trace trace=play(chart,root,start,end);
        int notes=0,drags=0,flicks=0,misses=0;
        for(Note n:chart.getNotes()){
            long hit=Math.round(n.getTimeSeconds()*1000);
            if(hit<start+40 || hit>end-40)continue;
            notes++;
            if(n.getType()!=2 && n.getType()!=4)throw new AssertionError("Unexpected chart revision");
            boolean caught=false,moved=false;Sample first=null;
            for(Sample s:trace.samples){
                if(Math.abs(s.time-hit)>35)continue;
                double t=s.time/1000.0;
                Point center=chart.position(n,t,VIEW,false),normal=chart.judgeNormal(n,t,VIEW);
                double lateral=Math.abs((s.x-center.getX())*normal.getY()-(s.y-center.getY())*normal.getX());
                if(lateral>Math.min(VIEW.getWidth()*.098,VIEW.getHeight()*.20)*Math.hypot(normal.getX(),normal.getY())+1)continue;
                caught=true;
                if(first==null)first=s;
                if(s.time!=first.time && Math.hypot(s.x-first.x,s.y-first.y)>=VIEW.getHeight()*.035)moved=true;
            }
            if(n.getType()==2)drags++;else flicks++;
            if(!caught || n.getType()==4 && !moved)misses++;
        }
        System.out.println("root="+root+" peak="+trace.peak()+" newDowns="+trace.newDowns+" yellows="+drags+" reds="+flicks+" missed="+misses);
        if(trace.peak()!=1 || trace.newDowns!=0 || notes!=expected || misses!=0)throw new AssertionError("Single-finger coverage failed");
    }

    static void pulse(boolean root) throws Exception {
        double on=.50025,off=.50075;
        Chart chart=ContactRegression.chart(new JSONArray().put(ContactRegression.rect(.02,-1,.49,2,on,off)),
            Arrays.asList(ContactRegression.n(0,3,.18,.80,.25,0),ContactRegression.n(1,3,.18,.80,.75,0)));
        // The pulse lies strictly between ordinary 4 ms probes.
        Point inside=new Point(250,150);
        if(TapMod.clearAt(chart,VIEW,inside,.492))throw new AssertionError("Sub-millisecond activation missed");
        // 1 finger at the activation, 2 before and after. Use transitions rather
        // than just counting MOVE samples (there may be none during the pulse).
        Trace trace=play(chart,root,440,570);
        trace.changes.sort(Comparator.comparingLong(e->e[0]));
        int held=0,before=0,during=0,after=0;
        for(long[] e:trace.changes){
            held+=e[1];
            if(e[0]<=450)before=held;
            if(e[0]<=500)during=held;
            if(e[0]<=555)after=held;
        }
        for(Sample s:trace.samples)if(s.time>=499 && s.time<=501 && s.x<490)throw new AssertionError("Held inside pulse");
        if(before!=2 || during!=1 || after!=2)throw new AssertionError("Noise handoff "+root+" "+before+"/"+during+"/"+after);
        System.out.println("root="+root+" short noise activation="+before+"/"+during+"/"+after);
    }

    public static void main(String[] args) throws Exception {
        ContactRegression.clear();AppSettings.edges=true;AppSettings.values.put("tapmod_enabled","false");
        List<Note> braid=new ArrayList<>();
        braid.add(ContactRegression.n(0,3,.20,.32,.15,0));
        braid.add(ContactRegression.n(1,3,.20,.32,.85,0));
        double[] lanes={.16,.48,.84,.18,.80,.54,.19,.81};
        for(int i=0;i<lanes.length;i++){
            double t=.5+i*.107;
            braid.add(ContactRegression.n(i+2,i%4==3?4:2,t,t,lanes[i],i%2));
        }
        Chart serial=ContactRegression.chart(new JSONArray(),braid);
        for(boolean root:new boolean[]{true,false})verifyRun(serial,root,450,1300,lanes.length);
        if(args.length>0){
            Chart chart;
            try(FileInputStream in=new FileInputStream(args[0])){
                chart=SharedChartImport.parseJson(PhiraImportDecoder.INSTANCE.decode(in,"lost.pez").getNormalizedJson());
            }
            for(String random:new String[]{"false","true"}){
                AppSettings.values.put("tapmod_enabled",random);
                for(boolean root:new boolean[]{true,false})verifyRun(chart,root,47950,51500,33);
            }
        }
        AppSettings.values.put("tapmod_enabled","false");TapMod.reload();
        for(boolean root:new boolean[]{true,false})pulse(root);
    }
}
