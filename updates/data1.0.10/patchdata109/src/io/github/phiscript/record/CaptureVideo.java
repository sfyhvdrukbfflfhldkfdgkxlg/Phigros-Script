package io.github.phiscript.record;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** One authorized display, with independent GPU outputs for OCR and 1080p60 video. */
public final class CaptureVideo {
    private static Context sourceOwner;
    private static ProjectionVideoBridge bridge;
    private static boolean requested;
    private static HandlerThread imageThread;
    private static Handler imageHandler;
    private static final Object readerLock=new Object();
    private static final IdentityHashMap<ImageReader,Handler> readerOwners=new IdentityHashMap<>();
    private static final Set<ImageReader> closingReaders=Collections.newSetFromMap(new IdentityHashMap<ImageReader,Boolean>());
    private CaptureVideo(){}
    public static synchronized void begin(Context c){
        stop(c);if(new AppSettings(c).flag("recording_on_play",true)){
            sourceOwner=c;imageThread=new HandlerThread("phi-capture-images");imageThread.start();imageHandler=new Handler(imageThread.getLooper());
        }
    }
    public static synchronized void playing(){
        requested=true;
        if(sourceOwner!=null&&bridge!=null)bridge.startRecording(sourceOwner.getApplicationContext());
    }
    /** Retained cross-DEX ABI. No scaling or Bitmap copying runs on the capture thread. */
    public static void frame(Context owner,Bitmap source,long captured){}
    public static synchronized void finished(){requested=false;if(bridge!=null)bridge.finishRecording();}
    public static synchronized void stop(Context owner){
        requested=false;sourceOwner=null;ProjectionVideoBridge old=bridge;bridge=null;if(old!=null)old.close();
        HandlerThread images=imageThread;Handler consumer=imageHandler;imageThread=null;imageHandler=null;
        if(images!=null){
            ImageReader[] pending;synchronized(readerLock){pending=readerOwners.keySet().toArray(new ImageReader[0]);}
            for(ImageReader reader:pending){Handler ownerHandler;synchronized(readerLock){ownerHandler=readerOwners.get(reader);}if(ownerHandler==consumer)closeReader(reader);}
            images.quitSafely();
        }
    }
    public static synchronized VirtualDisplay createVirtualDisplay(MediaProjection projection,String name,int width,int height,int density,int flags,Surface sink,VirtualDisplay.Callback callback,Handler handler){
        if(sourceOwner==null)return projection.createVirtualDisplay(name,width,height,density,flags,sink,callback,handler);
        ProjectionVideoBridge next=null;
        try{
            next=new ProjectionVideoBridge(width,height,sink);
            int[] size=RecordingPolicy.captureSize(width,height);
            VirtualDisplay result=projection.createVirtualDisplay(name,size[0],size[1],density,flags,next.inputSurface(),callback,handler);
            if(result==null)throw new IllegalStateException("屏幕捕获虚拟显示不可用");
            next.display(result,density);bridge=next;
            if(requested)next.startRecording(sourceOwner.getApplicationContext());
            return result;
        }catch(RuntimeException e){
            if(next!=null)next.close();
            RuntimeState.INSTANCE.log("1080p60 录屏准备失败，保留打谱截图："+e.getMessage());
            // No virtual display was created on initialization failure. Do not consume
            // an Android 14 consent token a second time after createVirtualDisplay failed.
            if(next!=null&&next.displayWasAttempted())throw e;
            return projection.createVirtualDisplay(name,width,height,density,flags,sink,callback,handler);
        }
    }
    public static synchronized void resize(VirtualDisplay display,int width,int height,int density){
        if(bridge!=null&&bridge.owns(display)){bridge.resize(width,height,density);return;}
        display.resize(width,height,density);
    }
    public static synchronized void setSurface(VirtualDisplay display,Surface sink){
        if(bridge!=null&&bridge.owns(display)){bridge.sink(sink);return;}
        display.setSurface(sink);
    }
    /** Image consumption must not wait behind configure(), which can wait for GL. */
    public static synchronized void setReaderListener(ImageReader reader,ImageReader.OnImageAvailableListener listener,Handler original){
        if(imageHandler!=null&&listener!=null)synchronized(readerLock){readerOwners.put(reader,imageHandler);}
        reader.setOnImageAvailableListener(listener,imageHandler==null?original:imageHandler);
    }
    public static synchronized void closeReader(ImageReader reader){
        Handler images;synchronized(readerLock){
            if(closingReaders.contains(reader))return;
            images=readerOwners.get(reader);if(images!=null)closingReaders.add(reader);
        }
        if(images==null){reader.close();return;}
        Runnable cleanup=()->{
            try{reader.setOnImageAvailableListener(null,null);reader.close();}
            finally{synchronized(readerLock){readerOwners.remove(reader);closingReaders.remove(reader);}}
        };
        if(!images.post(cleanup))cleanup.run();
    }
}
