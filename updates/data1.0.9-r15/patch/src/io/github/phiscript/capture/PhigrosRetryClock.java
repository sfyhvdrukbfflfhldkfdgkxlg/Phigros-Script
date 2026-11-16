package io.github.phiscript.capture;
import io.github.phiscript.AppSettings;
/** One retry delay, using the same epoch calculation as Phira. */
public final class PhigrosRetryClock {
    public static final String KEY="phigrosRestartDelay";
    public static final int DEFAULT_DELAY_MS=0;
    private PhigrosRetryClock(){}
    public static int readDelay(AppSettings prefs){
        try{int delay=Integer.parseInt(prefs.text(KEY,Integer.toString(DEFAULT_DELAY_MS)).trim());if(delay<0||delay>5000)throw new NumberFormatException();return delay;}
        catch(NumberFormatException error){throw new IllegalArgumentException("重试起始延迟应在 0～5000 ms");}
    }
    public static long epoch(long retryTouchDown,int delay,double chartOffset){
        return PhiraRestartClock.INSTANCE.epoch(retryTouchDown,delay,chartOffset,0,0);
    }
}
