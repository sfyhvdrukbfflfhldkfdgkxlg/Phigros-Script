package io.github.phiscript.input;
public class TouchService extends android.accessibilityservice.AccessibilityService {
 public static final String GAME_PACKAGE="com.PigeonGames.Phigros",PHIRA_PACKAGE="org.flos.phira";
 public String targetPackage=GAME_PACKAGE,reportedPackage;
 public String getTargetPackage(){return targetPackage;}
 public String getForegroundPackage(){return reportedPackage!=null?reportedPackage:foreground?targetPackage:"android.other";}
 public void selectGame(String p){targetPackage=p;}
 public static final Companion Companion=new Companion();public static class Companion {public TouchService current;public TouchService getCurrent(){return current;}}
 public TouchService(){Companion.current=this;}
 public boolean getGameForeground(){return foreground;}
 public long tapNormalized(float x,float y,int count,int interval,int width,int height,java.util.concurrent.atomic.AtomicBoolean stop){return android.os.SystemClock.uptimeMillis();}
 public GesturePlan prepareChart(io.github.phiscript.engine.Chart chart,io.github.phiscript.engine.Viewport viewport){
  int[] screen=ChartMultiTouchPlayer.dimensions(this);
  return GesturePlan.prepareForImport(chart,GestureProfile.read(this),viewport,TouchSafety.read(this,screen[0],screen[1],viewport).bounds,null);
 }
 public void play(io.github.phiscript.engine.Chart chart,long epoch,io.github.phiscript.engine.Viewport viewport,java.util.concurrent.atomic.AtomicBoolean stop,kotlin.jvm.functions.Function1<? super String,kotlin.Unit> status)throws InterruptedException{
  ChartMultiTouchPlayer.play(this,chart,epoch,viewport,stop,status,new java.util.concurrent.atomic.AtomicBoolean(),new java.util.concurrent.atomic.AtomicBoolean());
 }
}
