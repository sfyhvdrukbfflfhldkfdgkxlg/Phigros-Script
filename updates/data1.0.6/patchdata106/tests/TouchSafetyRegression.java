package io.github.phiscript.input;
import android.os.SystemClock;import android.view.WindowInsets;import android.graphics.Insets;
import io.github.phiscript.AppSettings;import io.github.phiscript.engine.*;import io.github.phiscript.root.*;
import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import kotlin.Unit;

public final class TouchSafetyRegression {
 static int checks;static final Viewport VIEW=new Viewport(0,0,1000,600);
 static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static void in(float x,float y,TouchBounds b){ok(x>=b.left&&x<=b.right&&y>=b.top&&y<=b.bottom,"path entered system edge: "+x+","+y);}
 static Chart corners(){List<Note> notes=new ArrayList<>();for(int i=0;i<8;i++)notes.add(GesturePlanRegression.n(i,i<4?3:4,.1,i<4?.6:.1,0,true,0));
  return Chart.Companion.fromNative$app_debug(notes,0,(n,t,v)->new Point((n.getId()%2)*999,(n.getId()%4<2?0:599)),(n,t,v)->new Point(n.getId()%2==0?-1:1,n.getId()%4<2?-1:1));}
 static void samples(RootClient c,TouchBounds b){ok(c.closed&&c.active.isEmpty(),"Root pointers leaked");for(float[] p:c.samples)in(p[2],p[3],b);}
 static void strokes(TouchService s,TouchBounds b){ok(s.roots.isEmpty(),"accessibility pointers leaked");for(android.accessibilityservice.GestureDescription g:s.history)for(android.accessibilityservice.GestureDescription.StrokeDescription stroke:g.strokes)for(float[] p:stroke.path.points)in(p[0],p[1],b);}
 public static void main(String[] args)throws Exception{
  AppSettings.edges=true;TouchService service=new TouchService();
  WindowInsets.bars=new Insets(80,40,70,90);WindowInsets.gestures=new Insets(100,10,110,120);
  TouchBounds system=TouchSafety.read(service,1000,600,null).bounds;
  ok(system.left==102&&system.top==42&&system.right==887&&system.bottom==477,"hidden bars, cutout or mandatory gestures ignored");
  android.os.Build.VERSION.SDK_INT=29;TouchBounds legacy=TouchSafety.read(service,1000,600,null).bounds;ok(legacy.left==20&&legacy.top==24&&legacy.right==979&&legacy.bottom==567,"pre-30 explicit margins missing");android.os.Build.VERSION.SDK_INT=35;
  TouchBounds b=new TouchBounds(20,24,979,567);
  for(float[] p:new float[][]{{20,24,-60,-60},{979,24,60,-60},{20,567,-60,60},{979,567,60,60}}){float[] end=b.flick(p[0],p[1],p[2],p[3]);in(end[0],end[1],b);ok(Math.hypot(end[0]-p[0],end[1]-p[1])>80,"edge flick collapsed instead of turning inward");}
  boolean rejected=false;try{new TouchBounds(100,100,101,101);}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"empty safe area accepted");
  TouchBounds sub=b.intersect(100,80,900,500);ok(sub.left==100&&sub.top==80&&sub.right==900&&sub.bottom==500,"calibration was projected instead of intersected");
  WindowInsets.bars=new Insets(0,0,0,0);WindowInsets.gestures=new Insets(0,0,0,0);AppSettings.smart=false;
  Chart chart=corners();GesturePlan plan=GesturePlan.obtain(chart,new GestureProfile(false,70,.12f));TouchSafety safety=TouchSafety.read(service,1000,600,VIEW);
  for(TouchEvent e:plan.events)for(long t=e.getStartMs();t<=e.getEndMs();t+=3){Point p=safety.point(plan,chart,e,t,VIEW,new int[]{1000,600},new boolean[1]);in(p.getX(),p.getY(),b);}
  for(boolean root:new boolean[]{true,false}){InputBackend.rootEnabled=root;SystemClock.now=100000;RootClient.after=null;RootClient.failAt=-1;service=new TouchService();
   ChartMultiTouchPlayer.play(service,chart,SystemClock.now,VIEW,new AtomicBoolean(),s->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());if(root)samples(RootClient.last,b);else strokes(service,b);
   SystemClock.now=100000;AtomicBoolean stop=new AtomicBoolean();final TouchService visual=service=new TouchService();
   RootClient.after=()->{if(RootClient.last.frames>=40)stop.set(true);};visual.after=g->{if(visual.sends>=30)stop.set(true);};
   VisualMultiTouchPlayer.play(visual,1000,600,()->new VisualTouchFrame(SystemClock.now,1000,600,Arrays.asList(new VisualTouch(1,VisualTouchKind.HOLD,0,0,0,0),new VisualTouch(2,VisualTouchKind.HOLD,1,1,0,0),new VisualTouch(3,VisualTouchKind.FLICK,1,0,.12f,-.12f))),stop,()->true,s->Unit.INSTANCE);
   if(root)samples(RootClient.last,b);else strokes(visual,b);
  }
  AppSettings.edges=false;TouchSafety off=TouchSafety.read(service,1000,600,VIEW);ok(!off.enabled&&off.bounds.left==0&&off.bounds.bottom==599,"disable did not restore original area");InputBackend.rootEnabled=false;RootClient.after=null;
  System.out.println("PASS "+checks+" edge-safety checks: hidden/mandatory insets, old Android margins, inward corner flicks, all Root/accessibility chart and visual path samples, continuation nudges and release (host input models).");
 }
}
