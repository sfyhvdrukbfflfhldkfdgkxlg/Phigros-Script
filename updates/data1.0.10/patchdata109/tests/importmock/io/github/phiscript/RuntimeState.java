package io.github.phiscript;
import io.github.phiscript.assets.PhiraLibrary;import java.util.concurrent.atomic.AtomicBoolean;
public final class RuntimeState {
 public static final RuntimeState INSTANCE=new RuntimeState();public static java.util.function.Consumer<String> onLog;
 private PhiraLibrary library;private final AtomicBoolean running=new AtomicBoolean();
 public PhiraLibrary getPhiraLibrary(){return library;}
 public void setPhiraLibrary(PhiraLibrary value){library=value;}
 public AtomicBoolean getRunning(){return running;}
 public void log(String text){if(Boolean.getBoolean("phi.test.verbose"))System.err.println(text);if(onLog!=null)onLog.accept(text);}
}
