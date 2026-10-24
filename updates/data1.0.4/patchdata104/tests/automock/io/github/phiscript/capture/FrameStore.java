package io.github.phiscript.capture;
public final class FrameStore {
 public CapturedFrame snapshot(){
  AutoEngineRegression.snapshots++;android.os.SystemClock.offset+=80;
  String s=AutoEngineRegression.scenario;int n=AutoEngineRegression.snapshots,p=AutoEngineRegression.phase;
  if(s.equals("servicechange")&&n==2)io.github.phiscript.input.TouchService.current=null;
  if((s.equals("ambiguous")||s.equals("noidentity")||s.equals("nogame")||s.equals("textdecoy"))&&n>24||s.equals("preview")&&n>8)AutoEngineRegression.stop.set(true);
  if(s.equals("cancelmenu")&&p==1)AutoEngineRegression.stop.set(true);
  if(s.equals("manualLate")&&n>32&&p==0)AutoEngineRegression.phase=p=1;
  if(n>240)throw new AssertionError("frame budget exceeded: "+s);
  if(s.equals("nohud")&&p==2&&++AutoEngineRegression.startFrames>15)android.os.SystemClock.offset+=20000;
  if(s.equals("textdecoy")&&p==1&&++AutoEngineRegression.startFrames>10)android.os.SystemClock.offset+=14000;
  if(p==2&&AutoEngineRegression.firstHud<0)AutoEngineRegression.firstHud=android.os.SystemClock.uptimeMillis();
  if(s.equals("stalepostgrant")&&AutoEngineRegression.warmed>0&&n<6)return new CapturedFrame(p,AutoEngineRegression.grantAt-2000);
  return new CapturedFrame(p);
 }
}
