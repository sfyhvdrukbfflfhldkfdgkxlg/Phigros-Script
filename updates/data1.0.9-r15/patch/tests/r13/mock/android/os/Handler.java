package android.os;
import java.util.PriorityQueue;
public final class Handler {
    static class Work implements Comparable<Work>{
        long time,serial;Runnable fn;
        public int compareTo(Work o){return time==o.time?Long.compare(serial,o.serial):Long.compare(time,o.time);}
    }
    static PriorityQueue<Work> queue=new PriorityQueue<>();static long serial;
    public Handler(Looper looper){}
    public boolean post(Runnable fn){return postAtTime(fn,SystemClock.now);}
    public boolean postAtTime(Runnable fn,long time){Work w=new Work();w.time=time;w.serial=serial++;w.fn=fn;queue.add(w);return true;}
    public static void until(long time){
        int calls=0;while(!queue.isEmpty()&&queue.peek().time<=time){if(++calls>10000)throw new AssertionError("busy loop");Work w=queue.remove();SystemClock.now=Math.max(SystemClock.now,w.time);w.fn.run();}
        SystemClock.now=Math.max(SystemClock.now,time);
    }
}
