package io.github.phiscript.input;

import android.os.SystemClock;
import io.github.phiscript.engine.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;

/** Reproduces the real Android conditions missed by the initial mock. */
public final class ProtocolRegression {
    public static void main(String[] args) throws Exception {
        for (boolean joining : new boolean[]{false,true}) {
            SystemClock.now=100000;
            List<Note> notes=new ArrayList<>();
            notes.add(new Note(0,3,.01,.4,0,1,0,true,0));
            if(joining)notes.add(new Note(1,1,.055,.055,1,1,0,true,0));
            notes.add(new Note(2,1,.5,.5,2,1,0,true,0));
            Chart c=Chart.Companion.fromNative$app_debug(notes,0,
                (n,t,v)->new Point(200+n.getId()*100,300),(n,t,v)->new Point(0,-1));
            TouchService s=new TouchService();
            try {
                ChartMultiTouchPlayer.play(s,c,SystemClock.now,new Viewport(0,0,1000,600),
                    new AtomicBoolean(),m->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
            } catch (RuntimeException e) {
                throw new AssertionError((joining?"join a note during hold":"stationary hold")
                    +": "+s.cancellationReason+"; dispatch="+s.sends,e);
            }
            if(s.downs.size()!=notes.size()||s.ups.size()!=notes.size()||!s.roots.isEmpty())
                throw new AssertionError("stopped after first hold or leaked fingers");
            System.out.println("PASS "+(joining?"hold plus later note":"stationary hold")
                +" then continue after UP: "+s.downs.size()+" notes; "+s.sends+" gestures");
        }
        SystemClock.now=100000;
        List<Note> overlapping=Arrays.asList(
            new Note(0,3,.02,.5,0,1,0,true,0),
            new Note(1,3,.1,.5,0,1,0,true,0),
            new Note(2,3,.2,.5,0,1,0,true,0));
        Chart held=Chart.Companion.fromNative$app_debug(overlapping,0,
            (n,t,v)->new Point(400,300),(n,t,v)->new Point(0,-1));
        List<TouchEvent> heldEvents=ChordPlanner.forChart(held);
        if(heldEvents.stream().filter(e->e.getNote().getType()==3).count()!=1
                ||heldEvents.stream().filter(e->e.getNote().getType()==1).count()!=2)
            throw new AssertionError("overlapping hold heads were lost");
        TouchService service=new TouchService();
        ChartMultiTouchPlayer.play(service,held,SystemClock.now,new Viewport(0,0,1000,600),new AtomicBoolean(),
            m->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
        if(service.downs.size()!=3||service.peak!=2||!service.roots.isEmpty())
            throw new AssertionError("shared hold with head retriggers failed");
        System.out.println("PASS same-position overlapping holds: sustain plus every head retrigger");

        SystemClock.now=100000;
        List<Note> stacked=new ArrayList<>();
        for(int copy=0;copy<4;copy++)for(int position=0;position<4;position++)
            stacked.add(new Note(copy*4+position,4,.5,.5,position,1+copy*.2,0,true,0));
        Chart flicks=Chart.Companion.fromNative$app_debug(stacked,0,
            (n,t,v)->new Point(200+(float)n.getPositionX()*150,300),(n,t,v)->new Point(0,-1));
        List<TouchEvent> flickEvents=ChordPlanner.forChart(flicks);
        if(flickEvents.size()!=16)throw new AssertionError("stacked flicks must not be discarded");
        ChordPlanner.checkOverlap(flickEvents,10);
        service=new TouchService();
        ChartMultiTouchPlayer.play(service,flicks,SystemClock.now,new Viewport(0,0,1000,600),new AtomicBoolean(),
            m->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
        if(service.downs.size()!=16||service.ups.size()!=16||!service.roots.isEmpty())
            throw new AssertionError("sixteen flicks did not all fire");
        System.out.println("PASS 16 stacked flicks at 4 positions: every flick scheduled; peak="+service.peak);
    }
}
