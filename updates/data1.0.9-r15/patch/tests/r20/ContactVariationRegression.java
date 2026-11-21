package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ContactVariationRegression {
    static final Viewport VIEW=new Viewport(0,0,1920,1080);
    static final TouchBounds FULL=new TouchBounds(0,0,1919,1079);
    static final GestureProfile PROFILE=GestureProfile.read(null);
    static final String KEY=PreparedChartStore.digest(new byte[]{12});
    static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    static void configure(int delay,float drift){
        AppSettings.values.put("tap_random_delay_ms",Integer.toString(delay));
        AppSettings.values.put("tap_screen_drift_permille",Float.toString(drift));
        TapMod.init(new Context());
    }
    static Chart chart(){
        List<Note> notes=new ArrayList<>();
        for(int i=0;i<20;i++){
            double hit=1+i*.2;
            notes.add(new Note(i*2,1,hit,hit,.25,1,0,true,0));
            notes.add(new Note(i*2+1,1,hit,hit,.75,1,0,true,0));
        }
        return Chart.Companion.fromNative$app_debug(notes,.173,
                (n,t,v)->new Point((float)n.getPositionX()*v.getWidth(),v.getHeight()/2),(n,t,v)->new Point(0,1));
    }
    public static void main(String[] args)throws Exception{
        AppSettings.values.clear();
        AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=AppSettings.edges=AppSettings.inward=true;
        AppSettings.values.put("tapmod_enabled","true");
        AppSettings.values.put("tap_vertical_permille","0");
        AppSettings.values.put("tap_jitter_permille","0");
        configure(0,0);Chart chart=chart();
        GesturePlan plain=GesturePlan.prepareForImport(chart,PROFILE,VIEW,FULL,null);
        configure(15,5);
        GesturePlan varied=GesturePlan.prepareForImport(chart,PROFILE,VIEW,FULL,null);
        int delays=0,up=0,down=0;long previousStart=-1,previousDelay=-1;
        require(varied.events.size()==plain.events.size(),"lost an event");
        for(int i=0;i<varied.events.size();i++){
            TouchEvent before=plain.events.get(i),after=varied.events.get(i);
            long delay=after.getStartMs()-before.getStartMs();
            require(after.getNote()==before.getNote()&&delay>=0&&delay<=15&&after.getEndMs()-before.getEndMs()==delay,"changed duration or note timing");
            if(previousStart==before.getStartMs())require(previousDelay==delay,"split a chord");
            previousStart=before.getStartMs();previousDelay=delay;
            if(delay>0)delays++;
            Point a=plain.preparedPoint(before,before.getStartMs(),null,0);
            Point b=varied.preparedPoint(after,after.getStartMs(),null,0);
            float dy=b.getY()-a.getY();
            require(b.getX()==a.getX()&&Math.abs(dy)<=5.401f,"drift exceeded the signed vertical bound");
            if(dy<0)up++;if(dy>0)down++;
        }
        require(delays>0&&up>0&&down>0,"sampled variation was missing");
        byte[] saved=varied.encodePrepared(KEY);
        require(Arrays.equals(saved,GesturePlan.decodePrepared(chart,PROFILE,VIEW,FULL,saved,KEY).encodePrepared(KEY)),"rerolled on disk load");
        require(chart.getOffsetSeconds()==.173,"retimed the calibration clock");
        configure(0,0);
        require(GesturePlan.cachedPrepared(chart,PROFILE,VIEW,FULL)==null,"settings change reused old touches");
        TapMod.init(new Context());ContactVariation v=new ContactVariation(20,5f);
        Note note=chart.getNotes().get(0);TouchEvent event=new TouchEvent(note,10,40);
        require(v.delay(123,Collections.singletonList(event),new TreeSet<Long>(Arrays.asList(10L,40L)))==0,"overlapped an exact handoff");
        JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",.4).put("y",.4))
                .put("topRightPercentage",new JSONObject().put("x",.6).put("y",.6)).put("enableTime",1).put("disableTime",2);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(chart);
        require(!ContactVariation.safe(chart,VIEW,980,new float[]{960},new float[]{540},new byte[]{0},20,0,null),"accepted delay into newly active noise");
        System.out.println("PASS randomized contact timing and screen-vertical drift: chords, bounds, noise, handoffs, and cache reload");
    }
}
