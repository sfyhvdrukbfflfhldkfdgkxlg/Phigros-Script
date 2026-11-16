package io.github.phiscript.input;

import io.github.phiscript.engine.*;
import java.util.*;
import org.json.*;

/** Read-only design fixtures: demonstrates why pixel guards must be composed
 * after XOR and why a finite grid is not a full uncertainty-neighbourhood test. */
public final class NoiseClearanceCounterexamples {
    private static int checks;private static final Viewport V=new Viewport(0,0,1000,600);
    private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private static JSONObject point(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    private static JSONObject screenRect(double left,double top,double right,double bottom)throws Exception{
        return new JSONObject().put("bottomLeftPercentage",point(left/1000,1-bottom/600)).put("topRightPercentage",point(right/1000,1-top/600))
            .put("appearTime",0).put("enableTime",0).put("disableTime",2).put("disappearTime",2);
    }
    private static Chart chart(JSONObject... zones)throws Exception{
        Note note=new Note(1,1,.5,.5,0,1,0,true,0);Chart c=Chart.Companion.fromNative$app_debug(Collections.singletonList(note),0,(n,t,v)->new Point(500,300),(n,t,v)->new Point(0,1));
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray(Arrays.asList(zones))));BlockAreaSet.bind(c);return c;
    }
    private static boolean blocked(Chart chart,double x,double y,double time){return BlockAreaSet.covers(chart,(float)x,(float)y,0,0,1000,600,time);}
    private static boolean gridClear(Chart chart,double x,double y,double radius,double time){for(double dx:new double[]{-radius,0,radius})for(double dy:new double[]{-radius,0,radius})if(blocked(chart,x+dx,y+dy,time))return false;return true;}
    public static void main(String[] args)throws Exception{
        Chart island=chart(screenRect(500.70,300.70,500.80,300.80));
        ok(gridClear(island,500,300,2,.5),"island fixture unexpectedly crosses 9 probe points");
        ok(blocked(island,500.75,300.75,.5),"interior subpixel noise island is absent");
        System.out.println("COUNTEREXAMPLE: center + 8 offsets are all clear, but the 2px uncertainty square contains blocked interior pixels.");

        JSONObject same=screenRect(499.5,299.5,500.5,300.5);Chart cancellation=chart(same,new JSONObject(same.toString()).put("isSubtract",true));
        for(double x=497.5;x<=502.5;x+=.25)for(double y=297.5;y<=302.5;y+=.25)ok(!blocked(cancellation,x,y,.5),"identical XOR domains must fully cancel");
        // Per-area inflation/deflation would expand normal and shrink/remove
        // its cancelling subtract, yielding a blocked center where none exists.
        ok(gridClear(cancellation,500,300,2,.5),"final-XOR clearance incorrectly closed a cancelled domain");
        System.out.println("COUNTEREXAMPLE: identical normal/subtract rectangles cancel everywhere; expanding them independently would create false blocked geometry.");

        Chart hole=chart(screenRect(490,290,510,310),screenRect(497,297,503,303).put("isSubtract",true));
        ok(gridClear(hole,500,300,2,.5),"6px subtractive hole should admit a full 2px uncertainty square");
        ok(!gridClear(hole,501.5,300,2,.5),"near-edge point should fail the 2px guard");
        Chart tiny=chart(screenRect(490,290,510,310),screenRect(498.5,298.5,501.5,301.5).put("isSubtract",true));
        ok(!blocked(tiny,500,300,.5)&&!gridClear(tiny,500,300,2,.5),"3px safe hole cannot robustly fit a 4px uncertainty square");
        System.out.println("BOUNDARY: 6px safe hole retains usable center with 2px margin; a 3px hole is nominally safe but has no 2px-robust center.");

        JSONObject late=screenRect(490,290,510,310).put("appearTime",.520).put("enableTime",.520).put("disableTime",.540).put("disappearTime",.540);Chart onset=chart(late);
        for(double time:new double[]{.496,.500,.504,.508,.512})ok(!blocked(onset,500,300,time),"old window fixture was not outside guard");
        ok(!BlockAreaSet.blockedAtChanges(onset,V,new Point(500,300),new Point(500,300),.496,.512),"old event window included late activation");
        ok(BlockAreaSet.blockedAtChanges(onset,V,new Point(500,300),new Point(500,300),.480,.524),"expanded dispatch/frame window missed actual activation");
        System.out.println("COUNTEREXAMPLE: +20ms activation is clear under the old -4/+12ms window but blocked within a -20/+24ms uncertainty window.");
        System.out.println("PASS "+checks+" read-only clearance design checks; production TapMod/BlockAreaSet unchanged.");
    }
}
