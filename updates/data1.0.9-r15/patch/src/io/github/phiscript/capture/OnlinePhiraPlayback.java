package io.github.phiscript.capture;

import android.content.Context;
import io.github.phiscript.*;
import io.github.phiscript.assets.*;
import io.github.phiscript.input.TouchService;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native capture bridge. The retained Phira engine owns confirmation and retry. */
public final class OnlinePhiraPlayback {
    public static boolean shouldAllowWithoutLibrary(Context context){return new AppSettings(context).flag("phira_online_library",false);}
    public static void run(FrameStore frames,SessionSettings settings,boolean preview,AtomicBoolean stop,RecognitionOverlay overlay,Context context)throws Exception{
        PhiraLibrary library=RuntimeState.INSTANCE.getPhiraLibrary();
        if(!shouldAllowWithoutLibrary(context)){new PhiraSessionEngine(frames,library,settings,preview,stop,overlay).run();return;}
        TouchService service=TouchService.Companion.getCurrent();if(service==null)throw new IllegalStateException("无障碍服务尚未连接");
        RuntimeState.INSTANCE.log("Phira 在线谱库：先识别曲名与难度，唯一匹配后下载导入；有重名歧义时请按编号导入");
        SessionChartSource source=SessionChartSource.phiraOnline(context,library,stop);
        Constructor<PhiraSessionEngine> constructor=PhiraSessionEngine.class.getDeclaredConstructor(FrameStore.class,SessionChartSource.class,SessionSettings.class,boolean.class,AtomicBoolean.class,RecognitionOverlay.class);
        try{constructor.newInstance(frames,source,settings,preview,stop,overlay).run();}
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw error;}
    }
}
