package io.github.phiscript.input;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** One serialized gesture stream: all continued pointers travel together. */
final class GestureChannel {
    final AccessibilityService service;
    final HandlerThread thread;
    final Handler handler;
    boolean accepted, cancelled;
    static final int POINTER_LIMIT = 10;
    private int allocated;
    private long chainBegan;

    static final class Live {
        final GestureDescription.StrokeDescription stroke;
        final float x, y;
        Live(GestureDescription.StrokeDescription stroke, float x, float y) {
            this.stroke = stroke; this.x = x; this.y = y;
        }
    }

    GestureChannel(AccessibilityService service) {
        this.service = service;
        thread = new HandlerThread("PhiMultiTouch");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    static Path path(float x, float y, float toX, float toY) {
        Path p = new Path(); p.moveTo(x, y);
        if (x != toX || y != toY) p.lineTo(toX, toY);
        return p;
    }

    /** AOSP cancels a continuation when it generates no MotionEvent at all.
     * Use a one-pixel move for stationary held pointers, keeping the exact
     * resulting endpoint for the next continuation handshake.
     */
    static float[] liveEndpoint(Live previous, float x, float y,
            float left, float top, float right, float bottom) {
        x = clamp(Math.round(x), left, right);
        y = clamp(Math.round(y), top, bottom);
        if (Math.round(previous.x) == Math.round(x)
                && Math.round(previous.y) == Math.round(y)) {
            if (x + 1 <= right) x += 1;
            else if (x - 1 >= left) x -= 1;
            else if (y + 1 <= bottom) y += 1;
            else if (y - 1 >= top) y -= 1;
            else throw new IllegalStateException("玩法视口太小，无法续接长键");
        }
        return new float[]{x, y};
    }

    int availableStarts() { return POINTER_LIMIT - allocated; }

    boolean oldChain() {
        return allocated > 0 && SystemClock.uptimeMillis() - chainBegan >= 4000;
    }

    <K> void send(GestureBatch<K> batch) throws InterruptedException {
        accepted = false;
        cancelled = false;
        if (batch.continuing == 0) { allocated=0; chainBegan=SystemClock.uptimeMillis(); }
        // AOSP retains ended stroke->pointer mappings until every finger is
        // UP. Counting only simultaneously held fingers can exhaust its IDs.
        if (allocated + batch.starts > POINTER_LIMIT)
            throw new IllegalStateException("连续手势触点编号不足，已停止");
        GestureDescription gesture=batch.build();
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger result = new AtomicInteger();
        accepted = service.dispatchGesture(gesture,
            new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) {
                    result.set(1); done.countDown();
                }
                @Override public void onCancelled(GestureDescription g) {
                    result.set(2); done.countDown();
                }
            }, handler);
        if (!accepted) throw new IllegalStateException("Android 拒绝多点触摸，请检查无障碍服务");
        if (!done.await(batch.duration + 1000, TimeUnit.MILLISECONDS))
            throw new IllegalStateException("多点手势回调超时，已停止并释放触点");
        if (result.get() == 2) {
            cancelled = true;
            allocated=0;
            throw new IllegalStateException("系统取消了手势，请重新开始演奏");
        }
        if (result.get() != 1) throw new IllegalStateException("手势未完成");
        allocated = batch.next.isEmpty() ? 0 : allocated + batch.starts;
    }

    /** A confirmed all-UP resets the framework's historical pointer mappings. */
    void finish(Collection<Live> active) throws InterruptedException {
        if (active.isEmpty()) return;
        GestureBatch<Live> batch=new GestureBatch<>();
        for (Live l : active)
            batch.add(l,l,path(l.x,l.y,l.x,l.y),0,1,false,l.x,l.y);
        send(batch);
        allocated=0;
    }

    /** A best-effort UP for every possibly held pointer, also on exceptions. */
    void release(Collection<Live> active) {
        if (active.isEmpty()) return;
        boolean interrupted = Thread.interrupted();
        try {
            finish(active);
        } catch (InterruptedException e) {
            interrupted = true;
        } catch (RuntimeException ignored) {
            // The framework may already have cancelled and lifted this stream.
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    void close() { thread.quitSafely(); }

    static void sleep(long milliseconds) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        SystemClock.sleep(milliseconds);
        if (Thread.interrupted()) throw new InterruptedException();
    }

    static float clamp(float v, float low, float high) {
        if (Float.isNaN(v) || Float.isInfinite(v))
            throw new IllegalArgumentException("谱面包含无效触点坐标");
        return Math.max(low, Math.min(high, v));
    }
}
