package io.github.phiscript.engine;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.DoublePredicate;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: /workspace/scratch/2b9f58baff87/analysis/classes11.dex */
public final class BlockAreaSet {
    private static final ThreadLocal<BlockAreaSet> pending = new ThreadLocal<>();
    public static void clear(){pending.remove();}
    private final BlockArea[] normal;
    private final BlockArea[] subtract;
    private final ActiveIndex normalIndex,subtractIndex;
    private final double[] changeTimes;
    private boolean singleInvert;
    // Radius in units of viewport height: 10 physical pixels at 1080p.
    private static final double TOUCH_RADIUS = 1.0 / 108.0;
    private static final Map<Object, BlockAreaSet> MAP = Collections.synchronizedMap(new WeakHashMap());
    private static final BlockAreaSet EMPTY = new BlockAreaSet(new BlockArea[0], new BlockArea[0]);
    private static final int AREA_HEADER_BYTES = 6 * Double.BYTES + 3 * Integer.BYTES;
    private static final int KEY_BYTES = 3 * Double.BYTES + 2;
    private static final int ANCHORED_KEY_BYTES = KEY_BYTES + 2 * Double.BYTES;

    private BlockAreaSet(BlockArea[] blockAreaArr, BlockArea[] blockAreaArr2) {
        this.normal = blockAreaArr;
        this.subtract = blockAreaArr2;
        this.normalIndex=new ActiveIndex(blockAreaArr);this.subtractIndex=new ActiveIndex(blockAreaArr2);
        ArrayList<Double> times=new ArrayList<>();
        for(BlockArea[] group:new BlockArea[][]{normal,subtract})for(BlockArea area:group){
            if(area.appear>=area.disappear)continue;
            times.add(area.appear);times.add(area.disappear);
            for(double[] track:new double[][]{area.move.time,area.rotate.time,area.scale.time})
                for(double time:track)if(time>area.appear && time<area.disappear)times.add(time);
        }
        Collections.sort(times);
        double[] unique=new double[times.size()];int size=0;
        for(double time:times)if(size==0 || time!=unique[size-1])unique[size++]=time;
        changeTimes=Arrays.copyOf(unique,size);
    }

    /** Sample both sides of a chart event, including extremely short enabled intervals. */
    private boolean atChanges(double from,double to,DoublePredicate test){
        int i=Arrays.binarySearch(changeTimes,from);
        if(i<0)i=-i-1;
        double previous=from;
        for(;i<changeTimes.length && changeTimes[i]<=to;i++){
            double time=changeTimes[i];
            if(time>previous && (test.test(previous+(time-previous)*.5) || test.test(Math.nextDown(time))))return true;
            if(test.test(time))return true;
            previous=time;
        }
        return previous!=from && previous<to && test.test(previous+(to-previous)*.5);
    }

    public static boolean blockedAtChanges(Chart chart,Viewport view,Point p,Point rounded,double from,double to){
        BlockAreaSet set=MAP.get(chart);
        if(set==null || set.changeTimes.length==0)return false;
        double offset=chart.getOffsetSeconds(),aspect=(double)view.getWidth()/view.getHeight();
        double x=(p.getX()-view.getLeft())/view.getWidth(),y=1.0-(p.getY()-view.getTop())/view.getHeight();
        double rx=(rounded.getX()-view.getLeft())/view.getWidth(),ry=1.0-(rounded.getY()-view.getTop())/view.getHeight();
        return set.atChanges(from+offset,to+offset,t->set.touchCovered(x,y,t,aspect) || set.touchCovered(rx,ry,t,aspect));
    }

