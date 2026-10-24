package io.github.phiscript.capture;
import android.content.Context;
public final class AutoOverlayFeedback {
 private final RecognitionOverlay overlay;private boolean playing;
 public AutoOverlayFeedback(Context c,RecognitionOverlay o){overlay=o;}
 public void show(String s){if(!playing){AutoEngineRegression.prepShows++;overlay.status(s);}}
 public void playing(){playing=true;AutoEngineRegression.playStatuses++;overlay.status("正在打谱");}
 public void waiting(String s){playing=false;show(s);}
 public void close(){AutoEngineRegression.cleanupHides++;overlay.hide();}
}
