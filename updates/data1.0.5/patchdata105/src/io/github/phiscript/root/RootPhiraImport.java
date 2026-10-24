package io.github.phiscript.root;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.assets.PhiraLibrary;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One cancellable import, without shell copying or root-owned staging files. */
public final class RootPhiraImport {
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static volatile Operation current;
    private RootPhiraImport(){}
    public static boolean isBusy(){return busy.get();}
    public static boolean isCancelling(){Operation op=current;return op!=null&&op.cancelled.get();}
    public static void start(Activity activity){
        if(RootPanel.blocked(activity)||!busy.compareAndSet(false,true))return;
        Operation operation=new Operation(activity);current=operation;
        operation.thread=new Thread(operation,"phi-phira-root-import");operation.thread.start();HomeScreen.refresh(activity);
    }
    public static void cancel(){
        Operation op=current;if(op==null||!op.cancelled.compareAndSet(false,true))return;
        RuntimeState.INSTANCE.log("正在取消 Phira 导入；已完成的谱面会保留");op.thread.interrupt();
        PhiraImportControl.cancel();op.destroyAsync();HomeScreen.refresh(op.activity);
    }
    private static final class Operation implements Runnable,PhiraImportPipe.Consumer {
        final Activity activity;final Context app;final AtomicBoolean cancelled=new AtomicBoolean(),finished=new AtomicBoolean(),ready=new AtomicBoolean(),cleanupStarted=new AtomicBoolean();
        volatile Process process;Thread thread;File workspace;PhiraLibrary library;
        final List<File> pending=new ArrayList<>();int imported,skipped;final StringBuilder errors=new StringBuilder();volatile String timeout;
        Operation(Activity activity){this.activity=activity;app=activity.getApplicationContext();library=RuntimeState.INSTANCE.getPhiraLibrary();}
        public void run(){
            String failure=null;Thread watchdog=null,stderr=null;int before=library==null?0:library.getEntries().size();
            try{
                workspace=new File(app.getCacheDir(),"phira-import-"+System.nanoTime());if(!workspace.mkdirs())throw new IOException("无法创建导入缓存");
                int user=android.os.Process.myUid()/100000;
                RuntimeState.INSTANCE.log("Root 正在读取当前安卓用户的 Phira 谱库；请完成 su 授权");
                process=RootProcess.start(app,"phira-export",Integer.toString(user));
                if(cancelled.get())throw new InterruptedException();
                stderr=new Thread(()->{byte[] buffer=new byte[1024];try{int n;while((n=process.getErrorStream().read(buffer))!=-1){synchronized(errors){errors.append(new String(buffer,0,n,"UTF-8"));if(errors.length()>4096)errors.delete(0,errors.length()-4096);}}}catch(Exception ignored){}},"phi-phira-import-errors");stderr.setDaemon(true);stderr.start();
                watchdog=new Thread(()->{
                    long began=System.nanoTime();
                    try{while(!finished.get()){
                        long seconds=(System.nanoTime()-began)/1000000000L;
                        if((!ready.get()&&seconds>45)||seconds>1200){timeout=ready.get()?"Phira 导入超过 20 分钟，已停止":"Root 授权超时，请在 Root 管理器中允许本应用";thread.interrupt();destroyAsync();return;}
                        Thread.sleep(500);
                    }}catch(InterruptedException e){Thread.currentThread().interrupt();}
                },"phi-phira-import-timeout");watchdog.setDaemon(true);watchdog.start();
                PhiraImportPipe.Result result=PhiraImportPipe.receive(new ImportPipeInput(process,cancelled,()->timeout),workspace,this);
                flush();
                if(!process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)||process.exitValue()!=0)throw new IOException("Root 谱库进程未正常完成");
                if(result.charts==0)throw new IOException(result.roots==0?"未找到当前安卓用户的 Phira 谱库目录；请先在官方 Phira 下载或导入谱面":"Phira 谱库里没有可读取谱面，请检查下载是否完成");
                if(imported==0)throw new IOException("已读取 "+result.charts+" 份，但均未能解析；请查看运行记录中的具体文件错误");
            }catch(OutOfMemoryError e){failure="导入内存不足，已保留完成的谱面；请缩小单份谱面后重试";}
            catch(Exception e){
                if(!cancelled.get()){
                    String diagnostic; synchronized(errors){diagnostic=errors.toString().trim();}
                    failure=timeout!=null?timeout:(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
                    if(!diagnostic.isEmpty())failure+="\nRoot 进程："+diagnostic;
                }
            }finally{
                finished.set(true);destroyAsync();if(watchdog!=null)watchdog.interrupt();if(stderr!=null)stderr.interrupt();
                delete(workspace);current=null;busy.set(false);
            }
            final String error=failure;final int added=library==null?0:Math.max(0,library.getEntries().size()-before);
            String summary=(cancelled.get()?"已取消；":"")+"成功处理 "+imported+" 份，新增 "+added+" 个谱面，跳过 "+skipped+" 份";
            if(error!=null)summary+="\n停止原因："+error;
            final String text=summary;RuntimeState.INSTANCE.log("Phira Root 导入："+text);
            activity.runOnUiThread(()->{
                if(!RootPanel.alive(activity))return;
                if(imported>0&&!RootPanel.isBusy()&&!RuntimeState.INSTANCE.getRunning().get()){new AppSettings(activity).save("phiraChartId","");PlayEntry.select(activity,1);}
                HomeScreen.refresh(activity);RootPanel.message(activity,error==null?"Phira 谱库导入结果":"Phira 导入未全部完成",text);
            });
        }
        @Override public void ready(){ready.set(true);RuntimeState.INSTANCE.log("Root 已确认，正在读取 Phira 已下载谱面");}
        @Override public void chart(File file,String label)throws Exception{
            if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedException();
            RuntimeState.INSTANCE.log("读取 Phira 谱面 "+(imported+skipped+pending.size()+1)+"："+shortName(label));pending.add(file);long bytes=0;for(File f:pending)bytes+=f.length();if(pending.size()>=16||bytes>=8L*1024*1024)flush();
        }
        @Override public void warning(String text){skipped++;RuntimeState.INSTANCE.log("Phira 读取跳过："+text);}
        private void flush()throws Exception{
            if(pending.isEmpty())return;if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedException();
            List<Uri> uris=new ArrayList<>();for(File f:pending)uris.add(Uri.fromFile(f));
            try{library=PlayEntry.importPhira(app,uris);imported+=pending.size();RuntimeState.INSTANCE.setPhiraLibrary(library);}
            catch(Exception batchFailure){
                for(File f:pending){
                    if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedException();
                    try{library=PlayEntry.importPhira(app,Collections.singletonList(Uri.fromFile(f)));RuntimeState.INSTANCE.setPhiraLibrary(library);imported++;}
                    catch(Exception invalid){skipped++;RuntimeState.INSTANCE.log("Phira 解析跳过："+invalid.getMessage());}
                }
            }finally{for(File f:pending)f.delete();pending.clear();}
            RuntimeState.INSTANCE.log("Phira 入库进度：成功 "+imported+"，跳过 "+skipped);
        }
        void destroyAsync(){Process p=process;if(p==null||!cleanupStarted.compareAndSet(false,true))return;Thread closer=new Thread(()->{p.destroyForcibly();try{p.getInputStream().close();}catch(Exception ignored){}try{p.getErrorStream().close();}catch(Exception ignored){}},"phi-phira-import-close");closer.setDaemon(true);closer.start();}
    }
    private static String shortName(String path){return path.length()>120?"…"+path.substring(path.length()-120):path;}
    private static void delete(File file){if(file==null||!file.exists())return;if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);}file.delete();}
}
