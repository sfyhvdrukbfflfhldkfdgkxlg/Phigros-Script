package io.github.phiscript.capture;
import android.graphics.Bitmap;import android.graphics.RectF;
import io.github.phiscript.SessionSettings;import io.github.phiscript.engine.Viewport;import io.github.phiscript.vision.NormalizedPoint;
/** Physical-screen Phigros controls, independent of Phira's viewport and retry settings. */
public final class PhigrosControls {
 private final PhigrosHudProbe hud;private final PhigrosPauseMenu menu=new PhigrosPauseMenu();private Viewport viewport;
 private final int screenWidth,screenHeight;private final boolean automaticViewport;private boolean measuredViewport;
 private NormalizedPoint retry,lastPause;private long firstAt=-1,lastAt=-1;private int votes;
 public PhigrosControls(SessionSettings settings,int width,int height){
  if(width<=height||height<=0)throw new IllegalArgumentException("请使用横屏游戏画面");
  screenWidth=width;screenHeight=height;hud=new PhigrosHudProbe(settings.getPauseRoi());RectF v=settings.getViewport();viewport=new Viewport(v.left*width,v.top*height,v.width()*width,v.height()*height);
  automaticViewport=v.left==0&&v.top==0&&v.right==1&&v.bottom==1;
 }
 public boolean menu(Bitmap bitmap){retry=menu.locate(bitmap);return retry!=null;}
 public NormalizedPoint retry(){if(retry==null)throw new IllegalStateException("暂停菜单尚未确认");return retry;}
 public boolean hud(Bitmap bitmap){boolean hit=hud.observe(bitmap)!=null;if(hit)measureViewport(bitmap);return hit;}
 public NormalizedPoint pause(Bitmap bitmap,long capturedAt){
  if(capturedAt<=lastAt)return null;lastAt=capturedAt;NormalizedPoint hit=hud.observe(bitmap);
  if(hit==null){resetVotes();return null;}measureViewport(bitmap);
  if(lastPause==null||Math.hypot(hit.getX()-lastPause.getX(),hit.getY()-lastPause.getY())>.018||capturedAt-firstAt>1200){votes=0;firstAt=capturedAt;}
  lastPause=hit;votes++;return votes>=3&&capturedAt-firstAt>=100?hit:null;
 }
 private void resetVotes(){votes=0;firstAt=-1;lastPause=null;}
 public void resetGame(){resetVotes();lastAt=-1;}
 public Viewport viewport(){return viewport;}
 /** Infer pillarboxing only from a long, straight visible judge line, never from dark art. */
 private void measureViewport(Bitmap bitmap){
  if(!automaticViewport||measuredViewport||bitmap.getWidth()/(float)bitmap.getHeight()<1.9f)return;
  int w=bitmap.getWidth(),h=bitmap.getHeight();int[] row=new int[w];
  for(int y=h/4;y<h*3/4;y+=Math.max(1,h/300)){
   bitmap.getPixels(row,0,w,0,y,w,1);int first=-1,last=-1,count=0;
   for(int x=0;x<w;x++){int c=row[x],r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;if(Math.min(r,Math.min(g,b))>=225&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<26){if(first<0)first=x;last=x;count++;}}
   if(first<0)continue;int span=last-first+1;
   if(count>=span*.985f&&span>=w*.55f&&Math.abs(span/(float)h-16/9f)<.025f&&Math.abs(first-(w-1-last))<w*.025f){
    viewport=new Viewport(first*(screenWidth/(float)w),0,span*(screenWidth/(float)w),screenHeight);measuredViewport=true;return;
   }
  }
 }
}
