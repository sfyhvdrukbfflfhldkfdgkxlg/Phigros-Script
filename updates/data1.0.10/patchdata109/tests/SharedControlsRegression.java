package io.github.phiscript.capture;
import android.graphics.Bitmap;import android.graphics.RectF;
import io.github.phiscript.SessionSettings;import io.github.phiscript.engine.Viewport;import io.github.phiscript.vision.*;

/** Draws standard pause/back/retry/play shapes into pixel buffers. No detector is substituted. */
public final class SharedControlsRegression {
 static int checks;
 static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void icon(Bitmap b,float cx,float cy,float size,int kind){
  for(int y=(int)(cy-size/2)-1;y<=cy+size/2+1;y++)for(int x=(int)(cx-size/2)-1;x<=cx+size/2+1;x++){
   double u=(x-cx)/size+.5,v=(y-cy)/size+.5,dx=u-.5,dy=v-.5,r=Math.sqrt(dx*dx+dy*dy);boolean fill;
   if(kind==0)fill=v>=.085&&v<=.915&&Math.abs(u-(Math.abs(v-.5)+.34))<=.085;
   else if(kind==1)fill=r>=.22&&r<=.36&&(dx<=0||dy>=0);
   else fill=u>=.23&&u<=.83&&Math.abs(v-.5)<=(.83-u)*.6;
   if(fill)b.setPixel(x,y,0xffeeeeee);
  }
 }
 static Bitmap menu(SessionSettings s,SharedPhiraControls c,int w,int h,int missing,boolean swapped){
  Bitmap b=new Bitmap(w,h);float width=c.viewport().getWidth(),x=s.retry.getX()*w,y=s.retry.getY()*h,unit=.06f*width,space=.085f*width;
  for(int i=0;i<3;i++)if(i!=missing)icon(b,x+(i-1)*space,y,unit*(i==1?4f/3:1),swapped?(i==0?2:i==2?0:1):i);
  return b;
 }
 static RectF view(SharedPhiraControls c,int w,int h){Viewport v=c.viewport();return new RectF(v.getLeft()/w,v.getTop()/h,(v.getLeft()+v.getWidth())/w,(v.getTop()+v.getHeight())/h);}
 static void statusMask(Bitmap bitmap,int w,int h){
  float density=h>=1000?3:2;AutoOverlayBounds bounds=new AutoOverlayBounds(w,h,density);int extra=Math.round(12*density);
  for(int y=Math.max(0,bounds.margin-extra);y<bounds.margin+bounds.height+extra;y++)for(int x=w-bounds.margin-bounds.width-extra;x<w;x++)bitmap.setPixel(x,y,0xff000000);
 }
 static Bitmap pause(SharedPhiraControls c,SessionSettings s,int w,int h){
  Bitmap b=new Bitmap(w,h);RectF roi=s.pause;
  if(s.automatic&&roi.left==0&&roi.top==0&&roi.right==.18f&&roi.bottom==.24f){RectF v=view(c,w,h);roi=new RectF(v.left,v.top,v.left+v.width()*.18f,v.top+v.height()*.24f);}
  int shortSide=Math.min(w,h),height=Math.max(18,Math.round(shortSide*.045f)),bar=Math.max(5,Math.round(shortSide*.010f)),gap=Math.max(5,bar);
  int left=Math.round(roi.left*w)+Math.round((roi.width()*w-(bar*2+gap))*.3f),top=Math.round(roi.top*h)+Math.round(roi.height()*h*.2f);
  for(int y=top;y<top+height;y++)for(int x=left;x<left+bar*2+gap;x++)if(x<left+bar||x>=left+bar+gap)b.setPixel(x,y,0xffeeeeee);
  return b;
 }
 static void controls(SessionSettings s,int w,int h){
  SharedPhiraControls c=new SharedPhiraControls(s,w,h);Bitmap menu=menu(s,c,w,h,-1,false);
  PhiraPauseDetector original=new PhiraPauseDetector(view(c,w,h),s.retry);
  ok(original.visible(menu),"fixture not accepted by actual original Phira detector");
  ok(c.menu(menu),"Phigros failed original Phira three-button glyphs at "+w+"x"+h);
  ok(c.menu(menu)==original.visible(menu),"shared/original menu mismatch");
  statusMask(menu,w,h);ok(c.menu(menu)&&original.visible(menu),"status window mask obscures three-button menu");
  ok(c.retry().getX()==s.retry.getX()&&c.retry().getY()==s.retry.getY(),"independent Phigros retry coordinate used");
  for(int missing=0;missing<3;missing++)ok(!c.menu(menu(s,c,w,h,missing,false)),"incomplete three-button menu accepted");
  ok(!c.menu(menu(s,c,w,h,-1,true)),"back/play positions swapped but menu accepted");
  ok(!c.menu(new Bitmap(w,h)),"blank/selection screen accepted as menu");
  Bitmap game=pause(c,s,w,h);statusMask(game,w,h);ok(!c.menu(game),"gameplay pause glyph accepted as menu");
  ok(c.hud(game),"raw pause glyph not found with shared Phira ROI");
  NormalizedPoint first=c.pause(game,1000),last=null;
  ok(first==null,"pause sent on one frame");
  for(int i=1;i<=5;i++){NormalizedPoint hit=c.pause(game,1000+i*80);if(hit!=null)last=hit;}
  ok(last!=null&&last.getX()>=0&&last.getX()<=1&&last.getY()>=0&&last.getY()<=1,"actual Phira GameplayDetector did not confirm pause");
  ok(c.pause(game,1400)==null,"repeated frame authorized pause");
  c.resetGame();ok(c.pause(game,2000)==null,"pause evidence not reset");
 }
 public static void main(String[] args){
  controls(new SessionSettings(),1000,600);
  controls(new SessionSettings(),2200,1000);
  SessionSettings custom=new SessionSettings();custom.retry=new NormalizedPoint(.6f,.52f);custom.pause=new RectF(.10f,.02f,.35f,.28f);controls(custom,1200,700);
  SessionSettings manual=new SessionSettings();manual.automatic=false;manual.viewport=new RectF(.08f,.04f,.92f,.96f);controls(manual,1200,700);
  boolean rejected=false;try{new SharedPhiraControls(new SessionSettings(),600,1000);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"portrait accepted");
  System.out.println("PASS "+checks+" pixel-level shared-controls checks using actual PhiraPauseDetector, PhiraMenuGlyphs, GameplayDetector, PhiraViewport and raw pause glyph detector.");
  System.out.println("Covers text-free menus, missing/swapped icons, blank screens, gameplay pause, default/wide/manual viewports, custom shared coordinates and frame consensus; synthetic pixels, no real-game captures.");
 }
}
