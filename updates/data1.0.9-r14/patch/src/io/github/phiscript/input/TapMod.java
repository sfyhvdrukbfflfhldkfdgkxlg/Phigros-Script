package io.github.phiscript.input;
import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import java.io.*;
import java.security.SecureRandom;

/** Randomize once per performance; keep each note's offset stable across hold samples. */
public final class TapMod {
    static final ThreadLocal<Boolean> planning = new ThreadLocal<>();
    static final class NoSafePoint extends IllegalStateException {
        NoSafePoint(String message) { super(message); }
    }
    private static Context app;
    private static volatile boolean enabled=true,noiseAvoid=true;
    private static volatile float vertical=35,jitter=6,escape=2000;
    private static final Map<Chart,Long> salts=Collections.synchronizedMap(new WeakHashMap<Chart,Long>());
    private static final SecureRandom random=new SecureRandom();
    public static void init(Context c){if(c!=null)app=c.getApplicationContext();reload();}
    public static synchronized void reload(){
        Properties f=new Properties();try(InputStream in=new FileInputStream("/sdcard/PhigrosScript/tapmod.conf")){f.load(in);}catch(Exception ignored){}
        enabled=bool(raw(f,"tapmod_enabled","true"));noiseAvoid=bool(raw(f,"tap_noise_avoid","true"));
        vertical=value(raw(f,"tap_vertical_permille","35"),35,0,200);
        jitter=value(raw(f,"tap_jitter_permille","6"),6,0,20);
        escape=value(raw(f,"tap_noise_escape_permille","2000"),2000,0,2000);
    }
    private static String raw(Properties f,String k,String d){String v=app==null?"":new AppSettings(app).text(k,"");return v.isEmpty()?f.getProperty(k,d):v;}
    private static boolean bool(String s){return "true".equalsIgnoreCase(s)||"1".equals(s)||"yes".equalsIgnoreCase(s)||"on".equalsIgnoreCase(s);}
    private static float value(String s,float d,float lo,float hi){try{float v=Float.parseFloat(s);return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):d;}catch(Exception e){return d;}}
    public static void begin(Chart c){reload();salts.put(c,random.nextLong());}
    private static long mix(long x){x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    private static double unit(long x){return (mix(x)>>>11)*0x1.0p-53*2-1;}
    private static long seed(Chart c,Note n){synchronized(salts){Long s=salts.get(c);if(s==null){s=random.nextLong();salts.put(c,s);}return s^Double.doubleToLongBits(n.getTimeSeconds())^mix(Double.doubleToLongBits(n.getPositionX()))^((long)n.getLineIndex()<<17)^(n.getAbove()?31:63);}}
    private static Point normal(Chart c,Note n,double t,Viewport v){Point p=c.judgeNormal(n,t,v);double m=Math.hypot(p.getX(),p.getY());if(!Double.isFinite(m)||m<1e-9)throw new IllegalStateException("判定线方向无效");return new Point((float)(p.getX()/m),(float)(p.getY()/m));}
    public static boolean avoids(Chart c){return noiseAvoid&&BlockAreaSet.count(c)>0;}
    private static boolean blocked(Chart c,Viewport v,Point p,double t){return BlockAreaSet.covers(c,p.getX(),p.getY(),v.getLeft(),v.getTop(),v.getWidth(),v.getHeight(),t);}
    public static Point adjustNote(Chart c,Note n,double t,Viewport v,boolean visual,Point p){
        if(visual||p==null)return p;
        if(!enabled)return avoid(c,n,t,v,p);
        if(!Float.isFinite(p.getX())||!Float.isFinite(p.getY())||v.getWidth()<=2||v.getHeight()<=2)throw new IllegalArgumentException("无效点击坐标");
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
        return !avoids(c) || (!blocked(c,v,p,t) && !blocked(c,v,new Point(Math.round(p.getX()),Math.round(p.getY())),t));
    }
    static Point withinBounds(Chart c,Note n,double time,Viewport v,Point p,TouchBounds bounds) {
        Point start=new Point(bounds.x(p.getX()),bounds.y(p.getY()));
        if(clearAt(c,v,start,time))return start;
        Point d=normal(c,n,time,v);
        float nx=d.getX(),ny=d.getY();
        double max=v.getHeight()*escape/1000.0, radius=ContactPlanner.radius(v);
        double clipping=(start.getX()-p.getX())*ny-(start.getY()-p.getY())*nx;
        Point best=null;double nearest=Double.POSITIVE_INFINITY;
        for(int lane=0;lane<=24;lane++) {
            double shift=lane==0?0:((lane+1)/2)*(radius/12)*(lane%2==0?-1:1);
            if(Math.abs(shift+clipping)>radius+.01 || Math.abs(shift)>max)continue;
            Point origin=new Point((float)(start.getX()+shift*ny),(float)(start.getY()-shift*nx));
            double reach=Math.sqrt(Math.max(0,max*max-shift*shift));
            double[] span={-reach,reach};
            if(!BlockAreaSet.clip(origin.getX(),nx,bounds.left,bounds.right,span) ||
               !BlockAreaSet.clip(origin.getY(),ny,bounds.top,bounds.bottom,span))continue;
            ArrayList<Double> edges=new ArrayList<>();
            edges.add(span[0]);edges.add(span[1]);edges.add(Math.max(span[0],Math.min(0,span[1])));
            BlockAreaSet.cuts(c,v,origin,nx,ny,time,span[0],span[1],edges);
            Collections.sort(edges);
            for(int i=1;i<edges.size();i++){
                double lo=edges.get(i-1),hi=edges.get(i),inset=Math.min(2,(hi-lo)*.5);
                for(int pass=0;pass<2;pass++){
                    double k=pass==0?Math.max(lo+inset,Math.min(0,hi-inset)):(lo+hi)*.5;
                    double dist=k*k+shift*shift;
                    if(dist>=nearest)continue;
                    Point point=new Point(bounds.x((float)(origin.getX()+k*nx)),bounds.y((float)(origin.getY()+k*ny)));
                    if(clearAt(c,v,point,time)){nearest=dist;best=point;}
                }
            }
            if(lane==0 && best!=null)return best;
        }
        if(best!=null)return best;
        throw new NoSafePoint("噪域内找不到可触控的同判定带落点");
    }
    public static void segment(Chart c,Viewport v,Point a,Point b,long from,long to){
        if(!avoids(c)||!BlockAreaSet.overlaps(c,from,to))return;
        int count=(int)Math.min(16384,Math.max(1,Math.max(Math.ceil(Math.hypot(b.getX()-a.getX(),b.getY()-a.getY())/2),Math.ceil(Math.abs(to-from)/2.0))));
        for(int i=0;i<=count;i++){double f=i/(double)count;Point p=new Point((float)(a.getX()+(b.getX()-a.getX())*f),(float)(a.getY()+(b.getY()-a.getY())*f));
            if(!clearAt(c,v,p,(from+(to-from)*f)/1000))throw new NoSafePoint("此段连续手势经过噪域，应在此抬指");}
    }
}
