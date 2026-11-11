package io.github.phiscript.capture;

import io.github.phiscript.AppSettings;
import io.github.phiscript.SessionSettings;
import io.github.phiscript.input.TouchService;
import android.content.Context;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;

/** The existing capture service owns projection, frames, notifications and stop behavior. */
public final class AutoSessionRouter {
    private final FrameStore frames;private final SessionSettings settings;
    private final boolean preview;private final AtomicBoolean stop;private final RecognitionOverlay overlay;
    private final Context context;
    public AutoSessionRouter(FrameStore frames,SessionSettings settings,boolean preview,AtomicBoolean stop,RecognitionOverlay overlay,Context context){
        this.frames=frames;this.settings=settings;this.preview=preview;this.stop=stop;this.overlay=overlay;this.context=context;
    }
    public void run()throws Exception{
        if(io.github.phiscript.root.ModeRouting.autoEngine(new AppSettings(context),settings.getMode())){
            SessionFeedback.clear(context);
            try{
                TouchService service=TouchService.Companion.getCurrent();
                if(service==null)throw new IllegalStateException("无障碍服务尚未连接，请重新启用后开始");
                OverlayHost.rebind(overlay,service);
                new PhigrosAutoEngine(service,frames,settings,preview,stop,overlay).run();
            }
            catch(InterruptedException e){throw e;}
            catch(LinkageError e){if(!stop.get())SessionFeedback.failed(context,e);throw new IllegalStateException("设备兼容错误："+e.getClass().getSimpleName(),e);}
            catch(Exception e){if(!stop.get())SessionFeedback.failed(context,e);throw e;}
        }else new SessionEngine(frames,settings,preview,stop,overlay).run();
    }
}
