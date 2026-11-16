package io.github.phiscript.engine;

import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

/* JADX INFO: loaded from: /workspace/scratch/2b9f58baff87/analysis/classes11.dex */
public final class BlockAreaSet {
    private static final ThreadLocal<BlockAreaSet> pending = new ThreadLocal<>();
    public static void clear(){pending.remove();}
    private final BlockArea[] normal;
    private final BlockArea[] subtract;
    private final ActiveIndex normalIndex,subtractIndex;
    private static final Map<Object, BlockAreaSet> MAP = Collections.synchronizedMap(new WeakHashMap());
    private static final BlockAreaSet EMPTY = new BlockAreaSet(new BlockArea[0], new BlockArea[0]);

    private BlockAreaSet(BlockArea[] blockAreaArr, BlockArea[] blockAreaArr2) {
        this.normal = blockAreaArr;
        this.subtract = blockAreaArr2;
        this.normalIndex=new ActiveIndex(blockAreaArr);this.subtractIndex=new ActiveIndex(blockAreaArr2);
    }

    public static void capture(JSONObject jSONObject) {
        BlockArea blockArea;
        if (jSONObject == null) {
            pending.remove();
            return;
        }
        try {
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("blockAreaList");
            if(jSONObject.has("blockAreaList")&&jSONArrayOptJSONArray==null)throw new IllegalArgumentException("噪域列表格式无效");
            if(jSONArrayOptJSONArray!=null&&jSONArrayOptJSONArray.length()>32768)throw new IllegalArgumentException("噪域数量超过 32768");
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
                    }else throw new IllegalArgumentException("噪域数据缺少坐标或事件无效："+i);
                }
                pending.set(new BlockAreaSet((BlockArea[]) arrayList.toArray(new BlockArea[arrayList.size()]), (BlockArea[]) arrayList2.toArray(new BlockArea[arrayList2.size()])));
                Log.i("BlockAreaSet", "captured areas=" + arrayList.size() + "+" + arrayList2.size());
                return;
            }
            pending.set(EMPTY);
        } catch (Exception th) {
            pending.remove();throw new IllegalArgumentException("无法读取噪域数据："+th.getMessage(),th);
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
        return blockAreaSet.normalIndex.covers(d2,d4,d,aspect)&&!blockAreaSet.subtractIndex.covers(d2,d4,d,aspect);
    }

    public static boolean overlaps(Chart chart,long fromMs,long toMs) {
        BlockAreaSet set=MAP.get(chart);
        if(set==null || set.normal.length==0)return false;
        double offset=chart.getOffsetSeconds();
        return set.normalIndex.overlaps(1,0,set.normal.length,fromMs/1000.0+offset,toMs/1000.0+offset);
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
        boolean covers(double x,double y,double time,double aspect){return areas.length>0&&find(1,0,areas.length,x,y,time,aspect);}
        boolean find(int node,int lo,int hi,double x,double y,double time,double aspect){
            if(maxEnd[node]<=time||areas[lo].appear>time)return false;
            if(hi-lo==1)return areas[lo].covers(x,y,time,aspect);
            int mid=(lo+hi)>>>1;return find(node*2,lo,mid,x,y,time,aspect)||find(node*2+1,mid,hi,x,y,time,aspect);
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
                    return d2 + (((z ? this.x[i6] : this.y[i6]) - d2) * BlockAreaSet.ease(d3 <= 0.0d ? 1.0d : (d - this.time[i4]) / d3, z ? this.easeX[i6] : this.easeY[i6]));
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
            for(int n=0;n<track.time.length;n++)if(!Double.isFinite(track.time[n])||!Double.isFinite(track.x[n])||!Double.isFinite(track.y[n])||track.easeX[n]<0||track.easeX[n]>14||track.easeY[n]<0||track.easeY[n]>14)throw new IllegalArgumentException("噪域事件数字或缓动类型无效");
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
            for(int n=0;n<anchors.time.length;n++)if(!Double.isFinite(anchors.time[n])||!Double.isFinite(anchors.x[n])||!Double.isFinite(anchors.y[n]))throw new IllegalArgumentException("噪域锚点无效");
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

        boolean covers(double x,double y,double time,double aspect) {
            if(time<appear||time>=disappear)return false;
            double sx=scale.value(time,true),sy=scale.value(time,false);
            if(Math.abs(sx)<1e-6||Math.abs(sy)<1e-6)return false;
            // Reverse translation, pixel-space rotation, then anchor-relative scale.
            double px=x-move.value(time,true),py=y-move.value(time,false);
            double ax=rotateAnchor.value(time,true)-centerX,ay=rotateAnchor.value(time,false)-centerY;
            double angle=Math.toRadians(rotate.value(time,true)),c=Math.cos(angle),s=Math.sin(angle);
            double dx=(px-ax)*aspect,dy=py-ay;
            px=ax+(dx*c+dy*s)/aspect;py=ay-dx*s+dy*c;
            ax=scaleAnchor.value(time,true)-centerX;ay=scaleAnchor.value(time,false)-centerY;
            px=ax+(px-ax)/sx+centerX;py=ay+(py-ay)/sy+centerY;
            return px>=minX-1e-8&&px<=maxX+1e-8&&py>=minY-1e-8&&py<=maxY+1e-8;
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
