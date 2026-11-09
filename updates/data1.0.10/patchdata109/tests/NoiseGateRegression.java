package io.github.phiscript.input;

import io.github.phiscript.assets.NoiseWindowReader;
import io.github.phiscript.assets.RpeCompatibilityNormalizer;
import io.github.phiscript.engine.*;
import java.util.*;
import org.json.*;

/** Active noise windows are chart time, and no optional contact survives them. */
public final class NoiseGateRegression {
    static int checks;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static JSONArray beat(int n){return new JSONArray().put(n).put(0).put(1);}
    static JSONObject tempo(int beat,int bpm)throws Exception{return new JSONObject().put("startTime",beat(beat)).put("bpm",bpm);}
    static JSONObject next()throws Exception{return new JSONObject().put("bottomLeft",new JSONObject().put("x",-100).put("y",-100)).put("topRight",new JSONObject().put("x",100).put("y",100)).put("appearTime",beat(1)).put("disappearTime",beat(8)).put("activeIntervals",new JSONArray().put(new JSONObject().put("startTime",beat(2)).put("endTime",beat(6))));}
    static JSONObject nativeArea(double a,double b)throws Exception{return new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",0).put("y",0)).put("topRightPercentage",new JSONObject().put("x",1).put("y",1)).put("appearTime",0).put("enableTime",a).put("disableTime",b).put("disappearTime",10);}
    static JSONObject window(double a,double b)throws Exception{return new JSONObject().put("blockAreaList",new JSONArray().put(nativeArea(a,b)));}
    static Note n(int id,int kind,double t){return new Note(id,kind,t,t,0,1,0,true,0);}
    static Chart chart(List<Note> notes){return Chart.Companion.fromNative$app_debug(notes,0,(n,t,v)->new Point(v.getWidth()*.5f+(float)n.getPositionX()*v.getHeight(),v.getHeight()*.5f),(n,t,v)->new Point(0,-1));}
    static void format()throws Exception{
        JSONObject root=new JSONObject().put("BPMList",new JSONArray().put(tempo(0,120)).put(tempo(4,60))).put("META",new JSONObject().put("offset",175)).put("blockAreaList",new JSONArray().put(next()));
        JSONArray spans=NoiseWindowReader.read(root);ok(spans.length()==1&&spans.getJSONArray(0).getDouble(0)==1&&spans.getJSONArray(0).getDouble(1)==4,"Next BPM changes/global beats or offset changed noise time");
        JSONObject note=new JSONObject().put("type",4).put("startTime",beat(2)).put("endTime",beat(2)).put("positionX",0).put("above",1);
        root.put("judgeLineList",new JSONArray().put(new JSONObject().put("eventLayers",new JSONArray()).put("notes",new JSONArray().put(note))));
        JSONObject converted=RpeCompatibilityNormalizer.normalize(root);ok(converted.getJSONArray("phiscriptNoiseWindows").toString().equals(spans.toString()),"RPE conversion discarded active noise metadata");
        root.getJSONArray("blockAreaList").getJSONObject(0).put("appearTime",beat(3)).put("disappearTime",beat(5));
        spans=NoiseWindowReader.read(root);ok(spans.getJSONArray(0).getDouble(0)==1.5&&spans.getJSONArray(0).getDouble(1)==3,"Next lifecycle did not clip active interval");
        JSONObject negative=new JSONObject().put("BPMList",new JSONArray().put(tempo(-4,120)).put(tempo(-2,60))).put("blockAreaList",new JSONArray().put(next()));
        spans=NoiseWindowReader.read(negative);ok(spans.getJSONArray(0).getDouble(0)==2&&spans.getJSONArray(0).getDouble(1)==6,"negative-beat tempo changes disagreed with chart beat-zero origin");
        JSONObject ready=next().put("activeIntervals",new JSONArray());root.put("blockAreaList",new JSONArray().put(ready));ok(NoiseWindowReader.read(root).length()==0,"visible/ready noise disabled gestures");
        root.put("blockAreaList",new JSONArray().put(nativeArea(.6,1)).put(nativeArea(.9,1.3)));spans=NoiseWindowReader.read(root);ok(spans.length()==1&&spans.getJSONArray(0).getDouble(0)==.6&&spans.getJSONArray(0).getDouble(1)==1.3,"native seconds were converted to beats or union failed");
        boolean rejected=false;try{root.put("blockAreaList",new JSONArray().put(next().put("bottomLeftPercentage",new JSONObject())));NoiseWindowReader.read(root);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("blockAreaList[0]");}ok(rejected,"ambiguous block format silently ignored");
        rejected=false;try{root.put("blockAreaList",new JSONArray().put(nativeArea(2,1)));NoiseWindowReader.read(root);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"reversed native active times accepted");
        JSONObject marker=new JSONObject().put("Texture","folder/isSubtract0.png").put("eventLayers",new JSONArray().put(new JSONObject().put("speedEvents",new JSONArray().put(new JSONObject().put("startTime",beat(1)).put("start",1)).put(new JSONObject().put("startTime",beat(2)).put("start",2)).put(new JSONObject().put("startTime",beat(4)).put("start",3)).put(new JSONObject().put("startTime",beat(6)).put("start",4)))));
        root.remove("blockAreaList");root.put("judgeLineList",new JSONArray().put(marker));spans=NoiseWindowReader.read(root);ok(spans.length()==1&&spans.getJSONArray(0).getDouble(0)==1&&spans.getJSONArray(0).getDouble(1)==2,"Line2Area speed lifecycle ignored");
        marker.put("eventLayers",new JSONArray());spans=NoiseWindowReader.read(root);ok(spans.length()==1&&spans.getJSONArray(0).getDouble(0)==0&&spans.getJSONArray(0).getDouble(1)==7200,"legacy alpha marker was silently dropped");
    }
    static void plans()throws Exception{
        List<Note> notes=new ArrayList<>();for(double t:new double[]{.2,.25,.3,.65,.7,.75,1.2,1.25,1.3})notes.add(n(notes.size(),2,t));
        for(GestureProfile profile:Arrays.asList(new GestureProfile(true,70,.12f,true,true,true),new GestureProfile(true,70,.12f,true))){
            Chart c=chart(notes);GesturePlan before=GesturePlan.obtain(c,profile);NoiseGate.attach(c,window(.6,1));GesturePlan p=GesturePlan.obtain(c,profile);ok(p!=before,"metadata attachment reused cached unsafe plan");
            ok(p.connectedGroups==2&&p.connectedNotes==6&&p.events.size()==5,"noise did not isolate each affected yellow while preserving safe runs");
            ok(p.events.size()-p.connectedGroups+p.connectedNotes+p.coveredDrags==notes.size(),"noise gate discarded a note");
            for(TouchEvent e:p.events)if(e.getNote().getTimeSeconds()>=.6&&e.getNote().getTimeSeconds()<1){ok(e.getEndMs()>e.getStartMs()&&p.isContinuous(e),"yellow lost its own sustained contact");ok(e.getEndMs()-e.getStartMs()<120,"noise yellow retained optional idle hold");}
            NoiseGate.attach(c,new JSONObject());ok(GesturePlan.obtain(c,profile).events.size()==before.events.size(),"removing metadata left stale barriers");
        }
        Chart bridge=chart(Arrays.asList(n(1,2,.5),n(2,2,.6)));NoiseGate.attach(bridge,window(.55,.57));ok(GesturePlan.obtain(bridge,new GestureProfile(true,70,.12f,true,true,true)).events.size()==2,"a bridge crossed active noise without an intermediate note");
        Chart hold=chart(Arrays.asList(new Note(1,3,.1,1.7,0,1,0,true,0),n(2,2,1.2)));NoiseGate.attach(hold,window(.6,1));GesturePlan hp=GesturePlan.obtain(hold,new GestureProfile(true,70,.12f,true,true,true));ok(hp.events.size()==2&&hp.coveredDrags==0,"post-noise yellow reused potentially infected long hold");ok(hp.events.get(0).getEndMs()==1700,"original hold was cut at the noise boundary");
        List<Note> rain=new ArrayList<>();for(int row=0;row<50;row++)for(int lane=0;lane<32;lane++)rain.add(new Note(row*32+lane,2,1+row*.03,1+row*.03,-.7+1.4*lane/31,1,0,true,0));
        Chart dense=chart(rain);NoiseGate.attach(dense,window(.8,2.8));GesturePlan dp=GesturePlan.obtain(dense,new GestureProfile(true,70,.12f,true,true,true));ok(dp.denseDragSections==0&&dp.connectedGroups==0&&dp.events.size()==1600,"noise retained eight-finger occupancy or lost original yellows");
        Chart boundary=chart(Arrays.asList(n(1,2,1.2)));NoiseGate.attach(boundary,window(.6,1));ok(!NoiseGate.intersects(boundary,1000,1100)&&NoiseGate.intersects(boundary,999,1000),"noise end was not left-closed/right-open");
    }
    public static void main(String[] args)throws Exception{format();plans();System.out.println("PASS "+checks+" noise checks: Next/native/Line2Area active times, BPM/lifecycle/offset, ready exclusion, no cross-window reuse, safe-run recovery, sustained yellow and original hold retention, dense fallback and cache invalidation. Conservative time barriers; no geometry avoidance or Android device run.");}
}
