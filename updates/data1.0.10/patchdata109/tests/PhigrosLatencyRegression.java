package io.github.phiscript.input;

import io.github.phiscript.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.*;
import android.os.SystemClock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;

/** Startup/drift interaction and the actual Root/AOSP scheduler gap handoff. */
public final class PhigrosLatencyRegression {
    static int checks;
    static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static void startupAndDrift(){
        for(long change:new long[]{-2000,2000}){
            ChartPlaybackClock clock=new ChartPlaybackClock(100000);
            clock.calibrate(100000+change);
            ChartPlaybackClock.Slice startup=clock.snapshot(100100);
            ok(!clock.refine(100024+change,101200),"drift replaced unfinished startup convergence");
            ok(clock.snapshot(101200)==startup,"rejected drift changed the startup projection");
            ok(startup.chartAt(108100)==8100-change,"startup convergence took longer than its original bound");
            ok(clock.refine(100024+change,108100),"drift refused after startup convergence");
        }
        for(long change:new long[]{-2000,2000}){
            ChartPlaybackClock clock=new ChartPlaybackClock(100000);
            ChartPlaybackClock.Slice submitted=clock.snapshot(100000);
            clock.calibrate(100000+change);
            ChartPlaybackClock.Slice aligned=clock.snapshotIdle(100300,4000);
            ok(aligned.chartAt(100300)==300-change,"empty intro retained a seconds-long phase error");
            ok(aligned.wallAt(4000)==104000+change,"future head did not use the confirmed origin");
            ok(submitted.chartAt(100300)==300,"in-flight projection was mutated");
        }
        ChartPlaybackClock crossing=new ChartPlaybackClock(100000);
        crossing.calibrate(98000);
        ChartPlaybackClock.Slice safe=crossing.snapshotIdle(100300,1000);
        ok(safe.chartAt(100300)==300,"idle correction jumped past an unsent head");
        ChartPlaybackClock atHead=new ChartPlaybackClock(100000);
        atHead.calibrate(100100);
        ok(atHead.snapshotIdle(101000,1000).chartAt(101000)==1000,"idle correction rewound a due head");
    }
    static void backends()throws Exception{
        List<Note> notes=new ArrayList<>();
        notes.add(new Note(0,3,.05,.6,-.3,1,0,true,0));
        notes.add(Data106Regression.n(1,1,4,0));
        notes.add(Data106Regression.n(2,1,4.2,0));
        Chart chart=Data104Regression.chart(notes);
        try{for(boolean root:new boolean[]{true,false})for(long change:new long[]{-2000,2000}){
            InputBackend.rootEnabled=root;SystemClock.now=100000;
            RootClient.after=null;RootClient.failAt=-1;
            TouchService service=new TouchService();ChartPlaybackClock clock=new ChartPlaybackClock(100000);
            AtomicBoolean requested=new AtomicBoolean();
            Runnable calibrate=()->{if(SystemClock.now>=100150&&requested.compareAndSet(false,true))clock.calibrate(100000+change);};
            if(root)RootClient.after=calibrate;else service.after=g->calibrate.run();
            ChartPlaybackClock.bind(clock);
            try{ChartMultiTouchPlayer.play(service,chart,100000,Data106Regression.V,new AtomicBoolean(),s->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());}
            finally{ChartPlaybackClock.unbind();RootClient.after=null;}
            ok(requested.get(),"no calibration during held contact");
            List<long[]> heads=root?RootClient.last.heads:service.downs;
            ok(heads.size()==3,"startup correction replayed/lost a head or restarted the hold");
            for(int i=1;i<heads.size();i++)ok(Math.abs(heads.get(i)[1]-(100000+change+4000+(i-1)*200))<=24,
                "idle gap did not settle before the future blue: "+root+" / "+change+" / "+heads.get(i)[1]);
            ok(root?RootClient.last.active.isEmpty():service.roots.isEmpty(),"calibrated contacts leaked");
        }}finally{InputBackend.rootEnabled=false;RootClient.after=null;}
    }
    public static void main(String[] args)throws Exception{
        startupAndDrift();backends();
        System.out.println("PASS "+checks+" Phigros latency checks: startup drift cannot reduce unfinished convergence to 3%, idle gaps settle a confirmed origin without crossing unsent heads, held contacts and submitted projections remain intact, and Root/AOSP models hit future heads after +/-2s startup errors. No device timing claim.");
    }
}
