package android.os;
public class SystemClock {public static volatile long offset;public static long uptimeMillis(){return System.nanoTime()/1000000+offset;}}
