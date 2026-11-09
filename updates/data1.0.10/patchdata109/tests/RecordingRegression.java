package io.github.phiscript.record;
import android.content.Context;import android.graphics.Bitmap;import android.hardware.display.VirtualDisplay;import android.media.ImageReader;import android.media.projection.MediaProjection;import android.os.Handler;import android.os.Looper;import android.view.Surface;import io.github.phiscript.AppSettings;
/** Policy plus the actual CaptureVideo token/reader lifecycle, on bounded host fakes. */
public final class RecordingRegression {
 static int checks;static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void videoSize(int w,int h,int a,int b){int[] size=RecordingPolicy.size(w,h);ok(size[0]==a&&size[1]==b,"wrong output dimensions");}
 static VirtualDisplay create(MediaProjection p){return CaptureVideo.createVirtualDisplay(p,"test",1280,720,320,16,new Surface(),null,new Handler(new Looper()));}
 public static void main(String[] args)throws Exception{
  videoSize(2400,1080,1920,1080);videoSize(1080,2400,1080,1920);videoSize(1280,720,1920,1080);videoSize(720,1280,1080,1920);
  int[] capture=RecordingPolicy.captureSize(2400,1080);ok(capture[0]==2400&&capture[1]==1080,"wide display aspect ratio lost");
  capture=RecordingPolicy.captureSize(720,1280);ok(capture[0]==1080&&capture[1]==1920,"portrait capture dimensions");
  boolean invalid=false;try{RecordingPolicy.size(0,1080);}catch(IllegalArgumentException expected){invalid=true;}ok(invalid,"invalid screen dimensions accepted");
  ok(RecordingPolicy.FPS==60&&RecordingPolicy.BIT_RATE>=12000000,"60fps quality policy");
  long last=-1;for(int i=0;i<=216000;i++){long at=RecordingPolicy.frameTimeNs(i);ok(at>last,"frame timestamp went backwards");if(i>0)ok(at-last==16666666L||at-last==16666667L,"frame interval rounded to 16/17ms");ok(RecordingPolicy.frameDueMs(i)*1000000L>=at&&RecordingPolicy.frameDueMs(i)*1000000L-at<1000000L,"scheduler misses fractional deadline");if(i%60==0)ok(at==(long)(i/60)*1000000000L,"long recording time drift");last=at;}
  ok(last==3600000000000L,"one hour duration drift");
  Context owner=new Context();AppSettings.enabled=false;CaptureVideo.begin(owner);MediaProjection direct=new MediaProjection();VirtualDisplay d=create(direct);ok(direct.calls==1&&direct.width==1280&&direct.height==720,"record-disabled capture changed");CaptureVideo.stop(owner);
  AppSettings.enabled=true;CaptureVideo.begin(owner);Handler original=new Handler(new Looper());ImageReader reader=new ImageReader();CaptureVideo.setReaderListener(reader,r->{},original);ok(reader.handler!=original,"OCR consumer shares configure handler");
  MediaProjection p=new MediaProjection();d=create(p);ok(p.calls==1&&p.width==1920&&p.height==1080,"capture did not use one high resolution display");CaptureVideo.playing();ok(ProjectionVideoBridge.last.starts==1,"recording did not follow playback");CaptureVideo.frame(owner,new Bitmap(),1); // This fake has no Bitmap copying methods: the real method must be inert.
  ProjectionVideoBridge.last.closed=true;CaptureVideo.resize(d,1600,900,320);CaptureVideo.setSurface(d,new Surface());ok(d.resizes==1&&d.surfaces==1,"failed bridge still intercepts rotation");
  reader.working=true;reader.handler.post(()->reader.working=false);CaptureVideo.stop(owner);CaptureVideo.closeReader(reader);ok(reader.closes==0,"reader closed while callback owns pixels");Handler.drainAll();ok(reader.closes==1,"reader leaked or duplicate cleanup");
  CaptureVideo.begin(owner);ImageReader old=new ImageReader();CaptureVideo.setReaderListener(old,r->{},original);old.working=true;old.handler.post(()->old.working=false);CaptureVideo.begin(new Context());ImageReader next=new ImageReader();CaptureVideo.setReaderListener(next,r->{},original);CaptureVideo.closeReader(old);ok(old.closes==0&&next.closes==0,"old generation closed synchronously or touched next reader");Handler.drainAll();ok(old.closes==1&&next.closes==0,"generation cleanup lost ownership");CaptureVideo.stop(owner);Handler.drainAll();ok(next.closes==1,"last reader leaked on stop");
  CaptureVideo.begin(owner);ProjectionVideoBridge.failInitialize=true;MediaProjection failedInit=new MediaProjection();create(failedInit);ok(failedInit.calls==1&&failedInit.width==1280,"initialization failure did not preserve native capture");ProjectionVideoBridge.failInitialize=false;CaptureVideo.stop(owner);
  CaptureVideo.begin(owner);ImageReader independent=new ImageReader();CaptureVideo.setReaderListener(independent,r->{},original);CaptureVideo.closeReader(independent);
  java.util.concurrent.CountDownLatch cleaned=new java.util.concurrent.CountDownLatch(1);Thread imageCleanup=new Thread(()->{try{Handler.drainAll();}finally{cleaned.countDown();}});boolean independentCleanup;
  synchronized(CaptureVideo.class){imageCleanup.start();independentCleanup=cleaned.await(1,java.util.concurrent.TimeUnit.SECONDS);}
  imageCleanup.join(1000);ok(independentCleanup&&independent.closes==1,"image cleanup waits on configuration/GL monitor");CaptureVideo.stop(owner);Handler.drainAll();
  CaptureVideo.begin(owner);MediaProjection rejected=new MediaProjection();rejected.fail=true;boolean failure=false;try{create(rejected);}catch(IllegalStateException expected){failure=true;}ok(failure&&rejected.calls==1,"MediaProjection token consumed twice on create failure");CaptureVideo.stop(owner);Handler.drainAll();
  System.out.println("PASS "+checks+" recording checks: 1080p orientation/aspect, 60Hz one-hour timing, actual CaptureVideo single-token/fallback hooks, independent image consumption and generation-safe queued cleanup. Host model; no device encoder/GL/performance claim.");
 }
}
