package io.github.phiscript.capture;

import io.github.phiscript.RuntimeState;
import io.github.phiscript.SessionSettings;
import io.github.phiscript.assets.SessionChartSource;
import io.github.phiscript.input.TouchService;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Phigros supplies chart data; PhiraSessionEngine owns the complete session. */
public final class PhigrosAutoEngine {
    private final PhiraSessionEngine engine;
    // Kept for binary compatibility with the original capture classes.
    public static final class ServiceChanged extends IllegalStateException {public ServiceChanged(){super("无障碍连接已变化");}}
    public PhigrosAutoEngine(TouchService service,FrameStore frames,SessionSettings settings,boolean preview,AtomicBoolean stop,RecognitionOverlay overlay)throws Exception{
        service.selectGame(TouchService.GAME_PACKAGE);
        SessionChartSource source=SessionChartSource.phigros(service,RuntimeState.INSTANCE.getLibrary(),stop);
        Constructor<PhiraSessionEngine> constructor=PhiraSessionEngine.class.getDeclaredConstructor(FrameStore.class,SessionChartSource.class,SessionSettings.class,boolean.class,AtomicBoolean.class,RecognitionOverlay.class);
        try{engine=constructor.newInstance(frames,source,settings,preview,stop,overlay);}
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw error;}
    }
    public void run(){engine.run();}
}
