package io.github.phiscript.capture;
import android.graphics.Bitmap;import android.graphics.RectF;
import io.github.phiscript.SessionSettings;import io.github.phiscript.engine.Viewport;import io.github.phiscript.vision.NormalizedPoint;
/** Physical-screen Phigros controls, independent of Phira's viewport and retry settings. */
public final class PhigrosControls {
 private final PhigrosHudProbe hud;private final PhigrosPauseMenu menu=new PhigrosPauseMenu();private final Viewport viewport;
 private NormalizedPoint retry,lastPause;private long firstAt=-1,lastAt=-1;private int votes;
 public PhigrosControls(SessionSettings settings,int width,int height){
  if(width<=height||height<=0)throw new IllegalArgumentException("请使用横屏游戏画面");
  hud=new PhigrosHudProbe(settings.getPauseRoi());RectF v=settings.getViewport();viewport=new Viewport(v.left*width,v.top*height,v.width()*width,v.height()*height);
 }
 public boolean menu(Bitmap bitmap){retry=menu.locate(bitmap);return retry!=null;}
 public NormalizedPoint retry(){if(retry==null)throw new IllegalStateException("暂停菜单尚未确认");return retry;}
 public boolean hud(Bitmap bitmap){return hud.observe(bitmap)!=null;}
 public NormalizedPoint pause(Bitmap bitmap,long capturedAt){
  if(capturedAt<=lastAt)return null;lastAt=capturedAt;NormalizedPoint hit=hud.observe(bitmap);
  if(hit==null){resetVotes();return null;}
  if(lastPause==null||Math.hypot(hit.getX()-lastPause.getX(),hit.getY()-lastPause.getY())>.018||capturedAt-firstAt>1200){votes=0;firstAt=capturedAt;}
  lastPause=hit;votes++;return votes>=3&&capturedAt-firstAt>=100?hit:null;
 }
 private void resetVotes(){votes=0;firstAt=-1;lastPause=null;}
 public void resetGame(){resetVotes();lastAt=-1;}
 public Viewport viewport(){return viewport;}
}
