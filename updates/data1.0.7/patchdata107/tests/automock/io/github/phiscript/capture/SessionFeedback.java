package io.github.phiscript.capture;
import android.content.Context;
public final class SessionFeedback {public static void stage(Context c,String text){if(text.contains("音符校准尚未完成"))AutoEngineRegression.calibrationTimedOut=true;}}
