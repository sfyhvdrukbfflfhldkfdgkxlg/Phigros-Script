package io.github.phiscript.input;
import android.content.Context;import io.github.phiscript.engine.*;import io.github.phiscript.capture.AutoEngineRegression;import java.util.concurrent.atomic.AtomicBoolean;import kotlin.*;import kotlin.jvm.functions.Function1;
public final class TouchService extends Context {
 public static final String GAME_PACKAGE="game";
 public static final Companion Companion=new Companion();public static TouchService current;public TouchService(){current=this;}public static class Companion {public TouchService getCurrent(){return current;}}
 public boolean getGameForeground(){
  if(AutoEngineRegression.focusWait>0){AutoEngineRegression.focusWait--;AutoEngineRegression.focusChecks++;if(AutoEngineRegression.scenario.equals("cancelgrant")&&AutoEngineRegression.focusChecks>=2)AutoEngineRegression.stop.set(true);return false;}return true;
 }
 public void selectGame(String s){}
 public long tapNormalized(float x,float y,int n,int i,int w,int h,AtomicBoolean stop){AutoEngineRegression.accessTaps++;return AutoEngineRegression.tap(x,y,n);}
 public void play(Chart c,long epoch,Viewport v,AtomicBoolean stop,Function1 f)throws InterruptedException{io.github.phiscript.record.CaptureVideo.playing();AutoEngineRegression.played(epoch,stop);}
}
