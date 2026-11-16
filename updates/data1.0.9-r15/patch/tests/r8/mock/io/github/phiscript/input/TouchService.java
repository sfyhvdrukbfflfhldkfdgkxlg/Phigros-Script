package io.github.phiscript.input;
import io.github.phiscript.*;import io.github.phiscript.capture.SessionTrace;
public final class TouchService extends android.accessibilityservice.AccessibilityService {
 public static final String GAME_PACKAGE="com.PigeonGames.Phigros",PHIRA_PACKAGE="org.flos.phira";public static final Companion Companion=new Companion();public static final class Companion{public TouchService current;public TouchService getCurrent(){return current;}}
 public String foregroundPackage=PHIRA_PACKAGE,selectedPackage=PHIRA_PACKAGE;public TouchService(){Companion.current=this;SessionTrace.service=this;}
 public void selectGame(String pkg){selectedPackage=pkg;}public String getForegroundPackage(){return foregroundPackage;}
 public boolean getGameForeground(){return selectedPackage.equals(foregroundPackage);}
 public long tapNormalized(float x,float y,int count,int interval,int width,int height,java.util.concurrent.atomic.AtomicBoolean stop){long receipt=android.os.SystemClock.uptimeMillis();SessionTrace.control(count,receipt,x,y);return receipt;}
 public void play(io.github.phiscript.engine.Chart chart,long epoch,io.github.phiscript.engine.Viewport viewport,java.util.concurrent.atomic.AtomicBoolean stop,kotlin.jvm.functions.Function1<? super String,kotlin.Unit> status)throws InterruptedException{SessionTrace.played(chart,epoch,viewport,stop);}
}
