package io.github.phiscript.capture;
import android.graphics.Bitmap;import android.os.SystemClock;import io.github.phiscript.engine.*;import io.github.phiscript.input.*;import io.github.phiscript.root.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Fake camera/transport events; production confirmation, retry, clock and monitor execute unchanged. */
public final class SessionTrace {
 public static String phase,ending;public static AtomicBoolean stop;public static TouchService service;public static final List<String> commands=new ArrayList<>();
 public static int playFrames,endedFrames,plays,offers,loads,rootDowns;public static long receipt,epoch,staleBegan,staleStopped;public static Chart chart;public static Viewport viewport;public static boolean rootConsumed;public static float retryX,retryY,pauseLeft;
 public static void reset(String outcome,boolean root){phase="GAME";ending=outcome;stop=new AtomicBoolean();commands.clear();playFrames=endedFrames=plays=offers=loads=rootDowns=0;receipt=epoch=staleBegan=staleStopped=0;chart=null;viewport=null;rootConsumed=false;retryX=retryY=pauseLeft=-1;SystemClock.now=100000;RootClient.created=0;RootClient.startupDelayMs=2000;RootClient.menuReleaseAdvanceMs=0;RootClient.failAt=-1;RootClient.after=null;InputBackend.rootEnabled=root;}
 public static void control(int count,long at){control(count,at,.5f,.5f);}
 public static void control(int count,long at,float x,float y){commands.add(count==2?"pause:2":"retry:1");if(count==2)phase="MENU";else{phase="RETRIED";receipt=at;retryX=x;retryY=y;}}
 public static void rootFrame(){int size=RootClient.last.heads.size();if(size==rootDowns)return;rootDowns=size;if(size==1)control(2,RootClient.last.heads.get(0)[1]);if(size==3){long[] head=RootClient.last.heads.get(2);control(1,head[1],Float.intBitsToFloat((int)head[2])/2400,Float.intBitsToFloat((int)head[3])/1080);}}
 public static Bitmap frame(){
  SystemClock.now+=130;Bitmap b=new Bitmap();b.paused=phase.equals("MENU")||phase.equals("RETRIED")&&ending.equals("retry-stuck");b.gameplay=!b.paused;
  if(phase.equals("PLAY")){
   playFrames++;
   if(playFrames>=2){
    if(ending.equals("stale")){if(staleBegan==0)staleBegan=SystemClock.now;SystemClock.now+=550;return null;}
    if(ending.equals("pause"))b.paused=true;
    if((ending.equals("result")||ending.equals("detected-retry")))b.result=true;
    if(ending.equals("dimension"))b.width=2300;
    if(ending.equals("switch"))b.title="Other Song";
    if(ending.equals("focus"))service.foregroundPackage="other.app";
    if(ending.equals("cancel"))stop.set(true);
   }
  }
  if(phase.equals("ENDED")){b.result=true;if(++endedFrames>=2)stop.set(true);}
  return b;
 }
 public static void played(Chart c,long at,Viewport v,AtomicBoolean cancelled)throws InterruptedException{
  chart=c;epoch=at;viewport=v;plays++;phase="PLAY";RootClient root=null;
  try{try{root=SharedChartPlayback.takeRoot(c);rootConsumed=root!=null;}catch(Exception error){throw new IllegalStateException(error);}
   long timeout=java.lang.System.nanoTime()+10_000_000_000L;while(!cancelled.get()&&java.lang.System.nanoTime()<timeout)Thread.sleep(1);
   if(!cancelled.get())throw new AssertionError("shared session failed to stop its player");if(staleBegan>0)staleStopped=SystemClock.now;
  }finally{if(root!=null)root.close();phase="ENDED";if(ending.equals("focus"))stop.set(true);}
 }
}
