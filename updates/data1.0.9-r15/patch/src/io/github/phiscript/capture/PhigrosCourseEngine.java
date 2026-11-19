package io.github.phiscript.capture;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import android.graphics.RectF;
import android.os.SystemClock;
import io.github.phiscript.*;
import io.github.phiscript.assets.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.input.*;
import io.github.phiscript.record.CaptureVideo;
import io.github.phiscript.root.*;
import io.github.phiscript.vision.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import kotlin.Unit;

/** Course transport never pauses or retries a song. All three clocks are
 * established from new visual evidence before any chart touch is submitted. */
public final class PhigrosCourseEngine {
    private final TouchService service;private final FrameStore frames;private final SessionSettings settings;
    private final AtomicBoolean stop;private final AutoOverlayFeedback feedback;private final SessionChartSource source;
    private final List<PhiraCandidate> candidates=new ArrayList<>();private final Map<String,PhiraEntry> entries=new HashMap<>();
    private final CourseHudProbe hud;private final PhigrosMenuGlyphDetector menu=new PhigrosMenuGlyphDetector();
    private long lastFrame=-1,nextLog;private int width,height;
    private enum End { FINISHED, TRANSITION, PAUSED, RESULT }
    private static final class Song {final PhiraCandidate id;final Chart chart;Song(PhiraCandidate id,Chart chart){this.id=id;this.chart=chart;}}
    private static final class Start {long epoch;PhigrosCourseClock clock;VisualNoteDetector detector;}
    public PhigrosCourseEngine(TouchService service,FrameStore frames,SessionSettings settings,AtomicBoolean stop,RecognitionOverlay overlay)throws Exception{
        this.service=service;this.frames=frames;this.settings=settings;this.stop=stop;
        source=SessionChartSource.phigros(service,RuntimeState.INSTANCE.getLibrary(),stop);feedback=new AutoOverlayFeedback(service,overlay);hud=new CourseHudProbe(settings.getPhiraPauseRoi());
        for(PhiraEntry entry:source.getEntries()){entries.put(entry.getId(),entry);candidates.add(new PhiraCandidate(entry.getId(),entry.getTitle(),entry.getLevel(),entry.getAliases()));}
    }
    public void run()throws Exception{
        try(SongIdentifier ocr=new SongIdentifier(new OcrConfig(settings.getTitleRoi(),settings.getDifficultyRoi()))){
            message(tr("课题模式：在课题选曲界面等待通道就绪，再开始三首课题"));
            long startupDeadline=SystemClock.uptimeMillis()+120000;
            while(!stop.get()){
                connected();
                if(SystemClock.uptimeMillis()>=startupDeadline)throw new IllegalStateException(tr("等待 Phigros 前台超时，请切入游戏的课题选曲画面后重试"));
                if(!GameForeground.isForeground(service)){diagnostic(tr("课题通道已启动，请切入 Phigros 的课题选曲画面"));sleep(100);continue;}
                try(CapturedFrame frame=fresh()){if(frame==null){sleep(30);continue;}width=frame.getScreenWidth();height=frame.getScreenHeight();break;}
            }
            PhigrosCourseGate course=new PhigrosCourseGate();
            while(!stop.get()&&!course.complete()){
                try(RootWarmup warm=InputBackend.rootEnabled?new RootWarmup(service,width,height):null){
                    // The first song can still be behind the projection sheet.
                    // Later songs must not wait through a real app switch.
                    if(course.stage()==1)while(!stop.get()&&!GameForeground.isForeground(service)){
                        connected();if(warm!=null)warm.check();
                        if(SystemClock.uptimeMillis()>=startupDeadline)throw new IllegalStateException(tr("等待 Phigros 前台超时，请切入游戏后重试"));
                        sleep(100);
                    }
                    check();lastFrame=Math.max(lastFrame,SystemClock.uptimeMillis()-1);
                    feedback.waiting(tr("课题 ")+course.stage()+tr("/3：通道已就绪，等待选曲转场和新歌曲"));
                    Song song=awaitSong(ocr,course,warm);Viewport viewport=viewport(song.id);
                    GesturePlan.preflight(song.chart,service,viewport);
                    Start start=align(ocr,song,course.firstHud(),viewport,warm);
                    course.playing();End end=play(ocr,song,viewport,start,warm);
                    if(stop.get())return;
                    if(end==End.PAUSED)throw new IllegalStateException(tr("课题已暂停，触控已释放；请重新开始课题再启动本模式"));
                    if(end==End.RESULT){message(tr("课题已结算，自动演奏结束"));return;}
                    course.finished(end==End.TRANSITION);
                }
            }
            if(course.complete())message(tr("课题三首演奏已结束，逐首校准完成，触控已释放"));
        }finally{CaptureVideo.finished();feedback.close();}
    }
    private Song awaitSong(SongIdentifier ocr,PhigrosCourseGate course,RootWarmup warm)throws Exception{
        long deadline=SystemClock.uptimeMillis()+180000;AutoEvidence prefetch=new AutoEvidence();Song prepared=null;
        while(!stop.get()&&SystemClock.uptimeMillis()<deadline){
            check();if(warm!=null)warm.check();
            try(CapturedFrame frame=fresh()){
                if(frame==null){sleep(25);continue;}dimensions(frame);
                boolean visible=hud.observe(frame.getBitmap())!=null;
                OcrLabels labels=ocr.readLabels(frame.getBitmap());PhiraCandidate id=source.match(labels,candidates);
                boolean paused=!visible&&(menu.locate(frame.getBitmap())!=null||labels.getPauseMenu()!=null);
                PhigrosCourseGate.Decision decision=course.observe(frame.getUptimeMs(),SystemClock.uptimeMillis(),visible,paused,labels.getResultScreen(),id==null?null:id.getId());
                if(visible)prefetch.reset();
                if(decision==PhigrosCourseGate.Decision.PAUSED)throw new IllegalStateException(tr("课题处于暂停菜单，未操作重试，请重新开始课题"));
                if(decision==PhigrosCourseGate.Decision.RESULT)throw new IllegalStateException(tr("课题已进入结算，自动演奏停止"));
                if(!visible&&id!=null&&prefetch.observe(id.getId(),frame.getUptimeMs(),SystemClock.uptimeMillis())&&(prepared==null||!prepared.id.getId().equals(id.getId()))){
                    message(tr("课题 ")+course.stage()+tr("/3：转场预读 ")+id.getTitle()+" / "+id.getLevel());prepared=new Song(id,source.load(id.getId(),id.getLevel()));
                }
                if(decision==PhigrosCourseGate.Decision.READY){
                    message(tr("课题 ")+course.stage()+tr("/3：已确认 ")+id.getTitle()+" / "+id.getLevel()+tr("，等待移动音符校准"));
                    return prepared!=null&&prepared.id.getId().equals(id.getId())?prepared:new Song(id,source.load(id.getId(),id.getLevel()));
                }
                diagnostic(tr("课题 ")+course.stage()+tr("/3：等待转场后的清晰曲名、难度和暂停图标"));
            }sleep(55);
        }
        throw new IllegalStateException(tr("课题等待已结束，请在课题选曲界面重新启动"));
    }
    private Start align(SongIdentifier ocr,Song song,long firstHud,Viewport viewport,RootWarmup warm)throws Exception{
        Start start=new Start();start.clock=new PhigrosCourseClock(song.chart,firstHud,width,height,viewport);start.detector=new VisualNoteDetector(new RectF(0,0,1,1));
        long deadline=SystemClock.uptimeMillis()+Math.max(18000,Math.min(600000,Math.round(song.chart.getFirstNoteSeconds()*1000)+15000)),nextRead=0;
        AutoEvidence missing=new AutoEvidence();
        while(!stop.get()&&SystemClock.uptimeMillis()<deadline){
            check();if(warm!=null)warm.check();
            try(CapturedFrame frame=fresh()){
                if(frame==null){sleep(15);continue;}dimensions(frame);long now=SystemClock.uptimeMillis();boolean visible=hud.observe(frame.getBitmap())!=null;
                if(!visible&&menu.locate(frame.getBitmap())!=null)throw new IllegalStateException(tr("课题校准期间已暂停，未发送谱面触控"));
                if(missing.observe(!visible?"missing":null,frame.getUptimeMs(),now))throw new IllegalStateException(tr("课题校准期间画面已切换，未发送谱面触控"));
                if(now>=nextRead){OcrLabels labels=ocr.readLabels(frame.getBitmap());verify(labels,song);if(labels.getResultScreen()||labels.getPauseMenu()!=null)throw new IllegalStateException(tr("课题校准期间已暂停或结算"));nextRead=SystemClock.uptimeMillis()+250;}
                if(!visible)continue;
                long aligned=start.clock.observe(start.detector.detect(frame.getBitmap()),frame.getUptimeMs(),SystemClock.uptimeMillis());
                if(aligned>=0){start.epoch=adjustEpoch(aligned);message(tr("本首多帧音符时钟已锁定，开始演奏并持续复核漂移"));return start;}
                diagnostic(tr("等待首组移动音符的多帧对齐证据；静止或歧义画面不会启动触控"));
            }sleep(12);
        }
        throw new IllegalStateException(tr("课题音符校准证据不足，未发送触控；请提前导入本地谱面并从课题选曲界面启动"));
    }
    private End play(SongIdentifier ocr,Song song,Viewport viewport,Start start,RootWarmup warm)throws Exception{
        check();RootClient client=warm==null?null:warm.take();ChartPlaybackClock clock=new ChartPlaybackClock(start.epoch);clock.calibrate(start.epoch);
        AtomicBoolean cancelled=new AtomicBoolean();AtomicReference<Exception> failure=new AtomicReference<>();RuntimeState.ActiveTouch owner=new RuntimeState.ActiveTouch(stop,cancelled);
        if(!RuntimeState.INSTANCE.getActiveTouchStop().compareAndSet(null,owner)){if(client!=null)client.close();throw new IllegalStateException(tr("已有触控任务"));}
        Thread worker=new Thread(()->{
            try{ChartPlaybackClock.bind(clock);CaptureVideo.playing();SharedChartPlayback.play(service,song.chart,start.epoch,viewport,cancelled,text->{RuntimeState.INSTANCE.log(text);return Unit.INSTANCE;},client);}
            catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){failure.set(e);}
            finally{ChartPlaybackClock.unbind();RuntimeState.INSTANCE.getActiveTouchStop().compareAndSet(owner,null);}
        },"phi-course-input");
        End end=End.FINISHED;long tail=Math.round(song.chart.getDurationSeconds()*1000);
        try{
            feedback.playing();worker.start();AutoEvidence missing=new AutoEvidence(),changed=new AutoEvidence();long lastRead=SystemClock.uptimeMillis(),nextRead=0,nextCalibration=0;
            while(worker.isAlive()&&!stop.get()&&!cancelled.get()){
                check();try(CapturedFrame frame=fresh()){
                    if(frame==null){if(SystemClock.uptimeMillis()-lastRead>900)throw new IllegalStateException(tr("课题画面已中断，释放触控"));sleep(15);continue;}
                    dimensions(frame);lastRead=SystemClock.uptimeMillis();boolean visible=hud.observe(frame.getBitmap())!=null;
                    if(!visible&&menu.locate(frame.getBitmap())!=null){end=End.PAUSED;break;}
                    if(lastRead>=nextRead||!visible){
                        OcrLabels labels=ocr.readLabels(frame.getBitmap());if(labels.getResultScreen()){end=End.RESULT;break;}if(labels.getPauseMenu()!=null){end=End.PAUSED;break;}
                        PhiraCandidate actual=visible?source.match(labels,candidates):null;
                        if(changed.observe(actual!=null&&!actual.getId().equals(song.id.getId())?actual.getId():null,frame.getUptimeMs(),SystemClock.uptimeMillis())){
                            if(clock.snapshot(lastRead).chartAt(lastRead)+150<tail)throw new IllegalStateException(tr("课题结束前曲目已变化，停止旧谱面"));
                            end=End.FINISHED;break;
                        }
                        nextRead=SystemClock.uptimeMillis()+150;
                    }
                    if(missing.observe(!visible?"missing":null,frame.getUptimeMs(),SystemClock.uptimeMillis())){
                        if(clock.snapshot(lastRead).chartAt(lastRead)+150<tail)throw new IllegalStateException(tr("课题音符尚未结束但画面已中断，未将曲中隐藏画面计为下一首"));
                        end=End.TRANSITION;break;
                    }
                    if(visible&&lastRead>=nextCalibration){nextCalibration=lastRead+100;long aligned=start.clock.observe(start.detector.detect(frame.getBitmap()),frame.getUptimeMs(),SystemClock.uptimeMillis());if(aligned>=0)clock.refine(adjustEpoch(aligned),lastRead);}
                }sleep(15);
            }
        }finally{
            cancelled.set(true);worker.interrupt();boolean interrupted=Thread.interrupted();
            try{long deadline=SystemClock.uptimeMillis()+2500;while(worker.isAlive()&&SystemClock.uptimeMillis()<deadline){try{worker.join(100);}catch(InterruptedException e){interrupted=true;}}
                if(worker.isAlive()){if(client!=null)client.close();throw new IllegalStateException(tr("课题触控仍在停止中，请结束会话后重试"));}
                RuntimeState.INSTANCE.getActiveTouchStop().compareAndSet(owner,null);
            }finally{if(client!=null)client.close();if(interrupted)Thread.currentThread().interrupt();}
        }
        if(failure.get()!=null)throw failure.get();return end;
    }
    private Viewport viewport(PhiraCandidate song){PhiraEntry entry=entries.get(song.getId());if(settings.getPhiraAutomaticViewport())return PhiraViewport.INSTANCE.bounds(width,height,entry.getAspectRatio(),entry.getForceAspectRatio());RectF rect=settings.getViewport();return new Viewport(rect.left*width,rect.top*height,rect.width()*width,rect.height()*height);}
    private long adjustEpoch(long aligned){return aligned-settings.getCaptureLagMs()+settings.getTouchOffsetMs();}
    private void verify(OcrLabels labels,Song song){PhiraCandidate actual=source.match(labels,candidates);if(actual!=null&&!actual.getId().equals(song.id.getId()))throw new IllegalStateException(tr("课题歌曲或难度已变化，已停止旧谱面"));}
    private CapturedFrame fresh(){CapturedFrame frame=frames.snapshot();if(frame==null)return null;long now=SystemClock.uptimeMillis();if(frame.getUptimeMs()<=lastFrame||frame.getUptimeMs()>now||now-frame.getUptimeMs()>900||frame.getBitmap().getWidth()<=frame.getBitmap().getHeight()){frame.close();return null;}lastFrame=frame.getUptimeMs();return frame;}
    private void dimensions(CapturedFrame frame){if(frame.getScreenWidth()!=width||frame.getScreenHeight()!=height)throw new IllegalStateException(tr("课题屏幕尺寸变化，已停止触控"));}
    private void connected()throws InterruptedException{if(stop.get()||Thread.currentThread().isInterrupted())throw new InterruptedException();if(TouchService.Companion.getCurrent()!=service)throw new IllegalStateException(tr("课题无障碍服务已变化，请重新开始"));}
    private void check()throws InterruptedException{connected();if(!GameForeground.isForeground(service))throw new IllegalStateException(tr("Phigros 已离开前台，课题演奏停止"));}
    private void sleep(long delay)throws InterruptedException{if(stop.get())throw new InterruptedException();Thread.sleep(delay);}
    private void message(String text){RuntimeState.INSTANCE.log(text);SessionFeedback.stage(service,text);feedback.show(text);}
    private void diagnostic(String text){if(SystemClock.uptimeMillis()>=nextLog){message(text);nextLog=SystemClock.uptimeMillis()+2000;}}
}
