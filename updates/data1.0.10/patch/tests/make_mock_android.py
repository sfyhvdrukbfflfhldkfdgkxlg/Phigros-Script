"""Small host-only input framework; validates Android continuation invariants.

These mocks are excluded from the APK. They do not replace device testing.
"""
from pathlib import Path

ROOT = Path(__file__).parent / "mocksrc"
FILES = {
"android/content/Context.java": """
package android.content; public class Context {}
""",
"android/graphics/Path.java": """
package android.graphics;
public class Path {
 public float firstX,firstY,lastX,lastY; public boolean empty=true;
 public final java.util.List<float[]> points=new java.util.ArrayList<>();
 public void moveTo(float x,float y){if(!empty)throw new AssertionError("multiple contours");firstX=lastX=x;firstY=lastY=y;empty=false;points.add(new float[]{x,y});}
 public void lineTo(float x,float y){lastX=x;lastY=y;points.add(new float[]{x,y});}
}
""",
"android/graphics/Rect.java": """
package android.graphics;
public class Rect {public int width(){return 1000;}public int height(){return 600;}}
""",
"android/os/SystemClock.java": """
package android.os;
public class SystemClock {
 public static long now=100000;
 public static long uptimeMillis(){return now++;}
 public static void sleep(long ms){now+=ms;}
}
""",
"android/os/Looper.java": """
package android.os;
public class Looper {static final Looper MAIN=new Looper(),WORKER=new Looper();
 public static Looper myLooper(){return WORKER;} public static Looper getMainLooper(){return MAIN;}public static void prepare(){}}
""",
"android/os/Handler.java": """
package android.os; public class Handler {public Handler(Looper l){}}
""",
"android/os/HandlerThread.java": """
package android.os; public class HandlerThread extends Thread {
 public HandlerThread(String s){}public synchronized void start(){}
 public Looper getLooper(){return Looper.WORKER;}public boolean quitSafely(){return true;}}
""",
"android/os/Build.java": """
package android.os; public class Build {public static class VERSION{public static int SDK_INT=35;}}
""",
"android/util/DisplayMetrics.java": """
package android.util; public class DisplayMetrics {public int widthPixels=1000,heightPixels=600;}
""",
"android/view/Display.java": """
package android.view; public class Display {public void getRealMetrics(android.util.DisplayMetrics d){}}
""",
"android/view/WindowMetrics.java": """
package android.view; public class WindowMetrics {public android.graphics.Rect getBounds(){return new android.graphics.Rect();}}
""",
"android/view/WindowManager.java": """
package android.view; public interface WindowManager {
 default WindowMetrics getMaximumWindowMetrics(){return new WindowMetrics();}
 default Display getDefaultDisplay(){return new Display();}}
""",
"android/accessibilityservice/GestureDescription.java": """
package android.accessibilityservice;
import android.graphics.Path;import java.util.*;
public final class GestureDescription {
 public static int maxStrokes=20; public final List<StrokeDescription> strokes;
 GestureDescription(List<StrokeDescription> s){strokes=new ArrayList<>(s);}
 public static int getMaxStrokeCount(){return maxStrokes;}
 public int getStrokeCount(){return strokes.size();}
 public StrokeDescription getStroke(int i){return strokes.get(i);}
 public static class StrokeDescription {
  static int next; public final int id=++next;public int parent=-1;
  public final Path path;public final long start,duration;public final boolean keep;
  public StrokeDescription(Path p,long s,long d,boolean k){if(p.empty||s<0||d<=0)throw new AssertionError("invalid stroke");path=p;start=s;duration=d;keep=k;}
  public long getStartTime(){return start;}public long getDuration(){return duration;}
  public Path getPath(){return path;}public boolean willContinue(){return keep;}
  public StrokeDescription continueStroke(Path p,long s,long d,boolean k){
   if(!keep)throw new AssertionError("continued released pointer");
   if(p.firstX!=path.lastX||p.firstY!=path.lastY)throw new AssertionError("continuation jumped");
   StrokeDescription r=new StrokeDescription(p,s,d,k);r.parent=id;return r;}
 }
 public static class Builder {
  final List<StrokeDescription> s=new ArrayList<>();
  public Builder addStroke(StrokeDescription d){s.add(d);if(s.size()>maxStrokes)throw new AssertionError("stroke capacity exceeded");return this;}
  public GestureDescription build(){if(s.isEmpty())throw new AssertionError("empty gesture");return new GestureDescription(s);}
 }
}
""",
"android/accessibilityservice/AccessibilityService.java": """
package android.accessibilityservice;
import android.os.*;import java.util.*;
public class AccessibilityService extends android.content.Context {
 public final aosp.audit.AospInputAudit audit=new aosp.audit.AospInputAudit();
 public boolean frameworkFailed;
 public int sends,cancelAt=-1,rejectAt=-1,peak; public boolean foreground=true;
 public String cancellationReason;
 public final Map<Integer,Integer> roots=new LinkedHashMap<>();
 public final List<long[]> downs=new ArrayList<>(),ups=new ArrayList<>();
 public final List<GestureDescription> history=new ArrayList<>();
 public java.util.function.Consumer<GestureDescription> after;
 public <T> T getSystemService(Class<T> c){return c.cast(new android.view.WindowManager(){});}
 public boolean dispatchGesture(GestureDescription g,GestureResultCallback cb,Handler h){
  sends++;history.add(g);if(sends==rejectAt){return false;}
  if(frameworkFailed){cb.onCancelled(g);return true;}
  if(sends==cancelAt){roots.clear();audit.cancel();cb.onCancelled(g);return true;}
  try{audit.dispatch(g);}catch(AssertionError e){frameworkFailed=true;roots.clear();audit.cancel();throw e;}
  // AOSP prepareToContinueOldGesture compares the FIRST sampled step with
  // existing pointers. Additional DOWNs at offset zero fail that handshake.
  if(!roots.isEmpty())for(GestureDescription.StrokeDescription s:g.strokes)
   if(s.parent<0&&s.start==0){cancellationReason="extra pointer in continuation's first step";roots.clear();cb.onCancelled(g);return true;}
  // A stationary continuation with no DOWN/UP/MOVE yields an empty event
  // list in MotionEventInjector, which reports cancellation to the app.
  boolean event=false;
  for(GestureDescription.StrokeDescription s:g.strokes)
   if(s.parent<0||!s.keep||Math.round(s.path.firstX)!=Math.round(s.path.lastX)||Math.round(s.path.firstY)!=Math.round(s.path.lastY))event=true;
  if(!event){cancellationReason="stationary continuation produced no MotionEvent";roots.clear();cb.onCancelled(g);return true;}
  Map<Integer,Integer> next=new LinkedHashMap<>();Set<Integer> continued=new HashSet<>();
  List<long[]> changes=new ArrayList<>();int fingers=roots.size();long duration=0,base=SystemClock.now;
  for(GestureDescription.StrokeDescription s:g.strokes){
   int root;
   if(s.parent>=0){if(!roots.containsKey(s.parent)||!continued.add(s.parent))throw new AssertionError("unknown/duplicate continuation");root=roots.get(s.parent);}
   else{root=s.id;downs.add(new long[]{root,base+s.start,Float.floatToIntBits(s.path.firstX),Float.floatToIntBits(s.path.firstY)});changes.add(new long[]{s.start,1});}
   if(!s.keep){ups.add(new long[]{root,base+s.start+s.duration});changes.add(new long[]{s.start+s.duration,-1});}
   else next.put(s.id,root);
   if(s.path.firstX<0||s.path.firstX>=1000||s.path.lastX<0||s.path.lastX>=1000||s.path.firstY<0||s.path.firstY>=600||s.path.lastY<0||s.path.lastY>=600)throw new AssertionError("offscreen stroke");
   duration=Math.max(duration,s.start+s.duration);
  }
  if(!continued.equals(roots.keySet()))throw new AssertionError("an existing hold was omitted");
  changes.sort((a,b)->a[0]!=b[0]?Long.compare(a[0],b[0]):Long.compare(a[1],b[1]));
  for(long[] c:changes){fingers+=c[1];peak=Math.max(peak,fingers);if(fingers>10||fingers<0)throw new AssertionError("physical finger limit: "+fingers);}
  roots.clear();roots.putAll(next);SystemClock.now+=duration+2;
  if(after!=null)after.accept(g);cb.onCompleted(g);return true;
 }
 public static class GestureResultCallback {public void onCompleted(GestureDescription g){}public void onCancelled(GestureDescription g){}}
}
""",
"io/github/phiscript/input/TouchService.java": """
package io.github.phiscript.input; public class TouchService extends android.accessibilityservice.AccessibilityService {public boolean getGameForeground(){return foreground;}}
""",
}
for name, body in FILES.items():
    p = ROOT / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(body)
print(f"Created {len(FILES)} host-only mock files.")
