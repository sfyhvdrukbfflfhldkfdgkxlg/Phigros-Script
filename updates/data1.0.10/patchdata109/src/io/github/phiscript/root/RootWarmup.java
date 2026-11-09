package io.github.phiscript.root;

import android.content.Context;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps an idle private input channel alive during OCR; transfers ownership before any chart DOWN. */
public final class RootWarmup implements AutoCloseable {
    private final RootClient client;
    private final AtomicBoolean waiting=new AtomicBoolean(true);
    private final Object lock=new Object();
    private final Thread keepalive;
    private volatile Exception failure;
    private boolean handedOff;
    private final int width,height;
    public RootWarmup(Context context,int width,int height)throws Exception{
        this.width=width;this.height=height;client=new RootClient(context,width,height);
        keepalive=new Thread(()->{
            try{while(waiting.get()){
                synchronized(lock){if(waiting.get())client.frame(new ArrayList<RootTouchState.Sample>());}
                Thread.sleep(100);
            }}catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception e){failure=e;waiting.set(false);}
        },"phi-root-auto-warm");keepalive.setDaemon(true);keepalive.start();
    }
    public long tap(float x,float y,int count,int interval,AtomicBoolean stop)throws Exception{
        if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1||count<1||count>2)throw new IllegalArgumentException("菜单点击参数无效");
        synchronized(lock){
            check();
            for(int i=0;i<count;i++){
                if(stop.get()||Thread.currentThread().isInterrupted())throw new InterruptedException();
                List<RootTouchState.Sample> down=new ArrayList<>();down.add(new RootTouchState.Sample(-100,x*Math.max(1,width-1),y*Math.max(1,height-1)));
                client.frame(down);
                try{Thread.sleep(55);}finally{client.frame(new ArrayList<RootTouchState.Sample>());}
                if(i+1<count)Thread.sleep(Math.max(80,Math.min(300,interval)));
            }
            return SystemClock.uptimeMillis();
        }
    }
    public void check()throws Exception{if(failure!=null)throw new IllegalStateException("Root 预连接失败",failure);}
    public RootClient take()throws Exception{
        check();synchronized(lock){waiting.set(false);}keepalive.interrupt();keepalive.join(1200);
        if(keepalive.isAlive())throw new IllegalStateException("Root 预连接尚未释放，已取消演奏");
        check();handedOff=true;return client;
    }
    public void close(){waiting.set(false);keepalive.interrupt();if(!handedOff)client.close();}
}
