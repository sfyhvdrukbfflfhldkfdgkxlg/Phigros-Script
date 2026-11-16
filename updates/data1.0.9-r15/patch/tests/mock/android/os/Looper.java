
package android.os;
public class Looper {static final Looper MAIN=new Looper(),WORKER=new Looper();
 public static Looper myLooper(){return WORKER;} public static Looper getMainLooper(){return MAIN;}public static void prepare(){}}
