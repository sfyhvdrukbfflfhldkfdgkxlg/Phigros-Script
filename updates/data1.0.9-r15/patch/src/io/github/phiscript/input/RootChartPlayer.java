package io.github.phiscript.input;

import android.os.Looper;
import android.os.SystemClock;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.TouchEvent;
import io.github.phiscript.engine.Viewport;
import io.github.phiscript.root.RootClient;
import io.github.phiscript.root.RootTouchState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Continuous root pointers: holds do not need accessibility gesture restarts. */
public final class RootChartPlayer {
    private RootChartPlayer(){}
    public static void play(TouchService service,Chart chart,long epoch,Viewport viewport,
            AtomicBoolean stop,Function1<? super String,Unit> status,
            AtomicBoolean playing,AtomicBoolean cancelled)throws InterruptedException{
        RootClient prepared=null;
        try{prepared=SharedChartPlayback.takeRoot(chart);playInternal(service,chart,epoch,viewport,stop,status,playing,cancelled,prepared);}
        catch(InterruptedException e){throw e;}
        catch(Exception e){throw new IllegalStateException("Root 输入准备失败："+e.getMessage(),e);}
        finally{if(prepared!=null)prepared.close();}
    }
    /** The manual controller connects before deriving its epoch. */
    public static void playPrepared(TouchService service,Chart chart,long epoch,Viewport viewport,
            AtomicBoolean stop,Function1<? super String,Unit> status,RootClient prepared)throws InterruptedException{
        if(prepared==null)throw new IllegalArgumentException("Root 通道未准备");
        playInternal(service,chart,epoch,viewport,stop,status,new AtomicBoolean(),new AtomicBoolean(),prepared);
    }
    private static void playInternal(TouchService service,Chart chart,long epoch,Viewport viewport,
            AtomicBoolean stop,Function1<? super String,Unit> status,
            AtomicBoolean playing,AtomicBoolean cancelled,RootClient prepared)throws InterruptedException{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("触控必须在后台线程执行");
        if(!playing.compareAndSet(false,true))throw new IllegalStateException("已有演奏在运行");
        cancelled.set(false);
        try{
            int[] screen=ChartMultiTouchPlayer.dimensions(service);
            if(!service.getGameForeground())throw new IllegalStateException("目标游戏尚未处于前台");
            if(viewport.getLeft()<0||viewport.getTop()<0||viewport.getRight()>screen[0]+1||viewport.getBottom()>screen[1]+1)
                throw new IllegalArgumentException("玩法视口超出屏幕");
            TouchSafety safety=TouchSafety.read(service,screen[0],screen[1],viewport);
            GesturePlan plan=GesturePlan.forPlayback(chart,GestureProfile.read(service),viewport,safety.bounds);
            if(io.github.phiscript.engine.BlockAreaSet.count(chart)>0)status.invoke(TapMod.avoids(chart)?"噪域避让已开启：读取到 "+io.github.phiscript.engine.BlockAreaSet.count(chart)+" 个激活区域，按原生变换与反转检查":"本谱含噪域，但设置已关闭避让；请开启“避开谱面噪域”");
            else if(!io.github.phiscript.engine.BlockAreaSet.hasMetadata(chart))status.invoke("本谱未保存噪域数据；旧版导入的谱面请从原始谱包重新导入");
            if(plan.unavailableNotes>0)status.invoke("规划中有 "+plan.unavailableNotes+" 个音符在判定窗口内未找到安全可用触点");
            List<TouchEvent> events=plan.events;
            ChartPlaybackClock clock=ChartPlaybackClock.resolve(epoch);
            try(RootClient client=prepared==null?new RootClient(service,screen[0],screen[1]):prepared){
                status.invoke("Root 谱面演奏：连接手势 "+plan.connectedGroups+" 组独立轨迹，长键持续保持；音量减停止");
                Map<Integer,TouchEvent> active=new LinkedHashMap<>();int cursor=0;
                long checked=0,lastTime=0,maxLate=0,submissionRejected=0,maxSafetyMs=0;boolean warned=false;int skipped=0;
                Map<Integer,Point> previous=new LinkedHashMap<>();
                while((cursor<events.size()||!active.isEmpty())&&!stop.get()&&!cancelled.get()&&service.getGameForeground()){
                    long wall=SystemClock.uptimeMillis();ChartPlaybackClock.Slice timing=clock.snapshot(wall);long now=timing.chartAt(wall);
                    if(wall-checked>=250){int[] current=ChartMultiTouchPlayer.dimensions(service);
                        if(current[0]!=screen[0]||current[1]!=screen[1])throw new IllegalStateException("屏幕尺寸变化，请重新开始");checked=wall;}
                    java.util.Iterator<TouchEvent> it=active.values().iterator();while(it.hasNext())if(it.next().getEndMs()<=now)it.remove();
                    Map<Integer,TouchEvent> admittedHeads=new LinkedHashMap<>();
                    while(cursor<events.size()&&events.get(cursor).getStartMs()<=now){
                        TouchEvent event=events.get(cursor);
                        if(event.getEndMs()>now&&(plan.isContinuous(event)||event.getStartMs()>=now-100)){
                            active.put(cursor,event);
                            if(event.getNote().getType()==1||event.getNote().getType()==3)admittedHeads.put(cursor,event);
                        }else skipped++;
                        cursor++;
                    }
                    List<RootTouchState.Sample> samples=new ArrayList<>();boolean[] bounded={false};
                    long next=now+(active.isEmpty()?100:8);
                    if(cursor<events.size())next=Math.min(next,events.get(cursor).getStartMs());
                    final long constructionDeadline=TapMod.avoids(chart)&&io.github.phiscript.engine.BlockAreaSet.overlaps(chart,now-TapMod.noiseLookBackMs(),timing.chartAt(wall+8)+TapMod.noiseLookAheadMs())?System.nanoTime()+8000000L:Long.MAX_VALUE;
                    for(Map.Entry<Integer,TouchEvent> e:active.entrySet()){
                        TouchEvent event=e.getValue();
                        next=Math.min(next,event.getEndMs());
                        try {
                            if(System.nanoTime()>=constructionDeadline)throw new TapMod.NoSafePoint("噪域本批触点计算超时");
                            Point old=previous.get(e.getKey());
                            Point p=safety.point(plan,chart,event,now,viewport,screen,bounded,old,lastTime);
                            if(System.nanoTime()>=constructionDeadline)throw new TapMod.NoSafePoint("噪域本批触点计算超时");
                            TapMod.segment(chart,viewport,old==null?p:old,p,old==null?now:lastTime,now,constructionDeadline);
                            samples.add(new RootTouchState.Sample(e.getKey(),p.getX(),p.getY()));
                        }catch(TapMod.NoSafePoint blocked){
                            // Omitting only this id emits its UP in the next root frame.
                            previous.remove(e.getKey());
                        }
                    }
                    if(samples.size()>10)throw new IllegalStateException("谱面需要超过十个独立触点");
                    long submissionWall=wall,submissionTime=now;
                    if(TapMod.avoids(chart)){submissionWall=SystemClock.uptimeMillis();submissionTime=timing.chartAt(submissionWall);}
                    if(TapMod.avoids(chart)&&io.github.phiscript.engine.BlockAreaSet.overlaps(chart,now-TapMod.noiseLookBackMs(),timing.chartAt(submissionWall+8)+TapMod.noiseLookAheadMs())){
                        long safetyBegan=submissionWall;
                        long maxAge=Math.max(1,TapMod.noiseLookAheadMs()-4),safetyDeadline=System.nanoTime()+8000000L;
                        Map<Long,Long> validationAt=new LinkedHashMap<>();
                        java.util.Iterator<RootTouchState.Sample> pending=samples.iterator();
                        while(pending.hasNext()){
                            RootTouchState.Sample sample=pending.next();Point p=new Point(sample.x,sample.y),old=previous.get((int)sample.key);
                            long at=SystemClock.uptimeMillis();validationAt.put(sample.key,at);
                            try{
                                if(timing.chartAt(at)-timing.chartAt(wall)>maxAge||System.nanoTime()>=safetyDeadline)throw new TapMod.NoSafePoint("旧噪域坐标已过期，释放后重算");
                                long actual=timing.chartAt(at);
                                if(!TapMod.clearAt(chart,viewport,p,actual/1000.0,safetyDeadline))throw new TapMod.NoSafePoint("发送时噪域已移入触点");
                                if(old!=null)TapMod.segment(chart,viewport,old,old,lastTime,actual,safetyDeadline);
                                TapMod.segment(chart,viewport,old==null?p:old,p,actual,actual,safetyDeadline);
                                // Root keeps this point stationary until the
                                // next 8ms sample reaches the input channel.
                                TapMod.segment(chart,viewport,p,p,actual,timing.chartAt(at+8),safetyDeadline);
                            }catch(TapMod.NoSafePoint blocked){pending.remove();previous.remove((int)sample.key);submissionRejected++;}
                        }
                        submissionWall=SystemClock.uptimeMillis();submissionTime=timing.chartAt(submissionWall);
                        maxSafetyMs=Math.max(maxSafetyMs,submissionWall-safetyBegan);
                        pending=samples.iterator();while(pending.hasNext()){
                            RootTouchState.Sample sample=pending.next();
                            if(timing.chartAt(submissionWall)-timing.chartAt(validationAt.get(sample.key))>maxAge||System.nanoTime()>=safetyDeadline){pending.remove();previous.remove((int)sample.key);submissionRejected++;}
                        }
                    }
                    if(!admittedHeads.isEmpty()){
                        if(!TapMod.avoids(chart))submissionTime=timing.chartAt(SystemClock.uptimeMillis());
                        for(RootTouchState.Sample sample:samples){
                            TouchEvent head=admittedHeads.get((int)sample.key);
                            if(head!=null)maxLate=Math.max(maxLate,submissionTime-Math.round(head.getNote().getTimeSeconds()*1000));
                        }
                    }
                    for(RootTouchState.Sample sample:samples)previous.put((int)sample.key,new Point(sample.x,sample.y));
                    client.frame(samples);previous.keySet().retainAll(active.keySet());lastTime=submissionTime;
                    if(bounded[0]&&!warned){status.invoke("部分音符已移入可触控区域，避免系统边缘手势；边缘音符可能无法命中");warned=true;}
                    GestureChannel.sleep(Math.max(1,Math.min(10,timing.wallAt(next)-SystemClock.uptimeMillis())));
                }
                // Acknowledged UP before normal termination; close also cancels on error.
                client.frame(new ArrayList<RootTouchState.Sample>());
                status.invoke("输入统计：跳过 "+skipped+" 个过期手势，最大按下调度延迟 "+Math.max(0,maxLate)+" ms");
                if(TapMod.avoids(chart))status.invoke("噪域提交复核：拒绝或抬起 "+submissionRejected+" 次触点，最大复核耗时 "+maxSafetyMs+" ms");
            }
            status.invoke(stop.get()||cancelled.get()||!service.getGameForeground()?"已停止 Root 触摸":"Root 演奏完成");
        }catch(InterruptedException e){throw e;}
        catch(Exception e){throw new IllegalStateException("Root 演奏失败："+e.getMessage(),e);}
        finally{playing.set(false);}
    }
}
