
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
