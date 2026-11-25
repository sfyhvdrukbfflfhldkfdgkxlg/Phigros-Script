package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Note;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.TouchEvent;
import io.github.phiscript.engine.Viewport;
import java.util.Arrays;

/** Check contact semantics at game-frame intervals as well as route anchor times. */
public final class PhiraContactRegression {
    private static final Viewport VIEW=new Viewport(0,0,2160,1080);
    private static final TouchBounds BOUNDS=new TouchBounds(0,0,2159,1079);

    private static Note note(int id,int kind,double hit,double tail){
        return new Note(id,kind,hit,tail,.45,1,0,true,0);
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private static double speed(Point a,Point b,int interval){
        return 2000*Math.hypot(a.getX()-b.getX(),a.getY()-b.getY())/(VIEW.getWidth()*interval);
    }

    private static void crossingDrag(){
        Note tap=note(0,1,.5,.5),drag=note(1,2,.5,.5);
        Chart chart=Chart.Companion.fromNative$app_debug(Arrays.asList(tap,drag),0,
            (n,t,v)->n==tap?new Point(650,280):new Point(1400,750),
            (n,t,v)->n==tap?new Point(0,1):new Point(1,0));
        GesturePlan plan=ContactPlanner.build(chart,VIEW,BOUNDS,new GestureProfile(false,70,.12f,false,false,false));
        require(plan.events.size()==1 && plan.coveredDrags==1,"crossing drag generated a second DOWN");
        TouchEvent event=plan.events.get(0);
        for(long ms=500;ms<=520;ms++){
            Point p=plan.routes.get(event).at(chart,ms,VIEW,BOUNDS);
            require(Math.abs(p.getX()-650)<VIEW.getWidth()*.118125,"shared DOWN missed the tap strip");
            require(Math.abs(p.getY()-750)<ContactPlanner.radius(VIEW),"shared DOWN missed the drag strip");
        }
    }

    private static void threeHeads(){
        Note upper=note(0,1,1,1),lower=note(1,1,1,1),hold=note(2,3,1,1.5);
        Chart chart=Chart.Companion.fromNative$app_debug(Arrays.asList(upper,lower,hold),0,
            (n,t,v)->n==hold?new Point(1560,828):new Point(550,n==upper?324:756),
            (n,t,v)->n==hold?new Point(-.39f,.92f):new Point(-1,0));
        TouchBounds clipped=new TouchBounds(0,150,2159,940);
        GesturePlan plan=ContactPlanner.build(chart,VIEW,clipped,new GestureProfile(false,70,.12f,false,false,false));
        require(plan.events.size()==3,"a chord head was merged");
        for(TouchEvent event:plan.events)if(event.getNote()==hold){
            for(long ms=1000;ms<=1016;ms++){
                Point p=plan.routes.get(event).at(chart,ms,VIEW,clipped);
                double own=Math.abs((p.getX()-1560)*.92+(p.getY()-828)*.39)/Math.hypot(.92,.39);
                double peer=Math.min(Math.abs(p.getY()-324),Math.abs(p.getY()-756));
                require(own<VIEW.getWidth()*.118125 && peer>Math.max(own,VIEW.getWidth()*.065875),
                    "the hold finger could steal a neighbouring tap in the same frame");
            }
            require(plan.routes.get(event).at(chart,1060,VIEW,clipped).equals(chart.position(hold,1.060,VIEW,false)),
                "head separation changed the sustained part of the hold");
        }
    }

    private static void stableHolds(){
        Note left=note(0,3,1,1.5),right=note(1,3,1,1.5);
        Chart chart=Chart.Companion.fromNative$app_debug(Arrays.asList(left,right),0,
            (n,t,v)->new Point(n==left?900:1080,(float)(300+t*100)),(n,t,v)->new Point(0,1));
        GesturePlan plan=ContactPlanner.build(chart,VIEW,BOUNDS,new GestureProfile(false,70,.12f,false,false,false));
        for(TouchEvent event:plan.events){
            for(long ms=1000;ms<=1500;ms+=10){
                Point expected=chart.position(event.getNote(),ms/1000.,VIEW,false);
                require(plan.routes.get(event).at(chart,ms,VIEW,BOUNDS).equals(expected),
                    "an unambiguous multiple hold was moved into another lane");
            }
        }
    }

    public static void main(String[] args){
        Note tap=note(0,1,.2,.2),hold=note(1,3,1,1.8),stack=note(2,3,1.2,1.5);
        Note drag=note(3,2,3,3),flick=note(4,4,5,5);
        Chart chart=Chart.Companion.fromNative$app_debug(Arrays.asList(tap,hold,stack,drag,flick),0,
            (n,t,v)->new Point(960,(float)(300+220*t%500)),(n,t,v)->new Point(0,1));
        TapMod.init(new Context());
        GesturePlan plan=ContactPlanner.build(chart,VIEW,BOUNDS,new GestureProfile(false,70,.12f,false,false,false));
        require(plan.events.size()==5 && plan.unavailableNotes==0,"lost a gesture");
        for(TouchEvent event:plan.events){
            ContactPlanner.Route route=plan.routes.get(event);
            Note n=event.getNote();
            if(n==tap){
                require(event.getStartMs()==200,"tap DOWN time");
                require(route.at(chart,200,VIEW,BOUNDS).equals(route.at(chart,240,VIEW,BOUNDS)),"tap slid with the line");
            }else if(n==hold || n==stack){
                require(event.getStartMs()==ContactPlanner.hit(n),"hold missing a distinct head");
                require(event.getEndMs()==Math.round(n.getEndSeconds()*1000),"hold tail shifted");
                require(!route.at(chart,event.getStartMs(),VIEW,BOUNDS).equals(route.at(chart,event.getEndMs(),VIEW,BOUNDS)),
                    "hold lost its native position track");
            }else if(n==drag){
                require(event.getStartMs()<3000 && event.getEndMs()>3020,"insufficient drag dwell");
            }else if(n==flick){
                // The first nonzero sample seeds FlickTracker; a second above the
                // threshold is required for every possible phase of a 60 Hz frame.
                for(int phase=0;phase<17;phase++){
                    int moving=0;
                    long from=event.getStartMs()+phase;
                    for(long t=from+17;t<event.getEndMs();t+=17){
                        double velocity=speed(route.at(chart,t-17,VIEW,BOUNDS),route.at(chart,t,VIEW,BOUNDS),17);
                        if(velocity>1.14)moving++;
                    }
                    require(moving>=2,"flick had fewer than two fast MOVE samples at phase "+phase);
                }
            }else throw new AssertionError("original note identity was lost");
        }
        crossingDrag();
        threeHeads();
        stableHolds();
        System.out.println("PASS distinct heads, native hold tracks, drag dwell, 17 flick phases, crossing drag, three-head ranks and stable holds");
    }
}
