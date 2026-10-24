package io.github.phiscript.capture;
import android.content.Context;import android.os.Handler;import android.view.*;import android.widget.*;
public final class OverlayLifecycleRegression {
 static RecognitionOverlay host(Context c){RecognitionOverlay o=new RecognitionOverlay();o.host(c);return o;}
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  Context c=new Context();RecognitionOverlay nativeOverlay=host(c);AutoOverlayFeedback f=new AutoOverlayFeedback(c,nativeOverlay);
  f.show("等待 Root");Handler.drain();ok(nativeOverlay.visible()&&nativeOverlay.hides==0,"window hidden during authorization");
  WindowManager.LayoutParams p=(WindowManager.LayoutParams)nativeOverlay.panel().getLayoutParams();
  ok(p.width==320&&p.height==84,"compact physical bounds");ok(p.gravity==(Gravity.TOP|Gravity.RIGHT),"overlay not anchored to physical upper right");
  ok(nativeOverlay.label().lines==3&&nativeOverlay.label().text.equals("等待 Root"),"status text missing");
  f.show("等待暂停菜单");Handler.drain();ok(nativeOverlay.nativeStatuses==1,"status update recreated panel or blocked capture again");ok(nativeOverlay.visible()&&nativeOverlay.label().text.equals("等待暂停菜单"),"menu waiting status disappeared");
  LinearLayout actions=(LinearLayout)nativeOverlay.panel().getChildAt(1);((Button)actions.getChildAt(2)).click.run();ok(nativeOverlay.stops==1,"existing stop callback lost");
  f.playing();Handler.drain();ok(nativeOverlay.visible()&&nativeOverlay.hides==0&&nativeOverlay.label().text.toString().contains("正在打谱"),"playback lost stop control");f.show("旧的准备状态");Handler.drain();ok(nativeOverlay.visible()&&nativeOverlay.label().text.toString().contains("正在打谱"),"preparation message overrode play status");
  f.waiting("等待下一首");Handler.drain();ok(nativeOverlay.visible()&&nativeOverlay.nativeStatuses==1,"post-playback waiting did not restore status");
  f.close();f.show("不可重开");f.waiting("不可恢复");Handler.drain();ok(!nativeOverlay.visible()&&nativeOverlay.hides==1,"closed overlay was recreated");
  RecognitionOverlay queued=host(c);AutoOverlayFeedback q=new AutoOverlayFeedback(c,queued);q.show("队列中的状态");q.playing();Handler.drain();ok(queued.visible()&&queued.label().text.toString().contains("正在打谱"),"queued preparation status reopened during play");
  RecognitionOverlay partly=host(c);AutoOverlayFeedback r=new AutoOverlayFeedback(c,partly);r.show("准备");Handler.step();r.playing();Handler.drain();ok(partly.visible()&&partly.label().text.toString().contains("正在打谱"),"native queued creation raced after hide");
  RecognitionOverlay stopped=host(c);AutoOverlayFeedback s=new AutoOverlayFeedback(c,stopped);s.show("准备");s.close();Handler.drain();ok(!stopped.visible()&&stopped.nativeStatuses==0,"pending status survived stop");
  RecognitionOverlay rotate=host(c);AutoOverlayFeedback rotation=new AutoOverlayFeedback(c,rotate);rotation.show("初始");Handler.drain();c.window.display.metrics.widthPixels=2200;c.window.display.metrics.heightPixels=1000;c.window.display.metrics.density=3;rotation.show("尺寸变化");Handler.drain();WindowManager.LayoutParams resized=(WindowManager.LayoutParams)rotate.panel().getLayoutParams();ok(resized.width==704&&resized.height==140&&rotate.nativeStatuses==1,"display change did not resize existing panel");rotation.close();
  ok(io.github.phiscript.RuntimeState.INSTANCE.errors==0,"native layout errors hidden by fallback");
  for(int[] screen:new int[][]{{1000,600,2},{2200,1000,3},{1920,1080,3},{800,480,2}}){AutoOverlayBounds bounds=new AutoOverlayBounds(screen[0],screen[1],screen[2]);int mask=12*screen[2];int left=screen[0]-bounds.margin-bounds.width-mask,bottom=bounds.margin+bounds.height+mask;ok(left>screen[0]*.18f,"overlay masks pause ROI");ok(bottom<screen[1]*.4f,"overlay masks central menu");}
  System.out.println("PASS "+checks+" actual overlay-helper lifecycle/layout checks: visible preparation, in-place updates, preserved stop, persistent play/stop control, return, cleanup and queued-UI races (host UI models).");
 }
}
