package io.github.phiscript.root;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/** One cancellable native decoder, shared by picker and Root imports. */
public final class PhiraImportControl {
    public static final class Session implements AutoCloseable {
        final Thread thread=Thread.currentThread();volatile boolean cancelled;
        private Session(){}
        @Override public void close(){current.compareAndSet(this,null);if(cancelled)Thread.interrupted();}
    }
    private static final AtomicReference<Session> current=new AtomicReference<>();
    private PhiraImportControl(){}
    public static Session begin(){Session s=new Session();if(!current.compareAndSet(null,s))throw new IllegalStateException("已有 Phira 导入任务");return s;}
    public static boolean isBusy(){return current.get()!=null;}
    public static boolean isCancelling(){Session s=current.get();return s!=null&&s.cancelled;}
    public static void cancel(){Session s=current.get();if(s!=null){s.cancelled=true;s.thread.interrupt();}}
    /** Injected at parser loop back-edges, so CPU work also observes cancellation. */
    public static void check(){Session s=current.get();if(Thread.currentThread().isInterrupted()||(s!=null&&s.thread==Thread.currentThread()&&s.cancelled))throw new CancellationException("已取消 Phira 导入");}
}
