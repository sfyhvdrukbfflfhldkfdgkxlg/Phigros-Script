package io.github.phiscript.root;

import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONArray;

/** Private pipe-only root worker. No socket, game-data writes or device-node access. */
public final class RootDaemon {
    public static final int MAGIC=0x50485232;
    public static void main(String[] args) throws Exception {
        if(android.os.Process.myUid()!=0)throw new SecurityException("Root 管理器未授予 uid 0");
        if(args.length==0)throw new IllegalArgumentException("缺少操作");
        if(args[0].equals("check")){System.out.print("PHI_ROOT_OK");return;}
        if(args[0].equals("phira-export")){
            if(args.length!=2)throw new IllegalArgumentException("Phira 用户参数无效");
            PhiraExport.write(PhiraExport.roots(Integer.parseInt(args[1])),System.out);return;
        }
        if(args[0].equals("entries")||args[0].equals("read")){assets(args);return;}
        if(!args[0].equals("input"))throw new IllegalArgumentException("未知操作");
        if(Looper.myLooper()==null)Looper.prepare();
        Class<?> c;
        try{c=Class.forName("android.hardware.input.InputManagerGlobal");}
        catch(ClassNotFoundException e){c=Class.forName("android.hardware.input.InputManager");}
        Object manager=c.getMethod("getInstance").invoke(null);
        Method inject=c.getMethod("injectInputEvent",InputEvent.class,int.class);
        DataInputStream in=new DataInputStream(System.in);
        DataOutputStream out=new DataOutputStream(System.out);
        out.writeInt(MAGIC);out.flush();
        int width=in.readInt(),height=in.readInt();
        RootTouchState state=new RootTouchState((down,time,action,points)->{
            MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[points.size()];
            MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[points.size()];
            for(int i=0;i<points.size();i++){
                RootTouchState.Pointer p=points.get(i);
                properties[i]=new MotionEvent.PointerProperties();properties[i].id=p.id;properties[i].toolType=1;
                coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=p.x;coordinates[i].y=p.y;
                coordinates[i].pressure=1;coordinates[i].size=1;
            }
            MotionEvent e=MotionEvent.obtain(down,time,action,points.size(),properties,coordinates,
                0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
            try{
                // Default display only. Do not redirect touches to another display.
                // Asynchronous queue acceptance avoids blocking the safety
                // watchdog on an unresponsive game window's render thread.
                if(!Boolean.TRUE.equals(inject.invoke(manager,e,0)))throw new IllegalStateException("系统拒绝 Root 触控");
            }finally{e.recycle();}
        },width,height);
        final long[] last={SystemClock.uptimeMillis()};
        final boolean[] closed={false};
        Thread watchdog=new Thread(()->{
            try{while(true){Thread.sleep(100);synchronized(state){
                if(closed[0])return;
                if(SystemClock.uptimeMillis()-last[0]>1000){
                    try{state.cancel(SystemClock.uptimeMillis());}finally{System.exit(2);}
                }
            }}}catch(Exception e){System.exit(3);}
        },"phi-root-release-watchdog");watchdog.setDaemon(true);watchdog.start();
        try{
            while(true){
                int count=in.readInt();if(count==-1)break;
                if(count<0||count>10)throw new IllegalArgumentException("触点数无效");
                List<RootTouchState.Sample> samples=new ArrayList<>();
                for(int i=0;i<count;i++)samples.add(new RootTouchState.Sample(in.readLong(),in.readFloat(),in.readFloat()));
                synchronized(state){state.apply(SystemClock.uptimeMillis(),samples);last[0]=SystemClock.uptimeMillis();}
                out.writeByte(1);out.flush();
            }
        }finally{synchronized(state){closed[0]=true;state.cancel(SystemClock.uptimeMillis());}}
    }
    private static void assets(String[] args)throws Exception{
        boolean list=args[0].equals("entries");
        int begin=list?1:3;
        if(args.length<=begin)throw new IllegalArgumentException("缺少已安装 APK 路径");
        String entry=list?null:args[1];int limit=list?0:Integer.parseInt(args[2]);
        if(!list&&(limit<1||limit>134217728||!entry.startsWith("assets/")||entry.contains("..")))
            throw new IllegalArgumentException("资源名称或限额无效");
        TreeSet<String> names=new TreeSet<>();
        for(int i=begin;i<args.length;i++){
            if(!args[i].startsWith("/data/app/")&&!args[i].startsWith("/system/")&&!args[i].startsWith("/product/"))
                throw new SecurityException("仅允许读取系统返回的已安装 APK");
            if(!args[i].endsWith(".apk"))throw new SecurityException("不是 APK");
            try(ZipFile zip=new ZipFile(args[i])){
                if(list){Enumeration<? extends ZipEntry> es=zip.entries();while(es.hasMoreElements()){
                    String name=es.nextElement().getName();if(name.startsWith("assets/"))names.add(name);
                    if(names.size()>100000)throw new IllegalArgumentException("APK 目录过大");
                }}else{
                    ZipEntry e=zip.getEntry(entry);if(e==null)continue;
                    if(e.getSize()>limit)throw new IllegalArgumentException("APK 资源超限");
                    try(InputStream in=zip.getInputStream(e)){
                        byte[] b=new byte[65536];int n,total=0;
                        while((n=in.read(b))!=-1){if(n>limit-total)throw new IllegalArgumentException("APK 资源超限");System.out.write(b,0,n);total+=n;}
                    }return;
                }
            }
        }
        if(list){byte[] data=new JSONArray(names).toString().getBytes("UTF-8");
            if(data.length>16777216)throw new IllegalArgumentException("APK 目录超限");System.out.write(data);}
        else throw new IllegalArgumentException("未找到谱面资源");
    }
}
