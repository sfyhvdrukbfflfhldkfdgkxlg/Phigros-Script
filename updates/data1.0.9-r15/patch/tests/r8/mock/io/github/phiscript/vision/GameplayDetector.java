package io.github.phiscript.vision;
import android.graphics.Bitmap;import android.graphics.RectF;
public final class GameplayDetector {
 public static final class Hit {public float getPauseX(){return .05f;}public float getPauseY(){return .05f;}}
 public GameplayDetector(RectF roi){io.github.phiscript.capture.SessionTrace.pauseLeft=roi.left;}public Hit observe(Bitmap bitmap,long at){return bitmap.gameplay&&!bitmap.result?new Hit():null;}public void reset(){}
}