    /** Detect a moving touch crossing a static or translating rectangle between samples.
     * Movement easing is a piecewise-linear 101-entry table; split at its knots,
     * then solve the relative touch/rectangle motion, including subtractive holes.
     * Rotating/scaling intervals retain the existing sampled checks in TapMod.
     */
    public static boolean crossesTranslation(Chart chart,Viewport view,Point first,Point last,double from,double to){
        BlockAreaSet set=MAP.get(chart);
        if(set==null||(set.normal.length==0&&set.subtract.length==0)||to<from)return false;
        double offset=chart.getOffsetSeconds(),a=from+offset,b=to+offset,aspect=(double)view.getWidth()/view.getHeight();
        double x0=(first.getX()-view.getLeft())/view.getWidth(),y0=1-(first.getY()-view.getTop())/view.getHeight();
        double x1=(last.getX()-view.getLeft())/view.getWidth(),y1=1-(last.getY()-view.getTop())/view.getHeight();
        if(set.touchCovered(x0,y0,a,aspect)||set.touchCovered(x1,y1,b,aspect))return true;
        if(a==b)return false;
        ArrayList<BlockArea> active=new ArrayList<>();
        if(set.normal.length!=0)set.normalIndex.collectWindow(1,0,set.normal.length,a,b,active);
        if(set.subtract.length!=0)set.subtractIndex.collectWindow(1,0,set.subtract.length,a,b,active);
        if(active.isEmpty())return false;
        java.util.TreeSet<Double> times=new java.util.TreeSet<>();times.add(a);times.add(b);
        for(BlockArea area:active){
            timeCut(times,area.appear,a,b);timeCut(times,area.disappear,a,b);
            for(Track track:new Track[]{area.move,area.rotate,area.scale}){
                int trackStart=Arrays.binarySearch(track.time,a);if(trackStart<0)trackStart=-trackStart-1;
                // Include the first keyframe after b: its segment may span the
                // whole query. Do not scan the rest of a long animation track.
                for(int i=trackStart;i<track.time.length&&(i==0||track.time[i-1]<b);i++){
                    double stamp=track.time[i];timeCut(times,stamp,a,b);
                    if(track!=area.move||i==0||stamp<=a||track.time[i-1]>=b)continue;
                    // Linear/constant segments need no easing subdivisions.
                    if(track.easeX[i-1]==0&&track.easeY[i-1]==0)continue;
                    double start=track.time[i-1],span=stamp-start;
                    if(span<=0)continue;
                    int lo=Math.max(1,(int)Math.floor((a-start)/span*100)),hi=Math.min(99,(int)Math.ceil((b-start)/span*100));
                    for(int k=lo;k<=hi;k++)timeCut(times,start+span*k/100,a,b);
                }
            }
        }
        ArrayList<Double> cuts=new ArrayList<>(times);
        for(int i=1;i<cuts.size();i++){
            double left=cuts.get(i-1),right=cuts.get(i),l=Math.nextUp(left),r=Math.nextDown(right),mid=left+(right-left)*.5;
            java.util.TreeSet<Double> crossings=new java.util.TreeSet<>();crossings.add(0.0);crossings.add(1.0);
            boolean exact=false;
            for(BlockArea area:active){
                if(mid<area.appear||mid>=area.disappear||!area.translationOnly(l,r))continue;
                double[] p=area.local(lerp(x0,x1,(l-a)/(b-a)),lerp(y0,y1,(l-a)/(b-a)),l,aspect);
                double[] q=area.local(lerp(x0,x1,(r-a)/(b-a)),lerp(y0,y1,(r-a)/(b-a)),r,aspect);
                if(p==null||q==null)continue;exact=true;
                cutCrossings(crossings,area,p,q,l,aspect,TOUCH_RADIUS);
                if(set.subtract.length!=0)cutCrossings(crossings,area,p,q,l,aspect,-TOUCH_RADIUS);
            }
            if(!exact)continue;
            double previous=0;
            for(double f:crossings){
                double t=lerp(l,r,(previous+f)*.5),u=(t-a)/(b-a);
                if(set.touchCovered(lerp(x0,x1,u),lerp(y0,y1,u),t,aspect))return true;
                t=lerp(l,r,f);u=(t-a)/(b-a);
                if(set.touchCovered(lerp(x0,x1,u),lerp(y0,y1,u),t,aspect))return true;
                previous=f;
            }
        }
        // A step may be active only at the exact keyframe, outside either limit.
        for(double t:cuts){double u=(t-a)/(b-a);if(set.touchCovered(lerp(x0,x1,u),lerp(y0,y1,u),t,aspect))return true;}
        return false;
    }
    private static void cutCrossings(java.util.Set<Double> crossings,BlockArea area,double[] p,double[] q,
                                     double time,double aspect,double radius){
        BlockArea.Geometry g=area.geometry(time,aspect);
        double x=radius/(aspect*g.sx)+1e-8,y=radius/g.sy+1e-8;
        crossing(crossings,p[0],q[0],area.minX-x);crossing(crossings,p[0],q[0],area.maxX+x);
        crossing(crossings,p[1],q[1],area.minY-y);crossing(crossings,p[1],q[1],area.maxY+y);
    }
    private static double lerp(double a,double b,double f){return a+(b-a)*f;}
    private static void timeCut(java.util.Set<Double> out,double time,double from,double to){if(time>from&&time<to)out.add(time);}
    private static void crossing(java.util.Set<Double> out,double a,double b,double edge){if(a==b)return;double f=(edge-a)/(b-a);if(f>0&&f<1)out.add(f);}

