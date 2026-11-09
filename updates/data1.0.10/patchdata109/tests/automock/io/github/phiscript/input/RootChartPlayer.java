package io.github.phiscript.input;
import io.github.phiscript.engine.*;import io.github.phiscript.root.RootClient;import io.github.phiscript.capture.AutoEngineRegression;import java.util.concurrent.atomic.AtomicBoolean;import kotlin.*;import kotlin.jvm.functions.Function1;
public final class RootChartPlayer {
 public static void playPrepared(TouchService s,Chart c,long at,Viewport v,AtomicBoolean cancel,Function1<? super String,Unit> status,RootClient client)throws InterruptedException{AutoEngineRegression.played(at,cancel,ChartPlaybackClock.resolve(at));}
}
