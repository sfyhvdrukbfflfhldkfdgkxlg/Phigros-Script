package io.github.phiscript.input;
import android.os.SystemClock;
import android.accessibilityservice.*;
import io.github.phiscript.engine.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;

/** Runs the actual AOSP conversion methods, including historical ID allocation. */
public final class SystemInputRegression {
    static Chart chart(List<Note> notes) {
        return Chart.Companion.fromNative$app_debug(notes,0,
            (n,t,v)->new Point(100+(float)n.getPositionX()*150,300),
            (n,t,v)->new Point(0,-1));
    }
    static void runChart(String name,List<Note> notes) throws Exception {
        SystemClock.now=100000;long epoch=SystemClock.now;
        Chart c=chart(notes);TouchService s=new TouchService();
        ChartMultiTouchPlayer.play(s,c,epoch,new Viewport(0,0,1000,600),new AtomicBoolean(),
            m->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
        for(TouchEvent e:ChordPlanner.forChart(c)) {
            float expected=100+(float)e.getNote().getPositionX()*150;
            boolean found=false;
            for(long[] d:s.downs)if(Math.abs(d[1]-epoch-e.getStartMs())<=55
                    &&Math.abs(Float.intBitsToFloat((int)d[2])-expected)<=2){found=true;break;}
            if(!found)throw new AssertionError("head missing: "+e);
        }
        if(!s.roots.isEmpty()||s.downs.size()!=s.ups.size())throw new AssertionError("unreleased finger");
        System.out.println("PASS "+name+": "+notes.size()+" note heads; "+s.downs.size()
            +" physical DOWNs including safe hold resumptions; "+s.audit.events+" valid AOSP events");
    }
    public static void main(String[] args)throws Exception {
        List<Note> notes=new ArrayList<>();
        notes.add(new Note(0,3,.02,3,0,1,0,true,0));
        notes.add(new Note(1,3,.02,3,1,1,0,true,0));
        for(int i=0;i<35;i++)for(int k=0;k<3;k++)
            notes.add(new Note(notes.size(),1,.1+i*.07,.1+i*.07,k+2,1,0,true,0));
        runChart("two sustained holds plus 105 repeated chord taps",notes);
        notes=new ArrayList<>();
        notes.add(new Note(0,3,.02,.1,0,1,0,true,0));
        notes.add(new Note(1,3,.02,.2,1,1,0,true,0));
        notes.add(new Note(2,1,.1,.1,2,1,0,true,0));
        runChart("hold UP and fresh DOWN at same chart timestamp",notes);
        SystemClock.now=100000;TouchService s=new TouchService();AtomicBoolean stop=new AtomicBoolean();
        s.after=g->{if(s.sends>=90)stop.set(true);};
        VisualMultiTouchPlayer.play(s,1000,600,()->{
            List<VisualTouch> touches=new ArrayList<>();
            touches.add(new VisualTouch(1,VisualTouchKind.HOLD,.2f,.5f,0,0));
            touches.add(new VisualTouch(2,VisualTouchKind.HOLD,.4f,.5f,0,0));
            for(int k=0;k<3;k++)touches.add(new VisualTouch(100+s.sends*3+k,
                VisualTouchKind.TAP,.6f+k*.1f,.5f,0,0));
            return new VisualTouchFrame(SystemClock.now,1000,600,touches);
        },stop,()->true,m->Unit.INSTANCE);
        if(!s.roots.isEmpty()||s.downs.size()!=s.ups.size())throw new AssertionError("visual finger leak");
        System.out.println("PASS visual holds with repeated chord taps: "+s.audit.events+" valid AOSP events");
    }
}
