package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.content.Context;
import android.graphics.RectF;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.SessionSettings;
import io.github.phiscript.assets.PhiraEntry;
import io.github.phiscript.assets.PhiraLibrary;
import io.github.phiscript.assets.ChartLibrary;
import io.github.phiscript.capture.PhiraViewport;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Viewport;
import io.github.phiscript.root.PhiraImportControl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** App-owned, bounded, content-addressed prepared touch data; no source files are changed. */
public final class PreparedChartStore {
    public static final long MAX_DISK_BYTES=256L*1024*1024;
    public static final int MAX_FILES=64;
    private static final Map<Chart,Binding> sources=Collections.synchronizedMap(new WeakHashMap<Chart,Binding>());
    private static final Object diskLock=new Object();
    private static final ThreadLocal<ImportGeometry> importBatch=new ThreadLocal<>();
    private static final class Geometry {
        final Viewport viewport;final TouchBounds bounds;
        Geometry(Viewport viewport,TouchBounds bounds){this.viewport=viewport;this.bounds=bounds;}
    }
    /** Device/settings snapshot is shared across every entry in one import. */
    private static final class ImportGeometry {
        final Context context;final int width,height;final boolean portrait;final SessionSettings settings;final GestureProfile profile;final Map<String,Geometry> variants=new HashMap<>();
        ImportGeometry(Context context){
            this.context=context;TapMod.init(context);DisplayMetrics metrics=new DisplayMetrics();((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(metrics);width=Math.max(metrics.widthPixels,metrics.heightPixels);height=Math.min(metrics.widthPixels,metrics.heightPixels);
            portrait=metrics.widthPixels<metrics.heightPixels;
            if(width<2||height<2)throw new IllegalStateException(tr("无法读取横屏尺寸，请在开始前重新计算触点"));settings=new AppSettings(context).snapshot();profile=GestureProfile.read(context);
        }
        Geometry get(double aspect,boolean force){String key=settings.getPhiraAutomaticViewport()?Double.toHexString(aspect)+"|"+force:"fixed";Geometry geometry=variants.get(key);if(geometry!=null)return geometry;Viewport viewport;
            if(settings.getPhiraAutomaticViewport())viewport=PhiraViewport.INSTANCE.bounds(width,height,aspect,force);else{RectF r=settings.getViewport();viewport=new Viewport(r.left*width,r.top*height,r.width()*width,r.height()*height);}
            geometry=new Geometry(viewport,TouchSafety.read(context,width,height,viewport,portrait).bounds);variants.put(key,geometry);return geometry;
        }
    }
    private static final class Binding {
        final String fingerprint;final Context context;
        Binding(String fingerprint,Context context){this.fingerprint=fingerprint;this.context=context;}
    }
    private PreparedChartStore(){}
    public static String digest(byte[] bytes){
        try{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static void bind(Chart chart,String json){if(chart!=null&&json!=null)sources.put(chart,new Binding(digest(json.getBytes(StandardCharsets.UTF_8)),null));}
    public static void bind(Chart chart,Context context,byte[] bytes){sources.put(chart,new Binding(digest(bytes),context.getApplicationContext()));}
    private static void check(AtomicBoolean cancel){if(Thread.currentThread().isInterrupted()||cancel!=null&&cancel.get())throw new java.util.concurrent.CancellationException(tr("已取消触点预计算"));PhiraImportControl.check();}
    private static boolean ownedPath(File path)throws IOException{
        return !Files.isSymbolicLink(path.toPath())&&path.getAbsoluteFile().equals(path.getCanonicalFile());
    }
    private static File directory(Context context)throws IOException{
        File base=context.getFilesDir().getCanonicalFile(),dir=new File(base,"prepared-touch-v1");
        if(!ownedPath(dir))throw new IOException(tr("预计算目录路径异常"));
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException(tr("无法创建触点预计算目录"));return dir;
    }
    private static File file(Context context,String name)throws IOException{
        if(!name.matches("[0-9a-f]{64}\\.[0-9a-f]{64}\\.ptg"))throw new IOException(tr("预计算文件标识无效"));
        File result=new File(directory(context),name);
        for(String suffix:new String[]{"",".new",".bak"}){File candidate=new File(result+suffix);if(!ownedPath(candidate))throw new IOException(tr("预计算文件路径异常"));}
        return result;
    }
    private static String key(GestureProfile p,Viewport v,TouchBounds b){
        String value="prepared-v"+PreparedTrajectory.VERSION+"|"+TapMod.preparationSignature()+"|"+p.connectDrags+"|"+p.catchDrags+"|"+p.connectFlicks+"|"+p.combineShots+"|"+p.gapMs+"|"+Float.toHexString(p.distance);
        for(float n:new float[]{v.getLeft(),v.getTop(),v.getWidth(),v.getHeight(),b.left,b.top,b.right,b.bottom})value+="|"+Float.toHexString(n);
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }
    private static byte[] read(File target)throws IOException{
        try(FileInputStream file=new AtomicFile(target).openRead();DataInputStream in=new DataInputStream(file)){
            long length=file.getChannel().size();
            if(length>PreparedTrajectory.MAX_BYTES)throw new IOException(tr("预计算文件过大"));
            byte[] bytes=new byte[(int)length];
            in.readFully(bytes);
            if(in.read()!=-1)throw new IOException(tr("预计算文件大小无效"));
            return bytes;
        }
    }
    private static List<File> files(Context context)throws IOException{
        File[] entries=directory(context).listFiles();Set<String> names=new TreeSet<>();
        if(entries!=null)for(File f:entries){String name=f.getName().replaceFirst("\\.(bak|new)$","");if(name.matches("[0-9a-f]{64}\\.[0-9a-f]{64}\\.ptg"))names.add(name);}
        List<File> result=new ArrayList<>();for(String name:names)result.add(file(context,name));return result;
    }
    private static long storedBytes(File target){return target.length()+new File(target+".new").length()+new File(target+".bak").length();}
    private static boolean exists(File target){return target.exists()||new File(target+".new").exists()||new File(target+".bak").exists();}
    private static void write(Context context,File target,byte[] bytes,AtomicBoolean cancel)throws IOException{
        if(bytes.length>PreparedTrajectory.MAX_BYTES)throw new IOException(tr("预计算文件超过容量"));
        synchronized(diskLock){
            check(cancel);List<File> prior=files(context);prior.remove(target);prior.sort((a,b)->Long.compare(a.lastModified(),b.lastModified()));long total=bytes.length;
            for(File f:prior)total+=storedBytes(f);int count=prior.size()+1;
            for(File f:prior){if(total<=MAX_DISK_BYTES&&count<=MAX_FILES)break;long length=storedBytes(f);new AtomicFile(f).delete();if(exists(f))throw new IOException(tr("预计算缓存空间不足，无法清理旧条目"));total-=length;count--;}
            if(total>MAX_DISK_BYTES||count>MAX_FILES)throw new IOException(tr("预计算缓存超过容量"));
            AtomicFile atomic=new AtomicFile(target);FileOutputStream stream=atomic.startWrite();
            try{check(cancel);stream.write(bytes);check(cancel);atomic.finishWrite(stream);}
            catch(IOException|RuntimeException failure){atomic.failWrite(stream);throw failure;}
        }
    }
    public static GesturePlan ensureForPlayback(Chart chart,TouchService service,Viewport viewport,TouchBounds bounds){
        return loadForPlayback(chart,GestureProfile.read(service),viewport,bounds);
    }
    static GesturePlan loadForPlayback(Chart chart,GestureProfile profile,Viewport viewport,TouchBounds bounds){
        TapMod.reload();
        GesturePlan memory=GesturePlan.cachedPrepared(chart,profile,viewport,bounds);
        if(memory!=null)return memory;
        Binding source=sources.get(chart);
        if(source!=null&&source.context!=null){
            synchronized(diskLock){
                try{
                    TapMod.init(source.context);
                    File target=file(source.context,source.fingerprint+"."+key(profile,viewport,bounds)+".ptg");
                    if(exists(target)){
                        GesturePlan ready=GesturePlan.decodePrepared(chart,profile,viewport,bounds,read(target),source.fingerprint);
                        target.setLastModified(System.currentTimeMillis());
                        return ready;
                    }
                }catch(IOException invalid){
                    throw new IllegalStateException(notPrepared(),invalid);
                }
            }
        }
        throw new IllegalStateException(notPrepared());
    }
    private static String notPrepared(){
        return io.github.phiscript.i18n.UiText.english()
            ?"Touch coordinates are missing or no longer match your screen or settings. Open Prepare touch points in the chart library and prepare the chart before starting."
            :tr("触点坐标缺失或已不匹配屏幕或设置。请在曲库打开「预计算触点」，准备这张谱面后再开始。");
    }
    public static GesturePlan ensure(Context context,Chart chart,GestureProfile profile,Viewport viewport,TouchBounds bounds,AtomicBoolean cancel){
        check(cancel);TapMod.init(context);GesturePlan memory=GesturePlan.cachedPrepared(chart,profile,viewport,bounds);if(memory!=null)return memory;Binding source=sources.get(chart);
        if(source==null)return GesturePlan.prepareForImport(chart,profile,viewport,bounds,cancel);
        File target;
        try{target=file(context,source.fingerprint+"."+key(profile,viewport,bounds)+".ptg");}
        catch(IOException failure){throw new IllegalStateException(tr("无法保存预计算触点"),failure);}
        synchronized(diskLock){
            if(target.exists()||new File(target+".bak").exists())try{
                GesturePlan ready=GesturePlan.decodePrepared(chart,profile,viewport,bounds,read(target),source.fingerprint);check(cancel);target.setLastModified(System.currentTimeMillis());
                RuntimeState.INSTANCE.log(tr("读取预计算触点：")+ready.preparedSampleCount()+tr(" 个采样"));return ready;
            }catch(IOException invalid){new AtomicFile(target).delete();RuntimeState.INSTANCE.log(tr("触点缓存失效，重新计算：")+invalid.getMessage());}
        }
        RuntimeState.INSTANCE.log(tr("正在预计算点击、长按与噪域安全轨迹…"));
        GesturePlan ready=GesturePlan.prepareForImport(chart,profile,viewport,bounds,cancel);
        try{
            check(cancel);
            write(context,target,ready.encodePrepared(source.fingerprint),cancel);
        }catch(IOException failure){
            GesturePlan.discardPrepared(chart);
            throw new IllegalStateException(tr("无法保存预计算触点"),failure);
        }catch(RuntimeException failure){
            GesturePlan.discardPrepared(chart);
            throw failure;
        }
        RuntimeState.INSTANCE.log(tr("触点预计算完成：")+ready.preparedSampleCount()+tr(" 个采样"));return ready;
    }
    public static GesturePlan prepareImported(Context context,Chart chart,byte[] bytes,double aspect,boolean force,AtomicBoolean cancel){
        bind(chart,context,bytes);
        return prepareBound(context,chart,aspect,force,cancel);
    }
    public static GesturePlan prepareBound(Context context,Chart chart,double aspect,boolean force,AtomicBoolean cancel){
        check(cancel);
        if(sources.get(chart)==null)throw new IllegalStateException(tr("无法读取谱面指纹"));
        ImportGeometry batch=importBatch.get();if(batch==null||!batch.context.getFilesDir().equals(context.getFilesDir()))batch=new ImportGeometry(context);Geometry geometry=batch.get(aspect,force);
        return ensure(context,chart,batch.profile,geometry.viewport,geometry.bounds,cancel);
    }
    /** Native Phira loader calls this after its source checksum and parse have passed. */
    public static void afterPhiraLoad(Context context,PhiraLibrary library,String id,Chart chart,byte[] bytes){
        bind(chart,context,bytes);
    }
    public static void afterChartLoad(ChartLibrary library,Chart chart,String json){
        try{
            java.lang.reflect.Field field=ChartLibrary.class.getDeclaredField("context");field.setAccessible(true);Context context=(Context)field.get(library);
            bind(chart,context,json.getBytes(StandardCharsets.UTF_8));
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(notPrepared(),failure);}
    }
    public static void preparePhira(Context context,PhiraLibrary library,PhiraEntry entry,AtomicBoolean cancel){
        check(cancel);
        Chart chart=library.load(entry.getId(),entry.getLevel());
        prepareBound(context,chart,entry.getAspectRatio(),entry.getForceAspectRatio(),cancel);
    }
    /** Source import has already committed. Incomplete preparation never removes a saved chart. */
    public static void afterPhiraImport(Context context,PhiraLibrary library,Map<String,String> fingerprints){
        if(Looper.myLooper()==Looper.getMainLooper())return;
        List<PhiraEntry> entries=new ArrayList<>();
        for(PhiraEntry entry:library.getEntries())if(fingerprints.containsKey(entry.getId()))entries.add(entry);
        int done=0,reused=0,computed=0;
        ImportGeometry previous=importBatch.get();
        try{ImportGeometry batch=new ImportGeometry(context);importBatch.set(batch);
        for(PhiraEntry entry:entries){
            try{check(null);String fingerprint=fingerprints.get(entry.getId());Geometry geometry=batch.get(entry.getAspectRatio(),entry.getForceAspectRatio());
                if(fingerprint!=null&&verified(context,fingerprint,batch.profile,geometry)){reused++;done++;continue;}
                RuntimeState.INSTANCE.log(tr("预计算谱面 ")+(done+1)+" / "+entries.size()+"："+entry.getTitle());preparePhira(context,library,entry,null);computed++;done++;}
            catch(java.util.concurrent.CancellationException cancelled){RuntimeState.INSTANCE.log(tr("谱面已保存；请在曲库继续预计算触点"));return;}
            catch(Exception failure){if(Thread.currentThread().isInterrupted()){RuntimeState.INSTANCE.log(tr("谱面已保存；触点预计算已取消"));return;}RuntimeState.INSTANCE.log(tr("谱面已保存，触点预计算失败：")+failure.getMessage());}
        }
        RuntimeState.INSTANCE.log(tr("导入阶段触点预计算完成：")+done+" / "+entries.size()+tr("；复用 ")+reused+tr(" 个，计算 ")+computed+tr(" 个"));
        }catch(java.util.concurrent.CancellationException cancelled){RuntimeState.INSTANCE.log(tr("谱面已保存；请在曲库继续预计算触点"));}
        catch(Exception failure){RuntimeState.INSTANCE.log(tr("谱面已保存，触点预计算失败：")+failure.getMessage());}
        finally{if(previous==null)importBatch.remove();else importBatch.set(previous);}
    }
    private static boolean verified(Context context,String fingerprint,GestureProfile profile,Geometry geometry){
        try{synchronized(diskLock){check(null);File target=file(context,fingerprint+"."+key(profile,geometry.viewport,geometry.bounds)+".ptg");if(!target.exists()&&!new File(target+".bak").exists())return false;
            try(InputStream in=new AtomicFile(target).openRead()){PreparedTrajectory.inspectPrepared(in,target.length(),profile,geometry.viewport,geometry.bounds,fingerprint);}check(null);target.setLastModified(System.currentTimeMillis());return true;
        }}catch(IOException invalid){return false;}
    }
    public static long[] statistics(Context context)throws IOException{
        synchronized(diskLock){List<File> entries=files(context);long bytes=0;for(File f:entries)bytes+=storedBytes(f);return new long[]{entries.size(),bytes};}
    }
    public static void clear(Context context)throws IOException{
        synchronized(diskLock){for(File f:files(context)){new AtomicFile(f).delete();if(exists(f))throw new IOException(tr("无法清理预计算触点"));}GesturePlan.clearPrepared();}
    }
}
