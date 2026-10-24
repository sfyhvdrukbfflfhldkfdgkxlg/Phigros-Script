package io.github.phiscript.capture;
import android.graphics.Bitmap;import io.github.phiscript.SessionSettings;import io.github.phiscript.engine.Viewport;import io.github.phiscript.vision.NormalizedPoint;
public final class PhigrosControls {
 private int votes;
 public PhigrosControls(SessionSettings s,int w,int h){}
 public boolean menu(Bitmap b){return (b.phase==1||b.phase==3)&&!AutoEngineRegression.scenario.equals("textdecoy")&&(!AutoEngineRegression.scenario.equals("menuDelayed")||AutoEngineRegression.snapshots>32);}
 public NormalizedPoint pause(Bitmap b,long at){if(b.phase!=0||(AutoEngineRegression.scenario.equals("nogame")||AutoEngineRegression.scenario.equals("manualLate"))){votes=0;return null;}if(AutoEngineRegression.warmed>0&&at<=AutoEngineRegression.grantAt)throw new AssertionError("pre-grant frame used for pause");return ++votes>=3?new NormalizedPoint(.05f,.05f):null;}
 public void resetGame(){votes=0;}
 public boolean hud(Bitmap b){return b.phase==0||b.phase==2&&!AutoEngineRegression.scenario.equals("nohud");}
 public NormalizedPoint retry(){return AutoEngineRegression.scenario.equals("adaptiveRetry")?new NormalizedPoint(.53f,.47f):new NormalizedPoint(.5f,.5f);}
 public Viewport viewport(){return new Viewport(0,0,1000,600);}
}
