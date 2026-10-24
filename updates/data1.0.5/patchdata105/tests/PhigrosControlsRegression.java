package io.github.phiscript.capture;
import android.graphics.Bitmap;import android.graphics.RectF;import io.github.phiscript.SessionSettings;import io.github.phiscript.vision.NormalizedPoint;
public final class PhigrosControlsRegression {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Bitmap picture(int w,int h,float cx,float cy,float gap,float unit,boolean invert,int missing,boolean swap){
  Bitmap b=new Bitmap(w,h);if(invert)for(int y=0;y<h;y++)for(int x=0;x<w;x++)b.setPixel(x,y,0xffeeeeee);
  for(int k=0;k<3;k++)if(k!=missing){int kind=swap?(k==0?2:k==2?0:1):k;float xx=(cx+(k-1)*gap)*w,yy=cy*h,size=unit*h;
   for(int y=(int)(yy-size/2)-1;y<=yy+size/2+1;y++)for(int x=(int)(xx-size/2)-1;x<=xx+size/2+1;x++){double u=(x-xx)/size+.5,v=(y-yy)/size+.5,r=Math.hypot(u-.5,v-.5);boolean fill=kind==0?v>=.085&&v<=.915&&Math.abs(u-(Math.abs(v-.5)+.34))<=.085:kind==1?r>=.22&&r<=.36&&(u<=.5||v>=.5):u>=.23&&u<=.83&&Math.abs(v-.5)<=(.83-u)*.6;if(fill)b.setPixel(x,y,invert?0xff151515:0xffeeeeee);}}
  return b;
 }
 static void scenario(int w,int h,float x,float y,float gap,float size,boolean invert){PhigrosControls c=new PhigrosControls(new SessionSettings(),w,h);ok(c.menu(picture(w,h,x,y,gap,size,invert,-1,false)),"adaptive menu missing");ok(Math.abs(c.retry().getX()-x)<.012&&Math.abs(c.retry().getY()-y)<.02,"retry not measured");for(int i=0;i<3;i++)ok(!c.menu(picture(w,h,x,y,gap,size,invert,i,false)),"incomplete menu accepted");ok(!c.menu(picture(w,h,x,y,gap,size,invert,-1,true)),"swapped controls accepted");ok(!c.menu(new Bitmap(w,h)),"blank accepted");}
 public static void main(String[] args){scenario(1280,720,.5f,.5f,.17f,.13f,false);scenario(2400,1080,.51f,.46f,.14f,.10f,false);scenario(2200,1000,.55f,.56f,.12f,.18f,true);scenario(1000,600,.48f,.44f,.12f,.09f,false);
  SessionSettings s=new SessionSettings();s.automatic=true;s.pause=new RectF(0,0,.18f,.24f);PhigrosControls c=new PhigrosControls(s,2400,1080);Bitmap game=new Bitmap(2400,1080);for(int y=38;y<83;y++)for(int x=31;x<61;x++)if(x<41||x>=51)game.setPixel(x,y,0xffeeeeee);
  ok(c.hud(game),"wide-screen physical pause missed");ok(c.pause(game,1000)==null,"one-frame pause");ok(c.pause(game,1080)==null,"two-frame pause");NormalizedPoint p=c.pause(game,1160);ok(p!=null&&p.getX()<.03f,"pause moved into Phira viewport");ok(c.pause(game,1160)==null,"duplicate frame");c.resetGame();ok(c.pause(game,2000)==null,"votes retained");ok(c.viewport().getLeft()==0&&c.viewport().getWidth()==2400,"Phira settings changed Phigros viewport");
  System.out.println("PASS "+checks+" Phigros pixel checks: physical wide-screen pause ROI, fresh frames, adaptive menu size/spacing/position/polarity, measured retry and incomplete/swapped/blank rejection. Synthetic pixels; no device run.");}
}
