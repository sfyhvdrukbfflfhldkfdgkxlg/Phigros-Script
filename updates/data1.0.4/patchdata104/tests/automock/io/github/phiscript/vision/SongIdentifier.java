package io.github.phiscript.vision;
import android.graphics.Bitmap;import java.util.*;import io.github.phiscript.capture.AutoEngineRegression;
public final class SongIdentifier implements AutoCloseable {
 public SongIdentifier(OcrConfig c){}
 public OcrLabels readLabels(Bitmap b){
  String s=AutoEngineRegression.scenario;boolean paused=b.phase==1||b.phase==3;
  if(AutoEngineRegression.warmed>0)AutoEngineRegression.postGrantReads++;
  if(b.phase==0)AutoEngineRegression.ocrBeforePause++;if(b.phase==1)AutoEngineRegression.identityReads++;
  List<TextRegion> regions=new ArrayList<>();
  boolean words=paused&&!s.equals("icononly");
  if(words){regions.add(new TextRegion("重新开始",.4f,.4f,.6f,.5f));regions.add(new TextRegion("继续",.4f,.6f,.6f,.7f));regions.add(new TextRegion("暂停",.4f,.2f,.6f,.3f));}
  boolean unknown=s.equals("preocrempty")&&b.phase==0||s.equals("noidentity")||s.equals("lateidentity")&&AutoEngineRegression.identityReads<=3;
  return new OcrLabels(unknown?Collections.emptyList():Collections.singletonList("Unit Song"),unknown?Collections.emptyList():Collections.singletonList(s.equals("changed")?"HD 12":"IN 15"),regions,words,false,words?new PauseMenu(new NormalizedPoint(.5f,.65f)):null);
 }
 public void close(){}
}
