package io.github.phiscript.capture;
import io.github.phiscript.*;import io.github.phiscript.input.TouchService;import io.github.phiscript.root.InputBackend;import java.util.concurrent.atomic.AtomicBoolean;
public final class AutoEngineRegression {
 public static String scenario;public static AtomicBoolean stop;public static volatile int phase;
 public static int loads,pauses,retries,warmed,taken,closed,plays,recordings,snapshots,startFrames,focusWait,focusChecks,postGrantReads,ocrBeforePause,identityReads,accessTaps;
 public static long firstHud,epoch,grantAt;public static String loadedDifficulty;public static float retryX,retryY;
 public static int prepShows,playStatuses,cleanupHides;
 static int checks;static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static void run(String name,boolean preview)throws Exception{
  scenario=name;stop=new AtomicBoolean();phase=name.equals("alreadyPaused")?1:0;
  loads=pauses=retries=warmed=taken=closed=plays=recordings=snapshots=startFrames=focusWait=focusChecks=postGrantReads=ocrBeforePause=identityReads=accessTaps=0;
  firstHud=grantAt=-1;epoch=0;loadedDifficulty=null;retryX=retryY=-1;
  prepShows=playStatuses=cleanupHides=0;RecognitionOverlay.visible=false;
  android.os.SystemClock.offset=0;RuntimeState.INSTANCE.active.set(null);InputBackend.rootEnabled=!name.equals("accessibility");
  try{new PhigrosAutoEngine(new TouchService(),new FrameStore(),new SessionSettings(),preview,stop,new RecognitionOverlay()).run();}
  catch(InterruptedException e){if(!stop.get())throw e;Thread.interrupted();}
  catch(PhigrosAutoEngine.ServiceChanged e){if(!name.equals("servicechange"))throw e;}
  catch(IllegalStateException e){if(!name.equals("nohud")&&!name.equals("textdecoy"))throw e;}
 }
 public static long tap(float x,float y,int count){
  if(focusWait>0)throw new AssertionError("input while grant dialog owns focus");
  if(!RecognitionOverlay.visible)throw new AssertionError("status overlay disappeared during preparation");
  if(count==2){if(phase!=0)throw new AssertionError("pause without gameplay");pauses++;if(!scenario.equals("pauseRetry")||pauses>=3)phase=1;}
  else{if(phase!=1)throw new AssertionError("retry without pause menu");retries++;retryX=x;retryY=y;phase=2;}
  return android.os.SystemClock.uptimeMillis();
 }
 public static void played(long at,AtomicBoolean cancel)throws InterruptedException{
  if(!RecognitionOverlay.visible)throw new AssertionError("stop control disappeared during chart playback");
  plays++;epoch=at;try{if(scenario.equals("pause")){phase=3;while(!cancel.get())Thread.sleep(10);}}finally{stop.set(true);}
 }
 public static void main(String[] args)throws Exception{
  run("normal",false);ok(loads==1&&pauses==1&&retries==1,"controls -> pause -> chart -> retry route");ok(warmed==1&&taken==1&&closed==1,"prepared Root route cleanup");ok(plays==1&&recordings==1,"recording follows verified play");ok(epoch>=firstHud&&epoch<=firstHud+40,"45-second Root grant affected visual origin");ok(RuntimeState.INSTANCE.active.get()==null,"input ownership leak");ok(ocrBeforePause>=2,"clear gameplay identity was not cached before pause");
  ok(prepShows>=5&&playStatuses==1&&cleanupHides==1&&!RecognitionOverlay.visible,"preparation/playback/cleanup overlay lifecycle");
  run("icononly",false);ok(plays==1&&retries==1&&retryX==.5f&&retryY==.5f,"icon-only menu required OCR pause/retry/continue labels");ok(ocrBeforePause>=2,"chart played without confirmed pre-pause identity");
  run("preocrempty",false);ok(pauses==1&&plays==1&&ocrBeforePause>0,"unreadable gameplay song text prevented automatic pause");
  run("blurredPause",false);ok(pauses==1&&plays==1&&retries==1&&identityReads==0&&ocrBeforePause>=2,"blurred paused labels prevented verified pre-pause identity reuse");
  run("pause",false);ok(plays==1&&pauses==1&&retries==1,"intentional user pause must stay paused");ok(closed==1&&RuntimeState.INSTANCE.active.get()==null,"pause cleanup");
  run("ambiguous",false);ok(pauses==1&&loads==0&&retries==0&&plays==0,"ambiguous song was retried/played");ok(closed==1,"ambiguous menu channel leak");
  run("preview",true);ok(pauses==0&&loads==0&&retries==0&&plays==0&&recordings==0&&warmed==0,"preview caused input/recording/Root channel");
  run("nohud",false);ok(retries==1&&plays==0&&recordings==0,"unverified restart started chart input");ok(closed==1&&RuntimeState.INSTANCE.active.get()==null,"failed restart cleanup");
  run("rootfocus",false);ok(focusChecks>=4&&pauses==1&&retries==1&&plays==1,"Root grant focus prevented auto pause/retry");
  run("changed",false);ok("HD".equals(loadedDifficulty)&&plays==1,"post-grant difficulty was not used");
  run("cancelgrant",false);ok(loads==0&&pauses==0&&retries==0&&plays==0&&closed==1,"cancellation during grant focus wait leaked input/channel");
  run("lateidentity",false);ok(pauses==1&&identityReads>=5&&plays==1,"temporary missing paused identity aborted workflow");
  run("noidentity",false);ok(pauses==1&&retries==0&&plays==0&&loads==0,"unknown paused identity caused chart input");ok(closed==1,"unknown identity channel leak");
  run("textdecoy",false);ok(pauses==1&&retries==0&&plays==0,"menu words without three glyphs caused retry");
  run("alreadyPaused",false);ok(pauses==0&&retries==1&&plays==1,"already paused game was paused again");
  run("nogame",false);ok(pauses==0&&retries==0&&loads==0&&plays==0,"selection/non-game screen was touched");
  run("cancelmenu",false);ok(pauses==1&&retries==0&&plays==0&&closed==1,"stop while waiting for menu ignored");
  run("stalepostgrant",false);ok(pauses==1&&plays==1,"discarding pre-grant frames blocked recovery");
  run("accessibility",false);ok(warmed==0&&accessTaps==2&&pauses==1&&retries==1&&plays==1,"accessibility backend did not use same controls flow");
  run("pauseRetry",false);ok(pauses==3&&retries==1&&plays==1,"pause retry failed");
  run("manualLate",false);ok(pauses==0&&retries==1&&plays==1,"manual pause failed");
  run("menuDelayed",false);ok(pauses==1&&plays==1,"delayed menu failed");
  run("adaptiveRetry",false);ok(retryX==.53f&&retryY==.47f&&plays==1,"retry position fixed");
  run("servicechange",false);ok(plays==0&&retries==0&&cleanupHides==1,"lost accessibility kept stale service or chart input");
  run("portrait",false);ok(snapshots>3&&pauses==1&&retries==1&&plays==1,"portrait-to-game landscape transition stopped detection");
  System.out.println("PASS "+checks+" actual automatic-engine workflow checks: authorize -> verified gameplay -> double pause -> icon-only menu -> unique chart -> detected retry -> fresh HUD -> Root/accessibility input and recording.");
  System.out.println("Includes missing pre-pause OCR, grant focus/cancellation, stale frames, changed/missing/ambiguous identity, text decoys, user pause, preview and missing restart receipt.");
  System.out.println("Host frame/OCR/input substitutions; real game rendering and music timing are not verified.");
 }
}
