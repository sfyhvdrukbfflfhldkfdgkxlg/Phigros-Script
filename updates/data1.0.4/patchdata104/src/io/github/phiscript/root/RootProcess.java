package io.github.phiscript.root;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** su is requested only by explicit UI actions or an explicitly selected backend. */
public final class RootProcess {
    private RootProcess(){}
    public static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    public static Process start(Context context,String...args) throws Exception {
        String command="CLASSPATH="+quote(context.getApplicationInfo().sourceDir)
            +" /system/bin/app_process /system/bin io.github.phiscript.root.RootDaemon";
        for(String arg:args) command+=" "+quote(arg);
        return new ProcessBuilder("su","-c",command).start();
    }
    public static byte[] capture(Context context,int limit,String...args) throws Exception {
        Process p=start(context,args);
        AtomicReference<byte[]> result=new AtomicReference<>();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread reader=new Thread(()->{try{result.set(bounded(p.getInputStream(),limit));}
            catch(Throwable e){failure.set(e);p.destroy();}},"phi-root-read");
        Thread errors=drain(p.getErrorStream());reader.start();
        try {
            if(!p.waitFor(45,TimeUnit.SECONDS)) throw new IllegalStateException("Root 请求超时，请在 Root 管理器中确认授权");
            reader.join(2000);
            if(reader.isAlive()||failure.get()!=null||p.exitValue()!=0||result.get()==null)
                throw new IllegalStateException("Root 读取失败或未授权；未修改游戏文件",failure.get());
            return result.get();
        } finally {p.destroy();reader.interrupt();errors.interrupt();}
    }
    public static Thread drain(InputStream in){
        Thread t=new Thread(()->{byte[] b=new byte[1024];try{while(in.read(b)!=-1){}}
            catch(Exception ignored){}},"phi-root-stderr");t.setDaemon(true);t.start();return t;
    }
    public static byte[] bounded(InputStream in,int limit) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;
        while((n=in.read(b))!=-1){if(n>limit-out.size())throw new IllegalArgumentException("Root 资源超过读取限额");out.write(b,0,n);}
        return out.toByteArray();
    }
}
