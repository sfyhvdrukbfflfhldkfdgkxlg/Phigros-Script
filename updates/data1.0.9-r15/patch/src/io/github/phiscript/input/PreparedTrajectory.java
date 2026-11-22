package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import io.github.phiscript.engine.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dense, physical-pixel trajectories. Invalid samples and unsafe connections
 * are explicit; lookup never evaluates a chart or searches a noise region. */
public final class PreparedTrajectory {
    public static final int VERSION=3,STEP_MS=4,MAX_SAMPLES=2500000,MAX_BYTES=40*1024*1024;
    static final int MAGIC=0x50544731;
    final IdentityHashMap<TouchEvent,Track> tracks=new IdentityHashMap<>();
    final String settings;final long salt;final Viewport view;final TouchBounds bounds;
    final GestureProfile profile;
    private final PixelAxis horizontal,vertical;

    /** Preserve fractional clamps exactly at viewport edges. Interior codes
     * are pixel integers. The four edge values are shared by the whole chart. */
    private static final class PixelAxis {
        private static final int MIN=32762,MAX=32763,MIN_PLUS_ONE=32764,MAX_MINUS_ONE=32765;
        final float min,max,minPlusOne,maxMinusOne;

        PixelAxis(float min,float max){
            this.min=min;
            this.max=max;
            minPlusOne=min+1;
            maxMinusOne=max-1;
        }

        char code(float value){
            int rounded=(int)value;
            if(value==rounded&&rounded>=0&&rounded<MIN)return (char)rounded;
            if(value==min)return MIN;
            if(value==max)return MAX;
            if(value==minPlusOne)return MIN_PLUS_ONE;
            if(value==maxMinusOne)return MAX_MINUS_ONE;
            throw new IllegalArgumentException(tr("无障碍坐标超出预计算范围"));
        }

        float coordinate(int code){
            switch(code){
                case MIN:return min;
                case MAX:return max;
                case MIN_PLUS_ONE:return minPlusOne;
                case MAX_MINUS_ONE:return maxMinusOne;
                default:return code>=0&&code<MIN?code:Float.NaN;
            }
        }

        boolean contains(float value){return value>=min&&value<=max;}
    }

    private static final int ALT_Y=0x8000,ALT_CODE=0x7fff,NO_ALTERNATE=0x7fff;
    private static final class Track {
        int start;
        int[] broken,critical;
        float[] x,y;
        char[] aospX,aospY,aospAlternate;
        byte[] connected;
        boolean continuous,flick;

        void allocate(int count){
            x=new float[count];
            y=new float[count];
            connected=new byte[count];
            aospX=new char[count];
            aospY=new char[count];
            aospAlternate=new char[count];
        }

        void index(){broken=new int[x.length];for(int i=1;i<x.length;i++)broken[i]=broken[i-1]+(connected[i]==0?1:0);}
        int sample(long at,boolean previous,long previousAt){
            long clamped=Math.max(start,Math.min(start+(long)x.length-1,at));
            int i=(int)(clamped-start);
            if(!Float.isFinite(x[i])||!Float.isFinite(y[i]))throw new TapMod.NoSafePoint(tr("预计算轨迹在此时刻没有安全落点"));
            if(previous&&previousAt<clamped){
                int from=(int)(Math.max(start,previousAt)-start);
                if(broken[i]!=broken[from])throw new TapMod.NoSafePoint(tr("预计算轨迹需在此断开触点后恢复"));
            }
            return i;
        }

        Point at(long at,boolean previous,long previousAt){
            int i=sample(at,previous,previousAt);
            return new Point(x[i],y[i]);
        }

        void prepareAccessibility(int i,PixelAxis horizontal,PixelAxis vertical){
            aospAlternate[i]=NO_ALTERNATE;
            if(!Float.isFinite(x[i]))return;

            // Keep the framework continuation's rounding, clipping and one
            // pixel move order. Both endpoints are stored before any playback.
            float px=GestureChannel.clamp(Math.round(x[i]),horizontal.min,horizontal.max);
            float py=GestureChannel.clamp(Math.round(y[i]),vertical.min,vertical.max);
            aospX[i]=horizontal.code(px);
            aospY[i]=vertical.code(py);
            if(px+1<=horizontal.max)aospAlternate[i]=horizontal.code(px+1);
            else if(px-1>=horizontal.min)aospAlternate[i]=horizontal.code(px-1);
            else if(py+1<=vertical.max)aospAlternate[i]=(char)(ALT_Y|vertical.code(py+1));
            else if(py-1>=vertical.min)aospAlternate[i]=(char)(ALT_Y|vertical.code(py-1));
        }

