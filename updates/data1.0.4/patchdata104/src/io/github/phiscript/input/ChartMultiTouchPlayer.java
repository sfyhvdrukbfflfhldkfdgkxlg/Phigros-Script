package io.github.phiscript.input;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.TouchEvent;
import io.github.phiscript.engine.TouchTimelineKt;
import io.github.phiscript.engine.Viewport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Shared by every chart decoder, independent of song identity and chart format. */
public final class ChartMultiTouchPlayer {
    private ChartMultiTouchPlayer() {}

    public static void play(TouchService service, Chart chart, long epoch,
            Viewport viewport, AtomicBoolean stop, Function1<? super String, Unit> status,
            AtomicBoolean playing, AtomicBoolean cancelled) throws InterruptedException {
        if (io.github.phiscript.root.InputBackend.rootEnabled) {
            RootChartPlayer.play(service,chart,epoch,viewport,stop,status,playing,cancelled);
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("触控必须在后台线程执行");
        if (!playing.compareAndSet(false, true))
            throw new IllegalStateException("已有演奏在运行");
        cancelled.set(false);
        GestureChannel channel = null;
        Map<TouchEvent, GestureChannel.Live> active = new LinkedHashMap<>();
        try {
            if (!service.getGameForeground()) throw new IllegalStateException("目标游戏尚未处于前台");
            int[] screen = dimensions(service);
            if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0
                || viewport.getLeft() < 0 || viewport.getTop() < 0
                || viewport.getRight() > screen[0] + 1 || viewport.getBottom() > screen[1] + 1)
                throw new IllegalArgumentException("玩法视口超出屏幕，请重新校准");
            GesturePlan plan=GesturePlan.obtain(chart,GestureProfile.read(service));
            TouchSafety safety=TouchSafety.read(service,screen[0],screen[1],viewport);
            List<TouchEvent> events = new ArrayList<>();
            long initial = SystemClock.uptimeMillis() - epoch;
            for (TouchEvent event : plan.events) {
                // Catch up a hold already in progress. Never filter a future head
                // merely because it is within 25 ms of starting the player.
                if (event.getEndMs() > initial
                    && (event.getNote().getType() == 3 || event.getNote().getType() == 2 || event.getStartMs() >= initial - 100))
                    events.add(event);
            }
            if (events.isEmpty()) { status.invoke("当前已无后续音符"); return; }
            channel = new GestureChannel(service);
            status.invoke("多点演奏：连续拖键 "+plan.connectedGroups+" 组独立轨迹，点击与长键分别续接");
            int cursor = 0;
            int strokeLimit = Math.min(GestureDescription.getMaxStrokeCount(), GestureChannel.POINTER_LIMIT);
            boolean warned = false;
            long lastDimensionCheck = 0;
            while ((cursor < events.size() || !active.isEmpty())
                    && !stop.get() && !cancelled.get() && service.getGameForeground()) {
                long wall = SystemClock.uptimeMillis();
                if (wall - lastDimensionCheck >= 250) {
                    int[] current = dimensions(service);
                    if (current[0] != screen[0] || current[1] != screen[1])
                        throw new IllegalStateException("屏幕尺寸变化，请重新开始");
                    lastDimensionCheck = wall;
                }
                long now = wall - epoch;
                if (active.isEmpty() && cursor < events.size()
                        && events.get(cursor).getStartMs() > now + 24) {
                    GestureChannel.sleep(Math.min(10, events.get(cursor).getStartMs() - now - 24));
                    continue;
                }
                // Expired one-shots are dropped, not replayed in a dense burst.
                while (cursor < events.size() && events.get(cursor).getEndMs() <= now) cursor++;
                if (active.isEmpty() && cursor >= events.size()) break;
                boolean expired=false;
                for (TouchEvent held : active.keySet()) if (held.getEndMs()<=now) expired=true;
                if (expired) {
                    // The callback can arrive after a chart tail. Lift those
                    // fingers before admitting a replacement chord, while
                    // preserving every other continuing finger.
                    GestureBatch<TouchEvent> retirement=new GestureBatch<>();
                    for (Map.Entry<TouchEvent,GestureChannel.Live> entry:active.entrySet()) {
                        GestureChannel.Live live=entry.getValue();
                        retirement.add(entry.getKey(),live,
                            GestureChannel.path(live.x,live.y,live.x,live.y),0,1,
                            entry.getKey().getEndMs()>now,live.x,live.y);
                    }
                    Map<TouchEvent,GestureChannel.Live> previous=active;
                    active=retirement.next;
                    try { channel.send(retirement); }
                    catch (InterruptedException | RuntimeException failure) {
                        if (channel.cancelled) active.clear();
                        else if (!channel.accepted) active=previous;
                        throw failure;
                    }
                    continue;
                }
                List<TouchEvent> resume=new ArrayList<>();
                int incoming=0;
                for (int i=cursor; i<events.size()
                        && events.get(i).getStartMs()<now+ChordPlanner.CHUNK_MS; i++) incoming++;
                boolean naturalEnd=cursor<events.size();
                if (naturalEnd) for (TouchEvent held : active.keySet())
                    if (held.getEndMs()>events.get(cursor).getStartMs()) naturalEnd=false;
                if (!active.isEmpty() && ((incoming > channel.availableStarts() && !naturalEnd)
                        || channel.oldChain())) {
                    resume.addAll(active.keySet());
                    channel.finish(active.values());
                    active.clear();
                    now=SystemClock.uptimeMillis()-epoch;
                    for (int i=resume.size()-1;i>=0;i--)
                        if (resume.get(i).getEndMs()<=now) resume.remove(i);
                    if (resume.isEmpty()) continue;
                }
                int capacity=Math.min(strokeLimit, active.size()+channel.availableStarts());
                long end = ChordPlanner.endOfBatch(events, cursor, active.size()+resume.size(), now, capacity);
                Map<TouchEvent, GestureChannel.Live> work = new LinkedHashMap<>(active);
                for (TouchEvent e : resume) work.put(e,null);
                while (cursor < events.size() && events.get(cursor).getStartMs() < end)
                    work.put(events.get(cursor++), null);
                GestureBatch<TouchEvent> batch = new GestureBatch<>();
                boolean[] bounded = new boolean[1];
                // The first step of a continued gesture must contain exactly
                // the old pointers. New fingers join strictly after that step.
                long newPointerOffset = active.isEmpty() ? 0 : 2;
                for (Map.Entry<TouchEvent, GestureChannel.Live> entry : work.entrySet()) {
                    TouchEvent e = entry.getKey();
                    GestureChannel.Live prior = entry.getValue();
                    long down = prior == null
                        ? Math.max(now + newPointerOffset, e.getStartMs()) : now;
                    long up = Math.max(down + 1, Math.min(e.getEndMs(), end));
                    boolean keep = e.getEndMs() > end;
                    Point from = prior == null ? safety.point(plan,chart, e, down, viewport, screen, bounded)
                        : new Point(prior.x, prior.y);
                    Point to = safety.point(plan,chart, e, up, viewport, screen, bounded);
                    if (prior != null && keep) {
                        float[] endpoint = GestureChannel.liveEndpoint(prior, to.getX(), to.getY(),
                            safety.bounds.left,safety.bounds.top,safety.bounds.right,safety.bounds.bottom);
                        to = new Point(endpoint[0], endpoint[1]);
                    }
                    Path path = new Path(); path.moveTo(from.getX(), from.getY());
                    // Sample moving/rotating judge lines within the chunk.
                    for (long t = down + 8; t < up; t += 8) {
                        Point p = safety.point(plan,chart, e, t, viewport, screen, bounded);
                        path.lineTo(p.getX(), p.getY());
                    }
                    path.lineTo(to.getX(), to.getY());
                    batch.add(e,prior,path,down-now,up-now,keep,to.getX(),to.getY());
                }
                // If waiting is interrupted, the just-submitted continuation is
                // the most recent candidate to lift, not its stale predecessor.
                Map<TouchEvent, GestureChannel.Live> previous = active;
                active = batch.next;
                try {
                    channel.send(batch);
                } catch (InterruptedException | RuntimeException failure) {
                    if (channel.cancelled) active.clear();
                    else if (!channel.accepted) active = previous;
                    throw failure;
                }
                if (bounded[0] && !warned) {
                    status.invoke("部分音符已移入可触控区域，避免系统边缘手势；边缘音符可能无法命中");
                    warned = true;
                }
            }
            status.invoke(stop.get() || cancelled.get() || !service.getGameForeground()
                ? "已停止触摸" : "演奏完成");
        } finally {
            try { if (channel != null) channel.release(active.values()); }
            finally { if (channel != null) channel.close(); playing.set(false); }
        }
    }

