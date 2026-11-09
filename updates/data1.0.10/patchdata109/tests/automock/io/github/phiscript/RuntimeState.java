package io.github.phiscript;
import io.github.phiscript.assets.ChartLibrary;import java.util.concurrent.atomic.*;
public final class RuntimeState {
 public static final RuntimeState INSTANCE=new RuntimeState();public final AtomicReference<ActiveTouch> active=new AtomicReference<>();
 public final ChartLibrary library=new ChartLibrary();public ChartLibrary getLibrary(){return library;}public AtomicReference<ActiveTouch> getActiveTouchStop(){return active;}public void log(String s){if(Boolean.getBoolean("phi.test.verbose"))System.out.println(s);}
 public static final class ActiveTouch {public ActiveTouch(AtomicBoolean owner,AtomicBoolean cancel){}}
}
