package android.os;import java.util.*;public class Looper {final Queue<Runnable> tasks=new ArrayDeque<>();boolean ending;}
