package io.github.phiscript.capture;
import android.content.Context;import android.provider.Settings;import android.view.accessibility.*;import io.github.phiscript.input.*;import java.util.*;
public class ForegroundHostRegression {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static AccessibilityEvent event(int type,String pkg,String cls){return new AccessibilityEvent(type,pkg,cls);}
 public static void main(String[] args)throws Exception{
  Context fallback=new Context();fallback.app=new Context();TouchService.current=null;ok(OverlayHost.context(fallback)==fallback.app,"fallback does not retain app context");ok(OverlayHost.type(fallback)==2038,"normal overlay type changed");Settings.permission=false;ok(!OverlayHost.allowed(fallback),"normal overlay bypasses permission");Settings.permission=true;ok(OverlayHost.allowed(fallback),"allowed normal overlay rejected");
  TouchService s=TouchService.current=new TouchService();Context host=OverlayHost.context(fallback);Settings.permission=false;ok(host==s&&OverlayHost.type(host)==2032&&OverlayHost.allowed(host),"accessibility token/context/type mismatch");TouchService.current=null;ok(OverlayHost.type(host)==2032,"live window type changed with global service state");
  String game="com.PigeonGames.Phigros",own=s.getPackageName();AccessibilityWindowInfo focus=new AccessibilityWindowInfo(1,true,game);s.windows=Arrays.asList(new AccessibilityWindowInfo(4,false,own),focus);
  ok(game.equals(ForegroundEvents.packageFor(s,event(32,own,"android.widget.Button"))),"control-window event displaced focused game");ok(focus.root.recycled,"focus root not recycled");
  ok(own.equals(ForegroundEvents.packageFor(s,event(32,own,"io.github.phiscript.MainActivity"))),"real home activity was ignored");
  s.windows=Arrays.asList(new AccessibilityWindowInfo(1,true,null));ok(ForegroundEvents.packageFor(s,event(32,own,"android.widget.LinearLayout"))==null,"unknown Unity root falsely became own app");ok(game.equals(ForegroundEvents.packageFor(s,event(32,game,"UnityPlayerActivity"))),"game event requires accessible Unity root");
  s.windows=Arrays.asList(new AccessibilityWindowInfo(3,true,"com.android.systemui"),focus);ok("".equals(ForegroundEvents.packageFor(s,event(4194304,null,null))),"notification shade/permission focus did not block input");
  ok("com.topjohnwu.magisk".equals(ForegroundEvents.packageFor(s,event(32,"com.topjohnwu.magisk","GrantActivity"))),"Root grant focus was ignored");
  s.windows=Arrays.asList(new AccessibilityWindowInfo(4,true,own),focus);ok(game.equals(ForegroundEvents.packageFor(s,event(4194304,null,null))),"accessibility overlay hid underlying game focus");
  s.windows=Arrays.asList(new AccessibilityWindowInfo(1,true,own));ok(own.equals(ForegroundEvents.packageFor(s,event(32,own,"android.widget.Button"))),"confirmed own application focus was ignored");
  s.fail=true;ok(ForegroundEvents.packageFor(s,event(4194304,null,null))==null,"OEM window exception escaped");ok(ForegroundEvents.packageFor(s,null)==null&&ForegroundEvents.packageFor(s,event(1,game,null))==null,"unrelated event changed focus");
  fallback.manager=(android.view.WindowManager)v->{};s.manager=(android.view.WindowManager)v->{};RecognitionOverlay overlay=new RecognitionOverlay(fallback);OverlayHost.rebind(overlay,s);ok(overlay.host()==s&&overlay.wm()==s.manager&&overlay.hides==1,"rebound window did not replace both context and manager");OverlayHost.rebind(overlay,s);ok(overlay.hides==1,"same-service bind unnecessarily removed live window");
  OverlayHost.remove(v->{throw new IllegalArgumentException("already detached");},new android.view.View());ok(true,"detached old service window failed cleanup");
  System.out.println("PASS "+checks+" actual foreground/overlay-host checks: stable service context/token/type, game-open and own-overlay events, inaccessible Unity roots, actual app switch, Root/system focus and safe OEM fallback (host windows).");
 }
}
