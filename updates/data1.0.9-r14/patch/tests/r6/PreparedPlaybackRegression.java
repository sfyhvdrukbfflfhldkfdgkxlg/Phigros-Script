package io.github.phiscript.input;
import android.content.Context;import android.os.SystemClock;import io.github.phiscript.*;import io.github.phiscript.engine.*;import io.github.phiscript.root.*;
import org.json.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;

public final class PreparedPlaybackRegression {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static final class Selection{private final int width=1000,height=600;}
 static final class Owner{private SessionSettings settings;private final AtomicBoolean stop=new AtomicBoolean();}
 static Chart chart(){List<Note> notes=new ArrayList<>();for(int i=0;i<20;i++)notes.add(new Note(i,1,i*.1,i*.1,0,1,0,true,0));return Chart.Companion.fromNative$app_debug(notes,0,(n,t,v)->new Point(500,300),(n,t,v)->new Point(0,1));}
 static SessionSettings settings(Context c)throws Exception{for(java.lang.reflect.Method m:AppSettings.class.getDeclaredMethods())if(m.getReturnType()==SessionSettings.class&&m.getParameterTypes().length==0)return (SessionSettings)m.invoke(new AppSettings(c));throw new AssertionError("settings factory missing");}
 public static void main(String[] args)throws Exception{
  boolean baseline=args.length>0;Context.root=Files.createTempDirectory("prepared-input").toFile();TouchService service=new TouchService();new AppSettings(service).save("tapmod_enabled","false");TapMod.init(service);InputBackend.rootEnabled=true;SystemClock.now=100000;
  RootClient.startupDelayMs=2000;RootClient.menuReleaseAdvanceMs=0;RootClient.created=0;RootClient.after=null;RootClient.failAt=-1;
  Chart chart=chart();
  if(baseline){RootChartPlayer.play(service,chart,SystemClock.now+50,new Viewport(0,0,1000,600),new AtomicBoolean(),s->kotlin.Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());ok(RootClient.last.heads.size()<5,"R5 root startup should miss most of this short chart");System.out.println("PASS R5 cold-start reproduction: "+RootClient.last.heads.size()+" / 20 heads emitted after a 2-second Root startup");return;}
  Owner owner=new Owner();owner.settings=settings(service);SharedChartPlayback.prepare(owner,null,new Selection(),chart);ok(RootClient.created==1,"Root process prepared once before retry");
  long epoch=SystemClock.uptimeMillis()+700;service.play(chart,epoch,new Viewport(0,0,1000,600),new AtomicBoolean(),s->kotlin.Unit.INSTANCE);
  ok(RootClient.created==1,"Phira service.play reuses prepared Root instead of cold-starting again");ok(RootClient.last.heads.size()==20,"all twenty heads emitted after prepared restart");ok(RootClient.last.closed&&RootClient.last.active.isEmpty(),"prepared input released after playback");SharedChartPlayback.close(owner);
  RootClient.startupDelayMs=0;RootClient.menuReleaseAdvanceMs=55;
  try(RootWarmup warm=new RootWarmup(service,1000,600)){
   long receipt=warm.tap(.5f,.5f,1,120,new AtomicBoolean());long down=RootClient.last.heads.get(0)[1];
   ok(receipt-down>=0&&receipt-down<10,"retry receipt anchored to acknowledged DOWN, not delayed UP");ok(SystemClock.now-receipt>=55,"receipt must precede completed menu release");
  }
  RootClient.menuReleaseAdvanceMs=0;RootClient.created=0;
  try(RootWarmup warm=new RootWarmup(service,1000,600)){
   RootClient ready=warm.take();SharedChartPlayback.preflight(chart);SharedChartPlayback.play(service,chart,SystemClock.uptimeMillis()+700,new Viewport(0,0,1000,600),new AtomicBoolean(),s->kotlin.Unit.INSTANCE,ready);
   ok(RootClient.created==1&&ready.heads.size()==20,"Phigros enters the same service.play path and consumes its prepared channel");ok(ready.closed&&ready.active.isEmpty(),"Phigros shared playback cleans up");
  }
  Owner cancelled=new Owner();cancelled.settings=settings(service);SharedChartPlayback.prepare(cancelled,null,new Selection(),chart);RootClient idle=RootClient.last;SharedChartPlayback.close(cancelled);ok(idle.closed,"cancel before retry closes idle Root channel");
  InputBackend.rootEnabled=false;Owner accessible=new Owner();accessible.settings=settings(service);SharedChartPlayback.prepare(accessible,null,new Selection(),chart);int created=RootClient.created;service.play(chart,SystemClock.uptimeMillis()+50,new Viewport(0,0,1000,600),new AtomicBoolean(),s->kotlin.Unit.INSTANCE);SharedChartPlayback.close(accessible);
  ok(RootClient.created==created&&service.downs.size()==20,"shared preparation retains non-root accessibility playback");
  System.out.println("PASS prepared playback "+checks+" assertions: Phira/Phigros shared service, root warmup, DOWN epoch, startup heads, cancellation and accessibility");
 }
}
