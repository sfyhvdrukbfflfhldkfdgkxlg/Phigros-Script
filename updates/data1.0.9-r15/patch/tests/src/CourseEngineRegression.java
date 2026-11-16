package io.github.phiscript.capture;
import io.github.phiscript.*;import io.github.phiscript.input.*;import io.github.phiscript.root.InputBackend;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Real course engine with frame/OCR/input substitutions. Calibration geometry is
 * tested separately against real native Chart and VisualScene classes. */
public final class CourseEngineRegression {
    public static String scenario;public static AtomicBoolean stop;public static volatile int plays;
    public static int frames,loads,warmed,closed,recordings,clockInstances,overlayClosed;public static long origin;
    public static volatile boolean pause,result,leftGame;public static int foregroundChecks;private static String error;
    private static final List<Long> epochs=new ArrayList<>();private static final Set<ChartPlaybackClock> clocks=Collections.newSetFromMap(new IdentityHashMap<ChartPlaybackClock,Boolean>());
    private static int checks;private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void played(long epoch,AtomicBoolean cancel,ChartPlaybackClock clock)throws Exception{
        ok(clock.hasCalibration(),"touch worker started before calibration");epochs.add(epoch);clocks.add(clock);plays++;
        if(scenario.equals("pause")){pause=true;while(!cancel.get())Thread.sleep(5);}
        if(scenario.equals("result")){result=true;while(!cancel.get())Thread.sleep(5);}
        if(scenario.equals("leave")){leftGame=true;while(!cancel.get())Thread.sleep(5);}
    }
    private static void run(String name,boolean root)throws Exception{
        scenario=name;stop=new AtomicBoolean();plays=frames=loads=warmed=closed=recordings=clockInstances=overlayClosed=foregroundChecks=0;pause=result=leftGame=false;origin=-1;error=null;epochs.clear();clocks.clear();android.os.SystemClock.now=10000;RuntimeState.INSTANCE.active.set(null);InputBackend.rootEnabled=root;
        try{new PhigrosCourseEngine(new TouchService(),new FrameStore(),new SessionSettings(),stop,new RecognitionOverlay()).run();}
        catch(InterruptedException e){if(!stop.get())throw e;Thread.interrupted();}
        catch(IllegalStateException e){error=e.getMessage();if(!name.equals("pause")&&!name.equals("nosync")&&!name.equals("leave")&&!stop.get())throw e;}
        ok(RuntimeState.INSTANCE.active.get()==null,"touch ownership leaked: "+name);ok(overlayClosed==1,"overlay was not closed: "+name);if(root)ok(closed==warmed,"Root channel leaked: "+name);
    }
    public static void main(String[] args)throws Exception{
        run("normal",true);ok(plays==3&&loads==3&&warmed==3&&closed==3,"Root course did not play exactly three songs");ok(clockInstances==3&&clocks.size()==3,"calibration/playback clock reused across songs");ok(epochs.get(0)<epochs.get(1)&&epochs.get(1)<epochs.get(2),"song origins not independent");ok(recordings==1,"course recording was split between songs");
        run("normal",false);ok(plays==3&&warmed==0&&clocks.size()==3,"accessibility course did not play three calibrated songs");
        run("startup",false);ok(plays==3&&foregroundChecks>=3,"projection-to-game startup failed instead of waiting");
        run("leave",true);ok(plays==1&&error!=null&&error.contains("前台"),"real app switch continued course input");
        run("continuous",true);ok(plays==3&&clocks.size()==3,"continuous-HUD Root course failed");
        run("continuous",false);ok(plays==3&&clocks.size()==3,"continuous-HUD accessibility course failed");
        run("pause",true);ok(plays==1&&error!=null&&error.contains("暂停"),"intentional pause continued to another song");
        run("result",true);ok(plays==1&&error==null,"result continued to another song");
        run("nosync",true);ok(plays==0&&recordings==0&&error!=null&&error.contains("校准"),"unconfirmed clock caused chart touches");
        run("nogap",false);ok(plays==0,"joining an active song was accepted as first course start");
        run("ambiguous",false);ok(plays==0&&loads==0,"ambiguous song was loaded or played");
        run("stale",false);ok(plays==0,"stale frames authorized course input");
        System.out.println("PASS "+checks+" actual course-engine checks: three Root/accessibility songs, per-song clocks, transition prefetch, pause/result, no calibration, stale/ambiguous/mid-song input rejection and cleanup.");
    }
}
