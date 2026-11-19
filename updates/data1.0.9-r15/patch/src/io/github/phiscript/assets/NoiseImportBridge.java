package io.github.phiscript.assets;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import io.github.phiscript.engine.BlockAreaSet;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Preserve noise before the native decoder normalizes and persists the selected chart.
 * Receives the decoder's already bounded, validated package map; never reopens the ZIP. */
public final class NoiseImportBridge {
    private NoiseImportBridge(){}
    public static byte[] normalizeSource(byte[] source){
        String text=new String(source,StandardCharsets.UTF_8);if(text.startsWith("\ufeff"))text=text.substring(1);
        if(!text.trim().startsWith("{"))return source;
        try{
            JSONObject root=new JSONObject(text);
            if(root.has("BlockAreaList")&&!root.has("blockAreaList"))root.put("blockAreaList",root.get("BlockAreaList"));
            if(!root.has("blockAreaList"))return source;
            convertNext(root);
            // Reject formats that cannot be evaluated, instead of saving a
            // seemingly playable chart whose noise metadata has been discarded.
            validate(root);
            return root.toString().getBytes(StandardCharsets.UTF_8);
        }catch(JSONException e){throw new IllegalArgumentException(tr("噪域元数据格式无效"),e);}
    }
    public static byte[] withPackageAreas(Map<String,byte[]> files,String chosen,byte[] source){
        byte[] normalized=normalizeSource(source);
        String text=new String(normalized,StandardCharsets.UTF_8);if(text.startsWith("\ufeff"))text=text.substring(1);
        try{
            JSONObject root=text.trim().startsWith("{")?new JSONObject(text):null;
            if(root!=null&&root.has("blockAreaList"))return normalized;
            String dir=parent(chosen),name=base(chosen),stem=name.contains(".")?name.substring(0,name.lastIndexOf('.')):name;
            List<String> siblings=new ArrayList<>();for(String path:files.keySet())if(parent(path).equals(dir)&&isSidecar(base(path)))siblings.add(path);
            String selected=unique(siblings,stem+".blockAreaList.json");
            if(selected==null)selected=unique(siblings,"blockAreaList.json");
            if(selected==null){if(siblings.size()==1)selected=siblings.get(0);else if(siblings.size()>1)throw new IllegalArgumentException(tr("包内存在多个噪域列表，请使用 <谱面名>.blockAreaList.json 指定"));}
            if(selected==null)return normalized;
            if(root==null)throw new IllegalArgumentException(tr("外置噪域列表只支持 JSON 谱面，请导出原生或 RPE JSON"));
            String side=new String(files.get(selected),StandardCharsets.UTF_8);if(side.startsWith("\ufeff"))side=side.substring(1);
            JSONObject doc=new JSONObject(side);Object areas=doc.has("blockAreaList")?doc.get("blockAreaList"):doc.get("BlockAreaList");
            root.put("blockAreaList",areas);convertNext(root);validate(root);
            return root.toString().getBytes(StandardCharsets.UTF_8);
        }catch(JSONException e){throw new IllegalArgumentException(tr("无法读取关联的噪域列表：")+chosen,e);}
    }
    private static String parent(String name){int at=name.lastIndexOf('/');return at<0?"":name.substring(0,at);}
    private static String base(String name){return name.substring(name.lastIndexOf('/')+1);}
    private static boolean isSidecar(String name){String lower=name.toLowerCase(Locale.ROOT);return lower.equals("blockarealist.json")||lower.endsWith(".blockarealist.json");}
    private static String unique(List<String> files,String target){String selected=null;for(String name:files)if(base(name).equalsIgnoreCase(target)){if(selected!=null)throw new IllegalArgumentException(tr("噪域列表名称存在大小写冲突：")+target);selected=name;}return selected;}
    private static void validate(JSONObject root)throws JSONException{
        JSONArray list=root.getJSONArray("blockAreaList");
        for(int i=0;i<list.length();i++){
            JSONObject area=list.getJSONObject(i);
            if(area.has("bottomLeft")||area.has("topRight"))throw new IllegalArgumentException("blockAreaList["+i+tr("] 为 Next 拍数噪域，当前版本尚不支持其变换，请导出原生秒制噪域；未导入以免漏打"));
        }
        try{BlockAreaSet.capture(root);}finally{BlockAreaSet.clear();}
    }
    /** Next static rectangles share RPE's stretched 1350x900 canvas. Animation
     * needs a distinct cumulative-transform evaluator and is rejected explicitly. */
    public static void convertNext(JSONObject root)throws JSONException{
        if(!root.has("blockAreaList"))return;
        JSONArray list=root.getJSONArray("blockAreaList");boolean next=false,nativeAreas=false;
        for(int i=0;i<list.length();i++){JSONObject a=list.getJSONObject(i);next|=a.has("bottomLeft")||a.has("topRight");nativeAreas|=a.has("bottomLeftPercentage")||a.has("topRightPercentage");}
        if(!next)return;
        if(nativeAreas)throw new IllegalArgumentException(tr("原生和 Next 噪域混合列表暂不支持，请分别导出"));
        Tempo tempo=new Tempo(root.getJSONArray("BPMList"));JSONArray converted=new JSONArray();
        for(int i=0;i<list.length();i++){
            JSONObject area=list.getJSONObject(i);
            for(String track:new String[]{"moveXEvents","moveYEvents","rotateEvents","scaleXEvents","scaleYEvents"}){
                JSONArray events=area.optJSONArray(track);
                if(events!=null&&events.length()!=0)throw new IllegalArgumentException("blockAreaList["+i+tr("] 的 Next 动态噪域暂不支持，请导出原生秒制噪域；未导入以免漏打"));
            }
            JSONObject bl=area.getJSONObject("bottomLeft"),tr=area.getJSONObject("topRight");
            JSONObject bottom=point(.5+finite(bl.getDouble("x"))/1350,.5+finite(bl.getDouble("y"))/900),top=point(.5+finite(tr.getDouble("x"))/1350,.5+finite(tr.getDouble("y"))/900);
            double appear=tempo.seconds(beat(area.get("appearTime"))),disappear=tempo.seconds(beat(area.get("disappearTime")));
            JSONArray intervals=area.optJSONArray("activeIntervals");
            if(intervals==null)throw new IllegalArgumentException(tr("Next 噪域缺少 activeIntervals"));
            for(int k=0;k<intervals.length();k++){
                JSONObject active=intervals.getJSONObject(k);double on=Math.max(appear,tempo.seconds(beat(active.get("startTime")))),off=Math.min(disappear,tempo.seconds(beat(active.get("endTime"))));
                if(off<=on)continue;if(converted.length()>=32768)throw new IllegalArgumentException(tr("Next 噪域激活段超过 32768"));
                converted.put(new JSONObject().put("bottomLeftPercentage",bottom).put("topRightPercentage",top).put("appearTime",on).put("enableTime",on).put("disableTime",off).put("disappearTime",off).put("isSubtract",area.optBoolean("isInvert",false)));
            }
        }
        root.put("blockAreaList",converted);
        JSONObject options=root.optJSONObject("noiseAreaOptions");
        root.put("phiscriptNoiseSingleInvert",options!=null&&options.optBoolean("ignoreTripleInversion",false));
    }
    private static JSONObject point(double x,double y)throws JSONException{return new JSONObject().put("x",x).put("y",y);}
    private static double finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException(tr("噪域数字无效"));return value;}
    private static double beat(Object value)throws JSONException{
        if(value instanceof Number)return finite(((Number)value).doubleValue());
        if(!(value instanceof JSONArray)||((JSONArray)value).length()!=3)throw new IllegalArgumentException(tr("噪域拍数必须为 [整数,分子,分母]"));
        JSONArray a=(JSONArray)value;double denominator=a.getDouble(2);if(denominator<=0)throw new IllegalArgumentException(tr("噪域拍数分母无效"));return finite(a.getDouble(0)+a.getDouble(1)/denominator);
    }
    private static final class Tempo{
        final List<double[]> items=new ArrayList<>();
        Tempo(JSONArray list)throws JSONException{for(int i=0;i<list.length();i++){JSONObject a=list.getJSONObject(i);double bpm=finite(a.getDouble("bpm"));if(bpm<=0)throw new IllegalArgumentException(tr("BPM 无效"));items.add(new double[]{beat(a.get("startTime")),bpm});}items.sort((a,b)->Double.compare(a[0],b[0]));if(items.isEmpty())throw new IllegalArgumentException(tr("BPMList 为空"));}
        double seconds(double beat){return primitive(beat)-primitive(0);}
        double primitive(double beat){double at=items.get(0)[0],bpm=items.get(0)[1],sum=0;if(beat<=at)return (beat-at)*60/bpm;for(int i=1;i<items.size();i++){double[] item=items.get(i);if(item[0]>beat)break;sum+=(item[0]-at)*60/bpm;at=item[0];bpm=item[1];}return sum+(beat-at)*60/bpm;}
    }
}
