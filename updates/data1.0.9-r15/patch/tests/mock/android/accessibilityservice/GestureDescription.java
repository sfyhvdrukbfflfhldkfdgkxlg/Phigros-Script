
package android.accessibilityservice;
import android.graphics.Path;import java.util.*;
public final class GestureDescription {
 public static int maxStrokes=20; public final List<StrokeDescription> strokes;
 GestureDescription(List<StrokeDescription> s){strokes=new ArrayList<>(s);}
 public static int getMaxStrokeCount(){return maxStrokes;}
 public int getStrokeCount(){return strokes.size();}
 public StrokeDescription getStroke(int i){return strokes.get(i);}
 public static class StrokeDescription {
  static int next; private int mId=++next;public int getId(){return mId;}public int parent=-1;
  public final Path path;public final long start,duration;public final boolean keep;
  public StrokeDescription(Path p,long s,long d,boolean k){if(p.empty||s<0||d<=0)throw new AssertionError("invalid stroke");path=p;start=s;duration=d;keep=k;}
  public long getStartTime(){return start;}public long getDuration(){return duration;}
  public Path getPath(){return path;}public boolean willContinue(){return keep;}
  public StrokeDescription continueStroke(Path p,long s,long d,boolean k){
   if(!keep)throw new AssertionError("continued released pointer");
   if(p.firstX!=path.lastX||p.firstY!=path.lastY)throw new AssertionError("continuation jumped");
   StrokeDescription r=new StrokeDescription(p,s,d,k);r.parent=mId;return r;}
 }
 public static class Builder {
  final List<StrokeDescription> s=new ArrayList<>();
  public Builder addStroke(StrokeDescription d){s.add(d);if(s.size()>maxStrokes)throw new AssertionError("stroke capacity exceeded");return this;}
  public GestureDescription build(){if(s.isEmpty())throw new AssertionError("empty gesture");return new GestureDescription(s);}
 }
}