        Point accessibilityEndpoint(long at,boolean continuous,long previousAt,float priorX,float priorY,
                PixelAxis horizontal,PixelAxis vertical){
            int i=sample(at,continuous,previousAt);
            float px=horizontal.coordinate(aospX[i]),py=vertical.coordinate(aospY[i]);
            if(Math.round(priorX)==Math.round(px)&&Math.round(priorY)==Math.round(py)){
                int alternate=aospAlternate[i];
                if(alternate==NO_ALTERNATE)throw new TapMod.NoSafePoint(tr("玩法视口太小，无法续接长键"));
                if((alternate&ALT_Y)==0)px=horizontal.coordinate(alternate);
                else py=vertical.coordinate(alternate&ALT_CODE);
            }
            return new Point(px,py);
        }
        long boundary(long now,long end){int lo=0,hi=critical.length;while(lo<hi){int mid=(lo+hi)>>>1;if(critical[mid]<=now)lo=mid+1;else hi=mid;}return lo==critical.length?end:Math.min(end,critical[lo]);}
    }
    private PreparedTrajectory(GestureProfile p,Viewport v,TouchBounds b,String settings,long salt){
        profile=p;view=v;bounds=b;this.settings=settings;this.salt=salt;
        horizontal=new PixelAxis(b.left,b.right);
        vertical=new PixelAxis(b.top,b.bottom);
    }
    boolean matches(Chart chart,GestureProfile p,Viewport v,TouchBounds b){return profile.equals(p)&&view.equals(v)&&same(bounds,b)&&settings.equals(TapMod.preparationSignature())&&salt==TapMod.preparationSalt(chart);}
    static boolean same(TouchBounds a,TouchBounds b){return a.left==b.left&&a.top==b.top&&a.right==b.right&&a.bottom==b.bottom;}
    Point point(TouchEvent event,long time,Point previous,long previousTime){Track t=tracks.get(event);if(t==null)throw new IllegalStateException(tr("预计算轨迹与手势不匹配"));return t.at(time,previous!=null,previousTime);}
    Point accessibilityEndpoint(TouchEvent event,long time,boolean continuous,long previousAt,float priorX,float priorY){
        Track track=tracks.get(event);
        if(track==null)throw new IllegalStateException(tr("预计算轨迹与手势不匹配"));
        return track.accessibilityEndpoint(time,continuous,previousAt,priorX,priorY,horizontal,vertical);
    }
    long boundary(TouchEvent e,long now,long end){Track t=tracks.get(e);return t==null?end:t.boundary(now,end);}
    boolean continuous(TouchEvent e){Track t=tracks.get(e);return t!=null&&t.continuous;}
    boolean flick(TouchEvent e){Track t=tracks.get(e);return t!=null&&t.flick;}
    boolean stationaryTap(TouchEvent event,Point point,long from,long to){
        Track t=tracks.get(event);
        if(t==null||event.getNote().getType()!=1||t.continuous||t.flick||from<t.start||to<from||to>=t.start+(long)t.x.length)return false;
        for(int i=(int)(from-t.start);i<=to-t.start;i++){
            if(t.x[i]!=point.getX()||t.y[i]!=point.getY()||(i>from-t.start&&t.connected[i]==0))return false;
        }
        return true;
    }
    public int sampleCount(){int n=0;for(Track t:tracks.values())n+=t.x.length;return n;}
    static PreparedTrajectory prepare(Chart chart,GesturePlan plan,GestureProfile profile,Viewport view,TouchBounds bounds,AtomicBoolean cancel){
        PreparedTrajectory out=new PreparedTrajectory(profile,view,bounds,TapMod.preparationSignature(),TapMod.preparationSalt(chart));
        TouchSafety safety=TouchSafety.forPreparation(bounds);int[] screen={(int)Math.ceil(view.getRight()),(int)Math.ceil(view.getBottom())};int total=0;boolean[] bounded=new boolean[1];
        for(TouchEvent event:plan.events){
            cancelled(cancel);long start=event.getStartMs(),end=event.getEndMs();
            if(start<Integer.MIN_VALUE||end>Integer.MAX_VALUE||end<=start)throw new IllegalArgumentException(tr("谱面轨迹时间超出预计算范围"));
            long count=(end-start+STEP_MS-1)/STEP_MS+1;
            ContactPlanner.Route route=plan.routes.get(event);
            NavigableSet<Long> keys=route==null?Collections.emptyNavigableSet():route.keys.navigableKeySet().subSet(start,true,end,true);
            for(long t:keys)if(t!=end&&(t-start)%STEP_MS!=0)count++;
            long milliseconds=end-start+1;
            if(milliseconds>MAX_SAMPLES-total)throw new IllegalArgumentException(tr("此谱预计算轨迹超过容量，请缩短谱面或减少持续触点"));
            total+=(int)milliseconds;
            Track track=new Track();track.continuous=plan.isContinuous(event);track.flick=plan.hasFlickPath(event);
            track.start=(int)start;
            track.allocate((int)milliseconds);
            int n=(int)count;
            int[] times=new int[n];
            boolean[] critical=new boolean[n];
            Iterator<Long> keyIterator=keys.iterator();long key=keyIterator.hasNext()?keyIterator.next():Long.MAX_VALUE,grid=start;
            // Both inputs are already ordered. Keep every exact route key and
            // every 4ms sample without boxing the dense grid into tree nodes.
            for(int i=0;i<n;i++){
                long t=Math.min(grid,key);times[i]=(int)t;critical[i]=t==start||t==end||t==key;
                if(t==grid)grid=t==end?Long.MAX_VALUE:Math.min(end,t+STEP_MS);
                if(t==key)key=keyIterator.hasNext()?keyIterator.next():Long.MAX_VALUE;
            }
            Point prior=null;long priorAt=start;
            for(int i=0;i<n;i++){int t=times[i];
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
                // Expand the certified coarse segment here. A span with no
                // safe connection has no intermediate coordinates.
                int current=(int)(t-start);
                for(long ms=priorAt+1;ms<t;ms++){
                    int index=(int)(ms-start);
                    if(connected){
                        float progress=(ms-priorAt)/(float)(t-priorAt);
                        track.x[index]=prior.getX()+(p.getX()-prior.getX())*progress;
                        track.y[index]=prior.getY()+(p.getY()-prior.getY())*progress;
                        track.connected[index]=1;
                    }else{
                        track.x[index]=Float.NaN;
                        track.y[index]=Float.NaN;
                    }
                    track.prepareAccessibility(index,out.horizontal,out.vertical);
                }
                track.x[current]=p==null?Float.NaN:p.getX();
                track.y[current]=p==null?Float.NaN:p.getY();
                track.connected[current]=(byte)(connected?1:0);
                track.prepareAccessibility(current,out.horizontal,out.vertical);
                if(i>0&&(!connected||prior==null||p==null)){critical[i-1]=true;critical[i]=true;}
                prior=p;priorAt=t;
            }
            int criticalCount=0;for(boolean mark:critical)if(mark)criticalCount++;track.critical=new int[criticalCount];int at=0;for(int i=0;i<n;i++)if(critical[i])track.critical[at++]=times[i];
            track.index();
            out.tracks.put(event,track);
        }
        cancelled(cancel);return out;
    }
    static void cancelled(AtomicBoolean cancel){if(Thread.currentThread().isInterrupted()||cancel!=null&&cancel.get())throw new IllegalStateException(tr("谱面轨迹预计算已取消"));}