    static int[] dimensions(TouchService service) {
        WindowManager wm = (WindowManager) service.getSystemService(WindowManager.class);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect bounds = wm.getMaximumWindowMetrics().getBounds();
            return new int[]{bounds.width(), bounds.height()};
        }
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    static Point point(Chart chart, TouchEvent e, long time, Viewport v,
            int[] screen, boolean[] bounded) {
        Point p;
        try {
            p = TouchTimelineKt.touchPosition(chart, e, time, v);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // A flick outside the viewport has no valid clipped segment in the
            // original code. Keep the rest of the song running at the edge.
            double seconds = Math.max(e.getStartMs(), Math.min(e.getEndMs(), time)) / 1000.0;
            p = chart.position(e.getNote(), seconds, v, false);
            if (p == null) throw ex;
            bounded[0] = true;
            if (e.getNote().getType() == 4) {
                float x = GestureChannel.clamp(p.getX(), 0, screen[0] - 1);
                float y = GestureChannel.clamp(p.getY(), 0, screen[1] - 1);
                Point n = chart.judgeNormal(e.getNote(), seconds, v);
                FlickGeometry.Pixel target = FlickGeometry.INSTANCE.endpoint(x, y,
                    n.getX() * v.getHeight() * .1f, n.getY() * v.getHeight() * .1f,
                    screen[0], screen[1]);
                float progress = Math.max(0, Math.min(1, (time - e.getStartMs()) / 60f));
                p = new Point(x + (target.getX() - x) * progress,
                    y + (target.getY() - y) * progress);
            }
        }
        if (p == null) throw new IllegalArgumentException("无法计算音符落点");
        float x = GestureChannel.clamp(p.getX(), Math.max(0, v.getLeft()),
            Math.min(screen[0] - 1, Math.nextDown(v.getRight())));
        float y = GestureChannel.clamp(p.getY(), Math.max(0, v.getTop()),
            Math.min(screen[1] - 1, Math.nextDown(v.getBottom())));
        if (x != p.getX() || y != p.getY()) bounded[0] = true;
        return new Point(x, y);
    }
}
