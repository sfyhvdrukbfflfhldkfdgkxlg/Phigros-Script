package io.github.phiscript.assets;

import android.content.Context;
import android.util.AtomicFile;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Note;
import io.github.phiscript.root.PhiraImportControl;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-chart atomic, version-scoped copies of every imported note's head and tail. */
public final class ChartTimingIndex {
    private static final int MAGIC=0x43544931,MAX_NOTES=1000000;
    private static final AtomicBoolean busy=new AtomicBoolean();
    private ChartTimingIndex(){}
    public static boolean isBusy(){return busy.get();}
    public static final class Summary {
        public final String source,songId,difficulty,signature;
        public final int count;
        public final long firstChartUs,offsetUs,firstMusicUs;
        Summary(String source,String songId,String difficulty,int count,long first,long offset,String signature){
            this.source=source;this.songId=songId;this.difficulty=difficulty;this.count=count;
            firstChartUs=first;offsetUs=offset;firstMusicUs=first+offset;this.signature=signature;
        }
        public String reference(Context context){
            try{FirstNoteCatalog.Entry entry=FirstNoteCatalog.get(context).find(songId,difficulty);
                if(entry==null)return "未收录";
                return entry.notes==count&&Math.abs(entry.firstMusicUs-firstMusicUs)<=1000&&Math.abs(entry.offsetUs-offsetUs)<=1000?"一致":"不同版本或数据";
            }catch(IOException e){return "基准不可用";}
        }
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));return s.toString();}
    private static String hash(String value){try{return hex(digest().digest(value.getBytes("UTF-8")));}catch(UnsupportedEncodingException e){throw new AssertionError(e);}}
    private static long micros(double value){
        if(!Double.isFinite(value)||Math.abs(value)>86400)throw new IllegalArgumentException("音符时间超出范围");
        return Math.round(value*1000000);
    }
    private static String source(ChartLibrary library){return hash(library.getSnapshot().toString());}
    private static File file(Context context,String source,String songId,String difficulty)throws IOException{
        if(source==null||source.isEmpty()||source.length()>512||songId==null||songId.isEmpty()||songId.length()>512||difficulty==null||difficulty.isEmpty()||difficulty.length()>24)
            throw new IOException("谱面时间表标识无效");
        File directory=new File(context.getFilesDir(),"chart-timing-v1/"+hash(source));
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建谱面时间目录");
        return new File(directory,hash(songId+"\n"+difficulty)+".cti");
    }
    private static void rows(DataOutputStream out,Chart chart)throws IOException{
        int index=0;
        for(Note note:chart.getNotes()){
            if((index++&255)==0)PhiraImportControl.check();
            long head=micros(note.getTimeSeconds()),tail=micros(note.getEndSeconds());
            if(head<0||tail<head||note.getType()<1||note.getType()>4)throw new IOException("音符时间表数据无效");
            out.writeInt(note.getId());out.writeByte(note.getType());out.writeLong(head);out.writeLong(tail);
        }
    }
    private static Summary analyze(String source,String song,String difficulty,Chart chart)throws IOException{
        int count=chart.getNotes().size();if(count<1||count>MAX_NOTES)throw new IOException("谱面时间表音符数量无效");
        long first=Long.MAX_VALUE,offset=micros(chart.getOffsetSeconds());
        for(Note note:chart.getNotes())first=Math.min(first,micros(note.getTimeSeconds()));
        MessageDigest signature=digest();
        DataOutputStream output=new DataOutputStream(new DigestOutputStream(new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},signature));
        output.writeInt(count);output.writeLong(offset);rows(output,chart);output.flush();
        return new Summary(source,song,difficulty,count,first,offset,hex(signature.digest()));
    }
    public static Summary readStored(Context context,String source,String song,String difficulty)throws IOException{
        File target=file(context,source,song,difficulty);if(target.length()>24L*MAX_NOTES+8192)throw new IOException("谱面时间表过大");
        AtomicFile atomic=new AtomicFile(target);MessageDigest checksum=digest();
        try(FileInputStream raw=atomic.openRead()){
            DataInputStream input=new DataInputStream(new DigestInputStream(raw,checksum));
            if(input.readInt()!=MAGIC||!input.readUTF().equals(source)||!input.readUTF().equals(song)||!input.readUTF().equals(difficulty))throw new IOException("谱面时间表来源不匹配");
            int count=input.readInt();long first=input.readLong(),offset=input.readLong();String signature=input.readUTF();
            if(count<1||count>MAX_NOTES||!signature.matches("[0-9a-f]{64}"))throw new IOException("谱面时间表头无效");
            MessageDigest timing=digest();DataOutputStream verify=new DataOutputStream(new DigestOutputStream(new OutputStream(){public void write(int b){}public void write(byte[] b,int o,int n){}},timing));
            verify.writeInt(count);verify.writeLong(offset);long minimum=Long.MAX_VALUE;
            for(int i=0;i<count;i++){
                if((i&255)==0)PhiraImportControl.check();
                int id=input.readInt(),type=input.readUnsignedByte();long head=input.readLong(),tail=input.readLong();
                if(type<1||type>4||head<0||tail<head||tail>86400000000L)throw new IOException("谱面时间表音符无效");
                minimum=Math.min(minimum,head);verify.writeInt(id);verify.writeByte(type);verify.writeLong(head);verify.writeLong(tail);
            }
            verify.flush();byte[] actual=checksum.digest(),stored=new byte[32];new DataInputStream(raw).readFully(stored);
            if(raw.read()!=-1||first!=minimum||!MessageDigest.isEqual(actual,stored)||!hex(timing.digest()).equals(signature))throw new IOException("谱面时间表校验失败");
            return new Summary(source,song,difficulty,count,first,offset,signature);
        }
    }
    public static synchronized Summary ensure(Context context,String source,String song,String difficulty,Chart chart)throws IOException{
        Summary summary=analyze(source,song,difficulty,chart);
        try{Summary old=readStored(context,source,song,difficulty);if(old.signature.equals(summary.signature))return old;}
        catch(FileNotFoundException e){}catch(IOException corrupt){}
        AtomicFile atomic=new AtomicFile(file(context,source,song,difficulty));FileOutputStream raw=atomic.startWrite();
        try{
            MessageDigest checksum=digest();DataOutputStream output=new DataOutputStream(new DigestOutputStream(raw,checksum));
            output.writeInt(MAGIC);output.writeUTF(source);output.writeUTF(song);output.writeUTF(difficulty);output.writeInt(summary.count);
            output.writeLong(summary.firstChartUs);output.writeLong(summary.offsetUs);output.writeUTF(summary.signature);rows(output,chart);output.flush();
            PhiraImportControl.check();raw.write(checksum.digest());atomic.finishWrite(raw);
        }catch(Exception|Error e){atomic.failWrite(raw);throw e;}
        return summary;
    }
    /** Called on playback too, so a cancelled or old import can recover one chart. */
    public static void ensureForPlayback(Context context,ChartLibrary library,String song,String difficulty,Chart chart){
        try{Summary summary=ensure(context,source(library),song,difficulty,chart);
            RuntimeState.INSTANCE.log("谱面时间表："+summary.count+" 个按键，音乐起点后首键 "+String.format(Locale.ROOT,"%.3f",summary.firstMusicUs/1000.0)+" ms；网络基准："+summary.reference(context));
        }catch(CancellationException e){throw e;}
        catch(IOException|RuntimeException e){RuntimeState.INSTANCE.log("谱面时间表保存未完成，使用已解析音符时间："+e.getMessage());}
    }
    /** Hooked into the shared native scan, covering Root, APK picker and installed APK imports. */
    public static void importLibrary(Context context,ChartLibrary library){
        if(!busy.compareAndSet(false,true))throw new IllegalStateException("已有谱面时间导入任务");
        int saved=0,failed=0,reused=0,matched=0,total=library.getLocations().size();String source=source(library);
        try(PhiraImportControl.Session session=PhiraImportControl.begin()){
            List<ChartLocation> locations=library.getLocations();int index=0;
            for(ChartLocation location:locations){
                PhiraImportControl.check();index++;
                String song=location.getSongId(),difficulty=location.getDifficulty();
                RuntimeState.INSTANCE.log("生成谱面时间表 "+index+" / "+locations.size()+"："+song+" / "+difficulty);
                try{
                    Summary result=null;
                    try{result=readStored(context,source,song,difficulty);reused++;}catch(IOException missing){}
                    if(result==null){Chart chart=library.load(song,difficulty);result=ensure(context,source,song,difficulty,chart);chart=null;}
                    saved++;String reference=result.reference(context);if(reference.equals("一致"))matched++;
                    RuntimeState.INSTANCE.log("已保存按键时间："+result.count+" 个，首键 "+String.format(Locale.ROOT,"%.3f",result.firstMusicUs/1000.0)+" ms；网络核对："+reference);
                }catch(CancellationException e){throw e;}
                catch(Exception e){failed++;RuntimeState.INSTANCE.log("谱面时间表跳过："+song+" / "+difficulty+"："+e.getMessage());}
            }
        }catch(CancellationException e){RuntimeState.INSTANCE.log("已取消时间表生成，已保存的谱面保留");}
        catch(OutOfMemoryError e){RuntimeState.INSTANCE.log("时间表生成内存不足，已保存的谱面保留；可在演奏时逐张补建");}
        catch(Exception e){RuntimeState.INSTANCE.log("谱面时间表生成未完成："+e.getMessage());}
        finally{busy.set(false);}
        RuntimeState.INSTANCE.log("谱面时间表已保存 "+saved+" 份，复用 "+reused+" 份，首键核对一致 "+matched+" 份，失败 "+failed+" 份，未处理 "+Math.max(0,total-saved-failed)+" 份；首键是音乐相对时间");
    }
}
