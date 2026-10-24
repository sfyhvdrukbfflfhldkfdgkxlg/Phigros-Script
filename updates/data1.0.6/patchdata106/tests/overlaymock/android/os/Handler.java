package android.os;
import java.util.ArrayDeque;
public final class Handler {private static final ArrayDeque<Runnable> q=new ArrayDeque<>();public Handler(Looper l){}public boolean post(Runnable r){q.add(r);return true;}public static void step(){if(!q.isEmpty())q.remove().run();}public static void drain(){int n=0;while(!q.isEmpty()){if(++n>1000)throw new AssertionError("UI queue loop");step();}}}
