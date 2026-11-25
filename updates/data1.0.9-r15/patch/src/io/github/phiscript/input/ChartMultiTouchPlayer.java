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
    // A continued pointer stays physically down while Android completes the batch.
    // Reserve the next 8 ms input tick and the 1 ms stroke required to lift it.
    private static final int CONTINUATION_RELEASE_MS = 9;
    private ChartMultiTouchPlayer() {}

    // Keep the timed loop free of log I/O. Only retain interruptions of a held
    // note or the initial admission of its head, not every retry while blocked.
    private static final class HoldInterruptions {
        final int[] ids=new int[1024];
        final long[] times=new long[ids.length], elapsedUs=new long[ids.length];
        final String[] phases=new String[ids.length], reasons=new String[ids.length];
        int count;
        void record(TouchEvent event,long time,String phase,String reason,long deadline){
            if(event.getNote().getType()!=3)return;
            int i=count++;if(i>=ids.length)return;
            ids[i]=event.getNote().getId();times[i]=time;phases[i]=phase;reasons[i]=reason;
            elapsedUs[i]=deadline==Long.MAX_VALUE?0:Math.max(0,(System.nanoTime()-(deadline-8000000L))/1000L);
        }
        void flush(){
            for(int i=0;i<Math.min(count,ids.length);i++){
                android.util.Log.i("PhiHoldTrace","chartMs="+times[i]+" note="+ids[i]+" phase="+phases[i]
                    +" workUs="+elapsedUs[i]+" reason="+reasons[i]);
            }
            android.util.Log.i("PhiHoldTrace","interruptions="+count);
        }
    }

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
        HoldInterruptions interruptions=new HoldInterruptions();
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
                            try{if(!TapMod.clearAt(chart,viewport,new Point(live.x,live.y),now/1000.0,retirePrepareDeadline)){
                                keep=false;interruptions.record(entry.getKey(),now,"retire","held point unsafe",retirePrepareDeadline);
                            }}
                            catch(TapMod.NoSafePoint blocked){keep=false;interruptions.record(entry.getKey(),now,"retire",blocked.getMessage(),retirePrepareDeadline);}
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
                            boolean stale=retireTiming.chartAt(retireWall)-retireTiming.chartAt(retireStart)>maxAge;
                            if(stale||System.nanoTime()>=retireDeadline){
                                interruptions.record(event,retireTiming.chartAt(retireWall),"retire-submit",stale?"batch age":"deadline before check",retireDeadline);return false;
                            }
                            Point p=new Point(prior.x,prior.y);
                            try{
                                TapMod.segment(chart,viewport,p,p,retireTiming.chartAt(retireStart),retireTiming.chartAt(retireWall+upOffset+CONTINUATION_RELEASE_MS),retireDeadline);
                                if(System.nanoTime()<retireDeadline)return true;
                                interruptions.record(event,retireTiming.chartAt(retireWall),"retire-submit","deadline after check",retireDeadline);return false;
                            }catch(TapMod.NoSafePoint blocked){interruptions.record(event,retireTiming.chartAt(retireWall),"retire-submit",blocked.getMessage(),retireDeadline);return false;}
                        });
                        long done=SystemClock.uptimeMillis();maxSafetyMs=Math.max(maxSafetyMs,done-retireWall);
                        if(!retirement.isEmpty()&&retireTiming.chartAt(done)-retireTiming.chartAt(retireWall)>maxAge)rejected.addAll(retirement.rejectUnsafe((event,prior,downOffset,upOffset,keep)->{
                            if(keep)interruptions.record(event,retireTiming.chartAt(done),"retire-submit","postvalidation age",retireDeadline);
                            return !keep;
                        }));
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
                    for(TouchEvent held:active.keySet())interruptions.record(held,now,"reopen","pointer capacity or chain reset",Long.MAX_VALUE);
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
                // Keep moving-noise batches short; the final pass checks the actual
                // Android arc-length timing of every leg before dispatch.
                boolean noiseWindow=TapMod.avoids(chart) && BlockAreaSet.overlaps(chart,now-TapMod.noiseLookBackMs(),end+TapMod.noiseLookAheadMs());
                if(noiseWindow)end=Math.min(end,now+12);
                if(TapMod.avoids(chart) && io.github.phiscript.engine.BlockAreaSet.overlaps(chart,now,end))
                    end=Math.min(end,Math.max(now+1,timing.chartAt(wall+8)));
                for(TouchEvent held:active.keySet())end=plan.nextBoundary(held,now,end);
                for(TouchEvent held:resume)end=plan.nextBoundary(held,now,end);
                for(int i=cursor;i<events.size()&&events.get(i).getStartMs()<end;i++){
                    TouchEvent head=events.get(i);
                    // DOWN can be scheduled inside this very batch; only a later
                    // change of path needs a boundary. Splitting at the DOWN makes
                    // a new chord wait for the previous Android gesture callback.
                    end=plan.nextBoundary(head,Math.max(now,head.getStartMs()),end);
                }
                Map<TouchEvent, GestureChannel.Live> work = new LinkedHashMap<>(active);
                for (TouchEvent e : resume) work.put(e,null);
                List<TouchEvent> newHeads=new ArrayList<>();
                while (cursor < events.size() && events.get(cursor).getStartMs() < end){
                    TouchEvent admitted=events.get(cursor++);
                    if(admitted.getEndMs()<=now){skipped++;continue;}
                    work.put(admitted,null);newHeads.add(admitted);
                }
                // A tap is judged on DOWN. In moving noise, a short stationary stroke
                // can survive two input ticks even when the following 48 ms path cannot.
                if(noiseWindow && active.isEmpty() && !newHeads.isEmpty() && newHeads.size()==work.size()){
                    long next=cursor<events.size()?timing.wallAt(events.get(cursor).getStartMs()):Long.MAX_VALUE;
                    GestureBatch<TouchEvent> taps=noiseTapChord(newHeads,plan,chart,viewport,safety,screen,timing,next);
                    if(taps!=null){
                        long sent=SystemClock.uptimeMillis();
                        for(TouchEvent e:newHeads)maxLate=Math.max(maxLate,timing.chartAt(sent+taps.startOffset(e))-Math.round(e.getNote().getTimeSeconds()*1000));
                        channel.send(taps);
                        continue;
                    }
                }
                // A single independent TAP only needs its DOWN position. Building
                // sampled geometry for its already-judged body can spend the
                // whole window before dispatch and rebase it to a 1 ms touch.
                // Keep its normal 48 ms contact only when it cannot delay another
                // head, alter a continuing route, or extend into active noise.
                if(active.isEmpty()&&work.size()==1&&newHeads.size()==1){
                    TouchEvent tap=newHeads.get(0);
                    long planned=Math.max(wall,timing.wallAt(tap.getStartMs()));
                    long nextHead=cursor<events.size()?timing.wallAt(events.get(cursor).getStartMs()):Long.MAX_VALUE;
                    long release=timing.wallAt(tap.getEndMs());
                    long plannedEnd=timing.chartAt(release);
                    if(tap.getNote().getType()==1&&!plan.isContinuous(tap)&&release>planned&&nextHead>=release
                            && (!TapMod.avoids(chart)||!BlockAreaSet.overlaps(chart,timing.chartAt(planned)-TapMod.noiseLookBackMs(),plannedEnd+TapMod.noiseLookAheadMs()))){
                        try{
                            boolean[] moved={false};Point p=safety.point(plan,chart,tap,timing.chartAt(planned),viewport,screen,moved);
                            long ready=SystemClock.uptimeMillis(),down=Math.max(ready,timing.wallAt(tap.getStartMs()));
                            long actualDown=timing.chartAt(down),actualEnd=timing.chartAt(release);
                            if(actualDown-tap.getStartMs()>100){skipped++;continue;}
                            if(release>down&&plan.stationaryTap(tap,p,actualDown,actualEnd)
                                    &&(!TapMod.avoids(chart)||!BlockAreaSet.overlaps(chart,actualDown-TapMod.noiseLookBackMs(),actualEnd+TapMod.noiseLookAheadMs()))){
                                GestureBatch<TouchEvent> shot=new GestureBatch<>();
                                shot.add(tap,null,GestureChannel.path(p.getX(),p.getY(),p.getX(),p.getY()),down-ready,release-ready,false,p.getX(),p.getY());
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
                    if(prior==null&&down>=e.getEndMs()){skipped++;continue;}
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
                        long step=noiseWindow?4:8;
                        for (long sample = downWall + step; sample < upWall; sample += step) {
                            long t=timing.chartAt(sample);
                            checkConstructionBudget(constructionDeadline);
                            Point p = noiseWindow?safety.point(plan,chart,e,t,viewport,screen,bounded,checkedPoint,checkedTime)
                                :safety.point(plan,chart,e,t,viewport,screen,bounded);
                            TapMod.segment(chart,viewport,checkedPoint,p,checkedTime,t,constructionDeadline);checkedPoint=p;checkedTime=t;
                            path.lineTo(p.getX(), p.getY());
                            pathPoints.add(p);
                        }
                        TapMod.segment(chart,viewport,checkedPoint,to,checkedTime,up,constructionDeadline);
                        path.lineTo(to.getX(), to.getY());
                        pathPoints.add(to);submittedPaths.put(e,pathPoints);
                        batch.add(e,prior,path,Math.max(0,downWall-wall),Math.max(1,upWall-wall),keep,to.getX(),to.getY());
                    } catch(TapMod.NoSafePoint blocked) {
                        if(prior!=null||newHeads.contains(e))interruptions.record(e,now,prior==null?"build-head":"build-held",blocked.getMessage(),constructionDeadline);
                        if(prior!=null)batch.add(e,prior,GestureChannel.path(prior.x,prior.y,prior.x,prior.y),0,1,false,prior.x,prior.y);
                        if(e.getEndMs()>end)blockedContacts.add(e);
                    }
                }
                if(batch.isEmpty()){GestureChannel.sleep(4);continue;}
                long submitted=SystemClock.uptimeMillis();batch.rebase(submitted-wall);
                // Geometry may have used most of this batch's wall-clock window.
                // Rebase changes its speed and a moving area may have advanced:
                // check the exact submitted constant-arc-length path again.
                if(TapMod.avoids(chart)&&BlockAreaSet.overlaps(chart,timing.chartAt(submitted)-TapMod.noiseLookBackMs(),timing.chartAt(submitted+batch.normalizedEndOffset()+CONTINUATION_RELEASE_MS)+TapMod.noiseLookAheadMs())){
                    final long safetyWall=submitted,computeWall=wall,maxAge=Math.max(1,TapMod.noiseLookAheadMs()-4);
                    final ChartPlaybackClock.Slice safetyTiming=timing;
                    final long safetyDeadline=System.nanoTime()+8000000L;
                    Map<TouchEvent,Long> validationAt=new java.util.IdentityHashMap<>();
                    List<TouchEvent> rejected=batch.rejectUnsafe((event,prior,downOffset,upOffset,keep)->{
                        List<Point> points=submittedPaths.get(event);if(points==null)return true;
                        // Never spend another search on already stale geometry
                        // while physical prior fingers are still held down.
                        boolean stale=safetyTiming.chartAt(safetyWall)-safetyTiming.chartAt(computeWall)>maxAge;
                        if(stale||System.nanoTime()>=safetyDeadline){
                            if(prior!=null||newHeads.contains(event))interruptions.record(event,safetyTiming.chartAt(safetyWall),
                                prior==null?"submit-head":"submit-held",stale?"batch age":"deadline before check",safetyDeadline);
                            return false;
                        }
                        validationAt.put(event,SystemClock.uptimeMillis());
                        try{
                            // A negative box query covers every spatial point at every
                            // guarded time, including Android's constant-arc interpolation,
                            // its rounded continuation, and the final stationary hold.
                            long first=prior==null?safetyWall+downOffset:computeWall;
                            long lastWall=safetyWall+upOffset+(keep?CONTINUATION_RELEASE_MS:0);
                            if(clearSubmittedBox(chart,viewport,points,safetyTiming.chartAt(first),safetyTiming.chartAt(lastWall))){
                                checkConstructionBudget(safetyDeadline);
                                return true;
                            }
                            if(prior!=null){Point held=new Point(prior.x,prior.y);TapMod.segment(chart,viewport,held,held,safetyTiming.chartAt(computeWall),safetyTiming.chartAt(safetyWall),safetyDeadline);}
                            checkSubmittedPath(chart,viewport,points,safetyTiming,safetyWall+downOffset,safetyWall+upOffset,safetyDeadline);
                            if(keep){
                                // rejectUnsafe provides the normalized UP, including other fingers'
                                // delayed starts. Preserve a way to lift at this point in the next batch.
                                Point last=points.get(points.size()-1);
                                long arrival=safetyWall+upOffset;
                                TapMod.segment(chart,viewport,last,last,safetyTiming.chartAt(arrival),
                                        safetyTiming.chartAt(arrival+CONTINUATION_RELEASE_MS),safetyDeadline);
                            }
                            if(System.nanoTime()<safetyDeadline)return true;
                            if(prior!=null||newHeads.contains(event))interruptions.record(event,safetyTiming.chartAt(safetyWall),
                                prior==null?"submit-head":"submit-held","deadline after check",safetyDeadline);
                            return false;
                        }
                        catch(TapMod.NoSafePoint blocked){
                            if(prior!=null||newHeads.contains(event))interruptions.record(event,safetyTiming.chartAt(safetyWall),
                                prior==null?"submit-head":"submit-held",blocked.getMessage(),safetyDeadline);
                            return false;
                        }
                    });
                    long checkedWall=SystemClock.uptimeMillis();
                    maxSafetyMs=Math.max(maxSafetyMs,checkedWall-safetyWall);
                    // clearAt/segment already guard the next 12ms. Do not allow
                    // an expensive verification to consume that guard itself.
                    if(!batch.isEmpty())rejected.addAll(batch.rejectUnsafe((event,prior,downOffset,upOffset,keep)->{
                        if(rejected.contains(event)||!validationAt.containsKey(event)||safetyTiming.chartAt(checkedWall)-safetyTiming.chartAt(validationAt.get(event))<=maxAge)return true;
                        if(prior!=null||newHeads.contains(event))interruptions.record(event,safetyTiming.chartAt(checkedWall),
                            prior==null?"submit-head":"submit-held","postvalidation age",safetyDeadline);
                        return false;
                    }));
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
            finally { if (channel != null) channel.close(); playing.set(false);interruptions.flush(); }
        }
    }

    static int[] dimensions(android.content.Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(android.content.Context.WINDOW_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                android.graphics.Rect bounds = wm.getMaximumWindowMetrics().getBounds();
                if (bounds.width()>1 && bounds.height()>1) return new int[]{bounds.width(), bounds.height()};
            } catch (UnsupportedOperationException unavailable) {
                // Some application contexts only expose display metrics.
            }
        }
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    private static GestureBatch<TouchEvent> noiseTapChord(List<TouchEvent> heads,GesturePlan plan,Chart chart,Viewport view,
            TouchSafety safety,int[] screen,ChartPlaybackClock.Slice timing,long nextWall){
        long first=heads.get(0).getStartMs();
        for(TouchEvent e:heads)if(e.getStartMs()!=first||e.getNote().getType()!=1||plan.isContinuous(e)||plan.hasFlickPath(e))return null;
        long wall=SystemClock.uptimeMillis(),down=Math.max(wall,timing.wallAt(first));
        long duration=Math.min(24,nextWall-down);
        for(TouchEvent e:heads)duration=Math.min(duration,timing.wallAt(e.getEndMs())-down);
        if(duration<16)return null;
        long deadline=System.nanoTime()+8000000L;
        GestureBatch<TouchEvent> batch=new GestureBatch<>();
        for(TouchEvent e:heads){
            try{
                Point p=safety.point(plan,chart,e,timing.chartAt(down),view,screen,new boolean[1]);
                long from=timing.chartAt(down),to=timing.chartAt(down+duration);
                if(!plan.stationaryTap(e,p,from,to))TapMod.segment(chart,view,p,p,from,to,deadline);
                batch.add(e,null,GestureChannel.path(p.getX(),p.getY(),p.getX(),p.getY()),down-wall,down-wall+duration,false,p.getX(),p.getY());
            }catch(TapMod.NoSafePoint blocked){return null;}
        }
        long elapsed=SystemClock.uptimeMillis()-wall;
        // Preserve the certified absolute UP and at least two input ticks.
        if(elapsed>down-wall+duration-16 || System.nanoTime()>=deadline)return null;
        batch.rebase(elapsed);
        return batch;
    }

    private static boolean clearSubmittedBox(Chart chart,Viewport view,List<Point> points,long from,long to){
        Point first=points.get(0);
        float minX=first.getX(),maxX=minX,minY=first.getY(),maxY=minY;
        for(int i=1;i<points.size();i++){
            Point p=points.get(i);
            minX=Math.min(minX,p.getX());maxX=Math.max(maxX,p.getX());
            minY=Math.min(minY,p.getY());maxY=Math.max(maxY,p.getY());
        }
        return !BlockAreaSet.mayTouchPath(chart,view,new Point(minX,minY),new Point(maxX,maxY),
                from/1000.0-TapMod.noiseLookBackMs()/1000.0,to/1000.0+TapMod.noiseLookAheadMs()/1000.0);
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
