package io.github.phiscript.assets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Gameplay-only RPE conversion following Phira's event/keyframe representation. */
public final class RpeCompatibilityNormalizer {
    private static final int[] EASING={2,2,4,3,7,6,5,8,10,9,13,12,11,14,16,15,19,18,22,21,25,24,23,26,28,27,31,30,32,29};
    private int events,notes;
    private RpeCompatibilityNormalizer(){}
    public static JSONObject normalize(JSONObject root)throws JSONException{return new RpeCompatibilityNormalizer().convert(root);}
    private JSONObject convert(JSONObject root)throws JSONException{
        Clock clock=new Clock(root.getJSONArray("BPMList"));
        JSONArray source=root.getJSONArray("judgeLineList");
        if(source.length()<1||source.length()>4096)throw invalid("判定线数量无效");
        int[] parent=new int[source.length()];boolean[] used=new boolean[source.length()];
        for(int i=0;i<source.length();i++){
            JSONObject line=source.getJSONObject(i);parent[i]=line.optInt("father",-1);
            if(parent[i]<-1||parent[i]>=source.length()||parent[i]==i)throw invalid("父子线索引无效");
            JSONArray ns=line.optJSONArray("notes");if(ns!=null)for(int j=0;j<ns.length();j++)if(ns.getJSONObject(j).optInt("isFake",0)==0)used[i]=true;
        }
        // Iterative traversal avoids stack overflows for deeply nested parent lines.
        for(int i=0;i<source.length();i++){
            int p=i;int depth=0;while(p!=-1){if(++depth>source.length())throw invalid("父子线存在循环");
                if(used[i])used[p]=true;p=parent[p];}
        }
        JSONArray output=new JSONArray(),warnings=new JSONArray();
        for(int i=0;i<source.length();i++){
            JSONObject line=source.getJSONObject(i);
            if(used[i]&&line.has("attachUI")&&!line.isNull("attachUI"))throw invalid("带可演奏音符的 UI 绑定线尚未适配，不能可靠计算落点");
            JSONObject object=new JSONObject();JSONArray layers=line.optJSONArray("eventLayers");
            if(layers==null)layers=new JSONArray();if(layers.length()>64)throw invalid("事件层超过 64 层");
            String[] names={"moveXEvents","moveYEvents","rotateEvents"};String[] keys={"x","y","rotation"};double[] factors={2.0/1350,2.0/900,-1};
            for(int k=0;k<names.length;k++){
                JSONArray tracks=new JSONArray();
                for(int j=0;j<layers.length();j++){
                    JSONObject layer=layers.optJSONObject(j);if(layer==null)continue;
                    JSONArray input=layer.optJSONArray(names[k]);if(input==null||input.length()==0)continue;
                    events+=input.length();if(events>100000)throw invalid("判定线事件总数超过 100000");
                    List<Frame> frames=new ArrayList<>();
                    // Phira emits two keyframes per event and sorts by time.
                    // Preserve stable equal-time ordering, rather than rejecting
                    // legitimate overlapping editor exports or truncating notes.
                    List<JSONObject> ordered=new ArrayList<>();for(int e=0;e<input.length();e++)ordered.add(input.getJSONObject(e));
                    ordered.sort((a,b)->Double.compare(beatUnchecked(a,"startTime"),beatUnchecked(b,"startTime")));
                    for(JSONObject e:ordered){
                        double start=clock.seconds(e.getJSONArray("startTime")),end=clock.seconds(e.getJSONArray("endTime"));
                        if(end<start)throw invalid("事件结束时间早于开始时间");
                        frames.add(new Frame(start,number(e,"start",null)*factors[k],tween(e)));
                        frames.add(new Frame(end,number(e,"end",null)*factors[k],new JSONObject().put("id",0)));
                    }
                    frames.sort((a,b)->Double.compare(a.time,b.time));JSONArray array=new JSONArray();
                    for(Frame f:frames)array.put(new JSONObject().put("time",f.time).put("value",f.value).put("tween",f.tween));
                    tracks.put(array);
                }
                object.put(keys[k],tracks);
            }
            JSONArray ns=line.optJSONArray("notes"),outNotes=new JSONArray();
            if(ns!=null){if(ns.length()>100000)throw invalid("单线音符数量过多");
                for(int j=0;j<ns.length();j++){
                    JSONObject n=ns.getJSONObject(j);if(n.optInt("isFake",0)!=0)continue;
                    if(++notes>100000)throw invalid("音符总数超过 100000");
                    int type=n.getInt("type");int mapped;
                    switch(type){case 1:mapped=1;break;case 2:mapped=3;break;case 3:mapped=4;break;case 4:mapped=2;break;default:throw invalid("未知音符类型："+type);}
                    double time=clock.seconds(n.getJSONArray("startTime"));
                    double end=type==2?clock.seconds(n.getJSONArray("endTime")):time;
                    if(time<0||end<time||end>7200)throw invalid("音符时间无效（最多两小时）");
                    int above=n.optInt("above",1);if(above==0)above=2;if(above!=1&&above!=2)throw invalid("音符 above 无效");
                    JSONObject position=new JSONObject().put("time",0).put("value",number(n,"positionX",null)*2/1350)
                        .put("tween",new JSONObject().put("id",0));
                    outNotes.put(new JSONObject().put("type",mapped).put("time",time).put("endTime",end)
                        .put("object",new JSONObject().put("x",new JSONArray().put(new JSONArray().put(position))))
                        .put("above",above==1).put("speed",number(n,"speed",1.0)).put("height",0).put("fake",false));
                }
            }
            output.put(new JSONObject().put("object",object).put("notes",outNotes).put("parent",parent[i]).put("rotWithParent",line.optBoolean("rotateWithFather",false)));
        }
        if(notes==0)throw invalid("没有可演奏音符");
        warnings.put("仅计算音符时刻和判定线落点；视觉、音效、视频与脚本不在本应用中渲染。新版判定扩展及 UI 绑定谱不保证兼容。");
        JSONObject meta=root.optJSONObject("META");
        return new JSONObject().put("phiscriptFormat",1).put("offset",meta==null?0:number(meta,"offset",0.0)/1000)
            .put("lines",output).put("phiscriptWarnings",warnings);
    }
    private static double beatUnchecked(JSONObject e,String key){try{return beat(e.getJSONArray(key));}catch(JSONException ex){throw invalid("事件拍数缺失");}}
    private static final class Frame{final double time,value;final JSONObject tween;Frame(double time,double value,JSONObject tween){this.time=time;this.value=value;this.tween=tween;}}
    private static JSONObject tween(JSONObject e)throws JSONException{
        int easing=Math.max(1,e.optInt("easingType",1));int id=easing<EASING.length?EASING[easing]:EASING[0];
        JSONObject out=new JSONObject().put("id",id);
        if(e.optInt("bezier",0)!=0){
            JSONArray p=e.getJSONArray("bezierPoints");if(p.length()!=4)throw invalid("贝塞尔参数数量无效");
            JSONArray values=new JSONArray();for(int i=0;i<4;i++){double value=p.getDouble(i);if(!Double.isFinite(value))throw invalid("贝塞尔参数无效");
                if((i==0||i==2)&&(value<0||value>1))throw invalid("贝塞尔时间控制点应在 0–1 之间");values.put(value);}
            out.put("bezier",values);
        }else{
            double left=Math.max(0,Math.min(1,number(e,"easingLeft",0.0))),right=Math.max(0,Math.min(1,number(e,"easingRight",1.0)));
            if(id>2&&left<right&&(left!=0||right!=1))out.put("left",left).put("right",right);
        }return out;
    }
    private static double number(JSONObject object,String key,Double fallback)throws JSONException{
        if(!object.has(key)||object.isNull(key)){if(fallback==null)throw invalid("缺少字段："+key);return fallback;}
        double value=object.getDouble(key);if(!Double.isFinite(value))throw invalid("数字无效："+key);return value;
    }
    private static double beat(JSONArray triple)throws JSONException{
        if(triple.length()!=3)throw invalid("拍数必须是三元数组");
        double a=triple.getDouble(0),b=triple.getDouble(1),c=triple.getDouble(2);
        if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(c)||c<=0)throw invalid("拍数分母或数值无效");
        double beat=a+b/c;if(!Double.isFinite(beat)||Math.abs(beat)>1000000)throw invalid("拍数超出范围");return beat==0?0:beat;
    }
    private static final class Clock{
        final double[] beats,bpms,times;
        Clock(JSONArray array)throws JSONException{
            if(array.length()<1||array.length()>10000)throw invalid("BPM 数量无效");
            TreeMap<Double,Double> tempos=new TreeMap<>();
            for(int i=0;i<array.length();i++){JSONObject t=array.getJSONObject(i);double bpm=number(t,"bpm",null);
                if(bpm<=0||bpm>10000)throw invalid("BPM 无效");tempos.put(beat(t.getJSONArray("startTime")),bpm);}
            beats=new double[tempos.size()];bpms=new double[tempos.size()];times=new double[tempos.size()];int i=0;
            for(Map.Entry<Double,Double> t:tempos.entrySet()){beats[i]=t.getKey();bpms[i]=t.getValue();
                // Anchor beat zero even when the first BPM begins at a nonzero/negative beat.
                times[i]=i==0?beats[i]*60/bpms[i]:times[i-1]+(beats[i]-beats[i-1])*60/bpms[i-1];i++;}
            double zero=raw(0);for(i=0;i<times.length;i++)times[i]-=zero;
        }
        private double raw(double beat){int low=0,high=beats.length;while(low<high){int mid=(low+high)>>>1;if(beats[mid]<=beat)low=mid+1;else high=mid;}
            int i=Math.max(0,low-1);return times[i]+(beat-beats[i])*60/bpms[i];}
        double seconds(JSONArray triple)throws JSONException{double value=raw(beat(triple));if(!Double.isFinite(value)||Math.abs(value)>1e9)throw invalid("时间超出范围");return value;}
    }
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException("RPE "+message);}
}
