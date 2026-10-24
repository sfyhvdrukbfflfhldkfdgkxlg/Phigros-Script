package io.github.phiscript.root;
import android.content.Context;import java.util.concurrent.atomic.AtomicBoolean;import io.github.phiscript.capture.AutoEngineRegression;
public final class RootWarmup implements AutoCloseable {
 boolean handed;public RootWarmup(Context c,int w,int h){AutoEngineRegression.warmed++;android.os.SystemClock.offset+=45000;AutoEngineRegression.grantAt=android.os.SystemClock.uptimeMillis();if(AutoEngineRegression.scenario.equals("rootfocus")||AutoEngineRegression.scenario.equals("cancelgrant"))AutoEngineRegression.focusWait=4;}
 public void check(){}
 public long tap(float x,float y,int n,int interval,AtomicBoolean stop){return AutoEngineRegression.tap(x,y,n);}
 public RootClient take(){AutoEngineRegression.taken++;handed=true;return new RootClient();}
 public void close(){if(!handed)AutoEngineRegression.closed++;}
}
