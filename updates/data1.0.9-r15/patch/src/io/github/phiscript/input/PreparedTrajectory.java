package io.github.phiscript.input;

import io.github.phiscript.engine.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dense, physical-pixel trajectories. Invalid samples and unsafe connections
 * are explicit; lookup never evaluates a chart or searches a noise region. */
public final class PreparedTrajectory {
    public static final int VERSION=1,STEP_MS=4,MAX_SAMPLES=1500000,MAX_BYTES=40*1024*1024;
    private static final int MAGIC=0x50544731;
    final IdentityHashMap<TouchEvent,Track> tracks=new IdentityHashMap<>();
    final String settings;final long salt;final Viewport view;final TouchBounds bounds;
    final GestureProfile profile;
    private static final class Track {
        int[] time,broken,critical;float[] x,y;byte[] connected;boolean continuous,flick;
        void index(){broken=new int[time.length];for(int i=1;i<time.length;i++)broken[i]=broken[i-1]+(connected[i]==0?1:0);}
        int floor(long at){int low=0,high=time.length;while(low<high){int mid=(low+high)>>>1;if(time[mid]<=at)low=mid+1;else high=mid;}return Math.max(0,low-1);}
        Point at(long at,boolean previous,long previousAt){
            long clamped=Math.max(time[0],Math.min(time[time.length-1],at));int i=floor(clamped);
            if(!Float.isFinite(x[i])||!Float.isFinite(y[i]))throw new TapMod.NoSafePoint("预计算轨迹在此时刻没有安全落点");
            if(previous&&previousAt<clamped){int from=floor(Math.max(time[0],previousAt));int end=i+(clamped>time[i]?1:0);if(broken[end]!=broken[from])throw new TapMod.NoSafePoint("预计算轨迹需在此断开触点后恢复");}
            if(clamped==time[i]||i+1==time.length)return new Point(x[i],y[i]);
            if(connected[i+1]==0||!Float.isFinite(x[i+1]))throw new TapMod.NoSafePoint("不能跨越预计算轨迹中的噪域间隙");
            float f=(clamped-time[i])/(float)(time[i+1]-time[i]);return new Point(x[i]+(x[i+1]-x[i])*f,y[i]+(y[i+1]-y[i])*f);
        }
        long boundary(long now,long end){int lo=0,hi=critical.length;while(lo<hi){int mid=(lo+hi)>>>1;if(critical[mid]<=now)lo=mid+1;else hi=mid;}return lo==critical.length?end:Math.min(end,critical[lo]);}
    }
    private PreparedTrajectory(GestureProfile p,Viewport v,TouchBounds b,String settings,long salt){profile=p;view=v;bounds=b;this.settings=settings;this.salt=salt;}
    boolean matches(Chart chart,GestureProfile p,Viewport v,TouchBounds b){return profile.equals(p)&&view.equals(v)&&same(bounds,b)&&settings.equals(TapMod.preparationSignature())&&salt==TapMod.preparationSalt(chart);}
    static boolean same(TouchBounds a,TouchBounds b){return a.left==b.left&&a.top==b.top&&a.right==b.right&&a.bottom==b.bottom;}
    Point point(TouchEvent event,long time,Point previous,long previousTime){Track t=tracks.get(event);if(t==null)throw new IllegalStateException("预计算轨迹与手势不匹配");return t.at(time,previous!=null,previousTime);}
    long boundary(TouchEvent e,long now,long end){Track t=tracks.get(e);return t==null?end:t.boundary(now,end);}
    boolean continuous(TouchEvent e){Track t=tracks.get(e);return t!=null&&t.continuous;}
    boolean flick(TouchEvent e){Track t=tracks.get(e);return t!=null&&t.flick;}
    public int sampleCount(){int n=0;for(Track t:tracks.values())n+=t.time.length;return n;}
    static PreparedTrajectory prepare(Chart chart,GesturePlan plan,GestureProfile profile,Viewport view,TouchBounds bounds,AtomicBoolean cancel){
        PreparedTrajectory out=new PreparedTrajectory(profile,view,bounds,TapMod.preparationSignature(),TapMod.preparationSalt(chart));
        TouchSafety safety=TouchSafety.forPreparation(bounds);int[] screen={(int)Math.ceil(view.getRight()),(int)Math.ceil(view.getBottom())};int total=0;boolean[] bounded=new boolean[1];
        for(TouchEvent event:plan.events){
            cancelled(cancel);long start=event.getStartMs(),end=event.getEndMs();
            if(start<Integer.MIN_VALUE||end>Integer.MAX_VALUE||end<=start)throw new IllegalArgumentException("谱面轨迹时间超出预计算范围");
            long count=(end-start+STEP_MS-1)/STEP_MS+1;
            ContactPlanner.Route route=plan.routes.get(event);
            NavigableSet<Long> keys=route==null?Collections.emptyNavigableSet():route.keys.navigableKeySet().subSet(start,true,end,true);
            for(long t:keys)if(t!=end&&(t-start)%STEP_MS!=0)count++;
            if(count>MAX_SAMPLES-total)throw new IllegalArgumentException("此谱预计算轨迹超过容量，请缩短谱面或减少持续触点");total+=(int)count;
            Track track=new Track();track.continuous=plan.isContinuous(event);track.flick=plan.hasFlickPath(event);
            int n=(int)count;track.time=new int[n];track.x=new float[n];track.y=new float[n];track.connected=new byte[n];boolean[] critical=new boolean[n];
            Iterator<Long> keyIterator=keys.iterator();long key=keyIterator.hasNext()?keyIterator.next():Long.MAX_VALUE,grid=start;
            // Both inputs are already ordered. Keep every exact route key and
            // every 4ms sample without boxing the dense grid into tree nodes.
            for(int i=0;i<n;i++){
                long t=Math.min(grid,key);track.time[i]=(int)t;critical[i]=t==start||t==end||t==key;
                if(t==grid)grid=t==end?Long.MAX_VALUE:Math.min(end,t+STEP_MS);
                if(t==key)key=keyIterator.hasNext()?keyIterator.next():Long.MAX_VALUE;
            }
            Point prior=null;long priorAt=start;
            for(int i=0;i<n;i++){int t=track.time[i];
                cancelled(cancel);Point p=null;boolean connected=false;
                try{bounded[0]=false;p=safety.preparePoint(plan,chart,event,t,view,screen,bounded,prior,priorAt);connected=prior!=null;}
                catch(TapMod.NoSafePoint blocked){
                    try{bounded[0]=false;p=safety.preparePoint(plan,chart,event,t,view,screen,bounded,null,t);}
                    catch(TapMod.NoSafePoint noPoint){/* Invalid samples remain explicit. */}
                }
                // preparePoint certifies these exact endpoints and millisecond
                // times via connects -> segment, or proves the entire guarded
                // connection has no active geometry. Fresh fallback stays
                // disconnected; repeating segment adds no safety evidence.
                track.x[i]=p==null?Float.NaN:p.getX();track.y[i]=p==null?Float.NaN:p.getY();track.connected[i]=(byte)(connected?1:0);
                if(i>0&&(!connected||prior==null||p==null)){critical[i-1]=true;critical[i]=true;}
                prior=p;priorAt=t;
            }
            int criticalCount=0;for(boolean mark:critical)if(mark)criticalCount++;track.critical=new int[criticalCount];int at=0;for(int i=0;i<n;i++)if(critical[i])track.critical[at++]=track.time[i];track.index();out.tracks.put(event,track);
        }
        cancelled(cancel);return out;
    }
    private static void cancelled(AtomicBoolean cancel){if(Thread.currentThread().isInterrupted()||cancel!=null&&cancel.get())throw new IllegalStateException("谱面轨迹预计算已取消");}
    /** Verifies a stored generation without constructing Chart/Track/sample arrays. */
    public static void inspectPrepared(InputStream source,long length,GestureProfile profile,Viewport view,TouchBounds bounds,String fingerprint)throws IOException{
        if(length<64||length>MAX_BYTES)throw new IOException("预计算文件大小无效");
        BufferedInputStream buffered=new BufferedInputStream(source,16384);final long[] left={length-32};
        InputStream body=new FilterInputStream(buffered){public int read()throws IOException{if(left[0]==0)return -1;int b=super.read();if(b<0)throw new EOFException();left[0]--;return b;}public int read(byte[] b,int off,int len)throws IOException{if(left[0]==0)return -1;int n=super.read(b,off,(int)Math.min(len,left[0]));if(n<0)throw new EOFException();left[0]-=n;return n;}};
        MessageDigest hash;try{hash=MessageDigest.getInstance("SHA-256");}catch(Exception error){throw new IOException(error);}
        DataInputStream in=new DataInputStream(new java.security.DigestInputStream(body,hash));
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION||!in.readUTF().equals(fingerprint)||!in.readUTF().equals(TapMod.preparationSignature()))throw new IOException("预计算版本、谱面或设置不匹配");in.readLong();
        ByteArrayOutputStream geometry=new ByteArrayOutputStream();writeGeometry(new DataOutputStream(geometry),profile,view,bounds);byte[] expected=geometry.toByteArray(),actual=new byte[expected.length];in.readFully(actual);if(!Arrays.equals(expected,actual))throw new IOException("预计算屏幕设置不匹配");
        for(int i=0;i<6;i++){int metric=in.readInt();if(metric<0||metric>10000000)throw new IOException("预计算统计损坏");}
        int count=in.readInt(),total=0,lastId=Integer.MIN_VALUE;long lastStart=Long.MIN_VALUE;if(count<0||count>100000)throw new IOException("预计算触点数量无效");
        for(int n=0;n<count;n++){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("已取消触点缓存检查");
            int id=in.readInt();long start=in.readLong(),end=in.readLong();if(start<Integer.MIN_VALUE||end>Integer.MAX_VALUE||end<=start||start<lastStart||start==lastStart&&id<lastId)throw new IOException("预计算时间顺序损坏");lastStart=start;lastId=id;in.readBoolean();in.readBoolean();int samples=in.readInt();if(samples<2||samples>MAX_SAMPLES-total)throw new IOException("预计算采样数量无效");total+=samples;
            long previous=Long.MIN_VALUE;boolean finiteBefore=false;
            for(int i=0;i<samples;i++){int t=in.readInt();float x=in.readFloat(),y=in.readFloat();int connection=in.readUnsignedByte();boolean finite=Float.isFinite(x)&&Float.isFinite(y);if(t<start||t>end||i==0&&t!=start||i==samples-1&&t!=end||i>0&&(t<=previous||t-previous>STEP_MS)||Float.isFinite(x)!=Float.isFinite(y)||Float.isInfinite(x)||Float.isInfinite(y)||finite&&(x<bounds.left||x>bounds.right||y<bounds.top||y>bounds.bottom)||connection>1||connection==1&&(i==0||!finite||!finiteBefore))throw new IOException("预计算坐标损坏");previous=t;finiteBefore=finite;}
            int critical=in.readInt();if(critical<1||critical>samples)throw new IOException("预计算边界无效");previous=Long.MIN_VALUE;for(int i=0;i<critical;i++){int t=in.readInt();if(t<start||t>end||t<=previous||i==0&&t!=start||i==critical-1&&t!=end)throw new IOException("预计算边界损坏");previous=t;}
        }
        if(left[0]!=0)throw new IOException("预计算文件含多余数据");byte[] footer=new byte[32];new DataInputStream(buffered).readFully(footer);if(buffered.read()!=-1||!MessageDigest.isEqual(hash.digest(),footer))throw new IOException("预计算文件校验失败");
    }
    static byte[] encode(GesturePlan plan,PreparedTrajectory p,String fingerprint)throws IOException{
        if(fingerprint==null||fingerprint.isEmpty()||fingerprint.length()>512)throw new IOException("谱面指纹无效");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(fingerprint);out.writeUTF(p.settings);out.writeLong(p.salt);
        writeGeometry(out,p.profile,p.view,p.bounds);
        for(int metric:new int[]{plan.connectedGroups,plan.connectedNotes,plan.coveredDrags,plan.denseDragSections,plan.denseDragNotes,plan.unavailableNotes})out.writeInt(metric);
        out.writeInt(plan.events.size());
        for(TouchEvent e:plan.events){Track t=p.tracks.get(e);out.writeInt(e.getNote().getId());out.writeLong(e.getStartMs());out.writeLong(e.getEndMs());out.writeBoolean(t.continuous);out.writeBoolean(t.flick);out.writeInt(t.time.length);
            for(int i=0;i<t.time.length;i++){out.writeInt(t.time[i]);out.writeFloat(t.x[i]);out.writeFloat(t.y[i]);out.writeByte(t.connected[i]);}
            out.writeInt(t.critical.length);for(int time:t.critical)out.writeInt(time);
            if(bytes.size()>MAX_BYTES-32)throw new IOException("预计算文件超过容量");
        }
        out.flush();byte[] body=bytes.toByteArray();bytes.write(digest(body));return bytes.toByteArray();
    }
    static GesturePlan decode(Chart chart,GestureProfile profile,Viewport view,TouchBounds bounds,byte[] encoded,String fingerprint)throws IOException{
        if(encoded==null||encoded.length<64||encoded.length>MAX_BYTES)throw new IOException("预计算文件大小无效");
        byte[] body=Arrays.copyOf(encoded,encoded.length-32),sum=Arrays.copyOfRange(encoded,encoded.length-32,encoded.length);
        if(!MessageDigest.isEqual(digest(body),sum))throw new IOException("预计算文件校验失败");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(body));
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION||!in.readUTF().equals(fingerprint))throw new IOException("谱面预计算版本或指纹不匹配");
        String settings=in.readUTF();long salt=in.readLong();if(!settings.equals(TapMod.preparationSignature()))throw new IOException("触控设置已改变，需要重新预计算");
        ByteArrayOutputStream geometry=new ByteArrayOutputStream();writeGeometry(new DataOutputStream(geometry),profile,view,bounds);byte[] expected=geometry.toByteArray(),actual=new byte[expected.length];in.readFully(actual);if(!Arrays.equals(expected,actual))throw new IOException("屏幕或手势设置已改变，需要重新预计算");
        int[] metrics=new int[6];for(int i=0;i<metrics.length;i++){metrics[i]=in.readInt();if(metrics[i]<0||metrics[i]>10000000)throw new IOException("预计算统计损坏");}
        int count=in.readInt();if(count<0||count>100000)throw new IOException("预计算触点数量无效");
        Map<Integer,Note> notes=new HashMap<>();for(Note note:chart.getNotes())if(notes.put(note.getId(),note)!=null)throw new IOException("谱面音符编号不唯一");
        PreparedTrajectory prepared=new PreparedTrajectory(profile,view,bounds,settings,salt);List<TouchEvent> events=new ArrayList<>();int total=0;long lastStart=Long.MIN_VALUE;int lastId=Integer.MIN_VALUE;
        for(int n=0;n<count;n++){
            Note note=notes.get(in.readInt());long start=in.readLong(),end=in.readLong();if(note==null||start<Integer.MIN_VALUE||end>Integer.MAX_VALUE||end<=start)throw new IOException("预计算音符已改变");
            if(start<lastStart||start==lastStart&&note.getId()<lastId)throw new IOException("预计算手势顺序损坏");lastStart=start;lastId=note.getId();
            TouchEvent event=new TouchEvent(note,start,end);Track track=new Track();track.continuous=in.readBoolean();track.flick=in.readBoolean();int samples=in.readInt();if(samples<2||samples>MAX_SAMPLES-total)throw new IOException("预计算采样数量无效");total+=samples;
            track.time=new int[samples];track.x=new float[samples];track.y=new float[samples];track.connected=new byte[samples];
            for(int i=0;i<samples;i++){
                int t=in.readInt();float x=in.readFloat(),y=in.readFloat();byte connection=in.readByte();
                if(t<start||t>end||i>0&&(t<=track.time[i-1]||(long)t-track.time[i-1]>STEP_MS)||connection<0||connection>1||Float.isFinite(x)!=Float.isFinite(y)||Float.isInfinite(x)||Float.isInfinite(y)||Float.isFinite(x)&&(x<bounds.left||x>bounds.right||y<bounds.top||y>bounds.bottom))throw new IOException("预计算坐标损坏");
                if(connection!=0&&(i==0||!Float.isFinite(x)||!Float.isFinite(track.x[i-1])))throw new IOException("预计算连接损坏");
                track.time[i]=t;track.x[i]=x;track.y[i]=y;track.connected[i]=connection;
            }
            if(track.time[0]!=start||track.time[samples-1]!=end)throw new IOException("预计算轨迹不完整");
            int critical=in.readInt();if(critical<1||critical>samples)throw new IOException("预计算边界数量无效");track.critical=new int[critical];
            for(int i=0;i<critical;i++){int t=in.readInt();if(t<start||t>end||i>0&&t<=track.critical[i-1]||Arrays.binarySearch(track.time,t)<0)throw new IOException("预计算边界损坏");track.critical[i]=t;}
            if(track.critical[0]!=start||track.critical[critical-1]!=end)throw new IOException("预计算边界不完整");
            for(int i=1;i<samples;i++)if(track.connected[i]==0&&(Arrays.binarySearch(track.critical,track.time[i-1])<0||Arrays.binarySearch(track.critical,track.time[i])<0))throw new IOException("预计算间隙边界不完整");
            track.index();events.add(event);prepared.tracks.put(event,track);
        }
        if(in.available()!=0)throw new IOException("预计算文件含多余数据");
        GesturePlan plan=GesturePlan.fromPrepared(events,metrics,prepared);TapMod.preparationSalt(chart,salt);return plan;
    }
    private static byte[] digest(byte[] data)throws IOException{try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(Exception e){throw new IOException(e);}}
    private static void writeGeometry(DataOutputStream out,GestureProfile p,Viewport v,TouchBounds b)throws IOException{
        out.writeBoolean(p.connectDrags);out.writeBoolean(p.catchDrags);out.writeBoolean(p.connectFlicks);out.writeBoolean(p.combineShots);out.writeInt(p.gapMs);out.writeFloat(p.distance);
        for(float f:new float[]{v.getLeft(),v.getTop(),v.getWidth(),v.getHeight(),b.left,b.top,b.right,b.bottom})out.writeFloat(f);
    }
}
