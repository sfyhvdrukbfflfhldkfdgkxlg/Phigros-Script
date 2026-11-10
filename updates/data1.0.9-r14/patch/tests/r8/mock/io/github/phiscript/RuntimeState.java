package io.github.phiscript;
import io.github.phiscript.assets.*;import java.util.*;import java.util.concurrent.atomic.*;
public final class RuntimeState {
 public static final RuntimeState INSTANCE=new RuntimeState();public static final List<String> logs=new ArrayList<>();
 public static final class ActiveTouch{private final AtomicBoolean owner,cancel;public ActiveTouch(AtomicBoolean owner,AtomicBoolean cancel){this.owner=owner;this.cancel=cancel;}public AtomicBoolean getOwner(){return owner;}public AtomicBoolean getCancel(){return cancel;}}
 private final AtomicBoolean running=new AtomicBoolean();private final AtomicReference<ActiveTouch> active=new AtomicReference<>();private ChartLibrary library;private PhiraLibrary phira;
 public AtomicBoolean getRunning(){return running;}public AtomicReference<ActiveTouch> getActiveTouchStop(){return active;}
 public void log(String text){logs.add(text);}public ChartLibrary getLibrary(){return library;}public void setLibrary(ChartLibrary library){this.library=library;}public PhiraLibrary getPhiraLibrary(){return phira;}public void setPhiraLibrary(PhiraLibrary value){phira=value;}
}
