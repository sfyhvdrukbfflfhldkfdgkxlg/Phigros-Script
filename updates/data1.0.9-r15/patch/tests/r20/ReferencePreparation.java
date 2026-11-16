package io.github.phiscript.input;

import io.github.phiscript.engine.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dense, physical-pixel trajectories. Invalid samples and unsafe connections
 * are explicit; lookup never evaluates a chart or searches a noise region. */
public final class ReferencePreparation {
    public static final int VERSION=1,STEP_MS=4,MAX_SAMPLES=1500000,MAX_BYTES=40*1024*1024;
    private static final int MAGIC=0x50544731;
    final IdentityHashMap<TouchEvent,Track> tracks=new IdentityHashMap<>();
    final String settings;final long salt;final Viewport view;final TouchBounds bounds;
    final GestureProfile profile;
    private static final class Track {
        int[] time,broken,critical;float[] x,y;byte[] connected;boolean continuous,flick;
        void index(){broken=new int[time.length];for(int i=1;i<time.length;i++)broken[i]=broken[i-1]+(connected[i]==0?1:0);}
        int floor(long at){int low=0,high=time.length;while(low<high){int mid=(low+high)>>>1;if(time[mid]<=at)low=mid+1;else high=mid;}return Math.max(0,low-1);}
        Point at(long at,boolean previous,long previousAt){
            long clamped=Math.max(time[0],Math.min(time[time.length-1],at));int i=floor(clamped);
            if(!Float.isFinite(x[i])||!Float.isFinite(y[i]))throw new TapMod.NoSafePoint("预计算轨迹在此时刻没有安全落点");
            if(previous&&previousAt<clamped){int from=floor(Math.max(time[0],previousAt));int end=i+(clamped>time[i]?1:0);if(broken[end]!=broken[from])throw new TapMod.NoSafePoint("预计算轨迹需在此断开触点后恢复");}
            if(clamped==time[i]||i+1==time.length)return new Point(x[i],y[i]);
            if(connected[i+1]==0||!Float.isFinite(x[i+1]))throw new TapMod.NoSafePoint("不能跨越预计算轨迹中的噪域间隙");
            float f=(clamped-time[i])/(float)(time[i+1]-time[i]);return new Point(x[i]+(x[i+1]-x[i])*f,y[i]+(y[i+1]-y[i])*f);
        }
        long boundary(long now,long end){int lo=0,hi=critical.length;while(lo<hi){int mid=(lo+hi)>>>1;if(critical[mid]<=now)lo=mid+1;else hi=mid;}return lo==critical.length?end:Math.min(end,critical[lo]);}
    }
    private ReferencePreparation(GestureProfile p,Viewport v,TouchBounds b,String settings,long salt){profile=p;view=v;bounds=b;this.settings=settings;this.salt=salt;}
    boolean matches(Chart chart,GestureProfile p,Viewport v,TouchBounds b){return profile.equals(p)&&view.equals(v)&&same(bounds,b)&&settings.equals(TapMod.preparationSignature())&&salt==TapMod.preparationSalt(chart);}
    static boolean same(TouchBounds a,TouchBounds b){return a.left==b.left&&a.top==b.top&&a.right==b.right&&a.bottom==b.bottom;}
    Point point(TouchEvent event,long time,Point previous,long previousTime){Track t=tracks.get(event);if(t==null)throw new IllegalStateException("预计算轨迹与手势不匹配");return t.at(time,previous!=null,previousTime);}
    long boundary(TouchEvent e,long now,long end){Track t=tracks.get(e);return t==null?end:t.boundary(now,end);}
    boolean continuous(TouchEvent e){Track t=tracks.get(e);return t!=null&&t.continuous;}
    boolean flick(TouchEvent e){Track t=tracks.get(e);return t!=null&&t.flick;}
    public int sampleCount(){int n=0;for(Track t:tracks.values())n+=t.time.length;return n;}
    static ReferencePreparation prepare(Chart chart,GesturePlan plan,GestureProfile profile,Viewport view,TouchBounds bounds,AtomicBoolean cancel){
        ReferencePreparation out=new ReferencePreparation(profile,view,bounds,TapMod.preparationSignature(),TapMod.preparationSalt(chart));
        ReferenceTouchSafety safety=ReferenceTouchSafety.forPreparation(bounds);int[] screen={(int)Math.ceil(view.getRight()),(int)Math.ceil(view.getBottom())};int total=0;
        for(TouchEvent event:plan.events){
            cancelled(cancel);long start=event.getStartMs(),end=event.getEndMs();
            if(start<Integer.MIN_VALUE||end>Integer.MAX_VALUE||end<=start)throw new IllegalArgumentException("谱面轨迹时间超出预计算范围");
            long estimated=(end-start+STEP_MS-1)/STEP_MS+2;if(estimated>MAX_SAMPLES-total)throw new IllegalArgumentException("此谱预计算轨迹超过容量，请缩短谱面或减少持续触点");
            TreeSet<Integer> times=new TreeSet<>();times.add((int)start);times.add((int)end);
            for(long t=start+STEP_MS;t<end;t+=STEP_MS)times.add((int)t);
            TreeSet<Integer> critical=new TreeSet<>();critical.add((int)start);critical.add((int)end);
            ContactPlanner.Route route=plan.routes.get(event);
            if(route!=null)for(Long t:route.keys.keySet())if(t>=start&&t<=end){times.add(t.intValue());critical.add(t.intValue());}
            if(times.size()>MAX_SAMPLES-total)throw new IllegalArgumentException("此谱预计算轨迹超过容量");total+=times.size();
            Track track=new Track();track.continuous=plan.isContinuous(event);track.flick=plan.hasFlickPath(event);
            int n=times.size();track.time=new int[n];track.x=new float[n];track.y=new float[n];track.connected=new byte[n];
            int i=0;Point prior=null;long priorAt=start;
            for(int t:times){
                cancelled(cancel);Point p=null;boolean connected=false;
                try{p=safety.preparePoint(plan,chart,event,t,view,screen,new boolean[1],prior,priorAt);connected=prior!=null;}
                catch(TapMod.NoSafePoint blocked){
                    try{p=safety.preparePoint(plan,chart,event,t,view,screen,new boolean[1],null,t);}
                    catch(TapMod.NoSafePoint noPoint){/* Invalid samples remain explicit. */}
                }
                if(p!=null&&prior!=null&&connected)try{TapMod.segment(chart,view,prior,p,priorAt,t);}catch(TapMod.NoSafePoint crossing){connected=false;}
                track.time[i]=t;track.x[i]=p==null?Float.NaN:p.getX();track.y[i]=p==null?Float.NaN:p.getY();track.connected[i]=(byte)(connected?1:0);
                if(i>0&&(!connected||prior==null||p==null)){critical.add(track.time[i-1]);critical.add(t);}
                prior=p;priorAt=t;i++;
            }
            track.critical=new int[critical.size()];i=0;for(int t:critical)track.critical[i++]=t;track.index();out.tracks.put(event,track);
        }
        cancelled(cancel);return out;
    }
    private static void cancelled(AtomicBoolean cancel){if(Thread.currentThread().isInterrupted()||cancel!=null&&cancel.get())throw new IllegalStateException("谱面轨迹预计算已取消");}

}
