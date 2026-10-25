package io.github.phiscript.capture;

import android.graphics.RectF;
import android.os.SystemClock;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.SessionSettings;
import io.github.phiscript.assets.ChartLibrary;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Viewport;
import io.github.phiscript.input.RootChartPlayer;
import io.github.phiscript.input.ChartPlaybackClock;
import io.github.phiscript.input.TouchService;
import io.github.phiscript.record.CaptureVideo;
import io.github.phiscript.root.InputBackend;
import io.github.phiscript.root.RootClient;
import io.github.phiscript.root.RootWarmup;
import io.github.phiscript.vision.Identity;
import io.github.phiscript.vision.NormalizedPoint;
import io.github.phiscript.vision.OcrConfig;
import io.github.phiscript.vision.OcrLabels;
import io.github.phiscript.vision.SongCandidate;
import io.github.phiscript.vision.SongIdentifier;
import io.github.phiscript.vision.SongMatcher;
import io.github.phiscript.vision.TextRegion;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Automatic identity -> verified pause/retry -> observed restart -> chart input. */
public final class PhigrosAutoEngine {
    private final TouchService service;private final FrameStore frames;private final SessionSettings settings;
    private final boolean preview;private final AtomicBoolean stop;private final RecognitionOverlay overlay;
    private final ChartLibrary library;private final List<SongCandidate> songs;private final AppSettings prefs;
    private long lastFrame=-1,nextDiagnostic;
    private int width,height;
    private PhigrosControls controls;
    private final AutoOverlayFeedback feedback;
    private static final class Selection {Identity identity;int width,height;long capturedAt;boolean beforePause;}
    private static final class PlaybackStart {
        final long epoch,deadline;final int noteOffset;
        final PhigrosChartClock calibration;final io.github.phiscript.vision.VisualNoteDetector detector;
        PlaybackStart(long epoch,long deadline,int noteOffset,PhigrosChartClock calibration){
            this.epoch=epoch;this.deadline=deadline;this.noteOffset=noteOffset;this.calibration=calibration;
            detector=calibration==null?null:new io.github.phiscript.vision.VisualNoteDetector(new RectF(0,0,1,1));
        }
    }
    private static final class FocusLost extends IllegalStateException {
        FocusLost(){super("Phigros 已离开前台");}
    }
    private static final class ScreenChanged extends IllegalStateException {
        ScreenChanged(){super("屏幕尺寸变化");}
    }
    public static final class ServiceChanged extends IllegalStateException {public ServiceChanged(){super("无障碍连接已变化");}}
    public PhigrosAutoEngine(TouchService service,FrameStore frames,SessionSettings settings,boolean preview,AtomicBoolean stop,RecognitionOverlay overlay){
        this.service=service;this.frames=frames;this.settings=settings;this.preview=preview;this.stop=stop;this.overlay=overlay;
        feedback=new AutoOverlayFeedback(service,overlay);prefs=new AppSettings(service);library=RuntimeState.INSTANCE.getLibrary();
        if(library==null)throw new IllegalStateException("自动检测读谱需要先导入 Phigros 谱库");
        songs=library.songs();service.selectGame(TouchService.GAME_PACKAGE);
    }
    public void run()throws Exception{
        message(preview?"Phigros 只识别预览：不会暂停、重试或发送谱面触控":"Phigros 自动检测已开启：等待暂停图标和三键菜单");
        try(SongIdentifier ocr=new SongIdentifier(new OcrConfig(settings.getTitleRoi(),settings.getDifficultyRoi()))){
            if(preview){while(!stop.get()){Selection s=identify(ocr);if(s==null)break;message("已识别："+s.identity.getTitle()+" / "+s.identity.getDifficulty()+"（只识别）");sleep(500);}return;}
            while(!stop.get()){
                Selection selected=waitScreen();if(selected==null)break;
                width=selected.width;height=selected.height;boolean playing=false;
                message(InputBackend.rootEnabled?"正在准备 Root 通道，授权后自动检测游戏控件":"权限已就绪，正在检测游戏暂停控件");
                try(RootWarmup warm=InputBackend.rootEnabled?new RootWarmup(service,width,height):null){
                    if(warm!=null)message("Root 已就绪：回到歌曲画面后自动双击暂停，先识别曲名与难度");
                    awaitForeground(warm);
                    lastFrame=Math.max(lastFrame,SystemClock.uptimeMillis()-1);
                    awaitPaused(ocr,selected,warm);
                    selected=identifyPaused(ocr,selected,warm);if(selected==null)break;
                    message("已识别 "+selected.identity.getTitle()+" / "+selected.identity.getDifficulty()+"，正在读取谱面");
                    Chart chart=library.load(selected.identity.getSongId(),selected.identity.getDifficulty());
                    io.github.phiscript.assets.ChartTimingIndex.ensureForPlayback(service,library,selected.identity.getSongId(),selected.identity.getDifficulty(),chart);
                    io.github.phiscript.input.GesturePlan.preflight(chart);
                    long retried=retry(ocr,selected,warm);
                    PlaybackStart start=startClock(ocr,selected,chart,retried,warm);
                    playing=true;play(ocr,selected,chart,controls.viewport(),start,warm);
                }catch(FocusLost e){
                    if(playing)throw e;
                    message("准备期间游戏焦点变化，返回 Phigros 后自动重新检测");continue;
                }catch(ScreenChanged e){
                    if(playing)throw e;
                    message("游戏画面尺寸变化，重新准备输入通道");continue;
                }
                if(!stop.get())awaitBoundary(ocr,selected);hudReset();
            }
        }catch(OutOfMemoryError e){throw new IllegalStateException("自动检测内存不足，请降低截屏尺寸后重试",e);}
        finally{CaptureVideo.finished();feedback.close();}
    }
    private Selection waitScreen()throws Exception{
        while(!stop.get()){
            if(!service.getGameForeground()){diagnostic("等待 Phigros 处于前台；只会操作游戏歌曲画面");sleep(120);continue;}
            try(CapturedFrame frame=fresh()){
                if(frame==null){diagnostic("等待新鲜的横屏游戏截图，请确认系统截屏已授权");sleep(40);continue;}
                Selection s=new Selection();s.width=frame.getScreenWidth();s.height=frame.getScreenHeight();
                controls=new PhigrosControls(settings,s.width,s.height,prefs.flag("phigros_fit_16_9",true));return s;
            }
        }return null;
    }
    private void awaitForeground(RootWarmup warm)throws Exception{
        while(!stop.get()&&!service.getGameForeground()){
            if(warm!=null)warm.check();diagnostic("授权已完成，等待 Phigros 恢复前台；返回游戏会自动继续");sleep(120);
        }
        if(stop.get())throw new InterruptedException();
    }
    private void awaitPaused(SongIdentifier ocr,Selection s,RootWarmup warm)throws Exception{
        PauseRestartGate menuGate=null;long pauseAt=-1,nextIdentity=0,identityBegan=-1;int attempts=0;AutoEvidence identityEvidence=new AutoEvidence();
        message("自动暂停：检测 Phigros 左上角暂停键或已打开的三键菜单");
        while(!stop.get()){
            checkForeground();if(warm!=null)warm.check();
            try(CapturedFrame frame=fresh()){
                if(frame==null){sleep(25);continue;}dimensions(frame,s);
                long now=SystemClock.uptimeMillis();boolean menu=controls.menu(frame.getBitmap());
                NormalizedPoint point=null;
                if(menu){
                    controls.resetGame();
                    if(menuGate==null)menuGate=new PauseRestartGate("phigros-controls",frame.getUptimeMs(),false,settings.getPauseSettleMs());
                }else if((pauseAt<0||now-pauseAt>=2500)&&attempts<3)point=controls.pause(frame.getBitmap(),frame.getUptimeMs());
                if(!menu&&controls.hud(frame.getBitmap())&&now>=nextIdentity){
                    if(identityBegan<0)identityBegan=now;
                    OcrLabels labels=ocr.readLabels(frame.getBitmap());Identity id=match(labels);nextIdentity=SystemClock.uptimeMillis()+180;
                    if(id!=null&&!labels.getResultScreen()&&library.hasChart(id.getSongId(),id.getDifficulty())){
                        if(s.identity!=null&&!key(s.identity).equals(key(id))){s.identity=null;s.beforePause=false;}
                        if(identityEvidence.observe(key(id),frame.getUptimeMs(),SystemClock.uptimeMillis())){s.identity=id;s.capturedAt=frame.getUptimeMs();s.beforePause=true;}
                    }else {identityEvidence.reset();if(s.capturedAt>0&&now-s.capturedAt>5000){s.identity=null;s.beforePause=false;}identityDiagnostic(labels);}
                }
                if(point!=null&&(s.identity==null||!s.beforePause||now-s.capturedAt>5000)&&now-identityBegan<1800){
                    diagnostic("已检测到暂停键，先识别清晰的曲名与难度，再自动暂停");point=null;
                }
                if(point!=null&&SystemClock.uptimeMillis()-frame.getUptimeMs()>900)point=null;
                if(point!=null){
                    checkForeground();pauseAt=tap(point,2,s,warm);attempts++;controls.resetGame();
                    menuGate=new PauseRestartGate("phigros-controls",pauseAt,false,settings.getPauseSettleMs());
                    RuntimeState.INSTANCE.log("已双击 Phigros 暂停键，等待三键菜单稳定");
                }
                if(menuGate!=null){
                    PauseRestartGate.Outcome outcome=menuGate.observe(frame.getUptimeMs(),SystemClock.uptimeMillis(),null,menu,!menu,false);
                    if(outcome==PauseRestartGate.Outcome.READY){RuntimeState.INSTANCE.log("已确认 Phigros 暂停菜单和重试图标位置");return;}
                    if(outcome==PauseRestartGate.Outcome.TIMEOUT){menuGate=null;message("尚未确认暂停菜单，可手动双击暂停；继续等待三键图标");}
                }
                diagnostic(pauseAt<0?"等待歌曲暂停图标或中央三按钮菜单；选歌界面不会点击":"已发出双击暂停，等待三按钮图标出现");
            }sleep(70);
        }throw new InterruptedException();
    }
    private Selection identifyPaused(SongIdentifier ocr,Selection s,RootWarmup warm)throws Exception{
        // Phigros blurs the title and difficulty behind the three-button menu.
        // Keep the two-frame identity read from the clear HUD immediately before pausing.
        if(s.beforePause&&s.identity!=null&&SystemClock.uptimeMillis()-s.capturedAt<=12000){
            message("已确认暂停菜单，使用暂停前识别的曲名与难度");return s;
        }
        AutoEvidence evidence=new AutoEvidence();long deadline=SystemClock.uptimeMillis()+30000;
        message("已自动暂停，正在识别曲名与难度；识别不明确时不会重试或演奏");
        while(!stop.get()&&SystemClock.uptimeMillis()<deadline){
            checkForeground();if(warm!=null)warm.check();
            try(CapturedFrame frame=fresh()){
                if(frame==null){sleep(30);continue;}dimensions(frame,s);
                if(!controls.menu(frame.getBitmap())){evidence.reset();diagnostic("等待已暂停的三按钮菜单稳定");sleep(80);continue;}
                OcrLabels labels=ocr.readLabels(frame.getBitmap());Identity id=match(labels);
                if(labels.getResultScreen()||id==null||!library.hasChart(id.getSongId(),id.getDifficulty())){
                    evidence.observe(null,frame.getUptimeMs(),SystemClock.uptimeMillis());identityDiagnostic(labels);continue;
                }
                if(evidence.observe(key(id),frame.getUptimeMs(),SystemClock.uptimeMillis())){
                    s.identity=id;s.capturedAt=frame.getUptimeMs();return s;
                }
            }sleep(150);
        }
        if(stop.get())throw new InterruptedException();
        throw new IllegalStateException("已经暂停，但无法唯一匹配谱库中的曲名/难度；请检查导入的游戏版本、曲名别名和识别区域");
    }
    private Selection identify(SongIdentifier ocr)throws Exception{
        AutoEvidence evidence=new AutoEvidence();
        while(!stop.get()){
            if(!service.getGameForeground()){evidence.reset();diagnostic("等待 Phigros 处于前台");sleep(120);continue;}
            try(CapturedFrame frame=fresh()){
                if(frame==null){sleep(40);continue;}
                OcrLabels labels=ocr.readLabels(frame.getBitmap());Identity id=match(labels);
                if(labels.getResultScreen()||id==null||!library.hasChart(id.getSongId(),id.getDifficulty())){
                    evidence.observe(null,frame.getUptimeMs(),SystemClock.uptimeMillis());identityDiagnostic(labels);continue;
                }
                if(evidence.observe(key(id),frame.getUptimeMs(),SystemClock.uptimeMillis())){
                    Selection s=new Selection();s.identity=id;s.width=frame.getScreenWidth();s.height=frame.getScreenHeight();s.capturedAt=frame.getUptimeMs();return s;
                }
            }sleep(150);
        }return null;
    }
    private long retry(SongIdentifier ocr,Selection s,RootWarmup warm)throws Exception{
        long after=SystemClock.uptimeMillis(),deadline=after+12000,lastIdentity=s.capturedAt;
        FreshFrameConsensus menu=new FreshFrameConsensus(3,240,2500,after);
        message("谱面已读取，复核三按钮图标后点击中央重试");
        while(!stop.get()&&SystemClock.uptimeMillis()<deadline){
            checkForeground();if(warm!=null)warm.check();
            try(CapturedFrame frame=fresh()){
                if(frame==null){sleep(25);continue;}dimensions(frame,s);
                boolean icons=controls.menu(frame.getBitmap());
                OcrLabels labels=s.beforePause?null:ocr.readLabels(frame.getBitmap());Identity id=labels==null?null:match(labels);if(labels!=null)verify(id,s,labels);
                long now=SystemClock.uptimeMillis();if(id!=null)lastIdentity=frame.getUptimeMs();
                boolean valid=now-frame.getUptimeMs()<900&&(s.beforePause||now-lastIdentity<=12000);
                if(menu.observe(icons&&valid?key(s.identity):null,frame.getUptimeMs())){
                    RuntimeState.INSTANCE.log("已确认 Phigros 重试图标，点击本次检测到的位置");
                    return tap(controls.retry(),1,s,warm);
                }
                diagnostic("等待 Phigros 三键图标复核，自动定位重试按钮");
            }sleep(100);
        }
        if(stop.get())throw new InterruptedException();
        throw new IllegalStateException("重试前未能复核暂停菜单或曲目，已取消输入；请检查截图和曲名识别区域");
    }
    private PlaybackStart startClock(SongIdentifier ocr,Selection s,Chart chart,long retried,RootWarmup warm)throws Exception{
        PhigrosStartGate gate=new PhigrosStartGate();gate.command(retried);
        long deadline=SystemClock.uptimeMillis()+18000,nextRead=0;int hudFrames=0;boolean confirmationSent=false;
        boolean align=prefs.flag("phigros_sync_visible_notes",true);
        int initialOffset=Integer.parseInt(prefs.text("phigros_auto_start_offset_ms","0"));
        int noteOffset=align?Integer.parseInt(prefs.text("phigros_note_clock_offset_ms","0")):0;
        if(noteOffset< -500||noteOffset>500)throw new IllegalArgumentException("音符校准微调应在 -500～500 ms");
        while(!stop.get()&&SystemClock.uptimeMillis()<deadline){
            checkForeground();if(warm!=null)warm.check();
            try(CapturedFrame frame=fresh()){
                if(frame==null){sleep(15);continue;}dimensions(frame,s);
                if(frame.getUptimeMs()<=retried)continue;
                long now=SystemClock.uptimeMillis();boolean visible=controls.hud(frame.getBitmap());
                boolean menu=!visible&&controls.menu(frame.getBitmap());hudFrames=visible?hudFrames+1:0;
                // The clicked position was positively identified as the central RETRY glyph.
                // Its disappearance followed by fresh gameplay HUD is the restart receipt.
                long origin=gate.observe(frame.getUptimeMs(),now,visible,menu,visible&&hudFrames>=2);
                if(origin>=0){
                    long epoch=PhigrosStartGate.epoch(origin+Math.round(chart.getOffsetSeconds()*1000),settings.getCaptureLagMs(),initialOffset,settings.getTouchOffsetMs());
                    long calibrateUntil=SystemClock.uptimeMillis()+Math.max(18000,Math.min(600000,(long)(Math.max(0,chart.getFirstNoteSeconds())*1000)+15000));
                    message(align?"已确认重试，先按原始谱面演奏，多帧轨迹校准在演奏中继续":"使用手动偏移与谱面 offset 开始演奏");
                    return new PlaybackStart(epoch,calibrateUntil,noteOffset,align?new PhigrosChartClock(chart,origin,s.width,s.height,controls.viewport()):null);
                }
                if(!confirmationSent&&!visible&&!menu&&now-retried>=2500&&now>=nextRead){
                    OcrLabels labels=ocr.readLabels(frame.getBitmap());verify(match(labels),s,labels);
                    nextRead=SystemClock.uptimeMillis()+400;
                    NormalizedPoint confirm=button(labels,"confirm");
                    if(confirm!=null&&retryQuestion(labels)){
                        retried=tap(confirm,1,s,warm);gate.command(retried);hudFrames=0;confirmationSent=true;
                    }
                }
                diagnostic("中央重试已点击，等待暂停菜单消失和新的游戏暂停图标");
            }sleep(12);
        }
        if(stop.get())throw new InterruptedException();
        throw new IllegalStateException("重试后未确认新的游戏画面，请检查截图和暂停菜单");
    }
    private void identityDiagnostic(OcrLabels labels){
        String title=String.valueOf(labels.getTitleTexts()),difficulty=String.valueOf(labels.getDifficultyTexts());
        diagnostic("曲名尚未唯一匹配；识别到曲名："+shortText(title)+"；难度："+shortText(difficulty));
    }
    private static String shortText(String s){return s.length()>80?s.substring(0,80)+"…":s;}
    private void play(SongIdentifier ocr,Selection s,Chart chart,Viewport viewport,PlaybackStart start,RootWarmup warm)throws Exception{
        checkForeground();RootClient client=warm==null?null:warm.take();
        ChartPlaybackClock clock=new ChartPlaybackClock(start.epoch);
        AtomicBoolean cancelled=new AtomicBoolean();AtomicReference<Exception> failure=new AtomicReference<>();
        RuntimeState.ActiveTouch owner=new RuntimeState.ActiveTouch(stop,cancelled);
        if(!RuntimeState.INSTANCE.getActiveTouchStop().compareAndSet(null,owner)){if(client!=null)client.close();throw new IllegalStateException("已有触控任务");}
        Thread worker=new Thread(()->{
            try{
                ChartPlaybackClock.bind(clock);
                if(client!=null){CaptureVideo.playing();RootChartPlayer.playPrepared(service,chart,start.epoch,viewport,cancelled,text->{RuntimeState.INSTANCE.log(text);return Unit.INSTANCE;},client);}
                else service.play(chart,start.epoch,viewport,cancelled,(Function1<String,Unit>)(text->{RuntimeState.INSTANCE.log(text);return Unit.INSTANCE;}));
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception e){failure.set(e);}
            finally{ChartPlaybackClock.unbind();if(client!=null)client.close();RuntimeState.INSTANCE.getActiveTouchStop().compareAndSet(owner,null);CaptureVideo.finished();}
        },"phi-auto-phigros-input");
        try{
            feedback.playing();worker.start();
            AutoEvidence missingHud=new AutoEvidence(),menu=new AutoEvidence();long nextRead=0,lastRead=SystemClock.uptimeMillis();boolean calibrating=start.calibration!=null;long nextCalibration=0;
            while(worker.isAlive()&&!stop.get()&&!cancelled.get()){
                checkForeground();
                try(CapturedFrame frame=fresh()){
                    if(frame==null){if(SystemClock.uptimeMillis()-lastRead>900)throw new IllegalStateException("游戏画面已中断，释放触控");sleep(15);continue;}
                    lastRead=SystemClock.uptimeMillis();dimensions(frame,s);
                    boolean hud=controls.hud(frame.getBitmap());
                    if(missingHud.observe(!hud?"missing":null,frame.getUptimeMs(),lastRead)){cancelled.set(true);break;}
                    if(calibrating&&!clock.hasCalibration()&&lastRead>start.deadline){calibrating=false;message("多帧校准证据不足，继续按原始谱面演奏");}
                    if(calibrating&&hud&&lastRead>=nextCalibration){
                        nextCalibration=lastRead+(clock.hasCalibration()?100:40);
                        try{
                            long aligned=start.calibration.observe(start.detector.detect(frame.getBitmap()),frame.getUptimeMs(),SystemClock.uptimeMillis());
                            if(aligned>=0){
                                long corrected=PhigrosStartGate.epoch(aligned,settings.getCaptureLagMs(),start.noteOffset,settings.getTouchOffsetMs());
                                if(!clock.hasCalibration()){
                                    clock.calibrate(corrected);
                                    message("多帧音符校准已锁定，平滑同步并持续复核漂移");
                                }else if(clock.refine(corrected,lastRead))RuntimeState.INSTANCE.log("音符时钟已小幅修正，现有触点保持连续");
                            }
                        }catch(RuntimeException e){calibrating=false;RuntimeState.INSTANCE.log("音符校准不可用，继续按原始谱面演奏："+e.getMessage());}
                    }
                    if(lastRead>=nextRead){
                        OcrLabels labels=ocr.readLabels(frame.getBitmap());Identity id=match(labels);
                        if(id!=null&&!key(id).equals(key(s.identity))){cancelled.set(true);break;}
                        if(menu.observe(labels.getResultScreen()||controls.menu(frame.getBitmap())?"exit":null,frame.getUptimeMs(),SystemClock.uptimeMillis())){cancelled.set(true);break;}
                        nextRead=SystemClock.uptimeMillis()+300;
                    }
                }sleep(15);
            }
        }finally{
            cancelled.set(true);worker.interrupt();boolean interrupted=Thread.interrupted();
            try{
                long deadline=SystemClock.uptimeMillis()+2500;
                while(worker.isAlive()&&SystemClock.uptimeMillis()<deadline){try{worker.join(100);}catch(InterruptedException e){interrupted=true;}}
                if(worker.isAlive()){if(client!=null)client.close();throw new IllegalStateException("触控仍在停止中，请结束会话后重试");}
                RuntimeState.INSTANCE.getActiveTouchStop().compareAndSet(owner,null);
            }finally{CaptureVideo.finished();if(interrupted)Thread.currentThread().interrupt();}
        }
        if(failure.get()!=null)throw failure.get();
        RuntimeState.INSTANCE.log("Phigros 本次演奏已结束；等待新的选曲/重试机会");
    }
    private void awaitBoundary(SongIdentifier ocr,Selection previous)throws Exception{
        feedback.waiting("本次演奏已结束，等待新的选曲或开局；暂停菜单不会自动再重试");
        AutoEvidence boundary=new AutoEvidence();
        while(!stop.get()){
            if(!service.getGameForeground())return;
            try(CapturedFrame frame=fresh()){
                if(frame==null){sleep(80);continue;}
                OcrLabels labels=ocr.readLabels(frame.getBitmap());Identity id=match(labels);
                boolean changed=id!=null&&!key(id).equals(key(previous.identity));
                // An intentional pause must stay paused; do not immediately retry it.
                if(labels.getResultScreen()||controls.menu(frame.getBitmap())){boundary.reset();continue;}
                boolean newOpportunity=id!=null&&(changed||!controls.hud(frame.getBitmap())||zeroScore(labels));
                if(boundary.observe(newOpportunity?"boundary":null,frame.getUptimeMs(),SystemClock.uptimeMillis()))return;
            }sleep(150);
        }
    }
    private CapturedFrame fresh(){
        CapturedFrame frame=frames.snapshot();if(frame==null)return null;long now=SystemClock.uptimeMillis();
        if(frame.getUptimeMs()<=lastFrame||frame.getUptimeMs()>now||now-frame.getUptimeMs()>900||frame.getBitmap().getWidth()<=frame.getBitmap().getHeight()){frame.close();return null;}
        lastFrame=frame.getUptimeMs();return frame;
    }
    private Identity match(OcrLabels labels)throws Exception{return SongMatcher.INSTANCE.match(labels.getTitleTexts(),labels.getDifficultyTexts(),songs);}
    private void verify(Identity id,Selection expected,OcrLabels labels){
        if(labels.getResultScreen())throw new IllegalStateException("已进入结算画面，未开始触控");
        if(id!=null&&!key(id).equals(key(expected.identity)))throw new IllegalStateException("歌曲或难度已变化，请重新启动检测");
    }
    private static String key(Identity id){return id.getSongId()+"\n"+id.getDifficulty();}
    private void checkForeground()throws InterruptedException{
        if(stop.get()||Thread.currentThread().isInterrupted())throw new InterruptedException();
        if(TouchService.Companion.getCurrent()!=service)throw new ServiceChanged();
        if(!service.getGameForeground())throw new FocusLost();
    }
    private void dimensions(CapturedFrame frame,Selection s){if(frame.getScreenWidth()!=s.width||frame.getScreenHeight()!=s.height)throw new ScreenChanged();}
    private long tap(NormalizedPoint point,int count,Selection s,RootWarmup warm)throws Exception{
        checkForeground();
        if(warm!=null)return warm.tap(point.getX(),point.getY(),count,settings.getDoubleTapIntervalMs(),stop);
        return service.tapNormalized(point.getX(),point.getY(),count,settings.getDoubleTapIntervalMs(),s.width,s.height,stop);
    }
    private static String clean(String text){return text.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s：:。.!！?？]","");}
    private static NormalizedPoint button(OcrLabels labels,String kind){
        NormalizedPoint found=null;
        for(Object item:labels.getRegions()){
            TextRegion region=(TextRegion)item;String text=clean(region.getText());
            boolean hit="retry".equals(kind)?text.equals("重试")||text.equals("重新开始")||text.equals("retry")||text.equals("restart"):
                text.equals("确认")||text.equals("确定")||text.equals("是")||text.equals("yes")||text.equals("confirm");
            if(!hit||!region.getValid$app_debug())continue;
            NormalizedPoint point=region.getCenter();
            if(point.getY()<.20f||point.getX()<.08f||point.getX()>.92f)continue;
            if(found!=null&&Math.hypot(found.getX()-point.getX(),found.getY()-point.getY())>.06)return null;
            found=point;
        }return found;
    }
    private static boolean zeroScore(OcrLabels labels){
        for(Object item:labels.getRegions()){
            TextRegion region=(TextRegion)item;
            if(region.getCenter().getX()<.55f||region.getCenter().getY()>.22f)continue;
            if(region.getText().replaceAll("\\s","").matches("0{6,7}"))return true;
        }return false;
    }
    private static boolean retryQuestion(OcrLabels labels){
        for(Object item:labels.getRegions()){String text=((TextRegion)item).getText();if(text.contains("是否")&&text.contains("重新")||text.contains("restart")&&text.contains("?"))return true;}return false;
    }
    private void message(String text){RuntimeState.INSTANCE.log(text);SessionFeedback.stage(service,text);feedback.show(text);}
    private void diagnostic(String text){if(SystemClock.uptimeMillis()>=nextDiagnostic){RuntimeState.INSTANCE.log(text);feedback.show(text);nextDiagnostic=SystemClock.uptimeMillis()+2000;}}
    private void sleep(long ms)throws InterruptedException{if(stop.get())throw new InterruptedException();if(TouchService.Companion.getCurrent()!=service)throw new ServiceChanged();Thread.sleep(ms);}
    private void hudReset(){lastFrame=-1;}
}