    public static void capture(JSONObject jSONObject) {
        BlockArea blockArea;
        if (jSONObject == null) {
            pending.remove();
            return;
        }
        try {
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray(jSONObject.has("blockAreaList")?"blockAreaList":"BlockAreaList");
            if((jSONObject.has("blockAreaList")||jSONObject.has("BlockAreaList"))&&jSONArrayOptJSONArray==null)throw new IllegalArgumentException(tr("噪域列表格式无效"));
            if(jSONArrayOptJSONArray!=null&&jSONArrayOptJSONArray.length()>32768)throw new IllegalArgumentException(tr("噪域数量超过 32768"));
            if (jSONArrayOptJSONArray != null) {
                ArrayList arrayList = new ArrayList(jSONArrayOptJSONArray.length());
                ArrayList arrayList2 = new ArrayList();
                for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                    JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i);
                    if (jSONObjectOptJSONObject != null && (blockArea = BlockArea.parse(jSONObjectOptJSONObject)) != null) {
                        if (blockArea.isSubtract) {
                            arrayList2.add(blockArea);
                        } else {
                            arrayList.add(blockArea);
                        }
                    }else throw new IllegalArgumentException(tr("噪域数据缺少坐标或事件无效：")+i);
                }
                BlockAreaSet captured=new BlockAreaSet((BlockArea[]) arrayList.toArray(new BlockArea[arrayList.size()]), (BlockArea[]) arrayList2.toArray(new BlockArea[arrayList2.size()]));
                captured.singleInvert=jSONObject.optBoolean("phiscriptNoiseSingleInvert",false);pending.set(captured);
                Log.i("BlockAreaSet", "captured areas=" + arrayList.size() + "+" + arrayList2.size());
                return;
            }
            pending.set(EMPTY);
        } catch (Exception th) {
            pending.remove();throw new IllegalArgumentException(tr("无法读取噪域数据：")+th.getMessage(),th);
        }
    }

    public static void bind(Object obj) {
        int length;
        if (obj == null) {
            return;
        }
        BlockAreaSet blockAreaSet = pending.get();
        pending.remove();
        MAP.put(obj, blockAreaSet == null ? EMPTY : blockAreaSet);
        StringBuilder sbAppend = new StringBuilder().append("bind chart, areas=");
        if (blockAreaSet == null) {
            length = 0;
        } else {
            length = blockAreaSet.subtract.length + blockAreaSet.normal.length;
        }
        Log.i("BlockAreaSet", sbAppend.append(length).toString());
    }

    public static boolean hasMetadata(Object obj){BlockAreaSet b=MAP.get(obj);return b!=null&&b!=EMPTY;}

    /** Numeric noise timeline for checking the actual submission time of prepared touches. */
    public static void writeCompiled(DataOutputStream out, Chart chart) throws IOException {
        BlockAreaSet set = MAP.get(chart);
        if (set == null || set == EMPTY) {
            out.writeByte(0);
            return;
        }
        out.writeByte(set.singleInvert ? 2 : 1);
        out.writeInt(set.normal.length);
        out.writeInt(set.subtract.length);
        for (BlockArea[] group : new BlockArea[][] {set.normal, set.subtract}) {
            for (BlockArea area : group) {
                out.writeDouble(area.minX);
                out.writeDouble(area.maxX);
                out.writeDouble(area.minY);
                out.writeDouble(area.maxY);
                out.writeDouble(area.appear);
                out.writeDouble(area.disappear);
                writeTrack(out, area.move, null);
                writeTrack(out, area.scale, area.scaleAnchor);
                writeTrack(out, area.rotate, area.rotateAnchor);
            }
        }
    }

    private static void writeTrack(DataOutputStream out, Track track, Anchors anchors) throws IOException {
        out.writeInt(track.time.length);
        for (int i = 0; i < track.time.length; i++) {
            out.writeDouble(track.time[i]);
            out.writeDouble(track.x[i]);
            out.writeDouble(track.y[i]);
            out.writeByte(track.easeX[i]);
            out.writeByte(track.easeY[i]);
            if (anchors != null) {
                out.writeDouble(anchors.x[i]);
                out.writeDouble(anchors.y[i]);
            }
        }
    }

    /** Reads from the enclosing length-bounded plan buffer. Only bind after the whole section validates. */
    public static void readCompiled(DataInputStream in, Chart chart) throws IOException {
        int flags = in.readUnsignedByte();
        if (flags == 0) {
            MAP.put(chart, EMPTY);
            return;
        }
        int normalCount = in.readInt();
        int subtractCount = in.readInt();
        long count = (long) normalCount + subtractCount;
        if (flags > 2 || normalCount < 0 || subtractCount < 0 || count > 32768 ||
                count * AREA_HEADER_BYTES > in.available()) throw invalidCompiled();
        BlockArea[] normal = new BlockArea[normalCount];
        BlockArea[] subtract = new BlockArea[subtractCount];
        for (BlockArea[] group : new BlockArea[][] {normal, subtract}) {
            for (int i = 0; i < group.length; i++) {
                double minX = readFinite(in), maxX = readFinite(in);
                double minY = readFinite(in), maxY = readFinite(in);
                double appear = readFinite(in), disappear = readFinite(in);
                if (maxX < minX || maxY < minY) throw invalidCompiled();
                double cx = (minX + maxX) * .5, cy = (minY + maxY) * .5;
                Track move = readTrack(in, readKeyCount(in, KEY_BYTES), cx, cy, null);
                Anchors scaleAnchor = new Anchors(readKeyCount(in, ANCHORED_KEY_BYTES), cx, cy);
                Track scale = readTrack(in, scaleAnchor.time.length, 1, 1, scaleAnchor);
                Anchors rotateAnchor = new Anchors(readKeyCount(in, ANCHORED_KEY_BYTES), cx, cy);
                Track rotate = readTrack(in, rotateAnchor.time.length, 0, 0, rotateAnchor);
                group[i] = new BlockArea(minX, maxX, minY, maxY, appear, disappear, group == subtract,
                        move, scale, rotate, scaleAnchor, rotateAnchor);
            }
        }
        BlockAreaSet set = new BlockAreaSet(normal, subtract);
        set.singleInvert = flags == 2;
        MAP.put(chart, set);
    }

    private static int readKeyCount(DataInputStream in, int bytesPerKey) throws IOException {
        int count = in.readInt();
        if (count < 0 || (long) count * bytesPerKey > in.available()) throw invalidCompiled();
        return count;
    }

    private static Track readTrack(DataInputStream in, int count, double x, double y, Anchors anchors) throws IOException {
        Track track = new Track(count, x, y);
        for (int i = 0; i < count; i++) {
            track.time[i] = readFinite(in);
            track.x[i] = readFinite(in);
            track.y[i] = readFinite(in);
            track.easeX[i] = in.readUnsignedByte();
            track.easeY[i] = in.readUnsignedByte();
            if ((i > 0 && track.time[i] < track.time[i - 1]) || track.easeX[i] > 14 || track.easeY[i] > 14)
                throw invalidCompiled();
            if (anchors != null) {
                anchors.time[i] = track.time[i];
                anchors.x[i] = readFinite(in);
                anchors.y[i] = readFinite(in);
            }
        }
        return track;
    }

    private static double readFinite(DataInputStream in) throws IOException {
        double value = in.readDouble();
        if (!Double.isFinite(value)) throw invalidCompiled();
        return value;
    }

    private static IOException invalidCompiled() {
        return new IOException(tr("噪域列表格式无效"));
    }

    public static int count(Object obj) {
        BlockAreaSet blockAreaSet = obj == null ? null : MAP.get(obj);
        if (blockAreaSet == null) {
            return 0;
        }
        return blockAreaSet.normal.length + blockAreaSet.subtract.length;
    }

    public static boolean covers(Object obj, float f, float f2, float f3, float f4, float f5, float f6, double d) {
        boolean z;
        BlockAreaSet blockAreaSet = obj == null ? null : MAP.get(obj);
        if (blockAreaSet == null || blockAreaSet == EMPTY || f5 <= 0.0f || f6 <= 0.0f || Double.isNaN(d) || Double.isInfinite(d)) {
            return false;
        }
        if(obj instanceof Chart)d += ((Chart)obj).getOffsetSeconds();
        double d2 = (f - f3) / f5;
        double d3 = (f2 - f4) / f6;
        Double.isNaN(d3);
        double d4 = 1.0d - d3;
        double aspect=(double)f5/f6;
        return blockAreaSet.covered(d2,d4,d,aspect);
    }

    private boolean covered(double x,double y,double t,double aspect){
        int inverted=subtractIndex.count(x,y,t,aspect,0);
        return normalIndex.covers(x,y,t,aspect,0) ^ (singleInvert?inverted==1:(inverted&1)!=0);
    }

    public static boolean touchesNoise(Chart chart,Viewport view,Point point,double seconds){
        BlockAreaSet set=MAP.get(chart);
        if(set==null||set==EMPTY)return false;
        double x=(point.getX()-view.getLeft())/view.getWidth();
        double y=1.0-(point.getY()-view.getTop())/view.getHeight();
        return set.touchCovered(x,y,seconds+chart.getOffsetSeconds(),(double)view.getWidth()/view.getHeight());
    }

    /** Bound all inside/outside states of the touch footprint before composing the XOR mask.
     * Expanding a subtractive rectangle unconditionally would enlarge safe holes. */
    private boolean touchCovered(double x,double y,double t,double aspect){
        int possible=subtractIndex.count(x,y,t,aspect,TOUCH_RADIUS);
        if(possible==0)return normalIndex.covers(x,y,t,aspect,TOUCH_RADIUS);
        int certain=subtractIndex.count(x,y,t,aspect,-TOUCH_RADIUS);
        boolean mayInvert=singleInvert ? certain<=1 && possible>=1 : possible!=certain || (certain&1)!=0;
        boolean mayKeep=singleInvert ? certain!=1 || possible!=1 : possible!=certain || (certain&1)==0;
        boolean unsafe=(mayKeep && normalIndex.covers(x,y,t,aspect,TOUCH_RADIUS))
            || (mayInvert && !normalIndex.covers(x,y,t,aspect,-TOUCH_RADIUS));
        if(!unsafe || possible==certain || (possible==1 && normal.length==0) || covered(x,y,t,aspect))return unsafe;
        // A coincident positive and subtractive edge cannot change membership
        // independently. Preserve cancellation through the whole footprint,
        // including where translating or rotating edges exactly coincide.
        return uncertainMaskCanBlock(x,y,t,aspect);
    }

    private static final class Footprint {
        final BlockArea.Geometry g;
        final double width,height;
        int inversions;
        boolean positive,definite;
        Footprint(BlockArea.Geometry g,double width,double height){this.g=g;this.width=width;this.height=height;}
        boolean same(BlockArea.Geometry other,double w,double h){
            return g.x==other.x && g.y==other.y && g.c==other.c && g.s==other.s && width==w && height==h;
        }
    }

    private boolean uncertainMaskCanBlock(double x,double y,double time,double aspect){
        ArrayList<BlockArea> active=new ArrayList<>();
        if(normal.length>0)normalIndex.collectWindow(1,0,normal.length,time,time,active);
        if(subtract.length>0)subtractIndex.collectWindow(1,0,subtract.length,time,time,active);
        ArrayList<Footprint> shapes=new ArrayList<>();
        for(BlockArea area:active){
            if(!area.covers(x,y,time,aspect,TOUCH_RADIUS))continue;
            BlockArea.Geometry geometry=area.geometry(time,aspect);
            double w=(area.maxX-area.minX)*geometry.sx,h=(area.maxY-area.minY)*geometry.sy;
            Footprint match=null;
            for(Footprint existing:shapes)if(existing.same(geometry,w,h)){match=existing;break;}
            if(match==null){match=new Footprint(geometry,w,h);shapes.add(match);}
            if(area.isSubtract)match.inversions++;else match.positive=true;
            match.definite|=area.covers(x,y,time,aspect,-TOUCH_RADIUS);
        }
        // Six states: positive union on/off, and zero, one or multiple
        // inversions (parity instead when the chart uses parity inversion).
        int states=1;
        for(Footprint shape:shapes){
            int next=shape.definite?0:states;
            for(int state=0;state<6;state++)if((states&(1<<state))!=0){
                int count=state%3+shape.inversions;
                int result=(state>=3||shape.positive?3:0)+(singleInvert?Math.min(2,count):count&1);
                next|=1<<result;
            }
            states=next;
        }
        return (states&((1<<1)|(1<<3)|(1<<5)))!=0;
    }

    public static boolean overlaps(Chart chart,long fromMs,long toMs) {
        BlockAreaSet set=MAP.get(chart);
        if(set==null)return false;
        double offset=chart.getOffsetSeconds();
        double from=fromMs/1000.0+offset,to=toMs/1000.0+offset;
        return (set.normal.length>0&&set.normalIndex.overlaps(1,0,set.normal.length,from,to)) || (set.subtract.length>0&&set.subtractIndex.overlaps(1,0,set.subtract.length,from,to));
    }

    /** Cut a screen-space line at all active positive and subtractive rectangle edges. */
    public static void cuts(Chart chart, Viewport v, Point p, float dx, float dy, double t,
                            double low, double high, java.util.List<Double> edges) {
        BlockAreaSet set=MAP.get(chart);
        if(set==null)return;
        set.cutAt(v,p,dx,dy,t+chart.getOffsetSeconds(),low,high,edges);
    }
    public static void cutsAtChanges(Chart chart,Viewport v,Point p,float dx,float dy,double from,double to,
                                     double low,double high,java.util.List<Double> edges){
        BlockAreaSet set=MAP.get(chart);
        if(set==null || set.changeTimes.length==0)return;
        double offset=chart.getOffsetSeconds();
        set.atChanges(from+offset,to+offset,t->{set.cutAt(v,p,dx,dy,t,low,high,edges);return false;});
    }
    private void cutAt(Viewport v,Point p,float dx,float dy,double stamp,
                       double low,double high,java.util.List<Double> edges){
        double x=(p.getX()-v.getLeft())/v.getWidth(),y=1.0-(p.getY()-v.getTop())/v.getHeight();
        double aspect=(double)v.getWidth()/v.getHeight();
        for(ActiveIndex index:new ActiveIndex[]{normalIndex,subtractIndex})
            if(index.areas.length!=0)index.edges(1,0,index.areas.length,x,y,dx/v.getWidth(),-dy/v.getHeight(),stamp,aspect,low,high,edges,subtract.length!=0);
    }
    public static boolean clip(double p,double step,double low,double high,double[] limits) {
        if(Math.abs(step)<1e-13)return p>=low && p<=high;
        double a=(low-p)/step,b=(high-p)/step;
        limits[0]=Math.max(limits[0],Math.min(a,b));limits[1]=Math.min(limits[1],Math.max(a,b));
        return limits[0]<=limits[1];
    }

    /** Interval index avoids scanning thousands of inactive areas for every touch sample. */
    private static final class ActiveIndex {
        final BlockArea[] areas;final double[] maxEnd;
        ActiveIndex(BlockArea[] source){areas=source.clone();java.util.Arrays.sort(areas,(a,b)->Double.compare(a.appear,b.appear));maxEnd=new double[Math.max(1,areas.length*4)];if(areas.length>0)build(1,0,areas.length);}
        double build(int node,int lo,int hi){if(hi-lo==1)return maxEnd[node]=areas[lo].disappear;int mid=(lo+hi)>>>1;return maxEnd[node]=Math.max(build(node*2,lo,mid),build(node*2+1,mid,hi));}
        boolean overlaps(int node,int lo,int hi,double left,double right) {
            if(maxEnd[node]<=left || areas[lo].appear>right)return false;
            if(hi-lo==1)return areas[lo].appear<areas[lo].disappear;
            int mid=(lo+hi)>>>1;
            return overlaps(node*2,lo,mid,left,right)||overlaps(node*2+1,mid,hi,left,right);
        }
        void collectWindow(int node,int lo,int hi,double left,double right,java.util.List<BlockArea> out){
            if(maxEnd[node]<=left||areas[lo].appear>right)return;
            if(hi-lo==1){if(areas[lo].appear<areas[lo].disappear)out.add(areas[lo]);return;}
            int mid=(lo+hi)>>>1;collectWindow(node*2,lo,mid,left,right,out);collectWindow(node*2+1,mid,hi,left,right,out);
        }
        void edges(int node,int lo,int hi,double x,double y,double dx,double dy,double t,double aspect,
                   double low,double high,java.util.List<Double> out,boolean both) {
            if(maxEnd[node]<=t || areas[lo].appear>t)return;
            if(hi-lo==1) {
                if(t<areas[lo].disappear){
                    areas[lo].edges(x,y,dx,dy,t,aspect,low,high,out,TOUCH_RADIUS);
                    if(both)areas[lo].edges(x,y,dx,dy,t,aspect,low,high,out,-TOUCH_RADIUS);
                }
                return;
            }
            int mid=(lo+hi)>>>1;
            edges(node*2,lo,mid,x,y,dx,dy,t,aspect,low,high,out,both);
            edges(node*2+1,mid,hi,x,y,dx,dy,t,aspect,low,high,out,both);
        }
        boolean covers(double x,double y,double time,double aspect,double radius){return areas.length>0&&find(1,0,areas.length,x,y,time,aspect,radius);}
        int count(double x,double y,double time,double aspect,double radius){return areas.length==0?0:count(1,0,areas.length,x,y,time,aspect,radius);}
        int count(int node,int lo,int hi,double x,double y,double time,double aspect,double radius){
            if(maxEnd[node]<=time||areas[lo].appear>time)return 0;
            if(hi-lo==1)return areas[lo].covers(x,y,time,aspect,radius)?1:0;
            int mid=(lo+hi)>>>1;return count(node*2,lo,mid,x,y,time,aspect,radius)+count(node*2+1,mid,hi,x,y,time,aspect,radius);
        }
        boolean find(int node,int lo,int hi,double x,double y,double time,double aspect,double radius){
            if(maxEnd[node]<=time||areas[lo].appear>time)return false;
            if(hi-lo==1)return areas[lo].covers(x,y,time,aspect,radius);
            int mid=(lo+hi)>>>1;return find(node*2,lo,mid,x,y,time,aspect,radius)||find(node*2+1,mid,hi,x,y,time,aspect,radius);
        }
    }

    private static final class Track {
        final double defaultX;
        final double defaultY;
        final int[] easeX;
        final int[] easeY;
        final double[] time;
        final double[] x;
        final double[] y;

        Track(int i, double d, double d2) {
            this.time = new double[i];
            this.x = new double[i];
            this.y = new double[i];
            this.easeX = new int[i];
            this.easeY = new int[i];
            this.defaultX = d;
            this.defaultY = d2;
        }

        double value(double d, boolean z) {
            int length = this.time.length;
            if (length != 0) {
                int i = 0;
                if (d >= this.time[0]) {
                    int i2 = length - 1;
                    int i3 = i2;
                    int i4 = 0;
                    while (i <= i3) {
                        int i5 = (i + i3) >>> 1;
                        if (this.time[i5] <= d) {
                            i = i5 + 1;
                            i4 = i5;
                        } else {
                            i3 = i5 - 1;
                        }
                    }
                    double d2 = z ? this.x[i4] : this.y[i4];
                    if (i4 >= i2) {
                        return d2;
                    }
                    int i6 = i4 + 1;
                    double d3 = this.time[i6] - this.time[i4];
                    return d2 + (((z ? this.x[i6] : this.y[i6]) - d2) * BlockAreaSet.ease(d3 <= 0.0d ? 1.0d : (d - this.time[i4]) / d3, z ? this.easeX[i4] : this.easeY[i4]));
                }
            }
            return z ? this.defaultX : this.defaultY;
        }
    }

    private static final class Anchors {
        final double defaultX;
        final double defaultY;
        final double[] time;
        final double[] x;
        final double[] y;

        Anchors(int i, double d, double d2) {
            this.time = new double[i];
            this.x = new double[i];
            this.y = new double[i];
            this.defaultX = d;
            this.defaultY = d2;
        }

        double value(double d, boolean z) {
            int length = this.time.length;
            if (length != 0) {
                int i = 0;
                if (d >= this.time[0]) {
                    int i2 = length - 1;
                    int i3 = 0;
                    while (i <= i2) {
                        int i4 = (i + i2) >>> 1;
                        if (this.time[i4] <= d) {
                            i = i4 + 1;
                            i3 = i4;
                        } else {
                            i2 = i4 - 1;
                        }
                    }
                    return z ? this.x[i3] : this.y[i3];
                }
            }
            return z ? this.defaultX : this.defaultY;
        }
    }

    private static final class BlockArea {
        final double appear;
        final double centerX;
        final double centerY;
        final double disappear;
        final boolean isSubtract;
        final double maxX;
        final double maxY;
        final double minX;
        final double minY;
        final Track move;
        final Track rotate;
        final Anchors rotateAnchor;
        final Track scale;
        final Anchors scaleAnchor;
        private final double[] scaledCenterX,scaledCenterY;
        private RotationPrefix[] rotationPrefixes;
        private int nextRotationPrefix,nextGeometry;
        private Geometry[] geometries;
        private Geometry recentGeometry;
        // Ring retention remains 96 immutable exact-time snapshots. The lookup
        // index has a fixed 256 primitive slots; no boxing or temporal rounding.
        private int[] geometryLookup;

        /** Immutable exact-time samples; no interpolation or temporal rounding. */
        private static final class Geometry {
            final long time,aspect;final double x,y,sx,sy,c,s;
            Geometry(double t,double aspect,double x,double y,double sx,double sy,double angle){
                time=Double.doubleToLongBits(t);this.aspect=Double.doubleToLongBits(aspect);
                this.x=x;this.y=y;this.sx=sx;this.sy=sy;c=Math.cos(angle);s=Math.sin(angle);
            }
        }
        /** Completed rotations as affine transforms in (aspect*x,y) space. */
        private static final class RotationPrefix {
            final long aspect;final double[] a,b,c,d,x,y;
            RotationPrefix(Track rotation,Anchors anchor,double metric){
                aspect=Double.doubleToLongBits(metric);int n=rotation.time.length;
                a=new double[n];b=new double[n];c=new double[n];d=new double[n];x=new double[n];y=new double[n];a[0]=d[0]=1;
                for(int i=0;i+1<n;i++){
                    double theta=Math.toRadians(rotation.x[i+1]-rotation.x[i]),cos=Math.cos(theta),sin=Math.sin(theta),ax=anchor.x[i]*metric,ay=anchor.y[i];
                    a[i+1]=cos*a[i]-sin*c[i];b[i+1]=cos*b[i]-sin*d[i];
                    c[i+1]=sin*a[i]+cos*c[i];d[i+1]=sin*b[i]+cos*d[i];
                    double dx=x[i]-ax,dy=y[i]-ay;x[i+1]=ax+dx*cos-dy*sin;y[i+1]=ay+dx*sin+dy*cos;
                }
            }
        }

        private BlockArea(double d, double d2, double d3, double d4, double d5, double d6, boolean z, Track track, Track track2, Track track3, Anchors anchors, Anchors anchors2) {
            this.minX = d;
            this.maxX = d2;
            this.minY = d3;
            this.maxY = d4;
            this.centerX = (d + d2) * 0.5d;
            this.centerY = (d3 + d4) * 0.5d;
            this.appear = d5;
            this.disappear = d6;
            this.isSubtract = z;
            this.move = track;
            this.scale = track2;
            this.rotate = track3;
            this.scaleAnchor = anchors;
            this.rotateAnchor = anchors2;
            int count=Math.max(1,scale.time.length);scaledCenterX=new double[count];scaledCenterY=new double[count];scaledCenterX[0]=centerX;scaledCenterY[0]=centerY;
            for(int i=0;i+1<scale.time.length;i++){
                scaledCenterX[i+1]=scaleAnchor.x[i]+(scaledCenterX[i]-scaleAnchor.x[i])*ratio(scale.x[i+1],scale.x[i]);
                scaledCenterY[i+1]=scaleAnchor.y[i]+(scaledCenterY[i]-scaleAnchor.y[i])*ratio(scale.y[i+1],scale.y[i]);
            }
        }

        static BlockArea parse(JSONObject jSONObject) {
            try {
                JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("topRightPercentage");
                JSONObject jSONObjectOptJSONObject2 = jSONObject.optJSONObject("bottomLeftPercentage");
                if (jSONObjectOptJSONObject != null && jSONObjectOptJSONObject2 != null) {
                    double dOptDouble = jSONObjectOptJSONObject.optDouble("x", 0.5d);
                    double dOptDouble2 = jSONObjectOptJSONObject.optDouble("y", 0.5d);
                    double dOptDouble3 = jSONObjectOptJSONObject2.optDouble("x", 0.5d);
                    double dOptDouble4 = jSONObjectOptJSONObject2.optDouble("y", 0.5d);
                    if(!Double.isFinite(dOptDouble)||!Double.isFinite(dOptDouble2)||!Double.isFinite(dOptDouble3)||!Double.isFinite(dOptDouble4))return null;
                    double dMin = Math.min(dOptDouble, dOptDouble3);
                    double dMax = Math.max(dOptDouble, dOptDouble3);
                    double dMin2 = Math.min(dOptDouble2, dOptDouble4);
                    double dMax2 = Math.max(dOptDouble2, dOptDouble4);
                    double d = (dMin + dMax) * 0.5d;
                    double d2 = (dMin2 + dMax2) * 0.5d;
                    Track track = parseTrack(jSONObject.optJSONArray("moveEvents"), 0, d, d2);
                    Track track2 = parseTrack(jSONObject.optJSONArray("scaleEvents"), 1, 1.0d, 1.0d);
                    Track track3 = parseTrack(jSONObject.optJSONArray("rotateEvents"), 2, 0.0d, 0.0d);
                    Anchors anchors = parseAnchors(jSONObject.optJSONArray("scaleEvents"), d, d2);
                    Anchors anchors2 = parseAnchors(jSONObject.optJSONArray("rotateEvents"), d, d2);
                    double dOptDouble5 = jSONObject.optDouble("appearTime", 0.0d);
                    return new BlockArea(dMin, dMax, dMin2, dMax2, Math.max(dOptDouble5, jSONObject.optDouble("enableTime", dOptDouble5)), Math.min(jSONObject.optDouble("disableTime", Double.MAX_VALUE), jSONObject.optDouble("disappearTime", Double.MAX_VALUE)), jSONObject.optBoolean("isSubtract", false), track, track2, track3, anchors, anchors2);
                }
                return null;
            } catch (Throwable th) {
                return null;
            }
        }

        private static Track parseTrack(JSONArray jSONArray, int i, double d, double d2) {
            ArrayList<JSONObject> arrayListCollect = collect(jSONArray);
            sort(arrayListCollect);
            Track track = new Track(arrayListCollect.size(), d, d2);
            for (int i2 = 0; i2 < arrayListCollect.size(); i2++) {
                JSONObject jSONObject = arrayListCollect.get(i2);
                track.time[i2] = jSONObject.optDouble("time", 0.0d);
                track.easeX[i2] = jSONObject.optInt("easeTypeX", jSONObject.optInt("easeType", 0));
                track.easeY[i2] = jSONObject.optInt("easeTypeY", jSONObject.optInt("easeType", 0));
                if (i == 0) {
                    JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("endPosition");
                    track.x[i2] = jSONObjectOptJSONObject == null ? d : jSONObjectOptJSONObject.optDouble("x", d);
                    track.y[i2] = jSONObjectOptJSONObject == null ? d2 : jSONObjectOptJSONObject.optDouble("y", d2);
                } else if (i == 1) {
                    JSONObject jSONObjectOptJSONObject2 = jSONObject.optJSONObject("scale");
                    track.x[i2] = jSONObjectOptJSONObject2 == null ? d : jSONObjectOptJSONObject2.optDouble("x", d);
                    track.y[i2] = jSONObjectOptJSONObject2 == null ? d2 : jSONObjectOptJSONObject2.optDouble("y", d2);
                } else {
                    double dOptDouble = jSONObject.optDouble("rotation", d);
                    track.x[i2] = dOptDouble;
                    track.y[i2] = dOptDouble;
                }
            }
            for(int n=0;n<track.time.length;n++)if(!Double.isFinite(track.time[n])||!Double.isFinite(track.x[n])||!Double.isFinite(track.y[n])||track.easeX[n]<0||track.easeX[n]>14||track.easeY[n]<0||track.easeY[n]>14)throw new IllegalArgumentException(tr("噪域事件数字或缓动类型无效"));
            return track;
        }

        private static Anchors parseAnchors(JSONArray jSONArray, double d, double d2) {
            ArrayList<JSONObject> arrayListCollect = collect(jSONArray);
            sort(arrayListCollect);
            Anchors anchors = new Anchors(arrayListCollect.size(), d, d2);
            for (int i = 0; i < arrayListCollect.size(); i++) {
                JSONObject jSONObject = arrayListCollect.get(i);
                anchors.time[i] = jSONObject.optDouble("time", 0.0d);
                JSONObject jSONObjectOptJSONObject = jSONObject.optJSONObject("anchor");
                anchors.x[i] = jSONObjectOptJSONObject == null ? d : jSONObjectOptJSONObject.optDouble("x", d);
                anchors.y[i] = jSONObjectOptJSONObject == null ? d2 : jSONObjectOptJSONObject.optDouble("y", d2);
            }
            for(int n=0;n<anchors.time.length;n++)if(!Double.isFinite(anchors.time[n])||!Double.isFinite(anchors.x[n])||!Double.isFinite(anchors.y[n]))throw new IllegalArgumentException(tr("噪域锚点无效"));
            return anchors;
        }

        private static ArrayList<JSONObject> collect(JSONArray jSONArray) {
            int length = jSONArray == null ? 0 : jSONArray.length();
            ArrayList<JSONObject> arrayList = new ArrayList<>(length);
            if (jSONArray != null) {
                for (int i = 0; i < length; i++) {
                    JSONObject jSONObjectOptJSONObject = jSONArray.optJSONObject(i);
                    if (jSONObjectOptJSONObject != null) {
                        arrayList.add(jSONObjectOptJSONObject);
                    }
                }
            }
            return arrayList;
        }

        private static void sort(ArrayList<JSONObject> arrayList) {
            for (int i = 1; i < arrayList.size(); i++) {
                JSONObject jSONObject = arrayList.get(i);
                double dOptDouble = jSONObject.optDouble("time", 0.0d);
                int i2 = i - 1;
                while (i2 >= 0 && arrayList.get(i2).optDouble("time", 0.0d) > dOptDouble) {
                    arrayList.set(i2 + 1, arrayList.get(i2));
                    i2--;
                }
                arrayList.set(i2 + 1, jSONObject);
            }
        }

        // Resolve the native transform in authoring percentages. Completed
        // anchor deltas remain in the center after a later key changes anchor.
        double[] center(double t,double aspect){
            Geometry g=geometry(t,aspect);return new double[]{g.x,g.y};
        }
        private synchronized Geometry geometry(double t,double aspect){
            long timeBits=Double.doubleToLongBits(t),aspectBits=Double.doubleToLongBits(aspect);
            Geometry recent=recentGeometry;
            if(recent!=null&&recent.time==timeBits&&recent.aspect==aspectBits)return recent;
            if(geometryLookup!=null){
                int slot=geometryHash(timeBits,aspectBits),entry;
                while((entry=geometryLookup[slot])!=0){
                    Geometry cached=geometries[entry-1];
                    if(cached.time==timeBits&&cached.aspect==aspectBits){recentGeometry=cached;return cached;}
                    slot=(slot+1)&255;
                }
            }
            int at=last(scale.time,t);double x=at<0?centerX:scaledCenterX[at],y=at<0?centerY:scaledCenterY[at];
            double scaleX=scale.value(t,true),scaleY=scale.value(t,false),rotation=rotate.value(t,true);
            if(at>=0&&at+1<scale.time.length){
                x=scaleAnchor.x[at]+(x-scaleAnchor.x[at])*ratio(scaleX,scale.x[at]);
                y=scaleAnchor.y[at]+(y-scaleAnchor.y[at])*ratio(scaleY,scale.y[at]);
            }
            at=last(rotate.time,t);
            if(at>=0&&rotate.time.length>1){
                if(at>0){
                    if(rotationPrefixes==null)rotationPrefixes=new RotationPrefix[2];RotationPrefix prefix=null;
                    for(RotationPrefix cached:rotationPrefixes)if(cached!=null&&cached.aspect==aspectBits){prefix=cached;break;}
                    if(prefix==null){prefix=new RotationPrefix(rotate,rotateAnchor,aspect);rotationPrefixes[nextRotationPrefix]=prefix;nextRotationPrefix=(nextRotationPrefix+1)%rotationPrefixes.length;}
                    double metricX=x*aspect,metricY=y;
                    x=(prefix.a[at]*metricX+prefix.b[at]*metricY+prefix.x[at])/aspect;
                    y=prefix.c[at]*metricX+prefix.d[at]*metricY+prefix.y[at];
                }
                if(at+1<rotate.time.length){
                    double theta=Math.toRadians(rotation-rotate.x[at]),cos=Math.cos(theta),sin=Math.sin(theta),ax=rotateAnchor.x[at],ay=rotateAnchor.y[at];
                    double dx=(x-ax)*aspect,dy=y-ay;x=ax+(dx*cos-dy*sin)/aspect;y=ay+dx*sin+dy*cos;
                }
            }
            Geometry result=new Geometry(t,aspect,x+move.value(t,true)-centerX,y+move.value(t,false)-centerY,Math.abs(scaleX),Math.abs(scaleY),Math.toRadians(rotation));
            if(geometries==null){geometries=new Geometry[96];geometryLookup=new int[256];}
            if(geometries[nextGeometry]!=null)removeGeometry(nextGeometry);
            geometries[nextGeometry]=result;insertGeometry(nextGeometry);recentGeometry=result;
            nextGeometry=(nextGeometry+1)%geometries.length;return result;
        }
        private static int geometryHash(long time,long aspect){long key=time^Long.rotateLeft(aspect,17);key^=key>>>33;key*=0xff51afd7ed558ccdL;return (int)(key^(key>>>32))&255;}
        private void insertGeometry(int index){
            Geometry value=geometries[index];int slot=geometryHash(value.time,value.aspect);
            while(geometryLookup[slot]!=0)slot=(slot+1)&255;
            geometryLookup[slot]=index+1;
        }
        private void removeGeometry(int index){
            Geometry old=geometries[index];int slot=geometryHash(old.time,old.aspect);
            while(geometryLookup[slot]!=index+1)slot=(slot+1)&255;
            // Close the hole by shifting only entries whose probe chain passes
            // through it. No tombstones, reallocation or quadratic reinsertion.
            int hole=slot;
            for(slot=(slot+1)&255;geometryLookup[slot]!=0;slot=(slot+1)&255){
                Geometry following=geometries[geometryLookup[slot]-1];int home=geometryHash(following.time,following.aspect);
                if(((slot-home)&255)>=((slot-hole)&255)){geometryLookup[hole]=geometryLookup[slot];hole=slot;}
            }
            geometryLookup[hole]=0;
        }
        static int last(double[] times,double t){int lo=0,hi=times.length;while(lo<hi){int mid=(lo+hi)>>>1;if(times[mid]<=t)lo=mid+1;else hi=mid;}return lo-1;}
        static double ratio(double value,double previous){return Math.abs(previous)<1e-38?1:value/previous;}
        void edges(double x,double y,double dx,double dy,double t,double aspect,double low,double high,java.util.List<Double> out,double radius) {
            double[] p=local(x,y,t,aspect),q=local(x+dx,y+dy,t,aspect);if(p==null||q==null)return;
            double[] span={low,high};
            Geometry g=geometry(t,aspect);
            double px=radius/(aspect*g.sx)+1e-8,py=radius/g.sy+1e-8;
            if(minX-px>maxX+px||minY-py>maxY+py)return;
            if(clip(p[0],q[0]-p[0],minX-px,maxX+px,span)&&clip(p[1],q[1]-p[1],minY-py,maxY+py,span)){out.add(span[0]);out.add(span[1]);}
        }
        boolean covers(double x,double y,double time,double aspect,double radius) {
            if(time<appear||time>=disappear)return false;
            Geometry g=geometry(time,aspect);if(g.sx<1e-6||g.sy<1e-6)return false;
            double dx=(x-g.x)*aspect,dy=y-g.y;
            double localX=centerX+(dx*g.c+dy*g.s)/aspect/g.sx;
            double localY=centerY+(-dx*g.s+dy*g.c)/g.sy;
            double px=radius/(aspect*g.sx)+1e-8,py=radius/g.sy+1e-8;
            return localX>=minX-px && localX<=maxX+px && localY>=minY-py && localY<=maxY+py;
        }
        boolean translationOnly(double a,double b){
            return scale.value(a,true)==scale.value(b,true)&&scale.value(a,false)==scale.value(b,false)&&rotate.value(a,true)==rotate.value(b,true);
        }
        double[] local(double x,double y,double time,double aspect){
            Geometry g=geometry(time,aspect);if(g.sx<1e-6||g.sy<1e-6)return null;
            double dx=(x-g.x)*aspect,dy=y-g.y;
            return new double[]{centerX+(dx*g.c+dy*g.s)/aspect/g.sx,centerY+(-dx*g.s+dy*g.c)/g.sy};
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean inside(double d, double d2, double[] dArr, double[] dArr2) {
        int length = dArr.length - 1;
        boolean z = false;
        for (int i = 0; i < dArr.length; i++) {
            if ((dArr2[i] > d2) != (dArr2[length] > d2) && d < (((dArr[length] - dArr[i]) * (d2 - dArr2[i])) / (dArr2[length] - dArr2[i])) + dArr[i]) {
                z = !z;
            }
            length = i;
        }
        return z;
    }

    static double ease(double p,int type){
        if(type==13)return 0;if(type==14)return 1;
        double q=Math.max(0,Math.min(1,p))*100;int k=(int)q;
        if(k>=100)return 1;
        return easeRaw(k/100.0,type)+(easeRaw((k+1)/100.0,type)-easeRaw(k/100.0,type))*(q-k);
    }
    private static double easeRaw(double d, int i) {
        if (d <= 0.0d) {
            return 0.0d;
        }
        if (d >= 1.0d) {
            return 1.0d;
        }
        if (i == 13) {
            return 0.0d;
        }
        if (i == 14) {
            return 1.0d;
        }
        if (i <= 0) {
            return d;
        }
        int i2 = i - 1;
        int i3 = (i2 / 3) + 2;
        int i4 = i2 % 3;
        if (i4 == 0) {
            return Math.pow(d, i3);
        }
        if (i4 == 1) {
            return 1.0d - Math.pow(1.0d - d, i3);
        }
        if (d >= 0.5d) {
            return 1.0d - (Math.pow(2.0d - (d * 2.0d), i3) * 0.5d);
        }
        return Math.pow(d * 2.0d, i3) * 0.5d;
    }
}
