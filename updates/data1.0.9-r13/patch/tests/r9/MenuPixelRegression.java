package io.github.phiscript.capture;

import android.content.Context;import android.graphics.*;import android.net.Uri;
import io.github.phiscript.*;import io.github.phiscript.assets.*;import io.github.phiscript.engine.*;import io.github.phiscript.root.ModeRouting;import io.github.phiscript.vision.*;
import javax.imageio.*;import javax.imageio.stream.ImageOutputStream;import java.awt.Graphics2D;import java.awt.RenderingHints;import java.awt.image.BufferedImage;
import java.io.*;import java.lang.reflect.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.zip.*;import org.json.*;

/** Real user pixels, real final-APK menu adapter, and actual shared-engine coordinate getters. */
public final class MenuPixelRegression {
 static int checks,positive,negative;static Context context;static PhiraLibrary phira;static ChartLibrary phigros;
 static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 interface Task{void run()throws Exception;}static void fails(Task task,String message)throws Exception{boolean failed=false;try{task.run();}catch(Exception expected){failed=true;}ok(failed,message);}
 static Bitmap bitmap(BufferedImage image){int w=image.getWidth(),h=image.getHeight();return new Bitmap(w,h,image.getRGB(0,0,w,h,null,0,w));}
 static BufferedImage picture(Bitmap bitmap){BufferedImage out=new BufferedImage(bitmap.width,bitmap.height,BufferedImage.TYPE_INT_RGB);out.setRGB(0,0,bitmap.width,bitmap.height,bitmap.pixels,0,bitmap.width);return out;}
 static Bitmap copy(Bitmap bitmap){return new Bitmap(bitmap.width,bitmap.height,bitmap.pixels.clone());}
 static Bitmap resized(Bitmap source,int width){int height=Math.round(source.height*(width/(float)source.width));BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(picture(source),0,0,width,height,null);g.dispose();return bitmap(image);}
 static Bitmap exposure(Bitmap source,double scale,boolean invert){Bitmap out=copy(source);for(int i=0;i<out.pixels.length;i++){int c=out.pixels[i],r=c>>>16&255,g=c>>>8&255,b=c&255;if(invert){r=255-r;g=255-g;b=255-b;}r=Math.min(255,(int)Math.round(r*scale));g=Math.min(255,(int)Math.round(g*scale));b=Math.min(255,(int)Math.round(b*scale));out.pixels[i]=0xff000000|r<<16|g<<8|b;}return out;}
 static Bitmap jpeg(Bitmap source,float quality)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();ImageWriter writer=ImageIO.getImageWritersByFormatName("jpeg").next();try(ImageOutputStream stream=ImageIO.createImageOutputStream(out)){writer.setOutput(stream);ImageWriteParam param=writer.getDefaultWriteParam();param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);param.setCompressionQuality(quality);writer.write(null,new IIOImage(picture(source),null,null),param);}finally{writer.dispose();}return bitmap(ImageIO.read(new ByteArrayInputStream(out.toByteArray())));}
 static void fill(Bitmap b,int left,int top,int right,int bottom,int color){for(int y=Math.max(0,top);y<Math.min(b.height,bottom);y++)for(int x=Math.max(0,left);x<Math.min(b.width,right);x++)b.setPixel(x,y,color);}
 static Bitmap shifted(Bitmap source,int width,int height,int left,int top){Bitmap out=new Bitmap(width,height);Arrays.fill(out.pixels,0xff202020);for(int y=0;y<source.height;y++)for(int x=0;x<source.width;x++)if(x+left>=0&&x+left<width&&y+top>=0&&y+top<height)out.setPixel(x+left,y+top,source.getPixel(x,y));return out;}
 static Bitmap iconRowTwice(Bitmap source){Bitmap out=new Bitmap(2200,900);Arrays.fill(out.pixels,0xff303030);for(int at:new int[]{480,1380})for(int y=312;y<386;y++)for(int x=600;x<942;x++)out.setPixel(at+x-600,405+y-312,source.getPixel(x,y));return out;}
 static final class Env {
  final Object engine,selection;final Method factory,active,roi,viewport;final boolean pgr;
  Env(boolean pgr,int width,int height,NormalizedPoint configured)throws Exception{
   this.pgr=pgr;ModeRouting.select(new AppSettings(context),pgr?0:1);SessionChartSource source=pgr?SessionChartSource.phigros(context,phigros,new AtomicBoolean()):SessionChartSource.phira(phira);
   SessionSettings settings=new SessionSettings(new RectF(0,0,1,1),new RectF(0,0,1,1),new RectF(0,0,1,1),0,0,new RectF(0,0,.18f,.24f),120,new NormalizedPoint(.5f,.5f),200,pgr?PlayMode.VISUAL:PlayMode.PHIRA,configured,700,0,new RectF(0,0,.18f,.24f),true,"");
   Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");Field one=unsafeClass.getDeclaredField("theUnsafe");one.setAccessible(true);Object unsafe=one.get(null);RecognitionOverlay overlay=(RecognitionOverlay)unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafe,RecognitionOverlay.class);
   Constructor<?> constructor=PhiraSessionEngine.class.getDeclaredConstructor(FrameStore.class,SessionChartSource.class,SessionSettings.class,boolean.class,AtomicBoolean.class,RecognitionOverlay.class);
   engine=constructor.newInstance(new FrameStore(),source,settings,false,new AtomicBoolean(),overlay);
   PhiraEntry entry=source.getEntries().get(0);Class<?> selectionClass=Class.forName("io.github.phiscript.capture.PhiraSessionEngine$Selection");Constructor<?> select=selectionClass.getDeclaredConstructor(PhiraCandidate.class,int.class,int.class);select.setAccessible(true);selection=select.newInstance(new PhiraCandidate(entry.getId(),entry.getTitle(),entry.getLevel(),entry.getAliases()),width,height);
   factory=PhiraSessionEngine.class.getDeclaredMethod("pauseMenuFor",selectionClass);factory.setAccessible(true);active=PhiraSessionEngine.class.getDeclaredMethod("activeRetryPoint");active.setAccessible(true);roi=PhiraSessionEngine.class.getDeclaredMethod("pauseRoiFor",selectionClass);roi.setAccessible(true);viewport=PhiraSessionEngine.class.getDeclaredMethod("viewportFor",selectionClass);viewport.setAccessible(true);
  }
  SessionPauseMenu menu()throws Exception{SessionPauseMenu menu=(SessionPauseMenu)factory.invoke(engine,selection);Field field=PhiraSessionEngine.class.getDeclaredField("pauseMenu");field.setAccessible(true);field.set(engine,menu);return menu;}
  NormalizedPoint point()throws Exception{return (NormalizedPoint)active.invoke(engine);}
 }
 static void hit(Env env,Bitmap image,float x,float y,String name)throws Exception{SessionPauseMenu menu=env.menu();ok(menu.visible(image),"real pixels not recognized: "+name);NormalizedPoint measured=env.point();ok(Math.abs(measured.getX()-x)<.009&&Math.abs(measured.getY()-y)<.012,"wrong physical retry point: "+name+" "+measured);ok(menu.visible(image),"stable menu rejected: "+name);positive++;}
 static void miss(Env env,Bitmap image,String name)throws Exception{SessionPauseMenu menu=env.menu();ok(!menu.visible(image),"non-menu accepted: "+name);fails(env::point,"failed detection must not retain a retry coordinate: "+name);negative++;}
 static Bitmap phiraImage(){Bitmap b=new Bitmap(1280,720);Arrays.fill(b.pixels,0xff151515);float width=1280,spacing=width*.085f,size=width*.06f;
  for(int k=0;k<3;k++){float square=k==1?size*4/3:size,cx=640+(k-1)*spacing,cy=360;for(int y=(int)(cy-square/2)-1;y<cy+square/2+1;y++)for(int x=(int)(cx-square/2)-1;x<cx+square/2+1;x++){
   double u=(x-cx)/square+.5,v=(y-cy)/square+.5,r=Math.hypot(u-.5,v-.5);boolean on=k==0?v>=.085&&v<=.915&&Math.abs(u-(.34+Math.abs(v-.5)))<=.085:k==1?r>=.22&&r<=.36&&!(u>.5&&v<.5):u>=.23&&u<=.83&&Math.abs(v-.5)<=.36*(.83-u)/.60;if(on)b.setPixel(x,y,0xffeeeeee);
  }}return b;
 }
 static void prepareLibraries()throws Exception{
  context=new Context();Context.root=Files.createTempDirectory("menu-real-pixels").toFile();
  String chart=new String(Files.readAllBytes(Paths.get("upstream/updates/data1.0.9/patch/tests/fixtures/pgr_v3_multihold.json")),StandardCharsets.UTF_8);File file=File.createTempFile("menu-",".pez",context.getCacheDir());try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){zip.putNextEntry(new ZipEntry("chart.json"));zip.write(chart.getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry("info.txt"));zip.write("#\nName: Menu Test\nLevel: IN\nChart: chart.json\n".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
  phira=PhiraLibrary.Companion.importFiles(context,Collections.singletonList(Uri.fromFile(file)),s->kotlin.Unit.INSTANCE);
  JSONObject catalog=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/assets/"+FeijipanSource.NEW_ASSET)),StandardCharsets.UTF_8));catalog.put("entries",new JSONArray().put(catalog.getJSONArray("entries").getJSONObject(0)));phigros=ExternalCharts.fromCatalog(context,catalog);
 }
 public static void main(String[] args)throws Exception{
  prepareLibraries();Bitmap photo=bitmap(ImageIO.read(new File("patch/tests/r9/fixtures/phigros-pause-menu.jpg")));ok(photo.width==1536&&photo.height==698,"original user image dimensions");
  boolean old=new PhiraPauseDetector(new RectF(0,0,1,1),new NormalizedPoint(.5f,.5f)).visible(photo);ok(!old,"R8 Phira template mismatch must reproduce on real screenshot");
  Env pgr=new Env(true,1536,698,new NormalizedPoint(.13f,.27f));hit(pgr,photo,.5f,.5f,"original JPEG despite wrong Phira retry setting");
  for(int width:new int[]{768,1024,1280,1920,2400})hit(pgr,resized(photo,width),.5f,.5f,"resize "+width);
  for(double level:new double[]{.70,.85,1.15})hit(pgr,exposure(photo,level,false),.5f,.5f,"exposure "+level);
  hit(pgr,exposure(photo,1,true),.5f,.5f,"inverted contrast");for(float quality:new float[]{.40f,.65f,.85f})hit(pgr,jpeg(photo,quality),.5f,.5f,"JPEG quality "+quality);
  Bitmap shifted=shifted(photo,2200,1000,520,42);hit(pgr,shifted,(768+520f)/2200,(349+42f)/1000,"off-center physical controls");
  for(int k=0;k<3;k++){Bitmap missing=copy(photo);int cx=k==0?623:k==1?768:909;fill(missing,cx-36,312,cx+36,386,0xff202020);miss(pgr,missing,"missing control "+k);}
  Bitmap swapped=copy(photo);for(int y=315;y<383;y++)for(int x=0;x<64;x++){swapped.setPixel(591+x,y,photo.getPixel(877+x,y));swapped.setPixel(877+x,y,photo.getPixel(591+x,y));}miss(pgr,swapped,"back and continue exchanged");
  Bitmap background=copy(photo);fill(background,590,310,945,389,0xff202020);miss(pgr,background,"background without icons");miss(pgr,new Bitmap(1536,698),"black frame");Bitmap bright=new Bitmap(1536,698);Arrays.fill(bright.pixels,0xffffffff);miss(pgr,bright,"white frame");miss(pgr,new Bitmap(200,400),"portrait frame");miss(pgr,iconRowTwice(photo),"two different complete menu rows");
  SessionPauseMenu unstable=pgr.menu();ok(unstable.visible(photo),"stability first observation");ok(!unstable.visible(shifted),"moving menu resets visual evidence");fails(pgr::point,"moving target is not clickable");ok(unstable.visible(shifted),"steady new position recovers");ok(!unstable.visible(background),"later miss clears target");fails(pgr::point,"miss cannot reuse old retry point");
  RectF physical=(RectF)pgr.roi.invoke(pgr.engine,pgr.selection);ok(physical.left==0&&physical.top==0&&Math.abs(physical.right-.18f)<.001,"Phigros pause ROI remains physical on wide screen");
  Env phi=new Env(false,1280,720,new NormalizedPoint(.5f,.5f));Bitmap reference=phiraImage();PhiraPauseDetector legacy=new PhiraPauseDetector(new RectF(0,0,1,1),new NormalizedPoint(.5f,.5f));ok(legacy.visible(reference),"positive original Phira glyph fixture");SessionPauseMenu original=phi.menu();ok(original.visible(reference)==legacy.visible(reference),"Phira delegates original detector unchanged");ok(phi.point().getX()==.5f&&phi.point().getY()==.5f,"Phira retains configured restart point");
  Env widePhi=new Env(false,2400,1080,new NormalizedPoint(.5f,.5f));RectF mapped=(RectF)widePhi.roi.invoke(widePhi.engine,widePhi.selection);Viewport viewport=(Viewport)widePhi.viewport.invoke(widePhi.engine,widePhi.selection);ok(Math.abs(mapped.left-viewport.getLeft()/2400)<.0001,"Phira keeps its original viewport-relative pause ROI");
  System.out.println("PASS actual menu pixels: "+checks+" assertions, "+positive+" real-image positive variants, "+negative+" negative scenes; original R8 mismatch reproduced, measured retry point and Phira behavior verified");
 }
}
