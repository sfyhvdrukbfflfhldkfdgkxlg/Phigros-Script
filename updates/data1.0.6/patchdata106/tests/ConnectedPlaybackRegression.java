package io.github.phiscript.input;
import android.os.SystemClock;import io.github.phiscript.AppSettings;import io.github.phiscript.engine.*;import io.github.phiscript.root.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import kotlin.Unit;
public final class ConnectedPlaybackRegression {
 static int checks;static final Viewport VIEW=new Viewport(0,0,1000,600);static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static List<Note> notes(){List<Note> n=new ArrayList<>();for(int i=0;i<15;i++)n.add(GesturePlanRegression.n(i,2,.1+i*.05,.1+i*.05,-.3f+i*.025f,true,0));n.add(GesturePlanRegression.n(100,3,.05,.95,.45f,true,0));n.add(GesturePlanRegression.n(101,1,.25,.25,-.45f,true,0));n.add(GesturePlanRegression.n(102,1,.4,.4,-.45f,true,0));n.add(GesturePlanRegression.n(103,4,.6,.6,-.45f,true,0));return n;}
 static RootClient root(Chart chart,long elapsed)throws Exception{
  SystemClock.now=100000;RootClient.failAt=-1;RootClient.after=null;InputBackend.rootEnabled=true;AppSettings.smart=true;AtomicBoolean playing=new AtomicBoolean();
  ChartMultiTouchPlayer.play(new TouchService(),chart,SystemClock.now-elapsed,VIEW,new AtomicBoolean(),s->Unit.INSTANCE,playing,new AtomicBoolean());
  ok(!playing.get()&&RootClient.last.closed&&RootClient.last.active.isEmpty(),"Root cleanup");return RootClient.last;
 }
 public static void main(String[] args)throws Exception{
  List<Note> original=notes();Chart chart=GesturePlanRegression.chart(original);GesturePlan plan=GesturePlan.obtain(chart,new GestureProfile(true,70,.12f));RootClient client=root(chart,0);
  ok(client.heads.size()==plan.events.size(),"Root did not assign one DOWN per independent contact");int chain=-1;for(int i=0;i<plan.events.size();i++)if(plan.events.get(i).getNote().getType()==2)chain=i;ok(chain>=0&&client.starts.get((long)chain)==1,"Root drag chain restarted");
  for(int i=0;i<15;i++){
   long target=100000+100+i*50;float[] closest=null;for(float[] sample:client.samples)if((int)sample[1]==chain&&(closest==null||Math.abs(sample[0]-target)<Math.abs(closest[0]-target)))closest=sample;
   ok(closest!=null&&Math.abs(closest[0]-target)<=9,"Root skipped drag anchor time");Point want=chart.position(original.get(i),(closest[0]-100000)/1000.0,VIEW,false);ok(Math.abs(closest[2]-want.getX())<5&&Math.abs(closest[3]-want.getY())<5,"Root path misses connected anchor");
  }
  client=root(chart,300);ok(client.heads.stream().anyMatch(h->plan.events.get((int)h[0]).getNote().getType()==2),"mid-chain Root catch-up dropped remaining drag run");ok(client.heads.stream().noneMatch(h->plan.events.get((int)h[0]).getNote().getId()==101),"expired tap replayed during catch-up");
  InputBackend.rootEnabled=false;SystemClock.now=100000;TouchService service=new TouchService();AtomicBoolean playing=new AtomicBoolean();ChartMultiTouchPlayer.play(service,chart,SystemClock.now,VIEW,new AtomicBoolean(),s->Unit.INSTANCE,playing,new AtomicBoolean());ok(!playing.get()&&service.roots.isEmpty(),"accessibility chain cleanup");ok(service.downs.size()==plan.events.size(),"accessibility failed independent connected contact allocation");
  SystemClock.now=100000;service=new TouchService();ChartMultiTouchPlayer.play(service,chart,SystemClock.now-300,VIEW,new AtomicBoolean(),s->Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());ok(service.downs.size()>=3&&service.roots.isEmpty(),"accessibility mid-chain catch-up lost run or release");
  AppSettings.smart=false;System.out.println("PASS "+checks+" actual Root/accessibility connected playback checks: one sustained chain, fresh tap/flick/hold heads, hit-position samples, mid-run catch-up and cleanup.");
 }
}
