package io.github.phiscript.capture;
import java.awt.image.BufferedImage;import javax.imageio.ImageIO;import java.io.File;import android.graphics.Bitmap;import io.github.phiscript.SessionSettings;
public final class PhigrosPhotoRegression {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static Bitmap load(String path)throws Exception {BufferedImage im=ImageIO.read(new File(path));Bitmap b=new Bitmap(im.getWidth(),im.getHeight());for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++)b.setPixel(x,y,im.getRGB(x,y));return b;}
 static Bitmap scale(Bitmap source,float factor){int w=Math.round(source.getWidth()*factor),h=Math.round(source.getHeight()*factor);Bitmap b=new Bitmap(w,h);for(int y=0;y<h;y++)for(int x=0;x<w;x++)b.setPixel(x,y,source.getPixel(Math.min(source.getWidth()-1,(int)(x/factor)),Math.min(source.getHeight()-1,(int)(y/factor))));return b;}
 static Bitmap missing(Bitmap source,int icon){Bitmap b=scale(source,1);float cx=new float[]{.406f,.5f,.592f}[icon];for(int y=(int)(b.getHeight()*.44);y<b.getHeight()*.56;y++)for(int x=(int)(b.getWidth()*(cx-.028));x<b.getWidth()*(cx+.028);x++)b.setPixel(x,y,0xff303038);return b;}
 public static void main(String[] args)throws Exception {
  Bitmap originalGame=load(args[0]),originalMenu=load(args[1]);
  for(float scale:new float[]{.5f,.75f,1,1.5f}){
   Bitmap game=scale(originalGame,scale),menu=scale(originalMenu,scale);PhigrosControls c=new PhigrosControls(new SessionSettings(),originalGame.getWidth(),originalGame.getHeight());
   ok(c.hud(game),"real game HUD missed at scale "+scale);ok(!c.hud(menu),"paused photo classified as game");ok(!c.menu(game),"game photo classified as menu");ok(c.menu(menu),"real three-icon menu missed at scale "+scale);
   ok(Math.abs(c.retry().getX()-.5f)<.015&&Math.abs(c.retry().getY()-.5f)<.02,"retry coordinate not measured");
   if(scale>=.75f){ok(Math.abs(c.viewport().getLeft()-147)<5&&Math.abs(c.viewport().getWidth()-1242)<8,"pillarbox/physical-screen mapping failed");}
   ok(c.pause(game,1000)==null&&c.pause(game,1080)==null,"single/stale frame authorized pause");io.github.phiscript.vision.NormalizedPoint p=c.pause(game,1160);
   ok(p!=null&&Math.abs(p.getX()-.118f)<.012&&Math.abs(p.getY()-.055f)<.015,"pause tap not on measured physical icon");
   for(int k=0;k<3;k++)ok(!c.menu(missing(menu,k)),"missing real menu icon accepted: "+k);
  }
  System.out.println("PASS "+checks+" checks on both user-provided Phigros JPEGs: actual thin retry ring, back/play row, measured pause/retry, scaled captures mapped to physical screen, observed pillarbox and missing-button rejection. Static screenshot tests; no device input/timing verification.");
 }
}
