package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import org.json.*;

/** Exact composed interval fixtures, with an independent screen-space oracle.
 * Safe planned gestures stay in place; an obstructed path must move or lift. */
public final class NoiseGestureRegression {
    private static int checks;
    private static final Viewport V=new Viewport(0,0,1000,600);
    private static final TouchBounds B=new TouchBounds(0,0,999,599);
    private static final class Strip {
        final double lo,hi;final boolean subtract;
        Strip(double lo,double hi,boolean subtract){this.lo=lo;this.hi=hi;this.subtract=subtract;}
    }
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void near(double actual,double expected,String why){ok(Math.abs(actual-expected)<.06,why+": expected "+expected+", actual "+actual);}
    private static JSONObject xy(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    private static JSONObject rect(double x0,double y0,double x1,double y1,boolean subtract)throws Exception{
        return new JSONObject().put("bottomLeftPercentage",xy(x0/1000,1-y1/600))
            .put("topRightPercentage",xy(x1/1000,1-y0/600)).put("isSubtract",subtract)
            .put("appearTime",0).put("enableTime",0).put("disableTime",2).put("disappearTime",2);
    }
    private static Chart chart(Point normal,JSONObject... areas)throws Exception{
        Note n=new Note(1,3,.1,1.5,0,1,0,true,0);
        Chart c=Chart.Companion.fromNative$app_debug(Collections.singletonList(n),0,(note,t,v)->new Point(500,300),(note,t,v)->normal);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray(Arrays.asList(areas))));BlockAreaSet.bind(c);return c;
    }
    private static Chart strips(Strip... strips)throws Exception{
        JSONObject[] areas=new JSONObject[strips.length];for(int i=0;i<strips.length;i++)areas[i]=rect(-100,strips[i].lo,1100,strips[i].hi,strips[i].subtract);
        return chart(new Point(0,1),areas);
    }
    private static Point choose(Chart c,double y){return TapMod.withinBounds(c,c.getNotes().get(0),.5,V,new Point(500,(float)y),B);}
    private static void clear(Chart c,Point p,double time){ok(TapMod.clearAt(c,V,p,time),"center was not clear over timing guard: "+p);ok(p.getX()>=B.left&&p.getX()<=B.right&&p.getY()>=B.top&&p.getY()<=B.bottom,"center escaped viewport");}
    private static void basic()throws Exception{
        Chart gap=strips(new Strip(-100,240,false),new Strip(360,700,false));
        Point p=choose(gap,255);near(p.getY(),300,"large noise did not center the reachable gap");near(p.getX(),500,"normal lane shifted laterally");clear(gap,p,.5);
        near(choose(gap,200).getY(),300,"blocked point did not take the center of the safe gap");
        Chart split=strips(new Strip(-100,240,false),new Strip(360,700,false),new Strip(290,310,false),new Strip(290,310,true));
        near(choose(split,255).getY(),300,"raw cancelled XOR cuts split a composed gap");
        Chart cancelled=strips(new Strip(200,400,false),new Strip(200,400,true));near(choose(cancelled,245).getY(),245,"fully cancelled geometry moved an ordinary safe point");
        Chart parity=strips(new Strip(-100,240,false),new Strip(360,700,false),new Strip(290,310,true),new Strip(290,310,true));
        near(choose(parity,255).getY(),300,"even subtract parity created a false gap boundary");
        Chart narrow=strips(new Strip(-100,298,false),new Strip(302,700,false));try{choose(narrow,200);throw new AssertionError("4px gap accepted without edge clearance");}catch(TapMod.NoSafePoint expected){checks++;}
        Chart lone=strips(new Strip(270,330,false));
        Point nearLone=choose(lone,338);ok(nearLone.getY()>=350,"point stayed against the hard footprint of one area");clear(lone,nearLone,.5);
        near(choose(lone,420).getY(),420,"single domain moved a point that already had enough clearance");
        Chart unrelated=chart(new Point(0,1),rect(50,100,100,500,false));near(choose(unrelated,245).getY(),245,"unrelated geometry recentered the viewport");
        Point unchanged=TapMod.withinBounds(gap,gap.getNotes().get(0),4,V,new Point(500,245),B);near(unchanged.getY(),245,"inactive domains changed a safe position");
    }
    private static void centeredVariation()throws Exception{
        AppSettings.values.put("tapmod_enabled","true");
        AppSettings.values.put("tap_random_delay_ms","20");
        AppSettings.values.put("tap_screen_drift_permille","12");
        try{
            Chart c=strips(new Strip(-100,240,false),new Strip(360,700,false));
            GesturePlan plan=GesturePlan.prepareForImport(c,GestureProfile.read(null),V,B,null);
            TouchEvent event=plan.events.get(0);
            near(event.getStartMs(),100,"random delay moved the centered head");
            near(plan.preparedPoint(event,event.getStartMs(),null,0).getY(),300,"random drift moved the gap center");
        }finally{
            AppSettings.values.put("tapmod_enabled","false");AppSettings.values.remove("tap_random_delay_ms");
            AppSettings.values.remove("tap_screen_drift_permille");TapMod.reload();
        }
    }
    private static void continuityAndBand()throws Exception{
        Chart wall=strips(new Strip(270,330,false));Point previous=new Point(500,200);
        Point held=TapMod.withinBounds(wall,wall.getNotes().get(0),.508,V,new Point(500,400),B,previous,500);
        near(held.getY(),270-600./30-1,"hold did not remain on reachable side of the barrier");TapMod.segment(wall,V,previous,held,500,508);checks++;
        JSONObject flash=rect(-100,100,1100,120,false).put("appearTime",.520).put("enableTime",.520).put("disableTime",.530).put("disappearTime",.530);
        Chart earlier=chart(new Point(0,1),rect(-100,300,1100,330,false),flash);
        Point retained=TapMod.withinBounds(earlier,earlier.getNotes().get(0),.55,V,new Point(500,50),B,new Point(500,50),500);
        near(retained.getY(),50,"centering rejected a safe stationary hold across an earlier flash");TapMod.segment(earlier,V,new Point(500,50),retained,500,550);checks++;
        Chart side=chart(new Point(0,1),rect(490,-100,510,700,false));Point lateral=choose(side,250);
        clear(side,lateral,.5);ok(Math.abs(lateral.getX()-500)<=ContactPlanner.radius(V)+.02,"lateral fallback escaped legal judgement band");
        // The only safe column lies halfway between the sampled lanes. Both a new head and an existing
        // hold must locate it despite the nominal lane being fully obstructed.
        JSONObject left=rect(-100,-100,497,700,false),right=rect(511,-100,1100,700,false);
        Chart slit=chart(new Point(0,1),left,right);
        Point prior=new Point(504,320),desired=new Point(500,300);
        Point saved=TapMod.withinBounds(slit,slit.getNotes().get(0),.55,V,desired,B,prior,500);
        ok(Math.abs(saved.getX()-504)<.1,"missed a reachable midpoint between search lanes");
        Point slitHead=TapMod.withinBounds(slit,slit.getNotes().get(0),.55,V,desired,B,null,0);
        near(slitHead.getX(),504,"narrow lateral gap missed a fresh head");
        JSONObject shutter=rect(-100,-100,1100,700,false).put("enableTime",.521).put("disableTime",.522);
        Chart flashes=chart(new Point(0,1),left,right,shutter);
        try{TapMod.withinBounds(flashes,flashes.getNotes().get(0),.55,V,desired,B,prior,500);
            throw new AssertionError("retained a contact through a transient full-width obstruction");
        }catch(TapMod.NoSafePoint expected){checks++;}
        for(int degree=0;degree<360;degree+=15){
            double theta=Math.toRadians(degree);Point normal=new Point((float)Math.cos(theta),(float)Math.sin(theta));
            Chart c=chart(normal,rect(450,250,550,350,false));Point q=choose(c,300);clear(c,q,.5);
            double lateralDistance=(q.getX()-500)*normal.getY()-(q.getY()-300)*normal.getX();
            ok(Math.abs(lateralDistance)<=ContactPlanner.radius(V)+.02,"rotated output violated judgement band at "+degree);
        }
    }
    private static JSONObject move(double time,double y)throws Exception{return new JSONObject().put("time",time).put("endPosition",xy(.5,y)).put("easeTypeX",0).put("easeTypeY",0);}
    private static void movingAndGuard()throws Exception{
        // Upper region moves down at 120 px/s. During the asymmetric default
        // guard its furthest bottom is 241.44; lower edge remains at 360.
        JSONObject upper=rect(-100,-100,1100,240,false).put("moveEvents",new JSONArray().put(move(0,1-10/600.0)).put(move(1,1-130/600.0)));
        // The source rectangle center is y=70; the track above centers at10..130.
        Chart c=chart(new Point(0,1),upper,rect(-100,360,1100,700,false));Point p=choose(c,255);
        near(p.getY(),300.72,"moving noise did not center the timing-guarded gap");clear(c,p,.5);
        for(String profile:new String[]{"16","24"}){
            AppSettings.values.put("tap_noise_clock_guard_ms",profile);TapMod.reload();Point q=choose(c,255);clear(c,q,.5);
            near(q.getY(),300+Integer.parseInt(profile)*.06,"moving gap did not follow timing guard "+profile);
        }
        AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.reload();
    }
    private static boolean blocked(Strip[] areas,double y){boolean normal=false;int inverted=0;for(Strip s:areas)if(y>=s.lo&&y<=s.hi){if(s.subtract)inverted++;else normal=true;}return normal^((inverted&1)!=0);}
    // The complete margin around each accepted point must be clear in the final composed mask.
    private static boolean footprintClear(Strip[] areas,double y){
        double radius=600./108;
        if(blocked(areas,y-radius)||blocked(areas,y+radius))return false;
        for(Strip a:areas)for(double e:new double[]{a.lo,a.hi}){
            if(e>=y-radius&&e<=y+radius && (blocked(areas,e)||blocked(areas,e-1e-6)||blocked(areas,e+1e-6)))return false;
        }
        return true;
    }
    private static void independentIntervals()throws Exception{
        Random r=new Random(20261120);long started=System.nanoTime();
        for(int trial=0;trial<180;trial++){
            int count=1+r.nextInt(8);Strip[] areas=new Strip[count];HashSet<Integer> used=new HashSet<>();
            for(int i=0;i<count;i++){int a,b;do{a=10+10*r.nextInt(58);}while(!used.add(a));do{b=10+10*r.nextInt(58);}while(!used.add(b));areas[i]=new Strip(Math.min(a,b),Math.max(a,b),r.nextBoolean());}
            Chart c=strips(areas);for(int sample=0;sample<4;sample++){
                double start;boolean edge;do{start=10.25+r.nextInt(579);edge=false;for(Strip area:areas)if(Math.abs(start-area.lo)<1||Math.abs(start-area.hi)<1)edge=true;}while(edge);
                Point p=choose(c,start);
                StringBuilder description=new StringBuilder();for(Strip area:areas)description.append(' ').append(area.lo).append("..").append(area.hi).append(area.subtract?'S':'N');
                ok(footprintClear(areas,p.getY())&&footprintClear(areas,Math.round(p.getY())),"margin intersects final mask on trial "+trial+description);near(p.getX(),500,"full-width strips required unnecessary lateral movement");clear(c,p,.5);
                double[] around={p.getY(),Math.round(p.getY())};for(double y:around)if(y>=0&&y<=599)ok(!blocked(areas,y),"gesture touched a blocked pixel");
            }
        }
        System.out.println("Independent 720 composed-gap searches: "+((System.nanoTime()-started)/1000000)+" ms");
    }
    private static void gestureRoutes()throws Exception{
        for(int type:new int[]{2,4}){
            for(boolean crossed:new boolean[]{false,true}){
                List<Note> notes=Arrays.asList(new Note(0,2,.5,.5,220,1,0,true,0),new Note(1,type,.68,.68,720,1,0,true,0));
                Chart chart=Chart.Companion.fromNative$app_debug(notes,0,(n,t,v)->new Point((float)n.getPositionX(),300),(n,t,v)->new Point(0,1));
                JSONObject area=crossed?rect(460,-100,530,700,false):rect(10,10,20,20,false);
                BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(chart);
                GesturePlan plan=ContactPlanner.build(chart,V,B,new GestureProfile(true,70,.12f,true,true,true));
                ok(plan.unavailableNotes==0,"missed eligible note");
                ok(plan.events.size()==(crossed?2:1),"unexpected gesture continuity for type "+type+" barrier="+crossed+" count="+plan.events.size());
                if(type==4)ok(plan.hasFlickPath(plan.events.get(plan.events.size()-1)),"red movement was lost");
            }
        }
    }
    private static void signature()throws Exception{
        Chart c=strips(new Strip(200,400,false));String signature=TapMod.preparationSignature();ok(signature.equals(TapMod.preparationSignature()),"preparation signature unstable");
        long salt=TapMod.preparationSalt(c);ok(TapMod.preparationSalt(c)==salt,"preparation salt was not stable");TapMod.preparationSalt(c,42);ok(TapMod.preparationSalt(c)==42,"prepared salt did not restore exactly");
        AppSettings.values.put("tap_noise_clock_guard_ms","24");TapMod.reload();ok(!signature.equals(TapMod.preparationSignature()),"guard did not invalidate preparation signature");
        AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.reload();ok(signature.equals(TapMod.preparationSignature()),"canonical signature did not restore");
        AppSettings.edges=!AppSettings.edges;ok(!signature.equals(TapMod.preparationSignature()),"edge guard flag did not invalidate preparation signature");AppSettings.edges=!AppSettings.edges;
        AppSettings.inward=!AppSettings.inward;ok(!signature.equals(TapMod.preparationSignature()),"inward flick flag did not invalidate preparation signature");AppSettings.inward=!AppSettings.inward;
        AppSettings.values.put("tap_jitter_permille","17");TapMod.reload();
        ok(signature.equals(TapMod.preparationSignature()),"inactive random offset invalidated cache");
        AppSettings.values.put("tap_noise_avoid","false");TapMod.reload();String withoutNoise=TapMod.preparationSignature();
        AppSettings.values.put("tap_noise_clock_guard_ms","24");AppSettings.values.put("tap_noise_escape_permille","5");TapMod.reload();
        ok(withoutNoise.equals(TapMod.preparationSignature()),"inactive guard or escape range invalidated cache");
        AppSettings.values.put("tapmod_enabled","on");AppSettings.values.put("tap_noise_avoid","1");
        TapMod.Settings effective=TapMod.readSettings(new Context());
        ok(effective.enabled&&effective.noiseAvoid&&effective.clock==24&&effective.jitter==17,"editor and playback disagreed on settings");
    }
    public static void main(String[] args)throws Exception{
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.init(new Context());
        basic();centeredVariation();continuityAndBand();movingAndGuard();independentIntervals();gestureRoutes();signature();
        System.out.println("PASS "+checks+" noise gesture checks: composed XOR/parity, large-mask gap centers, single-area clearance, narrow gaps, moving guards, reachable holds, legal rotated bands, independent interval oracle and preparation cache inputs.");
    }
}
