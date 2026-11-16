package io.github.phiscript.assets;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;

/** Converts official PGR tracks into the player's native timed keyframe scripts. */
public final class PgrScriptConverter {
    private PgrScriptConverter() {}
    private static final double LOW=-1000000000.0;
    private static double number(JSONObject o,String key,double fallback)throws JSONException {
        double n=o.has(key)?o.getDouble(key):fallback;
        if(Double.isNaN(n)||Double.isInfinite(n))throw new JSONException("非有限数字："+key);
        return n;
    }
    private static JSONObject frame(double t,double v,int tween)throws JSONException {
        return new JSONObject().put("time",t).put("value",v).put("tween",new JSONObject().put("id",tween));
    }
    private static void segment(List<JSONObject> frames,double a,double b,double x,double y)throws JSONException {
        if(b<=a)return;
        JSONObject first=frame(a,x,x==y?0:2);
        if(!frames.isEmpty()){
            JSONObject last=frames.get(frames.size()-1);
            if(last.getDouble("time")==a&&last.getDouble("value")==x)frames.remove(frames.size()-1);
        }
        frames.add(first);frames.add(frame(b,y,0));
    }
    private static final class Event {
        double start,end,a,b,tick,rawStart,rawEnd;
        double at(double t){
            double raw=t/tick;
            double f=rawEnd==rawStart?(raw<rawStart?0.0:1.0):Math.max(0.0,Math.min(1.0,(raw-rawStart)/(rawEnd-rawStart)));
            return a+(b-a)*f;
        }
    }
    private static double boundary(double raw,double tick){
        double seconds=raw*tick;
        if(Double.isNaN(seconds)||Double.isInfinite(seconds)||!(tick>0))throw new IllegalArgumentException("判定线时间无效");
        // Select the same side of an instantaneous jump as EventTrack's seconds/tick.
        // Multiplication and division need not round back to the same source tick.
        while(seconds/tick<raw)seconds=Math.nextUp(seconds);
        while(Math.nextDown(seconds)/tick>=raw)seconds=Math.nextDown(seconds);
        return seconds;
    }
    private static JSONArray track(JSONArray source,String a,String b,double tick,double scale,double bias,double high)throws JSONException {
        TreeMap<Double,Event> sorted=new TreeMap<>();
        if(source!=null)for(int i=0;i<source.length();i++){
            JSONObject raw=source.getJSONObject(i);Event e=new Event();
            e.tick=tick;e.rawStart=number(raw,"startTime",0);e.rawEnd=number(raw,"endTime",0);
            if(e.rawEnd<e.rawStart)continue;
            e.start=boundary(e.rawStart,tick);e.end=boundary(e.rawEnd,tick);
            // The official playback parser skips reversed scenery events too.
            if(e.end<e.start)continue;
            e.a=number(raw,a,0)*scale+bias;e.b=number(raw,b,0)*scale+bias;sorted.put(e.start,e);
        }
        List<JSONObject> frames=new ArrayList<>();
        if(sorted.isEmpty()){frames.add(frame(LOW,0,0));}
        else {
            List<Event> events=new ArrayList<>(sorted.values());int index=0;
            while(index+1<events.size()&&events.get(index+1).start<=LOW)index++;
            double left=LOW;
            while(left<high){
                Event e=events.get(index);double right=index+1<events.size()?Math.min(high,events.get(index+1).start):high;
                if(e.start==e.end&&e.start>left&&e.start<right){
                    segment(frames,left,e.start,e.a,e.a);segment(frames,e.start,right,e.b,e.b);
                }else {
                    TreeSet<Double> cuts=new TreeSet<>();cuts.add(left);cuts.add(right);
                    if(e.start>left&&e.start<right)cuts.add(e.start);
                    if(e.end>left&&e.end<right)cuts.add(e.end);
                    Double previous=null;for(double cut:cuts){if(previous!=null)segment(frames,previous,cut,e.at(previous),e.at(cut));previous=cut;}
                }
                left=right;if(left>=high)break;index++;
            }
        }
        JSONArray row=new JSONArray();for(JSONObject f:frames)row.put(f);
        return new JSONArray().put(row);
    }
    private static JSONArray constant(double value)throws JSONException{return new JSONArray().put(new JSONArray().put(frame(0,value,0)));}