    /** Commit device-specific timing, vertical translation, and hold anchoring only once. */
    GesturePlan finishDevice(Chart chart,List<TouchEvent> input,int[] metrics,AtomicBoolean cancel,GesturePlan raw){
        ContactVariation variation=TapMod.contactVariation();
        long seed=ContactVariation.chartSeed(chart);
        NavigableSet<Long> starts=new TreeSet<>();
        for(TouchEvent event:input)starts.add(event.getStartMs());
        List<TouchEvent> output=new ArrayList<>(input.size());
        for(int i=0;i<input.size();){
            cancelled(cancel);
            int end=i+1;
            while(end<input.size()&&input.get(end).getStartMs()==input.get(i).getStartMs())end++;
            List<TouchEvent> group=input.subList(i,end);
            int delay=variation.delay(seed,group,starts);
            float[] offsets=new float[group.size()];
            // If one chord member cannot move safely in time, preserve the whole chord's onset.
            if(!planVariation(chart,variation,seed,group,delay,offsets,cancel)){
                delay=0;
                planVariation(chart,variation,seed,group,0,offsets,cancel);
            }
            for(int j=0;j<group.size();j++){
                TouchEvent before=group.get(j);
                Track track=tracks.remove(before);
                TouchEvent event=delay==0?before:new TouchEvent(before.getNote(),before.getStartMs()+delay,before.getEndMs()+delay);
                tracks.put(event,track);
                track.start+=delay;
                for(int k=0;k<track.critical.length;k++)track.critical[k]+=delay;
                float dy=offsets[j];
                if(dy!=0)for(int k=0;k<track.y.length;k++)track.y[k]=bounds.y(track.y[k]+dy);
                ContactPlanner.Route route=raw==null?null:raw.routes.get(before);
                boolean anchored=(route==null||route.uses==1)&&HoldOscillation.stabilize(chart,view,event,
                        track.flick,track.x,track.y,track.connected,track.start,cancel,raw!=null);
                if(event.getNote().getType()==1&&!track.continuous&&!track.flick&&TapMod.avoids(chart)
                        &&BlockAreaSet.overlaps(chart,track.start-TapMod.noiseLookBackMs(),event.getEndMs()+TapMod.noiseLookAheadMs())
                        &&Float.isFinite(track.x[0])){
                    // Only the head is judged. Store the same contact through UP if its
                    // complete lifetime, including timing guards, clears moving noise.
                    Point head=new Point(track.x[0],track.y[0]);
                    try{
                        TapMod.segment(chart,view,head,head,track.start,event.getEndMs());
                        Arrays.fill(track.x,head.getX());
                        Arrays.fill(track.y,head.getY());
                        Arrays.fill(track.connected,1,track.connected.length,(byte)1);
                        anchored=true;
                    }catch(TapMod.NoSafePoint movingEdge){/* Keep the independently checked moving track. */}
                }
                if(dy!=0||anchored){
                    for(int k=0;k<track.y.length;k++){
                        if(!Float.isFinite(track.x[k]))track.aospX[k]=track.aospY[k]=0;
                        track.prepareAccessibility(k,horizontal,vertical);
                    }
                }
                if(anchored){
                    BitSet boundaries=new BitSet(track.y.length);
                    for(int time:track.critical)boundaries.set(time-track.start);
                    for(int k=1;k<track.y.length;k++){
                        if(track.connected[k]==0&&(Float.isFinite(track.x[k-1])||Float.isFinite(track.x[k]))){
                            boundaries.set(k-1);boundaries.set(k);
                        }
                    }
                    track.critical=new int[boundaries.cardinality()];
                    int cursor=0;
                    for(int k=boundaries.nextSetBit(0);k>=0;k=boundaries.nextSetBit(k+1))track.critical[cursor++]=track.start+k;
                    track.index();
                }
                output.add(event);
            }
            i=end;
        }
        return GesturePlan.fromPrepared(output,metrics,this);
    }

