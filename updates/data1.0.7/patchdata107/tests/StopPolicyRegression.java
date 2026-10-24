package io.github.phiscript.capture;
public final class StopPolicyRegression {
 static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  ok(SessionStopPolicy.shouldReport("录屏已结束",false),"unexpected projection stop was silent");
  ok(!SessionStopPolicy.shouldReport("录屏已结束",true),"normal projection teardown shown as failure");
  for(String reason:new String[]{"启动失败：权限","截屏启动失败：token","读取截屏失败：图像","画面调整失败：尺寸","悬浮窗不可用：权限"})ok(SessionStopPolicy.shouldReport(reason,false),"failure lost: "+reason);
  ok(!SessionStopPolicy.shouldReport(null,false),"normal engine completion shown as error");
  ok(!SessionStopPolicy.shouldReport("已停止",false),"explicit stop shown as failure");
  ok(!SessionStopPolicy.shouldReport("正在请求权限",false),"status shown as failure");
  ok(!SessionStopPolicy.shouldReport("画面调整失败：销毁中",true),"late teardown overwrote original reason");
  System.out.println("PASS "+checks+" capture-stop classification checks: unexpected projection loss and real failures visible, explicit stop/teardown quiet.");
 }
}
