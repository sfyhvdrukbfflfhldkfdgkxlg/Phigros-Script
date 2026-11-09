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
        if(new AppSettings(context).flag("phigros_auto_mode",false)){
            SessionFeedback.clear(context);
            try{
                while(!stop.get()){
                    TouchService service=TouchService.Companion.getCurrent();long deadline=SystemClock.uptimeMillis()+20000;
                    if(service==null)SessionFeedback.stage(context,"等待无障碍服务重连；连接后自动继续检测");
                    while(service==null&&!stop.get()&&!Thread.currentThread().isInterrupted()&&SystemClock.uptimeMillis()<deadline){Thread.sleep(200);service=TouchService.Companion.getCurrent();}
                    if(stop.get()||Thread.currentThread().isInterrupted())throw new InterruptedException();
                    if(service==null)throw new IllegalStateException("无障碍服务尚未连接，请在权限设置中重新启用；Root 模式同样需要它确认游戏前台");
                    OverlayHost.rebind(overlay,service);
                    try{new PhigrosAutoEngine(service,frames,settings,preview,stop,overlay).run();return;}
                    catch(PhigrosAutoEngine.ServiceChanged reconnect){SessionFeedback.stage(context,"无障碍服务已重新绑定，正在恢复控制窗和自动检测");}
                }
            }
            catch(InterruptedException e){throw e;}
            catch(LinkageError e){if(!stop.get())SessionFeedback.failed(context,e);throw new IllegalStateException("设备兼容错误："+e.getClass().getSimpleName(),e);}
            catch(Exception e){if(!stop.get())SessionFeedback.failed(context,e);throw e;}
        }else new SessionEngine(frames,settings,preview,stop,overlay).run();
    }
}
