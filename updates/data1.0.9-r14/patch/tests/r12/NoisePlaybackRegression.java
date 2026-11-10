package io.github.phiscript.input;
import io.github.phiscript.engine.*;
import io.github.phiscript.AppSettings;
import io.github.phiscript.root.*;
import android.os.SystemClock;
import org.json.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
public final class NoisePlaybackRegression {
    static Note note(int id,int type,double t,double end,double x){return new Note(id,type,t,end,x,1,0,true,0);}
    static Chart build(JSONObject area,Note... notes)throws Exception {
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));
        return Chart.Companion.fromNative$app_debug(Arrays.asList(notes),0,(n,t,v)->new Point((float)(n.getPositionX()*v.getWidth()),v.getHeight()/2),(n,t,v)->new Point(0,1));
    }
    static JSONObject area(double start,double end)throws Exception {
        return CoreTest.area(.1,-1,.5,2).put("appearTime",start).put("enableTime",start).put("disableTime",end).put("disappearTime",end);
    }
    static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args)throws Exception{
        TapMod.init(new android.content.Context());AppSettings.values.put("tapmod_enabled","false");TapMod.reload();
        Chart links=build(area(.70,.82),note(1,2,.31,.31,.3),note(2,2,.4,.4,.3),note(3,2,.75,.75,.3),note(4,2,1.05,1.05,.3),note(5,2,1.14,1.14,.3));
        GesturePlan plan=GesturePlan.obtain(links,new GestureProfile(true,70,.12f,true,true,true));
        require(plan.connectedGroups==2,"quiet chains must resume after noise");
        require(plan.events.size()==3,"two quiet groups plus one independent noise note");
        for(TouchEvent e:plan.events)require(!(e.getStartMs()<700&&e.getEndMs()>820),"cross-window connection");
        for(boolean root:new boolean[]{true,false}){
            SystemClock.now=100000;InputBackend.rootEnabled=root;TouchService s=new TouchService();long base=SystemClock.now;
            Chart held=build(area(.14,.27),note(1,3,.03,.48,.30),note(2,3,.03,.48,.78),note(3,1,.55,.55,.63));
            ChartMultiTouchPlayer.play(s,held,base,new Viewport(0,0,1000,600),new AtomicBoolean(),m->kotlin.Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
            List<long[]> downs=root?RootClient.last.heads:s.downs;int left=0,right=0,last=0;
            for(long[] v:downs){
                float x=Float.intBitsToFloat((int)v[2]);
                if(x<450)left++;else if(x>700)right++;else last++;
            }
            require(left>=2,"blocked hold should restart after release "+root+" "+left);
            require(right==1,"safe parallel hold should stay pressed "+root+" "+right);
            require(last==1,"post-zone tap missing "+root);
        }
        System.out.println("PASS active-window separation and quiet linking; one held finger releases/restarts, parallel hold continues, later TAP dispatched on both backends.");
    }
}
