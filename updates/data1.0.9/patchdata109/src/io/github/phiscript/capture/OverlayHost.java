package io.github.phiscript.capture;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.github.phiscript.input.TouchService;

/** The context, window token and window type must belong to the same live service. */
public final class OverlayHost {
    private OverlayHost(){}
    public static Context context(Context fallback){
        TouchService service=TouchService.Companion.getCurrent();
        return service!=null?service:fallback.getApplicationContext();
    }
    public static boolean allowed(Context host){return host instanceof AccessibilityService||Settings.canDrawOverlays(host);}
    public static int type(Context host){return host instanceof AccessibilityService?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;}
    public static void remove(WindowManager manager,View view){try{manager.removeViewImmediate(view);}catch(IllegalArgumentException detached){/* A service disconnect may already have detached it. */}}
    /** Reuse the capture service's same overlay object so masking and its stop callback stay valid. */
    public static void rebind(RecognitionOverlay overlay,Context host)throws Exception{
        Field context=RecognitionOverlay.class.getDeclaredField("context");context.setAccessible(true);
        if(context.get(overlay)==host)return;
        overlay.hide();
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Exception> failure=new AtomicReference<>();
        Runnable action=()->{try{
            Field manager=RecognitionOverlay.class.getDeclaredField("manager");manager.setAccessible(true);
            context.set(overlay,host);manager.set(overlay,host.getSystemService(Context.WINDOW_SERVICE));
        }catch(Exception e){failure.set(e);}finally{done.countDown();}};
        if(Looper.myLooper()==Looper.getMainLooper())action.run();else new Handler(Looper.getMainLooper()).post(action);
        if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("控制窗重连超时，请重新开始打谱");
        if(failure.get()!=null)throw new IllegalStateException("控制窗重连失败",failure.get());
    }
}
