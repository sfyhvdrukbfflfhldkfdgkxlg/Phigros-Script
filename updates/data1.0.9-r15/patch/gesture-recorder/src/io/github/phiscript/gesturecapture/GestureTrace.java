package io.github.phiscript.gesturecapture;

import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Independent sample format for editing in-game gestures. Input times are Android uptime. */
final class GestureTrace {
    static final int SAMPLE_LIMIT=60000;
    static final long TIME_LIMIT=600000;
    private final String name;
    private final int width,height;
    private final long start;
    private final JSONArray events=new JSONArray();
    private final LinkedHashMap<Integer,JSONObject> contacts=new LinkedHashMap<>();
    private int samples;
    private long last;
    private boolean closed;
    GestureTrace(String name,int width,int height,long start){
        if(width<1||height<1)throw new IllegalArgumentException("画布尚未就绪");
        this.name=name;this.width=width;this.height=height;this.start=start;last=start;
    }
    boolean hasContacts(){return !contacts.isEmpty();}
    boolean hasSamples(){return events.length()>0;}
    void frame(String action,int changed,long time,int[] ids,float[] xs,float[] ys,boolean historical)throws JSONException{
        if(closed||time<start||time<last)throw new IllegalStateException("采样时间顺序无效");
        if(time-start>TIME_LIMIT||samples+ids.length>SAMPLE_LIMIT)throw new IllegalStateException("达到录入上限");
        if(ids.length==0||ids.length!=xs.length||ids.length!=ys.length)throw new IllegalArgumentException("触点数据不完整");
        boolean down=action.equals("down"),up=action.equals("up"),move=action.equals("move");
        if(!down&&!up&&!move)throw new IllegalArgumentException("未知手势事件");
        LinkedHashMap<Integer,JSONObject> current=new LinkedHashMap<>();
        JSONArray points=new JSONArray();
        for(int i=0;i<ids.length;i++){
            int id=ids[i];
            if(id<0||current.containsKey(id)||!Float.isFinite(xs[i])||!Float.isFinite(ys[i]))throw new IllegalArgumentException("无效的触点");
            JSONObject p=new JSONObject().put("id",id).put("x",xs[i]).put("y",ys[i])
                .put("nx",xs[i]/width).put("ny",ys[i]/height);
            current.put(id,p);points.put(p);
        }
        if(down){
            if(contacts.containsKey(changed)||!current.containsKey(changed)||current.size()!=contacts.size()+1||!current.keySet().containsAll(contacts.keySet()))
                throw new IllegalStateException("触点按下序列不连续");
        }else if(!current.keySet().equals(contacts.keySet())||(up&&!current.containsKey(changed))||(move&&changed!=-1))
            throw new IllegalStateException("触点移动或抬起序列不连续");
        write(action,changed,time,points,historical);
        contacts.clear();contacts.putAll(current);if(up)contacts.remove(changed);
        samples+=ids.length;
    }
    private void write(String action,int changed,long time,JSONArray points,boolean historical)throws JSONException{
        events.put(new JSONObject().put("tMs",time-start).put("action",action)
            .put("changedId",changed).put("historical",historical).put("points",points));
        last=time;
    }
    JSONObject finish(long time,String reason)throws JSONException{
        if(closed)throw new IllegalStateException("录入已结束");
        closed=true;long end=Math.max(last,time);
        if(hasContacts()){JSONArray remaining=new JSONArray();for(JSONObject p:contacts.values())remaining.put(p);write("cancel",-1,end,remaining,false);contacts.clear();}
        return new JSONObject().put("format","phiscript.gesture-edit").put("version",1).put("name",name)
            .put("purpose","edit-application-gestures").put("timeUnit","ms").put("origin","canvas-top-left")
            .put("canvas",new JSONObject().put("widthPx",width).put("heightPx",height)).put("durationMs",end-start)
            .put("endReason",reason).put("events",events);
    }
}
