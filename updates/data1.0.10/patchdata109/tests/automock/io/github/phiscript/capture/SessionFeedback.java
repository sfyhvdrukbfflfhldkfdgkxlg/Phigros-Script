package io.github.phiscript.capture;
import android.content.Context;
public final class SessionFeedback {public static void stage(Context c,String text){if(text.contains("多帧校准证据不足"))AutoEngineRegression.calibrationTimedOut=true;}}