    private boolean planVariation(Chart chart,ContactVariation variation,long seed,List<TouchEvent> group,
                                  int delay,float[] offsets,AtomicBoolean cancel){
        for(int i=0;i<group.size();i++){
            TouchEvent event=group.get(i);
            Track track=tracks.get(event);
            float dy=variation.drift(seed,event,track.y,view,bounds);
            if((delay!=0||dy!=0)&&TapMod.avoids(chart)&&!track.flick&&Float.isFinite(track.x[0])){
                double head=track.start/1000.0;
                // Keep the chosen gap center at its certified chart time. A chord
                // falls back together if one member needs its original timing.
                if(BlockAreaSet.largeCoverage(chart,view,head)){
                    if(delay!=0)return false;
                    dy=0;
                }else if(dy!=0&&BlockAreaSet.nearNoise(chart,view,
                        new Point(track.x[0],bounds.y(track.y[0]+dy)),(track.start+delay)/1000.0)){
                    dy=0;
                }
            }
            if(!ContactVariation.safe(chart,view,track.start,track.x,track.y,track.connected,delay,dy,cancel)){
                if(dy==0||!ContactVariation.safe(chart,view,track.start,track.x,track.y,track.connected,delay,0,cancel))return false;
                dy=0;
            }
            offsets[i]=dy;
        }
        return true;
    }

