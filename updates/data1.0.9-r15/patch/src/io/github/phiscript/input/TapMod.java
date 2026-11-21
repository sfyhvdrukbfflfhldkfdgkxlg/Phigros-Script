package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;
import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import java.io.*;
import java.security.SecureRandom;

/** Keep each note's prepared random offset stable across hold samples. */
public final class TapMod {
    static final ThreadLocal<Boolean> planning = new ThreadLocal<>();
    static final ThreadLocal<java.util.concurrent.atomic.AtomicBoolean> preparationStop=new ThreadLocal<>();
    static final ThreadLocal<long[]> planningQueryCounts=new ThreadLocal<>();
    static void checkPlanningCancelled(){java.util.concurrent.atomic.AtomicBoolean stop=preparationStop.get();if(Thread.currentThread().isInterrupted()||stop!=null&&stop.get())throw new java.util.concurrent.CancellationException(tr("谱面预计算已取消"));}
    // Include the dispatch-to-input delay when a moving noise edge reaches a touch.
    private static final class Guard {
        final int backMs,aheadMs;final double back,ahead;final double[] shifts;
        Guard(int extra){
            backMs=extra==0?4:extra;aheadMs=extra==0?12:extra;back=backMs/1000.0;ahead=aheadMs/1000.0;
            if(extra==0)shifts=new double[]{0,.004,.008,.012,-.004};
            else{shifts=new double[(backMs+aheadMs)/4+1];for(int i=0;i<shifts.length;i++)shifts[i]=(-backMs+4*i)/1000.0;}
        }
    }
    private static volatile Guard guard=new Guard(0);
    /** Current session's timing uncertainty, in chart milliseconds. */
    public static int noiseLookBackMs(){return guard.backMs;}
    public static int noiseLookAheadMs(){return guard.aheadMs;}
    private static final long SEARCH_BUDGET_MS=8;
    private static final ThreadLocal<Boolean> runtimeSearch=new ThreadLocal<>();
    private static final java.util.concurrent.atomic.AtomicLong searchTimeouts=new java.util.concurrent.atomic.AtomicLong();
    public static long searchTimeouts(){return searchTimeouts.get();}
    static class NoSafePoint extends IllegalStateException {
        NoSafePoint(String message) { super(message); }
    }
    private static final class DeadlineExceeded extends NoSafePoint {
        DeadlineExceeded(){super(tr("噪域安全检查超时，先释放此触点并等待下一次重试"));}
    }
    private static Context app;
    private static volatile boolean enabled=true,noiseAvoid=true;
    private static volatile float vertical=35,jitter=6,escape=2000;
    private static volatile ContactVariation variation=new ContactVariation(6,3f);
    private static final Map<Chart,Long> salts=Collections.synchronizedMap(new WeakHashMap<Chart,Long>());
    private static final SecureRandom random=new SecureRandom();
    public static void init(Context c){if(c!=null)app=c.getApplicationContext();reload();}
    public static synchronized void reload(){
        Properties f=new Properties();try(InputStream in=new FileInputStream("/sdcard/PhigrosScript/tapmod.conf")){f.load(in);}catch(Exception ignored){}
        enabled=bool(raw(f,"tapmod_enabled","true"));noiseAvoid=bool(raw(f,"tap_noise_avoid","true"));
        vertical=value(raw(f,"tap_vertical_permille","35"),35,0,200);
        jitter=value(raw(f,"tap_jitter_permille","6"),6,0,20);
        int delay=Math.round(value(raw(f,"tap_random_delay_ms","6"),6,0,20));
        float drift=value(raw(f,"tap_screen_drift_permille","3"),3,0,12);
        variation=enabled?new ContactVariation(delay,drift):ContactVariation.OFF;
        escape=value(raw(f,"tap_noise_escape_permille","2000"),2000,0,2000);
        String clock=raw(f,"tap_noise_clock_guard_ms","0");guard=new Guard("16".equals(clock)?16:"24".equals(clock)?24:0);
    }
    private static String raw(Properties f,String k,String d){String v=app==null?"":new AppSettings(app).text(k,"");return v.isEmpty()?f.getProperty(k,d):v;}
    private static boolean bool(String s){return "true".equalsIgnoreCase(s)||"1".equals(s)||"yes".equalsIgnoreCase(s)||"on".equalsIgnoreCase(s);}
    private static float value(String s,float d,float lo,float hi){try{float v=Float.parseFloat(s);return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):d;}catch(Exception e){return d;}}
    public static void begin(Chart c){reload();salts.put(c,random.nextLong());searchTimeouts.set(0);}
    /** Exact preparation inputs; changing the safe-gap algorithm invalidates old paths. */
    static ContactVariation contactVariation(){return variation;}
    static String baseSignature(){return "gesture-variation-v4|"+enabled+"|"+noiseAvoid+"|"+Float.toHexString(vertical)+"|"+Float.toHexString(jitter)+"|"+Float.toHexString(escape)+"|"+guard.backMs+"|"+guard.aheadMs+"|"+preparationEdgeGuard()+"|"+preparationInwardFlick();}
    public static String preparationSignature(){return baseSignature()+"|"+variation.maxDelay+"|"+Float.toHexString(variation.maxDrift);}
    public static boolean preparationEdgeGuard(){return app==null||new AppSettings(app).flag("gesture_edge_guard",true);}
    public static boolean preparationInwardFlick(){return app==null||new AppSettings(app).flag("gesture_flick_inward",true);}
    public static long preparationSalt(Chart c){synchronized(salts){Long salt=salts.get(c);if(salt==null){salt=random.nextLong();salts.put(c,salt);}return salt;}}
    public static void preparationSalt(Chart c,long salt){synchronized(salts){salts.put(c,salt);}}
    private static long mix(long x){x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    private static double unit(long x){return (mix(x)>>>11)*0x1.0p-53*2-1;}
    private static long seed(Chart c,Note n){synchronized(salts){Long s=salts.get(c);if(s==null){s=random.nextLong();salts.put(c,s);}return s^Double.doubleToLongBits(n.getTimeSeconds())^mix(Double.doubleToLongBits(n.getPositionX()))^((long)n.getLineIndex()<<17)^(n.getAbove()?31:63);}}
    private static Point normal(Chart c,Note n,double t,Viewport v){Point p=c.judgeNormal(n,t,v);double m=Math.hypot(p.getX(),p.getY());if(!Double.isFinite(m)||m<1e-9)throw new IllegalStateException(tr("判定线方向无效"));return new Point((float)(p.getX()/m),(float)(p.getY()/m));}
    public static boolean avoids(Chart c){return noiseAvoid&&BlockAreaSet.count(c)>0;}
    private static boolean blocked(Chart c,Viewport v,Point p,double t){return BlockAreaSet.touchesNoise(c,v,p,t);}
    public static Point adjustNote(Chart c,Note n,double t,Viewport v,boolean visual,Point p){
        if(visual||p==null)return p;
        if(!enabled)return avoid(c,n,t,v,p);
        if(!Float.isFinite(p.getX())||!Float.isFinite(p.getY())||v.getWidth()<=2||v.getHeight()<=2)throw new IllegalArgumentException(tr("无效点击坐标"));
        Point dir=normal(c,n,t,v);long seed=seed(c,n);float amount=v.getHeight()*vertical/1000,amp=v.getHeight()*jitter/1000;
        float shift=amount+(float)unit(seed)*amp;
        float dx=dir.getX()*shift,dy=dir.getY()*shift,factor=1;
        if(dx>0)factor=Math.min(factor,(v.getRight()-1-p.getX())/dx);
        if(dx<0)factor=Math.min(factor,(v.getLeft()+1-p.getX())/dx);
        if(dy>0)factor=Math.min(factor,(v.getBottom()-1-p.getY())/dy);
        if(dy<0)factor=Math.min(factor,(v.getTop()+1-p.getY())/dy);
        factor=Math.max(0,factor);
        return avoid(c,n,t,v,new Point(p.getX()+dx*factor,p.getY()+dy*factor));
    }
    public static Point avoid(Chart c,Note n,double t,Viewport v,Point p){
        if(Boolean.TRUE.equals(planning.get()) || clearAt(c,v,p,t))return p;
        return withinBounds(c,n,t,v,p,new TouchBounds(v.getLeft(),v.getTop(),Math.nextDown(v.getRight()),Math.nextDown(v.getBottom())));
    }
    static boolean clearAt(Chart c,Viewport v,Point p,double t) {
        return clearAt(c,v,p,t,guard,Long.MAX_VALUE);
    }
    static boolean clearAt(Chart c,Viewport v,Point p,double t,long deadlineNanos) {
        return clearAt(c,v,p,t,guard,deadlineNanos);
    }
    private static boolean clearAt(Chart c,Viewport v,Point p,double t,Guard timing,long deadline) {
        long[] counts=planningQueryCounts.get();if(counts!=null)counts[0]++;
        checkDeadline(deadline);
        if(!avoids(c))return true;
        Point rounded=new Point(Math.round(p.getX()),Math.round(p.getY()));
        for(double shift:timing.shifts){
            checkDeadline(deadline);boolean first=blocked(c,v,p,t+shift);checkDeadline(deadline);if(first)return false;
            boolean second=blocked(c,v,rounded,t+shift);checkDeadline(deadline);if(second)return false;
        }
        boolean blocked=BlockAreaSet.blockedAtChanges(c,v,p,rounded,t-timing.back,t+timing.ahead);checkDeadline(deadline);if(blocked)return false;
        blocked=BlockAreaSet.crossesTranslation(c,v,p,p,t-timing.back,t+timing.ahead);checkDeadline(deadline);if(blocked)return false;
        blocked=BlockAreaSet.crossesTranslation(c,v,rounded,rounded,t-timing.back,t+timing.ahead);checkDeadline(deadline);return !blocked;
    }
    static Point withinBounds(Chart c,Note n,double time,Viewport v,Point p,TouchBounds bounds) {
        return withinBounds(c,n,time,v,p,bounds,null,0);
    }
    static Point runtimeWithinBounds(Chart c,Note n,double time,Viewport v,Point p,TouchBounds bounds,Point previous,long previousMs){
        Boolean old=runtimeSearch.get();runtimeSearch.set(true);
        try{return withinBounds(c,n,time,v,p,bounds,previous,previousMs);}
        catch(DeadlineExceeded exceeded){searchTimeouts.incrementAndGet();throw exceeded;}
        finally{if(old==null)runtimeSearch.remove();else runtimeSearch.set(old);}
    }
    /** Prefer a reachable point in the same judgement band before releasing a held finger. */
    static Point withinBounds(Chart c,Note n,double time,Viewport v,Point p,TouchBounds bounds,Point previous,long previousMs) {
        Guard timing=guard;
        // Preflight may search exhaustively before the music clock starts.
        // Runtime search must yield to the dispatch loop so a held pointer is
        // lifted rather than left alive while an expensive search misses its tail.
        long deadline=avoids(c)&&Boolean.TRUE.equals(runtimeSearch.get())&&!Boolean.TRUE.equals(planning.get())?System.nanoTime()+SEARCH_BUDGET_MS*1000000L:Long.MAX_VALUE;
        Point start=new Point(bounds.x(p.getX()),bounds.y(p.getY()));
        if(clearAt(c,v,start,time,timing,deadline)&&connects(c,v,previous,start,previousMs,time,deadline))
            return start;
        Point d=normal(c,n,time,v);
        float nx=d.getX(),ny=d.getY();
        double max=v.getHeight()*escape/1000.0, radius=ContactPlanner.radius(v);
        double clipping=(start.getX()-p.getX())*ny-(start.getY()-p.getY())*nx;
        Point best=null;double nearest=Double.POSITIVE_INFINITY;
        for(int lane=0;lane<=24;lane++) {
            checkSearchBudget(deadline);
            double shift=lane==0?0:((lane+1)/2)*(radius/12)*(lane%2==0?-1:1);
            if(Math.abs(shift+clipping)>radius+.01 || Math.abs(shift)>max)continue;
            Point origin=new Point((float)(start.getX()+shift*ny),(float)(start.getY()-shift*nx));
            double reach=Math.sqrt(Math.max(0,max*max-shift*shift));
            double[] span={-reach,reach};
            if(!BlockAreaSet.clip(origin.getX(),nx,bounds.left,bounds.right,span) ||
               !BlockAreaSet.clip(origin.getY(),ny,bounds.top,bounds.bottom,span))continue;
            ArrayList<Double> edges=new ArrayList<>();
            edges.add(span[0]);edges.add(span[1]);edges.add(Math.max(span[0],Math.min(0,span[1])));
            for(double shiftTime:timing.shifts){checkDeadline(deadline);BlockAreaSet.cuts(c,v,origin,nx,ny,time+shiftTime,span[0],span[1],edges);}
            checkDeadline(deadline);
            BlockAreaSet.cutsAtChanges(c,v,origin,nx,ny,time-timing.back,time+timing.ahead,span[0],span[1],edges);
            checkDeadline(deadline);
            Collections.sort(edges);
            for(int i=1;i<edges.size();i++){
                checkSearchBudget(deadline);
                double lo=edges.get(i-1),hi=edges.get(i);
                if(hi-lo<.001)continue;
                // Follow the original gesture with the smallest displacement on this lane.
                // The 2 px inset accommodates Android's integer touch coordinates.
                double inset=Math.min(2,(hi-lo)*.5);
                double k=Math.max(lo+inset,Math.min(0,hi-inset));
                double dist=k*k+shift*shift;
                if(dist>=nearest)continue;
                Point point=at(bounds,origin,nx,ny,k);
                if(clearAt(c,v,point,time,timing,deadline)&&connects(c,v,previous,point,previousMs,time,deadline)){
                    best=point;nearest=dist;
                }else if(previous!=null){
                    // A moving edge may obstruct the nearest endpoint during the approach.
                    // Try the previous finger's projection into this same interval.
                    k=Math.max(lo+inset,Math.min((previous.getX()-origin.getX())*nx+(previous.getY()-origin.getY())*ny,hi-inset));
                    dist=k*k+shift*shift;
                    if(dist>=nearest)continue;
                    point=at(bounds,origin,nx,ny,k);
                    if(clearAt(c,v,point,time,timing,deadline)&&connects(c,v,previous,point,previousMs,time,deadline)){
                        best=point;nearest=dist;
                    }
                }
            }
            if(lane==0&&best!=null)return best;
        }
        if(best!=null)return best;
        throw new NoSafePoint(tr("噪域内找不到可触控的同判定带落点"));
    }
    private static Point at(TouchBounds bounds,Point origin,float nx,float ny,double offset){return new Point(bounds.x((float)(origin.getX()+offset*nx)),bounds.y((float)(origin.getY()+offset*ny)));}
    private static void checkSearchBudget(long deadline){
        checkDeadline(deadline);
    }
    private static void checkDeadline(long deadline){
        checkPlanningCancelled();
        if(deadline!=Long.MAX_VALUE&&System.nanoTime()>=deadline)throw new DeadlineExceeded();
    }
    private static boolean connects(Chart c,Viewport v,Point from,Point to,long fromMs,double seconds,long deadline){
        if(from==null||!avoids(c))return true;
        try{segment(c,v,from,to,fromMs,Math.round(seconds*1000),deadline);return true;}
        catch(DeadlineExceeded exceeded){throw exceeded;}
        catch(NoSafePoint blocked){return false;}
    }
    public static void segment(Chart c,Viewport v,Point a,Point b,long from,long to){
        segment(c,v,a,b,from,to,Long.MAX_VALUE);
    }
    /** Cooperative deadline between geometry queries and samples. One
     * BlockAreaSet primitive itself cannot be interrupted mid-query. */
    public static void segment(Chart c,Viewport v,Point a,Point b,long from,long to,long deadline){
        long[] counts=planningQueryCounts.get();if(counts!=null)counts[1]++;
        checkDeadline(deadline);
        Guard timing=guard;
        if(!avoids(c)||!BlockAreaSet.overlaps(c,from-timing.backMs,to+timing.aheadMs))return;
        for(double shift:timing.shifts){checkDeadline(deadline);boolean blocked=BlockAreaSet.crossesTranslation(c,v,a,b,from/1000.0+shift,to/1000.0+shift);checkDeadline(deadline);if(blocked)throw new NoSafePoint(tr("移动噪域将扫过连续触点，应在此抬指"));}
        double dx=b.getX()-a.getX(),dy=b.getY()-a.getY(),distance=Math.hypot(dx,dy);
        int slices=(int)Math.min(4096,Math.max(1,Math.ceil(Math.abs(to-from)/4.0)));
        // Test intersections analytically as well as sampled pixels. A sub-pixel-wide
        // static strip can lie entirely between consecutive touch samples.
        if(distance>.001)for(int k=0;k<slices;k++){
            checkDeadline(deadline);
            double lo=k/(double)slices,hi=(k+1)/(double)slices;
            double time=(from+(to-from)*(lo+hi)*.5)/1000.0;
            ArrayList<Double> edges=new ArrayList<>();
            BlockAreaSet.cuts(c,v,a,(float)dx,(float)dy,time,lo,hi,edges);
            checkDeadline(deadline);
            BlockAreaSet.cutsAtChanges(c,v,a,(float)dx,(float)dy,
                (from+(to-from)*lo)/1000.0-timing.back,(from+(to-from)*hi)/1000.0+timing.ahead,lo,hi,edges);
            checkDeadline(deadline);
            if(edges.isEmpty())continue;
            edges.add(lo);edges.add(hi);Collections.sort(edges);
            for(int j=1;j<edges.size();j++){
                checkDeadline(deadline);
                double f=(edges.get(j-1)+edges.get(j))*.5;
                Point q=new Point((float)(a.getX()+dx*f),(float)(a.getY()+dy*f));
                if(!clearAt(c,v,q,(from+(to-from)*f)/1000.0,timing,deadline))throw new NoSafePoint(tr("连续手势穿过噪域"));
            }
        }
        int count=(int)Math.min(16384,Math.max(1,Math.max(Math.ceil(Math.hypot(b.getX()-a.getX(),b.getY()-a.getY())/2),Math.ceil(Math.abs(to-from)/2.0))));
        for(int i=0;i<=count;i++){double f=i/(double)count;Point p=new Point((float)(a.getX()+(b.getX()-a.getX())*f),(float)(a.getY()+(b.getY()-a.getY())*f));
            checkDeadline(deadline);if(!clearAt(c,v,p,(from+(to-from)*f)/1000,timing,deadline))throw new NoSafePoint(tr("此段连续手势经过噪域，应在此抬指"));}
    }
}
