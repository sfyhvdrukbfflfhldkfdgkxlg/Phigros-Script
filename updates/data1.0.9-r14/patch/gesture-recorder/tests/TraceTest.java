package io.github.phiscript.gesturecapture;

import org.json.JSONArray;
import org.json.JSONObject;
public class TraceTest{
    static void f(GestureTrace s,String action,int changed,long t,int...ids)throws Exception{
        float[] x=new float[ids.length],y=new float[ids.length];
        for(int i=0;i<ids.length;i++){x[i]=100*ids[i];y[i]=50*ids[i];}
        s.frame(action,changed,t,ids,x,y,action.equals("move"));
    }
    static void ok(boolean v){if(!v)throw new AssertionError();}
    public static void main(String[] a)throws Exception{
        GestureTrace s=new GestureTrace("双指\n样本",1000,500,1000);
        f(s,"down",2,1022,2);f(s,"down",6,1031,2,6);f(s,"move",-1,1033,6,2);
        f(s,"up",2,1033,6,2);f(s,"up",6,1041,6);
        f(s,"down",2,1160,2);
        JSONObject saved=new JSONObject(s.finish(1170,"停止").toString());
        JSONArray ev=saved.getJSONArray("events");
        ok(ev.length()==7);ok(ev.getJSONObject(0).getLong("tMs")==22);ok(ev.getJSONObject(2).getBoolean("historical"));
        ok(ev.getJSONObject(3).getInt("changedId")==2);ok(ev.getJSONObject(5).getInt("tMs")==160);
        JSONObject p=ev.getJSONObject(2).getJSONArray("points").getJSONObject(0);
        ok(p.getInt("id")==6 && Math.abs(p.getDouble("nx")-.6)<1e-5 && Math.abs(p.getDouble("ny")-.6)<1e-5);
        ok(ev.getJSONObject(6).getString("action").equals("cancel")&&ev.getJSONObject(6).getJSONArray("points").length()==1);
        ok(saved.getInt("durationMs")==170&&saved.getString("name").equals("双指\n样本"));
        GestureTrace empty=new GestureTrace("a",100,80,0);boolean bad=false;
        try{f(empty,"move",-1,0,4);}catch(IllegalStateException ex){bad=true;}ok(bad);
        f(empty,"down",4,2,4);bad=false;try{f(empty,"up",4,1,4);}catch(IllegalStateException ex){bad=true;}ok(bad);
        bad=false;try{f(empty,"move",-1,GestureTrace.TIME_LIMIT+1,4);}catch(IllegalStateException ex){bad=true;}ok(bad);
        System.out.println("PASS multi-touch, pointer order, historical samples, id reuse, inter-gesture gap, validation, limit and CANCEL");
        System.out.println(saved.toString(2));
    }
}