    private static final int SAMPLE_BYTES=15;
    private static final int SAMPLES_PER_BLOCK=1024;

    private static void readSamples(DataInputStream in,int count,Track output,PixelAxis horizontal,PixelAxis vertical,ByteBuffer block)throws IOException{
        boolean previousFinite=false;
        for(int i=0;i<count;i++){
            if(i%SAMPLES_PER_BLOCK==0){
                if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException(tr("已取消触点缓存检查"));
                int size=Math.min(SAMPLES_PER_BLOCK,count-i)*SAMPLE_BYTES;
                in.readFully(block.array(),0,size);
                ((java.nio.Buffer)block).rewind();
            }
            float x=block.getFloat(),y=block.getFloat();
            int connection=block.get()&255;
            char aospX=block.getChar(),aospY=block.getChar(),alternate=block.getChar();
            boolean finite=Float.isFinite(x)&&Float.isFinite(y);
            if(Float.isFinite(x)!=Float.isFinite(y)||Float.isInfinite(x)||Float.isInfinite(y)||
                    finite&&(!horizontal.contains(x)||!vertical.contains(y))||
                    connection>1||connection==1&&(i==0||!finite||!previousFinite))
                throw new IOException(tr("预计算坐标损坏"));

            if(finite){
                float px=horizontal.coordinate(aospX),py=vertical.coordinate(aospY);
                if(!horizontal.contains(px)||!vertical.contains(py))throw new IOException(tr("预计算坐标损坏"));
                if(alternate!=NO_ALTERNATE){
                    PixelAxis axis=(alternate&ALT_Y)==0?horizontal:vertical;
                    float base=(alternate&ALT_Y)==0?px:py;
                    float next=axis.coordinate(alternate&ALT_CODE);
                    if(!axis.contains(next)||(next!=base+1&&next!=base-1))throw new IOException(tr("预计算坐标损坏"));
                }
            }else if(aospX!=0||aospY!=0||alternate!=NO_ALTERNATE){
                throw new IOException(tr("预计算坐标损坏"));
            }

            if(output!=null){
                output.x[i]=x;
                output.y[i]=y;
                output.connected[i]=(byte)connection;
                output.aospX[i]=aospX;
                output.aospY[i]=aospY;
                output.aospAlternate[i]=alternate;
            }
            previousFinite=finite;
        }
    }

    private static void writeBoundaries(ByteBuffer out,Track track){
        int first=out.position();
        ((java.nio.Buffer)out).position(first+(track.x.length+7)/8);
        for(int time:track.critical){
            int offset=time-track.start;
            int index=first+(offset>>>3);
            out.put(index,(byte)(out.get(index)|(1<<(offset&7))));
        }
    }

    private static void readBoundaries(DataInputStream in,int count,Track output)throws IOException{
        byte[] bits=new byte[(count+7)/8];
        in.readFully(bits);
        int finalBit=(count-1)&7;
        int tail=bits[bits.length-1]&255;
        if((bits[0]&1)==0||(tail&(1<<finalBit))==0||(tail>>(finalBit+1))!=0)
            throw new IOException(tr("预计算边界损坏"));
        if(output==null)return;

        int size=0;
        for(byte value:bits)size+=Integer.bitCount(value&255);
        output.critical=new int[size];
        int cursor=0;
        for(int i=0;i<bits.length;i++){
            for(int marks=bits[i]&255;marks!=0;marks&=marks-1){
                int offset=(i<<3)+Integer.numberOfTrailingZeros(marks);
                output.critical[cursor++]=output.start+offset;
            }
        }
    }

    /** The checksum follows the track body in the same stream. */
    private static final class TrackBody extends FilterInputStream {
        int remaining;
        TrackBody(InputStream in,int length){super(in);remaining=length;}
        @Override public int read()throws IOException{
            if(remaining==0)return -1;
            int value=super.read();
            if(value<0)throw new EOFException();
            remaining--;
            return value;
        }
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{
            if(length==0)return 0;
            if(remaining==0)return -1;
            int n=super.read(bytes,offset,Math.min(length,remaining));
            if(n<0)throw new EOFException();
            remaining-=n;
            return n;
        }
    }

