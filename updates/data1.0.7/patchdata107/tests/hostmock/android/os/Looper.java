package android.os;public class Looper {private static final Looper main=new Looper();public static Looper myLooper(){return main;}public static Looper getMainLooper(){return main;}}
