package io.github.phiscript.assets;

import io.github.phiscript.engine.*;
import java.nio.file.*;
import org.json.*;

public final class RpeCompatibilityRegression {
    static int checks;
    static void ok(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static JSONArray time(int beat){return new JSONArray().put(beat).put(0).put(1);}
    static JSONObject event(int start,int end,double a,double b)throws Exception{return new JSONObject().put("startTime",time(start)).put("endTime",time(end)).put("start",a).put("end",b).put("easingType",1);}
    static JSONObject fixture()throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get("patch/tests/fixtures/rpe_empty_layers_bpm_parent.json")),"UTF-8"));}
    static Chart parse(JSONObject root)throws Exception{return PhiraChartParser.INSTANCE.parse(root.toString(),0);}
    public static void main(String[]args)throws Exception{
        JSONObject root=fixture();Chart original=parse(root);ok(original.getNotes().size()==6,"baseline notes");
        JSONObject line=root.getJSONArray("judgeLineList").getJSONObject(0);JSONArray layers=line.getJSONArray("eventLayers");
        layers.put(0,JSONObject.NULL);layers.put(2,new JSONObject().put("moveXEvents",new JSONArray()));
        ok(parse(root).getNotes().size()==6,"null/empty layers");
        System.out.println("PASS null and non-first empty layers preserve playable notes");
        root=fixture();line=root.getJSONArray("judgeLineList").getJSONObject(0);
        JSONArray overlap=new JSONArray().put(event(0,4,0,100)).put(event(2,3,50,80)).put(event(3,3,80,90));
        line.put("eventLayers",new JSONArray().put(new JSONObject().put("moveXEvents",overlap)));
        Chart chart=parse(root);ok(chart.getNotes().size()==6,"overlap notes");
        for(Note note:chart.getNotes())for(double seconds:new double[]{0,.5,1,1.5,2,3}){
            Point p=chart.position(note,seconds,new Viewport(0,0,1000,600),false);
            ok(p!=null&&Float.isFinite(p.getX())&&Float.isFinite(p.getY()),"overlap position");
        }
        System.out.println("PASS overlapping and zero-duration transform events produce finite positions");
        JSONObject e=overlap.getJSONObject(0);e.put("easingType",6).put("easingLeft",.25).put("easingRight",.75);
        JSONObject converted=RpeCompatibilityNormalizer.normalize(root);JSONObject tween=converted.getJSONArray("lines").getJSONObject(0).getJSONObject("object").getJSONArray("x").getJSONArray(0).getJSONObject(0).getJSONObject("tween");
        ok(tween.getDouble("left")==.25&&tween.getDouble("right")==.75,"easing clip");
        e.put("bezier",1).put("bezierPoints",new JSONArray().put(.2).put(-.5).put(.8).put(1.5));ok(parse(root).getNotes().size()==6,"bezier");
        System.out.println("PASS clipped easing and cubic Bezier (including Y overshoot)");
        root=fixture();JSONArray bpms=root.getJSONArray("BPMList");bpms.put(new JSONObject().put("bpm",120).put("startTime",time(0)));
        ok(parse(root).getNotes().size()==6,"duplicate BPM");
        root=fixture();line=root.getJSONArray("judgeLineList").getJSONObject(0);line.put("attachUI","score");
        boolean rejected=false;try{parse(root);}catch(IllegalArgumentException ex){rejected=ex.getMessage().contains("UI");}ok(rejected,"unsupported UI binding not rejected");
        root=fixture();root.getJSONArray("judgeLineList").getJSONObject(0).put("father",1);root.getJSONArray("judgeLineList").getJSONObject(1).put("father",0);
        rejected=false;try{parse(root);}catch(IllegalArgumentException ex){rejected=true;}ok(rejected,"parent cycle accepted");
        System.out.println("PASS duplicate BPM; unsafe gameplay UI-binding and cyclic parents fail explicitly");
        System.out.println("ALL "+checks+" RPE COMPATIBILITY CHECKS PASSED (host parser; not every RPE chart)");
    }
}
