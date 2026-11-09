package io.github.phiscript.assets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Conservative active-time barriers; this does not implement noise geometry. */
public final class NoiseWindowReader {
    private static final double LIMIT=7200;
    private NoiseWindowReader(){}
    public static JSONArray read(JSONObject root)throws JSONException {
        JSONArray saved=root.optJSONArray("phiscriptNoiseWindows");
        if(saved!=null)return checked(saved);
        List<double[]> spans=new ArrayList<>();
        JSONArray blocks=root.optJSONArray("blockAreaList");
        if(blocks==null)blocks=root.optJSONArray("BlockAreaList");
        Clock clock=null;
        if(blocks!=null){
            if(blocks.length()>10000)throw invalid("blockAreaList 数量超过 10000");
            for(int i=0;i<blocks.length();i++){
                JSONObject area=blocks.getJSONObject(i);String at="blockAreaList["+i+"]";
                boolean next=area.has("bottomLeft")||area.has("topRight"),nativeArea=area.has("bottomLeftPercentage")||area.has("topRightPercentage");
                if(next==nativeArea)throw invalid(at+" 矩形格式未知或冲突");
                if(next){
                    if(!area.has("bottomLeft")||!area.has("topRight"))throw invalid(at+" 缺少矩形边界");
                    if(clock==null)clock=new Clock(root.getJSONArray("BPMList"));
                    double appear=clock.seconds(area.getJSONArray("appearTime")),disappear=clock.seconds(area.getJSONArray("disappearTime"));
                    if(appear>disappear)throw invalid(at+" 生命周期无效");
                    JSONArray intervals=area.optJSONArray("activeIntervals");if(intervals==null)continue;
                    if(intervals.length()>10000)throw invalid(at+" 激活区间过多");
                    for(int j=0;j<intervals.length();j++){
                        JSONObject interval=intervals.getJSONObject(j);double a=clock.seconds(interval.getJSONArray("startTime")),b=clock.seconds(interval.getJSONArray("endTime"));
                        if(a>b)throw invalid(at+" 激活区间结束早于开始");
                        add(spans,Math.max(a,appear),Math.min(b,disappear));
                    }
                }else{
                    if(!area.has("bottomLeftPercentage")||!area.has("topRightPercentage"))throw invalid(at+" 缺少矩形边界");
                    double appear=number(area,"appearTime",0),disappear=number(area,"disappearTime",0);
                    double enable=number(area,"enableTime",0),disable=number(area,"disableTime",0);
                    if(appear>disappear||enable>disable)throw invalid(at+" 生命周期无效");
                    add(spans,Math.max(appear,enable),Math.min(disappear,disable));
                }
            }
        }
        // Line2Area image names are protocol markers. Speed starts encode
        // 1=appear, 2=active, 3=disabled, 4=hidden. Alpha-only legacy markers
        // conservatively disable linking for the chart, rather than guessing
        // the thresholds of layered/eased animation.
        JSONArray lines=root.optJSONArray("judgeLineList");
        if(lines!=null)for(int i=0;i<lines.length();i++){
            JSONObject line=lines.getJSONObject(i);String texture=line.optString("Texture","").replace('\\','/');
            texture=texture.substring(texture.lastIndexOf('/')+1);
            if(!texture.equalsIgnoreCase("isSubtract0.png")&&!texture.equalsIgnoreCase("isSubtract1.png"))continue;
            if(clock==null)clock=new Clock(root.getJSONArray("BPMList"));
            List<double[]> codes=new ArrayList<>();JSONArray layers=line.optJSONArray("eventLayers");
            if(layers!=null)for(int j=0;j<layers.length();j++){
                JSONObject layer=layers.optJSONObject(j);if(layer==null)continue;JSONArray events=layer.optJSONArray("speedEvents");if(events==null)continue;
                for(int k=0;k<events.length();k++){
                    JSONObject event=events.getJSONObject(k);double code=number(event,"start",0);
                    if(code>=1&&code<=4&&code==Math.rint(code))codes.add(new double[]{clock.seconds(event.getJSONArray("startTime")),code});
                }
            }
            if(codes.isEmpty()){add(spans,0,LIMIT);continue;}
            codes.sort((a,b)->Double.compare(a[0],b[0]));
            double active=Double.NaN;
            for(double[] code:codes){if(!Double.isNaN(active)){add(spans,active,code[0]);active=Double.NaN;}if(code[1]==2)active=code[0];}
            if(!Double.isNaN(active))add(spans,active,LIMIT);
        }
        return merged(spans);
    }
    public static JSONArray merge(JSONArray first,JSONArray second)throws JSONException{
        List<double[]> spans=new ArrayList<>();
        for(JSONArray list:new JSONArray[]{checked(first),checked(second)})for(int i=0;i<list.length();i++){JSONArray span=list.getJSONArray(i);add(spans,span.getDouble(0),span.getDouble(1));}
        return merged(spans);
    }
    private static JSONArray merged(List<double[]> spans)throws JSONException{
        spans.sort((a,b)->Double.compare(a[0],b[0]));JSONArray result=new JSONArray();
        double start=Double.NaN,end=0;
        for(double[] span:spans){if(Double.isNaN(start)){start=span[0];end=span[1];}else if(span[0]<=end)end=Math.max(end,span[1]);else{result.put(pair(start,end));start=span[0];end=span[1];}}
        if(!Double.isNaN(start))result.put(pair(start,end));return result;
    }
    private static JSONArray checked(JSONArray source)throws JSONException{
        if(source.length()>10000)throw invalid("保存的激活区间过多");
        double previous=-Double.MAX_VALUE;JSONArray result=new JSONArray();
        for(int i=0;i<source.length();i++){JSONArray p=source.getJSONArray(i);if(p.length()!=2)throw invalid("保存的激活区间格式无效");
            double a=p.getDouble(0),b=p.getDouble(1);if(!Double.isFinite(a)||!Double.isFinite(b)||a<0||a>=b||b>LIMIT||a<previous)throw invalid("保存的激活区间无效");previous=b;result.put(pair(a,b));}
        return result;
    }
    private static void add(List<double[]> spans,double a,double b){
        if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a)>1e9||Math.abs(b)>1e9)throw invalid("时间无效");
        a=Math.max(0,a);b=Math.min(LIMIT,b);if(a<b)spans.add(new double[]{a,b});
    }
    private static JSONArray pair(double a,double b)throws JSONException{return new JSONArray().put(a).put(b);}
    private static double number(JSONObject object,String key,double fallback)throws JSONException{
        double value=object.has(key)?object.getDouble(key):fallback;if(!Double.isFinite(value))throw invalid(key+" 不是有限数值");return value;
    }
    private static double beat(JSONArray p)throws JSONException{
        if(p.length()!=3)throw invalid("拍数必须是三元数组");double a=p.getDouble(0),b=p.getDouble(1),c=p.getDouble(2);
        if(!Double.isFinite(a)||!Double.isFinite(b)||!Double.isFinite(c)||c<=0)throw invalid("拍数无效");double value=a+b/c;if(!Double.isFinite(value)||Math.abs(value)>1e6)throw invalid("拍数超出范围");return value;
    }
    private static final class Clock{
        final double[] beats,bpms,times;
        Clock(JSONArray source)throws JSONException{
            if(source.length()==0||source.length()>10000)throw invalid("BPMList 数量无效");TreeMap<Double,Double> entries=new TreeMap<>();
            for(int i=0;i<source.length();i++){JSONObject item=source.getJSONObject(i);double bpm=item.getDouble("bpm");if(!Double.isFinite(bpm)||bpm<=0||bpm>10000)throw invalid("BPM 无效");entries.put(beat(item.getJSONArray("startTime")),bpm);}
            beats=new double[entries.size()];bpms=new double[entries.size()];times=new double[entries.size()];int i=0;
            for(Map.Entry<Double,Double> entry:entries.entrySet()){beats[i]=entry.getKey();bpms[i]=entry.getValue();times[i]=i==0?beats[i]*60/bpms[i]:times[i-1]+(beats[i]-beats[i-1])*60/bpms[i-1];i++;}
            // Match the chart normalizer's beat-zero origin, including maps
            // with tempo changes before zero. Audio offset remains separate.
            double zero=raw(0);for(i=0;i<times.length;i++)times[i]-=zero;
        }
        private double raw(double value){int lo=0,hi=beats.length;while(lo<hi){int m=(lo+hi)>>>1;if(beats[m]<=value)lo=m+1;else hi=m;}int i=Math.max(0,lo-1);return times[i]+(value-beats[i])*60/bpms[i];}
        double seconds(JSONArray p)throws JSONException{return raw(beat(p));}
    }
    private static IllegalArgumentException invalid(String why){return new IllegalArgumentException("噪域 "+why);}
}
