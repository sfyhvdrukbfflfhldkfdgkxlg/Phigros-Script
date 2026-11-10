package io.github.phiscript.input;

import io.github.phiscript.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** One preparation/ownership path for Phira and Phigros. Root starts before the retry clock. */
public final class SharedChartPlayback {
    private static final Map<Object,Prepared> OWNERS=new IdentityHashMap<>();
    private static final Map<Chart,Prepared> CHARTS=new IdentityHashMap<>();
    private static final ThreadLocal<RootClient> BOUND=new ThreadLocal<>();
    private SharedChartPlayback(){}
    private static final class Prepared {
        final Chart chart;final TouchService service;final SessionSettings settings;final AtomicBoolean stop;final int width,height;final RootWarmup warm;
        Prepared(Chart c,TouchService s,SessionSettings settings,AtomicBoolean stop,int w,int h,Viewport view)throws Exception{
            chart=c;service=s;this.settings=settings;this.stop=stop;width=w;height=h;
            GesturePlan.preflight(c,s,view);warm=InputBackend.rootEnabled?new RootWarmup(s,w,h):null;
        }
        void close(){if(warm!=null)warm.close();}
    }
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    public static void preflight(Chart chart){GesturePlan.preflight(chart);}
    public static synchronized void prepare(Object owner,Object recognizer,Object selection,Chart chart)throws Exception{
        int w=(Integer)field(selection,"width"),h=(Integer)field(selection,"height");
        prepare(owner,recognizer,selection,chart,new Viewport(0,0,w,h));
    }
    public static synchronized void prepare(Object owner,Object recognizer,Object selection,Chart chart,Viewport view)throws Exception{
        close(owner);TouchService service=TouchService.Companion.getCurrent();if(service==null)throw new IllegalStateException("无障碍服务已断开");
        int width=(Integer)field(selection,"width"),height=(Integer)field(selection,"height");
        Prepared ready=new Prepared(chart,service,(SessionSettings)field(owner,"settings"),(AtomicBoolean)field(owner,"stop"),width,height,view);
        OWNERS.put(owner,ready);CHARTS.put(chart,ready);RuntimeState.INSTANCE.log("谱面手势与输入通道已预备，重试后直接演奏");
    }
    public static long control(Object owner,float x,float y,int count,Object selection)throws Exception{
        Prepared ready; synchronized(SharedChartPlayback.class){ready=OWNERS.get(owner);}
        if(ready==null)throw new IllegalStateException("谱面输入尚未预备");
        if(ready.stop.get()||!ready.service.getGameForeground())throw new IllegalStateException("游戏已离开前台");
        if(TouchService.Companion.getCurrent()!=ready.service)throw new IllegalStateException("无障碍服务已变化，请重新开始");
        if(ready.warm!=null)return ready.warm.tap(x,y,count,ready.settings.getDoubleTapIntervalMs(),ready.stop);
        return ready.service.tapNormalized(x,y,count,ready.settings.getDoubleTapIntervalMs(),ready.width,ready.height,ready.stop);
    }
    public static RootClient takeRoot(Chart chart)throws Exception{
        RootClient bound=BOUND.get();if(bound!=null){BOUND.remove();return bound;}
        Prepared ready;synchronized(SharedChartPlayback.class){ready=CHARTS.remove(chart);}
        return ready==null||ready.warm==null?null:ready.warm.take();
    }
    public static void play(TouchService service,Chart chart,long epoch,Viewport viewport,AtomicBoolean stop,Function1<? super String,Unit> status,RootClient prepared)throws Exception{
        if(BOUND.get()!=null)throw new IllegalStateException("已有输入通道绑定");
        if(prepared!=null)BOUND.set(prepared);
        try{service.play(chart,epoch,viewport,stop,status);}
        finally{BOUND.remove();if(prepared!=null)prepared.close();}
    }
    public static synchronized void close(Object owner){Prepared p=OWNERS.remove(owner);if(p!=null){if(CHARTS.get(p.chart)==p)CHARTS.remove(p.chart);p.close();}}
}