    /** Import verification uses the same decoder, without retaining sample arrays. */
    public static void inspectPrepared(InputStream source,long length,GestureProfile profile,Viewport view,TouchBounds bounds,String fingerprint)throws IOException{
        readPrepared(null,profile,view,bounds,source,length,fingerprint);
    }
    static GesturePlan readPrepared(Chart chart,GestureProfile profile,Viewport view,TouchBounds bounds,InputStream source,long length,String fingerprint)throws IOException{
        return readPrepared(chart,profile,view,bounds,source,length,fingerprint,null,null,null);
    }
    /** The archive has already routed each touch. Convert its reference pixels once during the download. */
    static GesturePlan readCloud(Chart chart,GestureProfile profile,Viewport reference,TouchBounds referenceBounds,InputStream source,long length,String fingerprint,Viewport view,TouchBounds bounds,AtomicBoolean cancel)throws IOException{
        return readPrepared(chart,profile,reference,referenceBounds,source,length,fingerprint,view,bounds,cancel);
    }
    private static GesturePlan readPrepared(Chart chart,GestureProfile profile,Viewport view,TouchBounds bounds,InputStream source,long length,String fingerprint,Viewport destination,TouchBounds destinationBounds,AtomicBoolean cancel)throws IOException{
        if(length<64||length>MAX_BYTES)throw new IOException(tr("预计算文件大小无效"));
        BufferedInputStream buffered=new BufferedInputStream(source,16384);
        TrackBody body=new TrackBody(buffered,(int)length-32);
        MessageDigest hash;
        try{hash=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException error){throw new IOException(error);}
        DataInputStream in=new DataInputStream(new java.security.DigestInputStream(body,hash));

        if(in.readInt()!=MAGIC||in.readInt()!=VERSION||!in.readUTF().equals(fingerprint))throw new IOException(tr("谱面预计算版本或指纹不匹配"));
        String settings=in.readUTF();
        long salt=in.readLong();
        String deviceSettings=TapMod.preparationSignature();
        boolean legacyCloud=destination!=null && PreparedChartStore.LEGACY_CLOUD_SETTINGS.equals(settings);
        if(!settings.equals(deviceSettings)&&!legacyCloud)throw new IOException(tr("触控设置已改变，需要重新预计算"));
        ByteArrayOutputStream geometry=new ByteArrayOutputStream();
        writeGeometry(new DataOutputStream(geometry),profile,view,bounds);
        byte[] actual=new byte[geometry.size()];
        in.readFully(actual);
        if(!Arrays.equals(geometry.toByteArray(),actual))throw new IOException(tr("预计算屏幕设置不匹配"));
        int[] metrics=new int[6];
        for(int i=0;i<metrics.length;i++){
            metrics[i]=in.readInt();
            if(metrics[i]<0||metrics[i]>10000000)throw new IOException(tr("预计算统计损坏"));
        }
        int count=in.readInt();
        if(count<0||count>100000||26L*count>body.remaining)throw new IOException(tr("预计算触点数量无效"));
        Map<Integer,Note> notes=new HashMap<>();
        if(chart!=null)for(Note note:chart.getNotes()){
            if(notes.put(note.getId(),note)!=null)throw new IOException(tr("谱面音符编号不唯一"));
        }
        PreparedTrajectory prepared=new PreparedTrajectory(profile,destination==null?view:destination,
                destination==null?bounds:destinationBounds,deviceSettings,salt);
        PixelAxis inputX=new PixelAxis(bounds.left,bounds.right),inputY=new PixelAxis(bounds.top,bounds.bottom);
        List<TouchEvent> events=new ArrayList<>();
        ByteBuffer block=ByteBuffer.allocate(SAMPLES_PER_BLOCK*SAMPLE_BYTES);
        long lastStart=Long.MIN_VALUE;
        int total=0,lastId=Integer.MIN_VALUE;
        int emptyTracks=0;
        for(int n=0;n<count;n++){
            int id=in.readInt();
            long start=in.readLong(),end=in.readLong();
            if(start<Integer.MIN_VALUE||end>Integer.MAX_VALUE||end<=start||start<lastStart||start==lastStart&&id<lastId)
                throw new IOException(tr("预计算时间顺序损坏"));
            lastStart=start;
            lastId=id;
            int continuous=in.readUnsignedByte(),flick=in.readUnsignedByte(),samples=in.readInt();
            if(continuous>1||flick>1||samples!=end-start+1||samples>MAX_SAMPLES-total)
                throw new IOException(tr("预计算采样数量无效"));
            if((long)SAMPLE_BYTES*samples+(samples+7)/8>body.remaining)throw new EOFException();
            total+=samples;
            Track track=null;
            if(chart!=null){
                Note note=notes.get(id);
                if(note==null)throw new IOException(tr("预计算音符已改变"));
                TouchEvent event=new TouchEvent(note,start,end);
                track=new Track();
                track.continuous=continuous==1;
                track.flick=flick==1;
                track.start=(int)start;
                track.allocate(samples);
                events.add(event);
                prepared.tracks.put(event,track);
            }
            readSamples(in,samples,track,inputX,inputY,block);
            readBoundaries(in,samples,track);
            if(destination!=null&&!prepared.mapCloudTrack(track,chart,view,legacyCloud,cancel))emptyTracks++;
            if(track!=null)track.index();
        }
        if(body.remaining!=0)throw new IOException(tr("预计算文件含多余数据"));
        byte[] footer=new byte[32];
        new DataInputStream(buffered).readFully(footer);
        if(buffered.read()!=-1||!MessageDigest.isEqual(hash.digest(),footer))throw new IOException(tr("预计算文件校验失败"));
        if(chart==null)return null;
        if(count>0&&emptyTracks==count)throw new IOException(io.github.phiscript.root.UiDesign.words(
                "本机触控边界内没有可用的云端手势，请在 Swordalt 谱库按本机设置准备这张谱面。",
                "No cloud gesture fits your touch bounds. Prepare this chart locally in the Swordalt library."));
        TapMod.preparationSalt(chart,salt);
        if(legacyCloud)return prepared.finishDevice(chart,events,metrics,cancel,null);
        return GesturePlan.fromPrepared(events,metrics,prepared);
    }
    private boolean mapCloudTrack(Track track,Chart chart,Viewport source,boolean legacyCloud,AtomicBoolean cancel)throws IOException{
        float scaleX=view.getWidth()/source.getWidth(),scaleY=view.getHeight()/source.getHeight();
        boolean hasNoise=TapMod.avoids(chart),previousClipped=false;
        boolean playable=false;
        Point previous=null;
        for(int i=0;i<track.x.length;i++){
            if((i&255)==0)cancelled(cancel);
            if(!Float.isFinite(track.x[i])){previous=null;previousClipped=false;continue;}
            float x=view.getLeft()+(track.x[i]-source.getLeft())*scaleX;
            float y=view.getTop()+(track.y[i]-source.getTop())*scaleY;
            track.x[i]=bounds.x(x);track.y[i]=bounds.y(y);
            boolean clipped=track.x[i]!=x||track.y[i]!=y;
            if(hasNoise){
                Point current=new Point(track.x[i],track.y[i]);
                long time=(long)track.start+i;
                // Clipping at a system inset changes the certified segment. Do not publish a
                // modified contact across a noise region, even if both endpoints remain clear.
                if((legacyCloud||clipped||previousClipped)&&BlockAreaSet.overlaps(chart,time-1-TapMod.noiseLookBackMs(),time+TapMod.noiseLookAheadMs())){
                    Point authored=current;
                    boolean safe=TapMod.clearAt(chart,view,current,time/1000.0);
                    if(!safe || legacyCloud && !track.flick && BlockAreaSet.nearNoise(chart,view,current,time/1000.0)){
                        current=legacyCloud?nearbyCloudPoint(chart,current,time,safe):null;
                    }
                    if(current==null){
                        track.x[i]=track.y[i]=Float.NaN;
                        track.aospX[i]=track.aospY[i]=0;
                        track.aospAlternate[i]=NO_ALTERNATE;
                        track.connected[i]=0;
                        previous=null;previousClipped=false;
                        continue;
                    }
                    if(track.connected[i]!=0&&previous!=null){
                        try{TapMod.segment(chart,view,previous,current,time-1,time);}
                        catch(TapMod.NoSafePoint blocked){
                            if(safe && authored!=current){
                                try{
                                    TapMod.segment(chart,view,previous,authored,time-1,time);
                                    current=authored;
                                }catch(TapMod.NoSafePoint disconnected){track.connected[i]=0;}
                            }else track.connected[i]=0;
                        }
                    }
                    track.x[i]=current.getX();
                    track.y[i]=current.getY();
                }
                if(previous==null)track.connected[i]=0;
                previous=current;previousClipped=clipped;
            }
            track.prepareAccessibility(i,horizontal,vertical);
            playable=true;
        }
        return playable;
    }

