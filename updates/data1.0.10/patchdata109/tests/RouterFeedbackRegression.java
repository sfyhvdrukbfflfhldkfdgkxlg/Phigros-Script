package io.github.phiscript.capture;
import io.github.phiscript.*;import io.github.phiscript.input.TouchService;import java.util.concurrent.atomic.AtomicBoolean;
public final class RouterFeedbackRegression {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void run(String failure,boolean cancelled)throws Exception {
  SessionFeedback.clears=SessionFeedback.errors=SessionEngine.calls=PhigrosAutoEngine.calls=OverlayHost.binds=0;SessionFeedback.last=null;PhigrosAutoEngine.failure=failure;
  try{new AutoSessionRouter(new FrameStore(),new SessionSettings(),false,new AtomicBoolean(cancelled),new RecognitionOverlay(),new android.content.Context()).run();if(!failure.isEmpty()&&!failure.equals("reconnect")&&!cancelled)throw new AssertionError("error swallowed");}
  catch(InterruptedException e){if(!failure.equals("interrupt")&&!cancelled)throw e;}
  catch(IllegalStateException e){if(failure.isEmpty()&&TouchService.current!=null)throw e;}
 }
 public static void main(String[] args)throws Exception {
  run("",false);ok(SessionFeedback.clears==1&&SessionFeedback.errors==0&&PhigrosAutoEngine.calls==1,"successful session route");
  run("exception",false);ok(SessionFeedback.errors==1&&SessionFeedback.last.getMessage().equals("未能确认菜单"),"actual failure reason lost");ok(SessionFeedback.clears==1,"old error not cleared on fresh session");
  run("constructor",false);ok(SessionFeedback.errors==1&&PhigrosAutoEngine.calls==0,"startup failure was silent");
  run("linkage",false);ok(SessionFeedback.errors==1&&SessionFeedback.last instanceof NoSuchMethodError,"device API error not captured");
  run("interrupt",false);ok(SessionFeedback.errors==0,"interruption shown as error");
  run("exception",true);ok(SessionFeedback.errors==0,"user stop shown as failure");
  AppSettings.auto=false;run("",false);ok(SessionEngine.calls==1&&PhigrosAutoEngine.calls==0&&SessionFeedback.clears==0,"legacy visual route changed");
  AppSettings.auto=true;TouchService.missing=2;run("",false);ok(SessionEngine.calls==0&&PhigrosAutoEngine.calls==1&&SessionFeedback.errors==0,"temporary reconnect fell back to old engine");
  run("reconnect",false);ok(PhigrosAutoEngine.calls==2&&OverlayHost.binds==2&&SessionFeedback.errors==0,"live service rebind ended session or left stale window");
  TouchService.current=null;android.os.SystemClock.step=30000;run("",false);ok(SessionEngine.calls==0&&SessionFeedback.errors==1&&SessionFeedback.last.getMessage().contains("无障碍"),"missing accessibility silently exited/fell back");
  android.os.SystemClock.step=0;run("",true);ok(SessionFeedback.errors==0&&SessionEngine.calls==0&&PhigrosAutoEngine.calls==0,"cancelled reconnect still played or reported error");
  System.out.println("PASS "+checks+" actual router checks: reconnect resumes automatic mode, missing service reports a cause, startup/runtime/API failures reported, cancellation quiet and explicit legacy mode retained (host Android/reporter models).");
 }
}
