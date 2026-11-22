package android.os;

public final class SystemClock {
    public static long elapsedRealtime() { return System.nanoTime() / 1000000; }
    public static void sleep(long ms) { Thread.yield(); }
}
