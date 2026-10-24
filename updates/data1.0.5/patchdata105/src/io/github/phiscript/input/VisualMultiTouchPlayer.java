package io.github.phiscript.input;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

/** Multi-hold visual playback with independent UP and a bounded shot queue. */
public final class VisualMultiTouchPlayer {
    private VisualMultiTouchPlayer() {}
    private static final class Pending {
        final VisualTouch touch; final long at;
        Pending(VisualTouch touch, long at) { this.touch = touch; this.at = at; }
    }

    public static void play(AccessibilityService service, int width, int height,
            Function0<VisualTouchFrame> snapshot, AtomicBoolean stop,
            Function0<Boolean> allowed, Function1<? super String, Unit> status)
            throws InterruptedException {
        if (io.github.phiscript.root.InputBackend.rootEnabled) {
            RootVisualPlayer.play(service,width,height,snapshot,stop,allowed,status);
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("触控必须在后台线程执行");
        GestureChannel channel = new GestureChannel(service);
        TouchSafety safety=TouchSafety.read(service,width,height,null);TouchBounds bounds=safety.bounds;
        VisualChordState state = new VisualChordState(GestureProfile.read(service),bounds);
        Map<Long, GestureChannel.Live> active = new LinkedHashMap<>();
        ArrayDeque<Pending> pending = new ArrayDeque<>();
        long began = SystemClock.uptimeMillis();
        boolean started = false;
        try {
            while (!stop.get() && Boolean.TRUE.equals(allowed.invoke())) {
                VisualTouchFrame frame = snapshot.invoke();
                long now = SystemClock.uptimeMillis();
                if (frame == null) {
                    if (!active.isEmpty() || now - began > 2000)
                        throw new IllegalStateException("未收到视觉画面，已释放触点");
                    GestureChannel.sleep(6); continue;
                }
                if (frame.getScreenWidth() != width || frame.getScreenHeight() != height)
                    throw new IllegalStateException("屏幕尺寸变化，请重新开始");
                VisualTouchDemand demand = state.update(frame, now);
                Map<Long, VisualTouch> desired = new LinkedHashMap<>();
                for (VisualTouch t : demand.getHolds()) desired.put(t.getId(), t);
                for (VisualTouch t : demand.getShots()) pending.addLast(new Pending(t, now));
                if (pending.size() > 128)
                    throw new IllegalStateException("视觉点击积压过多，请降低游戏速度或使用谱面模式");
                while (!pending.isEmpty() && now - pending.peekFirst().at > 200) pending.removeFirst();
                if (active.isEmpty() && desired.isEmpty() && pending.isEmpty()) {
                    GestureChannel.sleep(6); continue;
                }
                if (!started) { status.invoke("纯视觉多点演奏已开始"); started = true; }
                int departing = 0, arriving = 0;
                for (Long id : active.keySet()) if (!desired.containsKey(id)) departing++;
                for (Long id : desired.keySet()) if (!active.containsKey(id)) arriving++;
                int possibleShots=Math.min(pending.size(),GestureChannel.POINTER_LIMIT-desired.size());
                if (!active.isEmpty() && (arriving+possibleShots > channel.availableStarts()
                        || active.size()+arriving > GestureChannel.POINTER_LIMIT || channel.oldChain())) {
                    channel.finish(active.values());
                    active.clear();
                    departing=0; arriving=desired.size();
                }
                int total = active.size() + arriving;
                int capacity = Math.min(GestureDescription.getMaxStrokeCount(),GestureChannel.POINTER_LIMIT);
                // Older/OEM builds can allow fewer strokes than AOSP. Release
                // departing contacts first, preserving every continuing hold.
                boolean releaseOnly = total > capacity;
                long offset = active.isEmpty() ? 0 : 2;
                long span = releaseOnly ? 2 : 32;
                GestureBatch<Long> batch = new GestureBatch<>();
                for (Map.Entry<Long, GestureChannel.Live> e : active.entrySet()) {
                    VisualTouch target = desired.get(e.getKey());
                    GestureChannel.Live l = e.getValue();
                    boolean keep = target != null;
                    float x = keep ? bounds.x(x(target, width)) : l.x;
                    float y = keep ? bounds.y(y(target, height)) : l.y;
                    if (keep) {
                        float[] endpoint = GestureChannel.liveEndpoint(l, x, y, bounds.left,bounds.top,bounds.right,bounds.bottom);
                        x = endpoint[0]; y = endpoint[1];
                    }
                    batch.add(e.getKey(),l,GestureChannel.path(l.x,l.y,x,y),
                        0,keep?span:1,keep,x,y);
                }
                if (!releaseOnly) {
                    for (VisualTouch t : desired.values()) if (!active.containsKey(t.getId())) {
                        float x = bounds.x(x(t, width)), y = bounds.y(y(t, height));
                        batch.add(t.getId(),null,GestureChannel.path(x,y,x,y),offset,span,true,x,y);
                    }
                    int slots = Math.min(capacity - total,
                        GestureChannel.POINTER_LIMIT - desired.size());
                    while (slots-- > 0 && !pending.isEmpty()) {
                        VisualTouch t = pending.removeFirst().touch;
                        float x = bounds.x(x(t, width)), y = bounds.y(y(t, height)), tx = x, ty = y;
                        boolean flick = t.getKind() == VisualTouchKind.FLICK;
                        if (flick) {
                            if(safety.inwardFlick||safety.enabled){float[] p=bounds.flick(x,y,t.getFlickDx()*width,t.getFlickDy()*height);tx=p[0];ty=p[1];}
                            else{FlickGeometry.Pixel p = FlickGeometry.INSTANCE.endpoint(x, y,
                                t.getFlickDx() * width, t.getFlickDy() * height, width, height);
                                tx = p.getX(); ty = p.getY();}
                        }
                        batch.add(t.getId(),null,GestureChannel.path(x,y,tx,ty),
                            offset,offset+(flick?30:18),false,tx,ty);
                    }
                }
                Map<Long, GestureChannel.Live> previous = active;
                active = batch.next;
                try {
                    channel.send(batch);
                } catch (InterruptedException | RuntimeException failure) {
                    if (channel.cancelled) active.clear();
                    else if (!channel.accepted) active = previous;
                    throw failure;
                }
            }
            status.invoke("已停止视觉触摸");
        } finally {
            try { channel.release(active.values()); }
            finally { channel.close(); }
        }
    }

    private static float x(VisualTouch t, int width) {
        return GestureChannel.clamp(t.getX() * width, 0, width - 1);
    }
    private static float y(VisualTouch t, int height) {
        return GestureChannel.clamp(t.getY() * height, 0, height - 1);
    }
}
