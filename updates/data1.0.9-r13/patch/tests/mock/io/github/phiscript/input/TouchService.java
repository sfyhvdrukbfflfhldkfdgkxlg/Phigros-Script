package io.github.phiscript.input;
public class TouchService extends android.accessibilityservice.AccessibilityService {
 public static final Companion Companion=new Companion();public static class Companion {public TouchService current;public TouchService getCurrent(){return current;}}
 public TouchService(){Companion.current=this;}
 public boolean getGameForeground(){return foreground;}
 public long tapNormalized(float x,float y,int count,int interval,int width,int height,java.util.concurrent.atomic.AtomicBoolean stop){return android.os.SystemClock.uptimeMillis();}
 public void play(io.github.phiscript.engine.Chart chart,long epoch,io.github.phiscript.engine.Viewport viewport,java.util.concurrent.atomic.AtomicBoolean stop,kotlin.jvm.functions.Function1<? super String,kotlin.Unit> status)throws InterruptedException{
  ChartMultiTouchPlayer.play(this,chart,epoch,viewport,stop,status,new java.util.concurrent.atomic.AtomicBoolean(),new java.util.concurrent.atomic.AtomicBoolean());
 }
}
