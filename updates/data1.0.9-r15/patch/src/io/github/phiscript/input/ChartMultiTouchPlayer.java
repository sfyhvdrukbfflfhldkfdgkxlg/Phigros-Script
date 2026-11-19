package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import io.github.phiscript.engine.BlockAreaSet;
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
            throw new IllegalStateException(tr("触控必须在后台线程执行"));
        if (!playing.compareAndSet(false, true))
            throw new IllegalStateException(tr("已有演奏在运行"));
        cancelled.set(false);
        GestureChannel channel = null;
        Map<TouchEvent, GestureChannel.Live> active = new LinkedHashMap<>();
        try {
            if (!service.getGameForeground()) throw new IllegalStateException(tr("目标游戏尚未处于前台"));
            int[] screen = dimensions(service);
            if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0
                || viewport.getLeft() < 0 || viewport.getTop() < 0
                || viewport.getRight() > screen[0] + 1 || viewport.getBottom() > screen[1] + 1)
                throw new IllegalArgumentException(tr("玩法视口超出屏幕，请重新校准"));
            TouchSafety safety=TouchSafety.read(service,screen[0],screen[1],viewport);
            GesturePlan plan=GesturePlan.forPlayback(chart,GestureProfile.read(service),viewport,safety.bounds);
            if(BlockAreaSet.count(chart)>0)status.invoke(TapMod.avoids(chart)?tr("噪域避让已开启：读取到 ")+BlockAreaSet.count(chart)+tr(" 个激活区域，按原生变换与反转检查"):tr("本谱含噪域，但设置已关闭避让；请开启“避开谱面噪域”"));
            else if(!BlockAreaSet.hasMetadata(chart))status.invoke(tr("本谱未保存噪域数据；旧版导入的谱面请从原始谱包重新导入"));
            if(plan.unavailableNotes>0)status.invoke(tr("规划中有 ")+plan.unavailableNotes+tr(" 个音符在判定窗口内未找到安全可用触点"));
            ChartPlaybackClock clock=ChartPlaybackClock.resolve(epoch);
            List<TouchEvent> events = new ArrayList<>();
            int skipped=0;long maxLate=0,submissionRejected=0,maxSafetyMs=0;
            long initialWall=SystemClock.uptimeMillis();
            long initial = clock.snapshot(initialWall).chartAt(initialWall);
            for (TouchEvent event : plan.events) {
                // Catch up a hold already in progress. Never filter a future head
                // merely because it is within 25 ms of starting the player.
                if (event.getEndMs() > initial
                    && (plan.isContinuous(event) || event.getStartMs() >= initial - 100))
                    events.add(event);
                else skipped++;
            }
            if (events.isEmpty()) { status.invoke(tr("当前已无后续音符")); return; }
            channel = new GestureChannel(service);
            if(!channel.reusesIds())status.invoke(tr("此系统的无障碍触点复用受限，密集长按建议使用 Root 模式"));
            status.invoke(tr("多点演奏：连接手势 ")+plan.connectedGroups+tr(" 组独立轨迹，蓝键保留按下，黄键滑接，红键来回滑动"));
            List<TouchEvent> blockedContacts=new ArrayList<>();
            int cursor = 0;
            int strokeLimit = Math.min(GestureDescription.getMaxStrokeCount(), GestureChannel.POINTER_LIMIT);
            boolean warned = false;
            long lastDimensionCheck = 0;
            while ((cursor < events.size() || !active.isEmpty() || !blockedContacts.isEmpty())
                    && !stop.get() && !cancelled.get() && service.getGameForeground()) {
                long wall = SystemClock.uptimeMillis();
                if (wall - lastDimensionCheck >= 250) {
                    int[] current = dimensions(service);
                    if (current[0] != screen[0] || current[1] != screen[1])
                        throw new IllegalStateException(tr("屏幕尺寸变化，请重新开始"));
                    lastDimensionCheck = wall;
                }
                ChartPlaybackClock.Slice timing=clock.snapshot(wall);
                long now = timing.chartAt(wall);
                for(int i=blockedContacts.size()-1;i>=0;i--)
                    if(blockedContacts.get(i).getEndMs()<=now)blockedContacts.remove(i);
                if (active.isEmpty() && blockedContacts.isEmpty() && cursor < events.size()
                        && events.get(cursor).getStartMs() > now + 24) {
                    GestureChannel.sleep(Math.max(1,Math.min(10, timing.wallAt(events.get(cursor).getStartMs()-24)-wall)));
                    continue;
                }
                // Expired one-shots are dropped, not replayed in a dense burst.
                while (cursor < events.size() && events.get(cursor).getEndMs() <= now){cursor++;skipped++;}
                if (active.isEmpty() && blockedContacts.isEmpty() && cursor >= events.size()) break;
                boolean expired=false;
                for (TouchEvent held : active.keySet()) if (held.getEndMs()<=now) expired=true;
                if (expired) {
                    // The callback can arrive after a chart tail. Lift those
                    // fingers before admitting a replacement chord, while
                    // preserving every other continuing finger.
                    GestureBatch<TouchEvent> retirement=new GestureBatch<>();
                    final long retirePrepareDeadline=System.nanoTime()+8000000L;
                    for (Map.Entry<TouchEvent,GestureChannel.Live> entry:active.entrySet()) {
                        GestureChannel.Live live=entry.getValue();
                        boolean keep=entry.getKey().getEndMs()>now;
                        if(keep){
                            try{if(!TapMod.clearAt(chart,viewport,new Point(live.x,live.y),now/1000.0,retirePrepareDeadline))keep=false;}
                            catch(TapMod.NoSafePoint blocked){keep=false;}
                            if(!keep)blockedContacts.add(entry.getKey());
                        }
                        retirement.add(entry.getKey(),live,
                            GestureChannel.path(live.x,live.y,live.x,live.y),0,1,
                            keep,live.x,live.y);
                    }
                    if(TapMod.avoids(chart)){
                        final long retireWall=SystemClock.uptimeMillis(),retireStart=wall,maxAge=Math.max(1,TapMod.noiseLookAheadMs()-4);
                        final ChartPlaybackClock.Slice retireTiming=timing;final long retireDeadline=retirePrepareDeadline;
                        List<TouchEvent> rejected=retirement.rejectUnsafe((event,prior,downOffset,upOffset,keep)->{
                            if(!keep)return true;
                            if(retireTiming.chartAt(retireWall)-retireTiming.chartAt(retireStart)>maxAge||System.nanoTime()>=retireDeadline)return false;
                            Point p=new Point(prior.x,prior.y);
                            try{TapMod.segment(chart,viewport,p,p,retireTiming.chartAt(retireStart),retireTiming.chartAt(retireWall+upOffset+8),retireDeadline);return System.nanoTime()<retireDeadline;}
                            catch(TapMod.NoSafePoint blocked){return false;}
                        });
                        long done=SystemClock.uptimeMillis();maxSafetyMs=Math.max(maxSafetyMs,done-retireWall);
                        if(!retirement.isEmpty()&&retireTiming.chartAt(done)-retireTiming.chartAt(retireWall)>maxAge)rejected.addAll(retirement.rejectUnsafe((event,prior,downOffset,upOffset,keep)->!keep));
                        submissionRejected+=new java.util.HashSet<>(rejected).size();
                        for(TouchEvent event:rejected)if(event.getEndMs()>timing.chartAt(done)&&!blockedContacts.contains(event))blockedContacts.add(event);
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
                List<TouchEvent> resume=new ArrayList<>(blockedContacts);blockedContacts.clear();
                int incoming=resume.size();
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
                    wall=SystemClock.uptimeMillis();timing=clock.snapshot(wall);now=timing.chartAt(wall);
                    for (int i=resume.size()-1;i>=0;i--)
                        if (resume.get(i).getEndMs()<=now) resume.remove(i);
                    if (resume.isEmpty()) continue;
                }
                int capacity=Math.min(strokeLimit, active.size()+channel.availableStarts());
                long end = ChordPlanner.endOfBatch(events, cursor, active.size()+resume.size(), now, capacity);
                // Android traverses a Path at constant arc length. In a moving
                // noise window submit one checked straight leg per stroke, so
                // its on-device position has the same timing as the safety test.
                boolean noiseWindow=TapMod.avoids(chart) && BlockAreaSet.overlaps(chart,now-TapMod.noiseLookBackMs(),end+TapMod.noiseLookAheadMs());
                if(noiseWindow)end=Math.min(end,now+12);
                // Android samples each stroke by arc length. Use one straight leg at a time around
                // moving noise so the path checked below is the path sent to the framework.
                if(TapMod.avoids(chart) && io.github.phiscript.engine.BlockAreaSet.overlaps(chart,now,end))
                    end=Math.min(end,Math.max(now+1,timing.chartAt(wall+8)));
                for(TouchEvent held:active.keySet())end=plan.nextBoundary(held,now,end);
                for(TouchEvent held:resume)end=plan.nextBoundary(held,now,end);
                for(int i=cursor;i<events.size()&&events.get(i).getStartMs()<end;i++)end=plan.nextBoundary(events.get(i),now,end);
                Map<TouchEvent, GestureChannel.Live> work = new LinkedHashMap<>(active);
                for (TouchEvent e : resume) work.put(e,null);
                List<TouchEvent> newHeads=new ArrayList<>();
                while (cursor < events.size() && events.get(cursor).getStartMs() < end){
                    TouchEvent admitted=events.get(cursor++);work.put(admitted,null);newHeads.add(admitted);
                }
                // A single independent TAP only needs its DOWN position. Building
                // sampled geometry for its already-judged body can spend the
                // whole window before dispatch and rebase it to a 1 ms touch.
                // Keep its normal 48 ms contact only when it cannot delay another
                // head, alter a continuing route, or extend into active noise.
                if(active.isEmpty()&&work.size()==1&&newHeads.size()==1){
                    TouchEvent tap=newHeads.get(0);long heldMs=48;
                    long planned=Math.max(wall,timing.wallAt(tap.getStartMs()));
                    long nextHead=cursor<events.size()?timing.wallAt(events.get(cursor).getStartMs()):Long.MAX_VALUE;
                    long plannedEnd=timing.chartAt(planned+heldMs);
                    if(tap.getNote().getType()==1&&!plan.isContinuous(tap)&&nextHead-planned>=heldMs
                            && (!TapMod.avoids(chart)||!BlockAreaSet.overlaps(chart,timing.chartAt(planned)-TapMod.noiseLookBackMs(),plannedEnd+TapMod.noiseLookAheadMs()))){
                        try{
                            boolean[] moved={false};Point p=safety.point(plan,chart,tap,timing.chartAt(planned),viewport,screen,moved);
                            long ready=SystemClock.uptimeMillis(),down=Math.max(ready,timing.wallAt(tap.getStartMs()));
                            long actualDown=timing.chartAt(down),actualEnd=timing.chartAt(down+heldMs);
                            if(actualDown-tap.getStartMs()>100){skipped++;continue;}
                            if(nextHead-down>=heldMs&&(!TapMod.avoids(chart)||!BlockAreaSet.overlaps(chart,actualDown-TapMod.noiseLookBackMs(),actualEnd+TapMod.noiseLookAheadMs()))){
                                GestureBatch<TouchEvent> shot=new GestureBatch<>();
                                shot.add(tap,null,GestureChannel.path(p.getX(),p.getY(),p.getX(),p.getY()),down-ready,down-ready+heldMs,false,p.getX(),p.getY());
                                long submitted=SystemClock.uptimeMillis();shot.rebase(submitted-ready);
                                maxLate=Math.max(maxLate,timing.chartAt(submitted+shot.startOffset(tap))-Math.round(tap.getNote().getTimeSeconds()*1000));
                                channel.send(shot);
                                if(moved[0]&&!warned){status.invoke(tr("部分音符已移入可触控区域，避免系统边缘手势；边缘音符可能无法命中"));warned=true;}
                                continue;
                            }
                        }catch(TapMod.NoSafePoint blocked){/* Retain the ordinary per-contact release handling. */}
                    }
                }
                GestureBatch<TouchEvent> batch = new GestureBatch<>();
                Map<TouchEvent,List<Point>> submittedPaths=new java.util.IdentityHashMap<>();
                final long constructionDeadline=noiseWindow?System.nanoTime()+8000000L:Long.MAX_VALUE;
                boolean[] bounded = new boolean[1];
                // The first step of a continued gesture must contain exactly
                // the old pointers. New fingers join strictly after that step.
                long newPointerOffset = active.isEmpty() ? 0 : 2;
                for (Map.Entry<TouchEvent, GestureChannel.Live> entry : work.entrySet()) {
                    TouchEvent e = entry.getKey();
                    GestureChannel.Live prior = entry.getValue();
                    // The continuation handshake is measured in wall milliseconds,
                    // including while the chart clock is slewing faster or slower.
                    long downWall=prior==null?Math.max(wall+newPointerOffset,timing.wallAt(e.getStartMs())):wall;
                    long down=timing.chartAt(downWall);
                    long up = Math.max(down + 1, Math.min(e.getEndMs(), end));
                    boolean keep = e.getEndMs() > end;
                    try {
                        checkConstructionBudget(constructionDeadline);
                        Point from = prior == null ? safety.point(plan,chart, e, down, viewport, screen, bounded)
                            : new Point(prior.x, prior.y);
                        checkConstructionBudget(constructionDeadline);
                        Point to;
                        if(prior!=null&&keep){
                            to=plan.preparedAccessibilityEndpoint(e,up,noiseWindow,down,prior);
                        }else{
                            to=noiseWindow?safety.point(plan,chart,e,up,viewport,screen,bounded,from,down)
                                :safety.point(plan,chart,e,up,viewport,screen,bounded);
                        }
                        checkConstructionBudget(constructionDeadline);
                        Path path = new Path(); path.moveTo(from.getX(), from.getY());
                        List<Point> pathPoints=new ArrayList<>();pathPoints.add(from);
                        Point checkedPoint=from;long checkedTime=down;
                        // Sample moving/rotating judge lines within the chunk.
                        long upWall=Math.max(downWall+1,timing.wallAt(up));
                        for (long sample = downWall + 8; !noiseWindow && sample < upWall; sample += 8) {
                            long t=timing.chartAt(sample);
                            checkConstructionBudget(constructionDeadline);
                            Point p = safety.point(plan,chart, e, t, viewport, screen, bounded);
                            TapMod.segment(chart,viewport,checkedPoint,p,checkedTime,t,constructionDeadline);checkedPoint=p;checkedTime=t;
                            path.lineTo(p.getX(), p.getY());
                            pathPoints.add(p);
                        }
                        TapMod.segment(chart,viewport,checkedPoint,to,checkedTime,up,constructionDeadline);
                        path.lineTo(to.getX(), to.getY());
                        pathPoints.add(to);submittedPaths.put(e,pathPoints);
                        batch.add(e,prior,path,Math.max(0,downWall-wall),Math.max(1,upWall-wall),keep,to.getX(),to.getY());
                    } catch(TapMod.NoSafePoint blocked) {
                        if(prior!=null)batch.add(e,prior,GestureChannel.path(prior.x,prior.y,prior.x,prior.y),0,1,false,prior.x,prior.y);
                        if(e.getEndMs()>end)blockedContacts.add(e);
                    }
                }
                if(batch.isEmpty()){GestureChannel.sleep(4);continue;}
                long submitted=SystemClock.uptimeMillis();batch.rebase(submitted-wall);
                // Geometry may have used most of this batch's wall-clock window.
                // Rebase changes its speed and a moving area may have advanced:
                // check the exact submitted constant-arc-length path again.
                if(TapMod.avoids(chart)&&BlockAreaSet.overlaps(chart,timing.chartAt(submitted)-TapMod.noiseLookBackMs(),timing.chartAt(submitted+batch.endOffset())+TapMod.noiseLookAheadMs())){
                    final long safetyWall=submitted,computeWall=wall,maxAge=Math.max(1,TapMod.noiseLookAheadMs()-4);
                    final ChartPlaybackClock.Slice safetyTiming=timing;
                    final long safetyDeadline=System.nanoTime()+8000000L;
                    Map<TouchEvent,Long> validationAt=new java.util.IdentityHashMap<>();
                    List<TouchEvent> rejected=batch.rejectUnsafe((event,prior,downOffset,upOffset,keep)->{
                        List<Point> points=submittedPaths.get(event);if(points==null)return true;
                        // Never spend another search on already stale geometry
                        // while physical prior fingers are still held down.
                        if(safetyTiming.chartAt(safetyWall)-safetyTiming.chartAt(computeWall)>maxAge||System.nanoTime()>=safetyDeadline)return false;
                        validationAt.put(event,SystemClock.uptimeMillis());
                        try{
                            if(prior!=null){Point held=new Point(prior.x,prior.y);TapMod.segment(chart,viewport,held,held,safetyTiming.chartAt(computeWall),safetyTiming.chartAt(safetyWall),safetyDeadline);}
                            checkSubmittedPath(chart,viewport,points,safetyTiming,safetyWall+downOffset,safetyWall+upOffset,safetyDeadline);
                            return System.nanoTime()<safetyDeadline;
                        }
                        catch(TapMod.NoSafePoint blocked){return false;}
                    });
                    long checkedWall=SystemClock.uptimeMillis();
                    maxSafetyMs=Math.max(maxSafetyMs,checkedWall-safetyWall);
                    // clearAt/segment already guard the next 12ms. Do not allow
                    // an expensive verification to consume that guard itself.
                    if(!batch.isEmpty())rejected.addAll(batch.rejectUnsafe((event,prior,downOffset,upOffset,keep)->
                        rejected.contains(event)||!validationAt.containsKey(event)||safetyTiming.chartAt(checkedWall)-safetyTiming.chartAt(validationAt.get(event))<=maxAge));
                    long checkedTime=timing.chartAt(checkedWall);
                    submissionRejected+=new java.util.HashSet<>(rejected).size();
                    for(TouchEvent event:rejected)if(event.getEndMs()>checkedTime&&!blockedContacts.contains(event))blockedContacts.add(event);
                    if(batch.isEmpty()){GestureChannel.sleep(1);continue;}
                    submitted=checkedWall;
                }
                for(TouchEvent head:newHeads)if(head.getNote().getType()==1||head.getNote().getType()==3){
                    long offset=batch.startOffset(head);
                    if(offset>=0)maxLate=Math.max(maxLate,timing.chartAt(submitted+offset)-Math.round(head.getNote().getTimeSeconds()*1000));
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
                    status.invoke(tr("部分音符已移入可触控区域，避免系统边缘手势；边缘音符可能无法命中"));
                    warned = true;
                }
            }
            status.invoke(stop.get() || cancelled.get() || !service.getGameForeground()
                ? tr("已停止触摸") : tr("演奏完成"));
            status.invoke(tr("输入统计：跳过 ")+skipped+tr(" 个过期手势，最大按下调度延迟 ")+Math.max(0,maxLate)+" ms");
            if(TapMod.avoids(chart))status.invoke(tr("噪域提交复核：拒绝或抬起 ")+submissionRejected+tr(" 次触点，最大复核耗时 ")+maxSafetyMs+" ms");
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

    private static void checkSubmittedPath(Chart chart,Viewport viewport,List<Point> points,ChartPlaybackClock.Slice timing,long downWall,long upWall,long deadline){
        double total=0;for(int i=1;i<points.size();i++)total+=Math.hypot(points.get(i).getX()-points.get(i-1).getX(),points.get(i).getY()-points.get(i-1).getY());
        Point previous=points.get(0);long from=timing.chartAt(downWall);double traversed=0;
        if(total<.0001){TapMod.segment(chart,viewport,previous,previous,from,timing.chartAt(upWall),deadline);return;}
        for(int i=1;i<points.size();i++){
            if(System.nanoTime()>=deadline)throw new TapMod.NoSafePoint(tr("噪域提交复核超时，释放此触点后重试"));
            Point next=points.get(i);traversed+=Math.hypot(next.getX()-previous.getX(),next.getY()-previous.getY());
            long to=timing.chartAt(downWall+Math.round((upWall-downWall)*traversed/total));
            TapMod.segment(chart,viewport,previous,next,from,to,deadline);previous=next;from=to;
        }
    }
    private static void checkConstructionBudget(long deadline){if(deadline!=Long.MAX_VALUE&&System.nanoTime()>=deadline)throw new TapMod.NoSafePoint(tr("噪域本批轨迹计算超时，先释放触点后重试"));}

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
        if (p == null) throw new IllegalArgumentException(tr("无法计算音符落点"));
        float x = GestureChannel.clamp(p.getX(), Math.max(0, v.getLeft()),
            Math.min(screen[0] - 1, Math.nextDown(v.getRight())));
        float y = GestureChannel.clamp(p.getY(), Math.max(0, v.getTop()),
            Math.min(screen[1] - 1, Math.nextDown(v.getBottom())));
        if (x != p.getX() || y != p.getY()) bounded[0] = true;
        return new Point(x, y);
    }
}