    public static byte[] convert(byte[] original,JSONObject entry,JSONObject catalog)throws JSONException {
        JSONObject raw=new JSONObject(new String(original,StandardCharsets.UTF_8));int version=raw.getInt("formatVersion");
        if(version!=1&&version!=3)throw new JSONException("只支持 PGR 1/3");
        JSONArray input=raw.getJSONArray("judgeLineList"),lines=new JSONArray();double duration=0;
        // Determine the playable range before translating animations; omit post-chart scenery.
        for(int i=0;i<input.length();i++){
            JSONObject line=input.getJSONObject(i);double bpm=line.getDouble("bpm");if(!(bpm>0)||Double.isInfinite(bpm))throw new JSONException("BPM 无效");double tick=1.875/bpm;
            for(String key:new String[]{"notesAbove","notesBelow"}){
                JSONArray notes=line.optJSONArray(key);if(notes!=null)for(int j=0;j<notes.length();j++){
                    JSONObject n=notes.getJSONObject(j);double start=n.getDouble("time")*tick;
                    duration=Math.max(duration,start+(n.getInt("type")==3?number(n,"holdTime",0)*tick:0));
                }
            }
        }
        double high=duration+10;
        for(int i=0;i<input.length();i++){
            JSONObject source=input.getJSONObject(i);double bpm=source.getDouble("bpm");if(!(bpm>0))throw new JSONException("BPM 无效");double tick=1.875/bpm;
            JSONArray moves=source.optJSONArray("judgeLineMoveEvents");
            if(version==1&&moves!=null){
                moves=new JSONArray(moves.toString());
                for(int j=0;j<moves.length();j++){
                    JSONObject e=moves.getJSONObject(j);double a=e.getDouble("start"),b=e.getDouble("end");
                    e.put("start",Math.floor(a/1000)/880).put("end",Math.floor(b/1000)/880)
                        .put("start2",a%1000/520).put("end2",b%1000/520);
                }
            }
            JSONObject object=new JSONObject().put("x",track(moves,"start","end",tick,2,-1,high))
                .put("y",track(moves,"start2","end2",tick,2,-1,high))
                .put("rotation",track(source.optJSONArray("judgeLineRotateEvents"),"start","end",tick,1,0,high));
            JSONArray notes=new JSONArray();
            for(String key:new String[]{"notesAbove","notesBelow"}){
                JSONArray incoming=source.optJSONArray(key);if(incoming==null)continue;
                for(int j=0;j<incoming.length();j++){
                    JSONObject n=incoming.getJSONObject(j);int type=n.getInt("type");double start=n.getDouble("time")*tick;
                    JSONObject out=new JSONObject().put("type",type).put("time",start).put("above",key.equals("notesAbove"))
                        .put("speed",number(n,"speed",1)).put("height",number(n,"floorPosition",0))
                        .put("object",new JSONObject().put("x",constant(n.getDouble("positionX")*0.1125)));
                    if(type==3)out.put("endTime",start+number(n,"holdTime",0)*tick);
                    notes.put(out);
                }
            }
            lines.put(new JSONObject().put("parent",-1).put("rotWithParent",false).put("object",object).put("notes",notes));
        }
        JSONObject script=new JSONObject().put("phiscriptFormat",1).put("offset",number(raw,"offset",0)).put("lines",lines)
            .put("META",new JSONObject().put("name",entry.getString("title")).put("level",entry.getString("difficulty")+" "+entry.optString("level","?")))
            .put("phiscriptExport",new JSONObject().put("version",1).put("repository",catalog.getString("repository"))
                .put("chartCommit",catalog.getString("chartCommit")).put("path",entry.getString("path"))
                .put("sourceSha1",entry.optString("sourceSha1",entry.getString("sha1"))).put("sourceFormatVersion",version));
        if(raw.has("blockAreaList"))script.put("blockAreaList",raw.getJSONArray("blockAreaList"));
        return canonical(script).getBytes(StandardCharsets.UTF_8);
    }

    /** Stable serialization makes desktop exports and Android cache repair identical. */
    public static String canonical(Object value)throws JSONException {StringBuilder out=new StringBuilder();write(out,value);return out.toString();}
    private static void quote(StringBuilder out,String s){
        out.append('"');for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){
            case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;
            case '\b':out.append("\\b");break;case '\f':out.append("\\f");break;case '\n':out.append("\\n");break;
            case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;
            default:if(c<32){String h=Integer.toHexString(c);out.append("\\u");for(int k=h.length();k<4;k++)out.append('0');out.append(h);}else out.append(c);
        }}out.append('"');
    }
    private static void write(StringBuilder out,Object v)throws JSONException {
        if(v==null||v==JSONObject.NULL){out.append("null");return;}
        if(v instanceof JSONObject){
            JSONObject object=(JSONObject)v;List<String> keys=new ArrayList<>();Iterator<String> it=object.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);
            out.append('{');boolean comma=false;for(String k:keys){if(comma)out.append(',');comma=true;quote(out,k);out.append(':');write(out,object.get(k));}out.append('}');
        }else if(v instanceof JSONArray){JSONArray a=(JSONArray)v;out.append('[');for(int i=0;i<a.length();i++){if(i>0)out.append(',');write(out,a.get(i));}out.append(']');}
        else if(v instanceof Number){double d=((Number)v).doubleValue();if(Double.isNaN(d)||Double.isInfinite(d))throw new JSONException("非有限数字");out.append(BigDecimal.valueOf(d).stripTrailingZeros().toPlainString());}
        else if(v instanceof Boolean)out.append(v.toString());else quote(out,v.toString());
    }
}
