package io.github.phiscript.input;
import io.github.phiscript.*;import io.github.phiscript.engine.*;import io.github.phiscript.root.*;
import android.os.SystemClock;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import kotlin.Unit;

public final class Data107Regression {
 static int checks;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void clock()throws Exception{
  for(long correction:new long[]{-4300,-250,-1,0,1,250,4300}){
   ChartPlaybackClock clock=new ChartPlaybackClock(100000);
   ChartPlaybackClock.Slice original=clock.snapshot(100000);
   ok(original.chartAt(100500)==500,"original chart blocked before calibration");
   ok(clock.calibrate(100000+correction)&&!clock.calibrate(200000),"calibration was not one-shot");
   ok(original.chartAt(100700)==700,"submitted projection mutated while in flight");
   ChartPlaybackClock.Slice adjusted=clock.snapshot(100700);
   ok(adjusted.chartAt(100700)==700,"handoff jumped or rewound chart time");
   long previous=700;
   for(long wall=100701;wall<121000;wall++){
    long time=adjusted.chartAt(wall);
    ok(time>=previous&&time-previous<=2,"calibration skipped/rewound scheduler time");
    long back=adjusted.wallAt(time);
    ok(back<=wall+1&&back>=wall-1,"chart/wall conversion inconsistent");
    previous=time;
   }
   ok(adjusted.chartAt(121000)==21000-correction,"did not converge to calibrated data");
  }
  ChartPlaybackClock clock=new ChartPlaybackClock(100000);ChartPlaybackClock.bind(clock);
  try{
   ok(ChartPlaybackClock.resolve(100000)==clock,"worker did not receive shared clock");
   ok(ChartPlaybackClock.resolve(99999)!=clock,"clock leaked to another epoch");
   AtomicBoolean isolated=new AtomicBoolean();Thread thread=new Thread(()->isolated.set(ChartPlaybackClock.resolve(100000)!=clock));thread.start();thread.join();ok(isolated.get(),"clock leaked across threads");
  }finally{ChartPlaybackClock.unbind();}
  ok(ChartPlaybackClock.resolve(100000)!=clock,"clock binding leaked after playback");
 }
 static void backends()throws Exception{
  List<Note> notes=new ArrayList<>();notes.add(new Note(0,3,.05,5,-.3,1,0,true,0));notes.add(new Note(1,3,.05,5.1,.3,1,0,true,0));
  for(int i=0;i<20;i++)notes.add(Data106Regression.n(10+i,1,.2+i*.2,0));Chart chart=Data104Regression.chart(notes);
  AppSettings.smart=AppSettings.catchDrags=AppSettings.combos=AppSettings.flicks=true;
  try{for(boolean root:new boolean[]{true,false})for(long correction:new long[]{-180,180}){
   InputBackend.rootEnabled=root;SystemClock.now=100000;RootClient.after=null;RootClient.failAt=-1;
   TouchService service=new TouchService();ChartPlaybackClock clock=new ChartPlaybackClock(100000);AtomicBoolean requested=new AtomicBoolean();
   Runnable update=()->{if(SystemClock.now>=100650&&requested.compareAndSet(false,true))clock.calibrate(100000+correction);};
   if(root)RootClient.after=update;else service.after=g->update.run();
   ChartPlaybackClock.bind(clock);
   try{ChartMultiTouchPlayer.play(service,chart,100000,Data106Regression.V,new AtomicBoolean(),s->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());}
   finally{ChartPlaybackClock.unbind();RootClient.after=null;}
   ok(requested.get(),"calibration not requested during play");
   List<long[]> heads=root?RootClient.last.heads:service.downs;
   ok(heads.size()==22,"calibration replayed/lost blue or hold heads: "+root+" / "+correction+" / "+heads.size());
   int left=0,right=0,blue=0;
   for(long[] h:heads){float x=Float.intBitsToFloat((int)h[2]);
    if(Math.abs(x-320)<1)left++;else if(Math.abs(x-680)<1)right++;
    else if(Math.abs(x-500)<1){
     if(blue>=10)ok(Math.abs(h[1]-(100000+correction+200+blue*200))<=24,"future blue did not use calibrated time");
     if(root)ok(RootClient.last.releases.get(h[0])-h[1]>=33,"slew shortened blue below 30 Hz frame");
     blue++;
    }
   }
   ok(left==1&&right==1&&blue==20,"active holds restarted during calibration");
   ok(root?RootClient.last.active.isEmpty():service.roots.isEmpty(),"calibrated contacts leaked");
  }}finally{AppSettings.smart=AppSettings.catchDrags=AppSettings.combos=AppSettings.flicks=false;InputBackend.rootEnabled=false;}
 }
 public static void main(String[] args)throws Exception{clock();backends();System.out.println("PASS "+checks+" progressive clock checks: immediate original data, immutable in-flight batches, one-shot bounded monotonic calibration, converged timing, worker isolation, both Root/AOSP backends with two continuing holds and 20 fresh blue heads. Host models only.");}
}
