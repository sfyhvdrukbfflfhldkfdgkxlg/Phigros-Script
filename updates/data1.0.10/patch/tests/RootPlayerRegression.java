package io.github.phiscript.input;

import android.os.SystemClock;
import io.github.phiscript.assets.PhiraChartParser;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;

public final class RootPlayerRegression {
    static int checks;
    static final Viewport VIEW=new Viewport(0,0,1000,600);
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void reset(){SystemClock.now=100000;RootClient.failAt=-1;RootClient.after=null;InputBackend.rootEnabled=true;}
    static RootClient play(Chart c)throws Exception{
        TouchService service=new TouchService();AtomicBoolean playing=new AtomicBoolean();
        ChartMultiTouchPlayer.play(service,c,SystemClock.now,VIEW,new AtomicBoolean(),s->Unit.INSTANCE,playing,new AtomicBoolean());
        ok(!playing.get(),"playing flag stuck");RootClient client=RootClient.last;
        ok(client.closed&&client.active.isEmpty(),"root client not released");return client;
    }
    public static void main(String[]args)throws Exception{
        for(int file=1;file<=3;file++){
            reset();String json=new String(Files.readAllBytes(Paths.get("analysis/realcharts/chart_"+file+".json")),"UTF-8");
            Chart chart=PhiraChartParser.INSTANCE.parse(json,0);List<TouchEvent> events=GesturePlan.obtain(chart,GestureProfile.read(new TouchService())).events;
            RootClient client=play(chart);ok(client.heads.size()==events.size(),"root head count on chart "+file+": "+client.heads.size()+"/"+events.size());
            for(long[] head:client.heads){int index=(int)head[0];TouchEvent e=events.get(index);long elapsed=head[1]-100000;
                ok(elapsed>=e.getStartMs()-1&&elapsed<=e.getStartMs()+35,"root head timing "+index+": "+elapsed+" vs "+e.getStartMs());
                ok(client.starts.get(head[0])==1,"hold restarted or head repeated");}
            System.out.println("PASS reference chart "+file+": "+events.size()+"/"+events.size()+" root heads; each physical task DOWN once; peak "+client.peak+" fingers; no invalid input or leftovers");
        }
        reset();Chart chart=PhiraChartParser.INSTANCE.parse(new String(Files.readAllBytes(Paths.get("patch/tests/fixtures/pgr_v3_multihold.json")),"UTF-8"),0);
        AtomicBoolean stop=new AtomicBoolean(),playing=new AtomicBoolean();RootClient.after=()->stop.set(true);
        ChartMultiTouchPlayer.play(new TouchService(),chart,SystemClock.now,VIEW,stop,s->Unit.INSTANCE,playing,new AtomicBoolean());
        ok(!playing.get()&&RootClient.last.active.isEmpty(),"stop leaves pointers");
        reset();RootClient.failAt=10;playing=new AtomicBoolean();boolean failed=false;
        try{ChartMultiTouchPlayer.play(new TouchService(),chart,SystemClock.now,VIEW,new AtomicBoolean(),s->Unit.INSTANCE,playing,new AtomicBoolean());}
        catch(IllegalStateException e){failed=true;}ok(failed&&!playing.get()&&RootClient.last.active.isEmpty(),"root rejection cleanup");
        System.out.println("PASS explicit stop and root transport rejection release pointers and ownership");
        reset();AtomicBoolean visualStop=new AtomicBoolean();RootClient.after=()->{if(RootClient.last.frames>=400)visualStop.set(true);};
        VisualMultiTouchPlayer.play(new TouchService(),1000,600,()->{
            List<VisualTouch> touches=new ArrayList<>();
            touches.add(new VisualTouch(1,VisualTouchKind.HOLD,.2f,.5f,0,0));
            touches.add(new VisualTouch(2,VisualTouchKind.HOLD,.4f,.5f,0,0));
            int group=RootClient.last.frames/10;
            for(int k=0;k<3;k++)touches.add(new VisualTouch(100+group*3+k,k==2?VisualTouchKind.FLICK:VisualTouchKind.TAP,.6f+k*.1f,.5f,0,-.08f));
            return new VisualTouchFrame(SystemClock.now,1000,600,touches);
        },visualStop,()->true,s->Unit.INSTANCE);
        ok(RootClient.last.starts.get(1L)==1&&RootClient.last.starts.get(2L)==1,"visual root holds restarted");
        ok(RootClient.last.active.isEmpty()&&RootClient.last.closed,"visual root cleanup");
        ok(RootClient.last.peak==5,"visual root chord peak");
        System.out.println("PASS root visual: continuous holds with 40 repeated three-finger tap/flick chords, then stop");
        InputBackend.rootEnabled=false;
        System.out.println("ALL "+checks+" ROOT PLAYER CHECKS PASSED (host mock transport; no Android/su execution)");
    }
}
