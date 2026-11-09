package io.github.phiscript;
import android.graphics.RectF;import io.github.phiscript.vision.NormalizedPoint;
public final class SessionSettings {
 public boolean automatic=true;public RectF viewport=new RectF(0,0,1,1),pause=new RectF(0,0,.18f,.24f);public NormalizedPoint retry=new NormalizedPoint(.5f,.5f);
 public RectF getViewport(){return viewport;}public boolean getPhiraAutomaticViewport(){return automatic;}public RectF getPhiraPauseRoi(){return pause;}public NormalizedPoint getPhiraRestartPoint(){return retry;}
 public RectF getPauseRoi(){return pause;}
}
