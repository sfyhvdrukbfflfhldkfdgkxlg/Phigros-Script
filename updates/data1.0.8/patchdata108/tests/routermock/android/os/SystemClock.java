package android.os;
public class SystemClock {public static long now,step;public static long uptimeMillis(){long t=now;now+=step;return t;}}
