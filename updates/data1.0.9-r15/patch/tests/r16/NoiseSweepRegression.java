package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.Arrays;
import org.json.*;

/** Reproduces translation tunnelling between the old 4 ms guard samples. */
public final class NoiseSweepRegression {
    static int checks;static final Viewport V=new Viewport(0,0,1000,600);
    static void ok(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    static JSONObject point(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    static JSONObject area(double x0,double y0,double x1,double y1)throws Exception {
        return new JSONObject().put("bottomLeftPercentage",point(x0,y0)).put("topRightPercentage",point(x1,y1))
            .put("appearTime",0).put("enableTime",0).put("disableTime",2).put("disappearTime",2);
    }
    static JSONObject move(double time,double x,double y,int ease)throws Exception {
        return new JSONObject().put("time",time).put("endPosition",point(x,y)).put("easeTypeX",ease).put("easeTypeY",ease);
    }
    static Chart chart(double offset,JSONObject... areas)throws Exception {
        Note n=new Note(1,3,.1,1.5,0,1,0,true,0);
        Chart c=Chart.Companion.fromNative$app_debug(Arrays.asList(n),offset,(note,t,v)->new Point(500,300),(note,t,v)->new Point(0,1));
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray(Arrays.asList(areas))));BlockAreaSet.bind(c);return c;
    }
    static void translation()throws Exception {
        JSONObject needle=area(.49995,-1,.50005,2).put("moveEvents",new JSONArray().put(move(0,-.00125,.5,0)).put(move(1,.99875,.5,0)));
        Point p=new Point(500,300);Chart c=chart(0,needle);
        for(double t:new double[]{.496,.500,.504,.508,.512})ok(!BlockAreaSet.covers(c,500,300,0,0,1000,600,t),"fixture intersects old guard");
        ok(BlockAreaSet.covers(c,500,300,0,0,1000,600,.50125),"fixture must sweep across the real touch");
        ok(!TapMod.clearAt(c,V,p,.5),"high-speed narrow noise tunneled through dispatch guard");
        boolean blocked=false;try{TapMod.segment(c,V,p,p,500,508);}catch(TapMod.NoSafePoint expected){blocked=true;}
        ok(blocked,"stationary sustained finger was left down during sweep");
        ok(BlockAreaSet.crossesTranslation(c,V,new Point(490,300),new Point(510,300),.5,.508),"relative touch/zone movement crossing");
        ok(!BlockAreaSet.crossesTranslation(c,V,new Point(800,300),new Point(810,300),.5,.508),"unrelated safe lane rejected");
        Chart offset=chart(1,needle);ok(!TapMod.clearAt(offset,V,p,-.5),"chart offset omitted or applied twice");
        JSONObject hole=area(.49,-2,.51,3).put("isSubtract",true).put("moveEvents",needle.getJSONArray("moveEvents"));
        Chart subtract=chart(0,needle,hole);ok(!TapMod.clearAt(subtract,V,p,.5),"inverted overhang outside normal must block under native XOR");
        JSONObject matching=new JSONObject(needle.toString()).put("isSubtract",true);
        ok(TapMod.clearAt(chart(0,needle,matching),V,p,.5),"exact matching normal/inversion must cancel during sweep");
    }
    static void easing()throws Exception {
        Point p=new Point(500,300);
        for(int easing=0;easing<=12;easing++){
            JSONObject strip=area(.499999,-1,.500001,2).put("moveEvents",new JSONArray().put(move(0,0,.5,easing)).put(move(1,1,.5,0)));
            Chart c=chart(0,strip);
            // The full second crosses x=.5 for every monotonic easing, even when
            // entry/exit both fit between raster or framework input samples.
            ok(BlockAreaSet.crossesTranslation(c,V,p,p,0,1),"easing lookup-table knots missed type "+easing);
        }
        JSONObject flash=area(.4,.4,.6,.6).put("appearTime",.50025).put("enableTime",.50025).put("disableTime",.50075).put("disappearTime",.50075);
        Chart c=chart(0,flash);ok(!TapMod.clearAt(c,V,p,.5),"sub-millisecond activation regression");
        ok(!BlockAreaSet.crossesTranslation(c,V,p,p,.50075,.501),"right-open deactivation boundary regression");
    }
    static void continuation()throws Exception {
        Chart c=chart(0,area(.4,.45,.6,.55));Note note=c.getNotes().get(0);Point previous=new Point(500,200),desired=new Point(500,400);
        ok(TapMod.clearAt(c,V,desired,.508),"destination should be clear across wall");
        Point safe=TapMod.withinBounds(c,note,.508,V,desired,new TouchBounds(20,20,980,580),previous,500);
        ok(safe.getY()<270&&Math.abs(safe.getX()-500)<.01,"hold did not retain reachable side of the same judgement band: "+safe);
        TapMod.segment(c,V,previous,safe,500,508);checks++;
    }
    public static void main(String[] args)throws Exception {
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new Context());
        translation();easing();continuation();
        System.out.println("PASS "+checks+" noise sweep checks: fast narrow translation, relative motion, offsets, moving holes, easing knots, 0.5 ms activation, right-open boundaries and reachable hold continuation.");
    }
}