    // Legacy cloud archives contain routed contacts but no moving judge-line normals.
    // Keep repairs local to the authored contact and certify continuity separately.
    private Point nearbyCloudPoint(Chart chart,Point origin,long time,boolean originSafe){
        float step=Math.max(1f,view.getHeight()/216f);
        Point fallback=originSafe?origin:null;
        // The cloud timeline lacks the judge-line normal. Limit the correction to
        // 3.3% of the play height, independently of orientation and display size.
        for(int ring=1;ring<=7;ring++){
            for(int direction=0;direction<8;direction++){
                double angle=direction*Math.PI/4;
                Point trial=new Point(bounds.x(origin.getX()+(float)Math.cos(angle)*step*ring),
                        bounds.y(origin.getY()+(float)Math.sin(angle)*step*ring));
                if(!TapMod.clearAt(chart,view,trial,time/1000.0))continue;
                if(!BlockAreaSet.nearNoise(chart,view,trial,time/1000.0))return trial;
                if(fallback==null)fallback=trial;
            }
        }
        return fallback;
    }
    static byte[] encode(GesturePlan plan,PreparedTrajectory p,String fingerprint)throws IOException{
        if(fingerprint==null||fingerprint.isEmpty()||fingerprint.length()>512)throw new IOException(tr("谱面指纹无效"));
        ByteArrayOutputStream header=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(header);
        out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(fingerprint);out.writeUTF(p.settings);out.writeLong(p.salt);
        writeGeometry(out,p.profile,p.view,p.bounds);
        for(int metric:new int[]{plan.connectedGroups,plan.connectedNotes,plan.coveredDrags,plan.denseDragSections,plan.denseDragNotes,plan.unavailableNotes})out.writeInt(metric);
        out.writeInt(plan.events.size());
        long length=header.size()+32L;
        for(Track track:p.tracks.values()){
            int count=track.x.length;
            length+=26+(long)SAMPLE_BYTES*count+(count+7)/8;
        }
        if(length>MAX_BYTES)throw new IOException(tr("预计算文件超过容量"));
        // Use one exact-size buffer for the potentially large millisecond table.
        // ByteBuffer's default big-endian layout matches DataInputStream.
        ByteBuffer encoded=ByteBuffer.allocate((int)length);
        encoded.put(header.toByteArray());
        for(TouchEvent e:plan.events){
            Track t=p.tracks.get(e);
            encoded.putInt(e.getNote().getId()).putLong(e.getStartMs()).putLong(e.getEndMs());
            encoded.put((byte)(t.continuous?1:0)).put((byte)(t.flick?1:0)).putInt(t.x.length);
            for(int i=0;i<t.x.length;i++){
                encoded.putFloat(t.x[i]).putFloat(t.y[i]).put(t.connected[i]);
                encoded.putChar(t.aospX[i]).putChar(t.aospY[i]).putChar(t.aospAlternate[i]);
            }
            writeBoundaries(encoded,t);
        }
        encoded.put(digest(encoded.array(),encoded.position()));
        return encoded.array();
    }
    private static byte[] digest(byte[] data,int length)throws IOException{
        try{MessageDigest sha=MessageDigest.getInstance("SHA-256");sha.update(data,0,length);return sha.digest();}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
    private static void writeGeometry(DataOutputStream out,GestureProfile p,Viewport v,TouchBounds b)throws IOException{
        out.writeBoolean(p.connectDrags);out.writeBoolean(p.catchDrags);out.writeBoolean(p.connectFlicks);out.writeBoolean(p.combineShots);out.writeInt(p.gapMs);out.writeFloat(p.distance);
        for(float f:new float[]{v.getLeft(),v.getTop(),v.getWidth(),v.getHeight(),b.left,b.top,b.right,b.bottom})out.writeFloat(f);
    }
}
