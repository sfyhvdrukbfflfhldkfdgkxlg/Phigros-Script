package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import org.json.*;

/** Exact composed interval fixtures, with an independent screen-space oracle.
 * A midpoint is a directional clearance choice, not a global 2D guarantee. */
public final class NoiseSafeGapCenterRegression {
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
        Point p=choose(gap,245);near(p.getY(),300,"safe point hugging upper edge was not centered");near(p.getX(),500,"normal lane shifted laterally");clear(gap,p,.5);
        near(choose(gap,200).getY(),300,"blocked point stopped at near-boundary inset");
        Chart split=strips(new Strip(-100,240,false),new Strip(360,700,false),new Strip(290,310,false),new Strip(290,310,true));
        near(choose(split,245).getY(),300,"raw cancelled XOR cuts split a composed gap");
        Chart cancelled=strips(new Strip(200,400,false),new Strip(200,400,true));near(choose(cancelled,245).getY(),245,"fully cancelled geometry moved an ordinary safe point");
        Chart parity=strips(new Strip(-100,240,false),new Strip(360,700,false),new Strip(290,310,true),new Strip(290,310,true));
        near(choose(parity,245).getY(),300,"even subtract parity created a false gap boundary");
        Chart narrow=strips(new Strip(-100,298,false),new Strip(302,700,false));Point n=choose(narrow,200);near(n.getY(),300,"usable 4px gap was discarded");clear(narrow,n,.5);
        Chart unrelated=chart(new Point(0,1),rect(50,100,100,500,false));near(choose(unrelated,245).getY(),245,"unrelated geometry recentered the viewport");
        Point unchanged=TapMod.withinBounds(gap,gap.getNotes().get(0),4,V,new Point(500,245),B);near(unchanged.getY(),245,"inactive domains changed a safe position");
    }
    private static void continuityAndBand()throws Exception{
        Chart wall=strips(new Strip(270,330,false));Point previous=new Point(500,200);
        Point held=TapMod.withinBounds(wall,wall.getNotes().get(0),.508,V,new Point(500,400),B,previous,500);
        near(held.getY(),135,"reachable hold component did not keep its center");TapMod.segment(wall,V,previous,held,500,508);checks++;
        JSONObject flash=rect(-100,100,1100,120,false).put("appearTime",.520).put("enableTime",.520).put("disableTime",.530).put("disappearTime",.530);
        Chart earlier=chart(new Point(0,1),rect(-100,300,1100,330,false),flash);
        Point retained=TapMod.withinBounds(earlier,earlier.getNotes().get(0),.55,V,new Point(500,50),B,new Point(500,50),500);
        near(retained.getY(),50,"centering rejected a safe stationary hold across an earlier flash");TapMod.segment(earlier,V,new Point(500,50),retained,500,550);checks++;
        Chart side=chart(new Point(0,1),rect(490,-100,510,700,false));Point lateral=choose(side,250);
        clear(side,lateral,.5);ok(Math.abs(lateral.getX()-500)<=ContactPlanner.radius(V)+.02,"lateral fallback escaped legal judgement band");
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
        Chart c=chart(new Point(0,1),upper,rect(-100,360,1100,700,false));Point p=choose(c,245);
        near(p.getY(),(241.44+360)/2,"moving temporal-guard edge was not used in the centered gap");clear(c,p,.5);
        for(String profile:new String[]{"16","24"}){
            AppSettings.values.put("tap_noise_clock_guard_ms",profile);TapMod.reload();Point q=choose(c,245);clear(c,q,.5);
            double edge=240+120*Integer.parseInt(profile)/1000.0;near(q.getY(),(edge+360)/2,"conservative guard midpoint "+profile);
        }
        AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.reload();
    }
    private static boolean blocked(Strip[] areas,double y){boolean normal=false;int inverted=0;for(Strip s:areas)if(y>=s.lo&&y<=s.hi){if(s.subtract)inverted++;else normal=true;}return normal^((inverted&1)!=0);}
    private static double oracle(Strip[] areas,double start){
        TreeSet<Double> cuts=new TreeSet<>();cuts.add(0.0);cuts.add(599.0);
        for(Strip s:areas){if(s.lo>0&&s.lo<599)cuts.add(s.lo);if(s.hi>0&&s.hi<599)cuts.add(s.hi);}
        ArrayList<Double> edges=new ArrayList<>(cuts);ArrayList<double[]> gaps=new ArrayList<>();double open=Double.NaN,last=Double.NaN;boolean anyBlocked=false;
        for(int i=1;i<edges.size();i++){
            double lo=edges.get(i-1),hi=edges.get(i);boolean safe=!blocked(areas,(lo+hi)*.5);
            if(safe){if(!Double.isNaN(open)&&lo==last&&!blocked(areas,lo))last=hi;else{if(!Double.isNaN(open))gaps.add(new double[]{open,last});open=lo;last=hi;}}
            else{anyBlocked=true;if(!Double.isNaN(open)){gaps.add(new double[]{open,last});open=Double.NaN;}}
        }
        if(!Double.isNaN(open))gaps.add(new double[]{open,last});if(!anyBlocked&&!blocked(areas,start))return start;
        double best=Double.NaN,width=-1,dist=Double.POSITIVE_INFINITY;boolean occupied=false;
        for(double[] g:gaps){double mid=(g[0]+g[1])*.5,w=g[1]-g[0],d=Math.abs(mid-start);boolean contains=!blocked(areas,start)&&g[0]<start&&g[1]>start;
            if(Double.isNaN(best)||contains&&!occupied||contains==occupied&&(w>width||w==width&&d<dist)){best=mid;width=w;dist=d;occupied=contains;}}
        return best;
    }
    private static void independentIntervals()throws Exception{
        Random r=new Random(20261120);long started=System.nanoTime();
        for(int trial=0;trial<180;trial++){
            int count=1+r.nextInt(8);Strip[] areas=new Strip[count];HashSet<Integer> used=new HashSet<>();
            for(int i=0;i<count;i++){int a,b;do{a=10+10*r.nextInt(58);}while(!used.add(a));do{b=10+10*r.nextInt(58);}while(!used.add(b));areas[i]=new Strip(Math.min(a,b),Math.max(a,b),r.nextBoolean());}
            Chart c=strips(areas);for(int sample=0;sample<4;sample++){
                double start;boolean edge;do{start=10.25+r.nextInt(579);edge=false;for(Strip area:areas)if(Math.abs(start-area.lo)<1||Math.abs(start-area.hi)<1)edge=true;}while(edge);
                double expected=oracle(areas,start);Point p=choose(c,start);
                StringBuilder description=new StringBuilder();for(Strip area:areas)description.append(' ').append(area.lo).append("..").append(area.hi).append(area.subtract?'S':'N');
                near(p.getY(),expected,"independent composed interval oracle trial "+trial+" start="+start+description);near(p.getX(),500,"full-width strips required unnecessary lateral movement");clear(c,p,.5);
                double[] around={p.getY()-1,p.getY(),p.getY()+1};for(double y:around)if(y>=0&&y<=599)ok(!blocked(areas,y),"center lacked pixel rounding clearance");
            }
        }
        System.out.println("Independent 720 composed-gap searches: "+((System.nanoTime()-started)/1000000)+" ms");
    }
    private static void signature()throws Exception{
        Chart c=strips(new Strip(200,400,false));String signature=TapMod.preparationSignature();ok(signature.equals(TapMod.preparationSignature()),"preparation signature unstable");
        long salt=TapMod.preparationSalt(c);ok(TapMod.preparationSalt(c)==salt,"preparation salt was not stable");TapMod.preparationSalt(c,42);ok(TapMod.preparationSalt(c)==42,"prepared salt did not restore exactly");
        AppSettings.values.put("tap_noise_clock_guard_ms","24");TapMod.reload();ok(!signature.equals(TapMod.preparationSignature()),"guard did not invalidate preparation signature");
        AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.reload();ok(signature.equals(TapMod.preparationSignature()),"canonical signature did not restore");
        AppSettings.edges=!AppSettings.edges;ok(!signature.equals(TapMod.preparationSignature()),"edge guard flag did not invalidate preparation signature");AppSettings.edges=!AppSettings.edges;
        AppSettings.inward=!AppSettings.inward;ok(!signature.equals(TapMod.preparationSignature()),"inward flick flag did not invalidate preparation signature");AppSettings.inward=!AppSettings.inward;
    }
    public static void main(String[] args)throws Exception{
        AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tap_noise_clock_guard_ms","0");TapMod.init(new Context());
        basic();continuityAndBand();movingAndGuard();independentIntervals();signature();
        System.out.println("PASS "+checks+" safe-gap center checks: composed XOR/parity, edge-safe midpoint, narrow gaps, moving guards, reachable holds, legal rotated bands, independent interval oracle and preparation cache inputs.");
    }
}
