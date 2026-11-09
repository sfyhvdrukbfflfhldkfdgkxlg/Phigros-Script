package io.github.phiscript.root;

import android.content.Context;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Each frame is acknowledged, so queued events cannot accumulate after stop. */
public final class RootClient implements AutoCloseable {
    private final Process process;
    private final DataOutputStream out;
    private final LinkedBlockingQueue<Integer> replies=new LinkedBlockingQueue<>(16);
    private final Thread reader;
    private final Thread errors;
    private final StringBuilder diagnostics=new StringBuilder();
    private boolean closed;
    public RootClient(Context context,int width,int height)throws Exception{
        process=RootProcess.start(context,"input");out=new DataOutputStream(process.getOutputStream());
        errors=new Thread(()->{byte[] bytes=new byte[1024];try{int n;while((n=process.getErrorStream().read(bytes))!=-1){
            synchronized(diagnostics){diagnostics.append(new String(bytes,0,n,"UTF-8"));if(diagnostics.length()>4096)diagnostics.delete(0,diagnostics.length()-4096);}
        }}catch(Exception ignored){}},"phi-root-diagnostics");errors.setDaemon(true);errors.start();
        reader=new Thread(()->{try{DataInputStream in=new DataInputStream(process.getInputStream());
            replies.put(in.readInt());int value;while((value=in.read())!=-1)replies.put(value);
        }catch(Exception ignored){}finally{replies.offer(-1);}},"phi-root-ack");reader.setDaemon(true);reader.start();
        try{
            Integer hello=replies.poll(45,TimeUnit.SECONDS);
            if(hello==null||hello!=RootDaemon.MAGIC){String detail; synchronized(diagnostics){detail=diagnostics.toString().trim();}
                throw new IllegalStateException("Root 触控启动失败，请检查 su 授权及系统兼容性"+(detail.isEmpty()?"":"\n"+detail));}
            out.writeInt(width);out.writeInt(height);out.flush();
        }catch(Exception e){close();throw e;}
    }
    public synchronized void frame(List<RootTouchState.Sample> samples)throws Exception{
        if(closed)throw new IllegalStateException("Root 通道已关闭");
        if(samples.size()>10)throw new IllegalArgumentException("最多支持 10 个独立触点");
        out.writeInt(samples.size());
        for(RootTouchState.Sample s:samples){out.writeLong(s.key);out.writeFloat(s.x);out.writeFloat(s.y);}
        out.flush();Integer ack=replies.poll(900,TimeUnit.MILLISECONDS);
        if(ack==null||ack!=1)throw new IllegalStateException("Root 触控未确认，已停止；守护进程将释放触点");
    }
    @Override public synchronized void close(){
        if(closed)return;closed=true;
        // EOF reaches the worker's finally block. Its independent watchdog is
        // retained if this process vanishes before that cleanup can execute.
        try{out.writeInt(-1);out.flush();out.close();}catch(Exception ignored){}
        boolean interrupted=Thread.interrupted();
        try{
            long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(500);boolean exited=false;
            while(!exited&&System.nanoTime()<deadline){
                try{exited=process.waitFor(50,TimeUnit.MILLISECONDS);}catch(InterruptedException e){interrupted=true;}
            }
            if(!exited)process.destroy();
        }finally{if(interrupted)Thread.currentThread().interrupt();}
        reader.interrupt();
        errors.interrupt();
    }
}
