package io.github.phiscript.assets;

import android.content.Context;
import android.util.AtomicFile;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.root.ManualChartControl;
import io.github.phiscript.root.PhiraImportControl;
import io.github.phiscript.root.RootPanel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Deletes only the application's own imported Phira/Phira Pro copy. */
public final class PhiraLibraryDeletion {
    public static final class Plan {
        public final String id,title,level;
        private final String indexDigest;private final boolean phigros;
        private Plan(PhiraEntry e,String digest,boolean phigros){id=e.getId();title=e.getTitle();level=e.getLevel();indexDigest=digest;this.phigros=phigros;}
    }
    public static final class Result {
        public final boolean deleted,fileDeleted;public final int remaining;
        Result(boolean deleted,boolean fileDeleted,int remaining){this.deleted=deleted;this.fileDeleted=fileDeleted;this.remaining=remaining;}
    }
    private PhiraLibraryDeletion(){}
    private static void requireIdle(){
        if(RuntimeState.INSTANCE.getRunning().get()||RuntimeState.INSTANCE.getActiveTouchStop().get()!=null||ManualChartControl.isOpen()||RootPanel.isBusy())throw new IllegalStateException("请先停止识别、演奏和导入，再删除本机谱面");
    }
    private static void requireNoPlayback(){
        if(RuntimeState.INSTANCE.getRunning().get()||RuntimeState.INSTANCE.getActiveTouchStop().get()!=null||ManualChartControl.isOpen())throw new IllegalStateException("当前任务正在使用谱库，无法删除");
    }
    private static File directory(Context context)throws IOException{
        File files=context.getApplicationContext().getFilesDir().getCanonicalFile(),dir=new File(files,"phira");
        if(!dir.getCanonicalFile().equals(dir.getAbsoluteFile()))throw new IOException("谱库目录不是应用内的规范路径");
        return dir;
    }
    private static File checked(File parent,String name)throws IOException{
        File child=new File(parent,name);if(!child.getCanonicalFile().equals(child.getAbsoluteFile()))throw new IOException("谱库文件路径异常");return child;
    }
    private static File indexFile(File dir)throws IOException{
        checked(dir,"index.json.bak");checked(dir,"index.json.new");return checked(dir,"index.json");
    }
    private static byte[] readIndex(File file)throws IOException{
        try(InputStream in=new AtomicFile(file).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>256*1024)throw new IOException("谱库索引过大");out.write(buf,0,n);}return out.toByteArray();
        }
    }
    private static String digest(byte[] bytes)throws Exception{
        StringBuilder s=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",value&255));return s.toString();
    }
    public static List<PhiraEntry> list(Context context)throws Exception{
        return list(context,false);
    }
    public static List<PhiraEntry> list(Context context,boolean phigros)throws Exception{
        context=phigros?ExternalCharts.localStorage(context):context;
        requireIdle();synchronized(PhiraLibrary.Companion){indexFile(directory(context));PhiraLibrary library=PhiraLibrary.Companion.restore(context);return library==null?Collections.emptyList():library.getEntries();}
    }
    public static Plan prepare(Context context,String id)throws Exception{
        return prepare(context,id,false);
    }
    public static Plan prepare(Context context,String id,boolean phigros)throws Exception{
        context=phigros?ExternalCharts.localStorage(context):context;
        requireIdle();if(id==null||!id.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("谱面标识无效");
        synchronized(PhiraLibrary.Companion){
            File dir=directory(context),index=indexFile(dir);
            PhiraLibrary library=PhiraLibrary.Companion.restore(context);PhiraEntry selected=null;
            if(library!=null)for(PhiraEntry e:library.getEntries())if(e.getId().equals(id))selected=e;
            if(selected==null)throw new IllegalStateException("该谱面已不存在，请刷新列表");
            checked(new File(dir,"charts"),id+".json");
            return new Plan(selected,digest(readIndex(index)),phigros);
        }
    }
    /** The false path is used by cancelled confirmation; it never mutates storage. */
    public static Result commit(Context context,Plan plan,boolean confirmed)throws Exception{
        if(!confirmed)return new Result(false,false,-1);
        requireIdle();if(plan==null)throw new IllegalArgumentException("缺少删除确认");
        Context rootContext=context.getApplicationContext(),app=plan.phigros?ExternalCharts.localStorage(rootContext):rootContext;
        try(PhiraImportControl.Session session=PhiraImportControl.begin()){
            synchronized(PhiraLibrary.Companion){
                requireNoPlayback();File dir=directory(app),index=indexFile(dir),chart=checked(new File(dir,"charts"),plan.id+".json");
                byte[] old=readIndex(index);if(!digest(old).equals(plan.indexDigest))throw new IllegalStateException("谱库已变化，请重新选择要删除的谱面");
                JSONObject root=new JSONObject(new String(old,StandardCharsets.UTF_8));if(root.getInt("version")!=1)throw new IllegalStateException("谱库版本不支持");
                JSONArray entries=root.getJSONArray("entries"),remaining=new JSONArray();int removed=0;
                for(int i=0;i<entries.length();i++){JSONObject row=entries.getJSONObject(i);if(plan.id.equals(row.getString("id"))){if(!plan.title.equals(row.getString("title"))||!plan.level.equals(row.getString("level")))throw new IllegalStateException("谱面资料已变化");removed++;}else remaining.put(row);}
                if(removed!=1)throw new IllegalStateException("谱面不唯一或已删除");
                PhiraImportControl.check();requireNoPlayback();AtomicFile atomic=new AtomicFile(index);
                if(remaining.length()==0){
                    // Native restore requires at least one row. Removing all
                    // AtomicFile generations represents an empty library.
                    checked(dir,"index.json.bak");checked(dir,"index.json.new");atomic.delete();
                    if(index.exists()||new File(index+".bak").exists()||new File(index+".new").exists())throw new IOException("无法删除空谱库索引，谱面文件仍保留");
                }else{
                    root.put("entries",remaining);FileOutputStream out=atomic.startWrite();
                    try{out.write(root.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception error){atomic.failWrite(out);throw error;}
                }
                // After the index commit, finish memory/selection cleanup even
                // if the user cancels. A deleted chart must not remain usable
                // through an old in-memory library or an AtomicFile backup.
                session.close();if(!plan.phigros)RuntimeState.INSTANCE.setPhiraLibrary(null);
                PhiraLibrary restored=remaining.length()==0?null:PhiraLibrary.Companion.restore(app);
                if(plan.phigros)ExternalCharts.refreshLocalAfterDeletion(rootContext,restored);else RuntimeState.INSTANCE.setPhiraLibrary(restored);
                AppSettings prefs=new AppSettings(rootContext);if(!plan.phigros&&plan.id.equals(prefs.text("phiraChartId","")))prefs.save("phiraChartId","");
                boolean unlinked=!chart.exists()||chart.delete();
                try{io.github.phiscript.input.PreparedChartStore.clear(app);}catch(IOException error){RuntimeState.INSTANCE.log("预计算缓存清理未完成："+error.getMessage());}
                RuntimeState.INSTANCE.log("已删除本机 "+(plan.phigros?"Phigros":"Phira")+" 谱面："+plan.title+" / "+plan.level+"；剩余 "+remaining.length()+" 个"+(unlinked?"":"（索引已移除，但文件清理失败）"));
                return new Result(true,unlinked,remaining.length());
            }
        }
    }
}
