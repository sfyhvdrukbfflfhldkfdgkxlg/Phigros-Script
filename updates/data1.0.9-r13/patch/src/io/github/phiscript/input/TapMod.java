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
    private static volatile float vertical=35,jitter=6,escape=320;
    private static final Map<Chart,Long> salts=Collections.synchronizedMap(new WeakHashMap<Chart,Long>());
    private static final SecureRandom random=new SecureRandom();
    public static void init(Context c){if(c!=null)app=c.getApplicationContext();reload();}
    public static synchronized void reload(){
        Properties f=new Properties();try(InputStream in=new FileInputStream("/sdcard/PhigrosScript/tapmod.conf")){f.load(in);}catch(Exception ignored){}
        enabled=bool(raw(f,"tapmod_enabled","true"));noiseAvoid=bool(raw(f,"tap_noise_avoid","true"));
        vertical=value(raw(f,"tap_vertical_permille","35"),35,0,200);
        jitter=value(raw(f,"tap_jitter_permille","6"),6,0,20);
        escape=value(raw(f,"tap_noise_escape_permille","320"),320,0,2000);
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
        if(Boolean.TRUE.equals(planning.get())||!avoids(c)||!blocked(c,v,p,t))return p;
        Point dir=normal(c,n,t,v);float max=v.getHeight()*escape/1000,step=Math.max(1,v.getHeight()*.002f);
        for(float d=step;d<=max+step;d+=step)for(int sign:new int[]{1,-1}){
            float delta=Math.min(d,max)*sign,x=p.getX()+dir.getX()*delta,y=p.getY()+dir.getY()*delta;
            if(x<v.getLeft()+1||x>v.getRight()-1||y<v.getTop()+1||y>v.getBottom()-1)continue;
            Point q=new Point(x,y);if(!blocked(c,v,q,t))return q;
        }
        throw new NoSafePoint("噪域内暂时没有同判定线投影的安全落点");
    }
    public static void segment(Chart c,Viewport v,Point a,Point b,long from,long to){
        if(!BlockAreaSet.overlaps(c,from,to))return;
        int count=(int)Math.min(16384,Math.max(1,Math.max(Math.ceil(Math.hypot(b.getX()-a.getX(),b.getY()-a.getY())/2),Math.ceil(Math.abs(to-from)/2.0))));
        for(int i=0;i<=count;i++){double f=i/(double)count;Point p=new Point((float)(a.getX()+(b.getX()-a.getX())*f),(float)(a.getY()+(b.getY()-a.getY())*f));
            if(blocked(c,v,p,(from+(to-from)*f)/1000))throw new NoSafePoint("此段连续手势经过噪域，应在此抬指");}
    }
}
