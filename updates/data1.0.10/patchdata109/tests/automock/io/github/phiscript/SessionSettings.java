package io.github.phiscript;
import android.graphics.RectF;
public class SessionSettings {
 public RectF getPauseRoi(){return new RectF(0,0,.18f,.24f);}public RectF getTitleRoi(){return new RectF(0,0,1,1);}public RectF getDifficultyRoi(){return new RectF(0,0,1,1);}public RectF getViewport(){return new RectF(0,0,1,1);}
 public int getDoubleTapIntervalMs(){return 100;}public int getCaptureLagMs(){return 0;}public int getTouchOffsetMs(){return 0;}public int getPauseSettleMs(){return 1000;}
}
