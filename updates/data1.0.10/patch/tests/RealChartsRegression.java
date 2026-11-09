package io.github.phiscript.input;

import io.github.phiscript.assets.*;
import io.github.phiscript.engine.*;
import java.nio.file.*;
import java.util.*;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;

public final class RealChartsRegression {
    public static void main(String[] args) throws Exception {
        int passed=0;
        for (int i=1;i<=3;i++) {
            String raw=new String(Files.readAllBytes(Paths.get("analysis/realcharts/chart_"+i+".json")),"UTF-8");
            try {
                Chart c=PhiraChartParser.INSTANCE.parse(raw,0);
                List<TouchEvent> events=ChordPlanner.forChart(c);
                try {ChordPlanner.checkOverlap(events,10);}catch(RuntimeException limit) {
                    List<long[]> changes=new ArrayList<>();
                    for(int k=0;k<events.size();k++){TouchEvent e=events.get(k);changes.add(new long[]{e.getStartMs(),1,k});changes.add(new long[]{e.getEndMs(),-1,k});}
                    changes.sort((a,b)->a[0]!=b[0]?Long.compare(a[0],b[0]):Long.compare(a[1],b[1]));
                    Set<Integer> live=new LinkedHashSet<>();
                    for(long[] change:changes){if(change[1]>0)live.add((int)change[2]);else live.remove((int)change[2]);if(live.size()>10){
                        System.out.println("PEAK OVERFLOW at "+change[0]+"ms");
                        for(int k:live){TouchEvent e=events.get(k);Note n=e.getNote();System.out.println("  "+n+" interval="+e.getStartMs()+".."+e.getEndMs());}break;}}
                    throw limit;
                }
                int holds=0;for(Note n:c.getNotes())if(n.getType()==3)holds++;
                System.out.println("PARSED chart "+i+": logical="+c.getNotes().size()
                    +", physical="+events.size()+", holds="+holds+", duration="+c.getDurationSeconds());
                if(args.length>0 && args[0].equals("play")) {
                    SystemClock.now=100000;
                    long epoch=SystemClock.now-Math.max(0,Math.round(c.getFirstNoteSeconds()*1000)-50);
                    TouchService service=new TouchService();AtomicBoolean playing=new AtomicBoolean();
                    final int index=i;
                    ChartMultiTouchPlayer.play(service,c,epoch,new Viewport(0,0,1000,600),
                        new AtomicBoolean(),s->{System.out.println("STATUS "+index+": "+s);return Unit.INSTANCE;},
                        playing,new AtomicBoolean());
                    if(!service.roots.isEmpty()||playing.get())throw new AssertionError("leaked long-key state");
                    boolean[] used=new boolean[service.downs.size()];
                    int matched=0;
                    for(TouchEvent event:events) {
                        int best=-1;double score=Double.POSITIVE_INFINITY;
                        for(int k=0;k<service.downs.size();k++)if(!used[k]){
                            long[] down=service.downs.get(k);long at=down[1]-epoch;
                            long delta=Math.abs(at-event.getStartMs());if(delta>80)continue;
                            double distance=Double.POSITIVE_INFINITY;
                            for(long sample=at-6;sample<=at+2;sample++){
                                Point p=ChartMultiTouchPlayer.point(c,event,sample,new Viewport(0,0,1000,600),
                                    new int[]{1000,600},new boolean[1]);
                                distance=Math.min(distance,Math.hypot(p.getX()-Float.intBitsToFloat((int)down[2]),
                                    p.getY()-Float.intBitsToFloat((int)down[3])));
                            }
                            if(distance<=12&&delta+distance<score){best=k;score=delta+distance;}
                        }
                        if(best<0)throw new AssertionError("missing note head: "+event);
                        used[best]=true;matched++;
                    }
                    if(service.ups.size()!=service.downs.size())throw new AssertionError("unbalanced DOWN/UP");
                    System.out.println("PLAYED chart "+i+": "+matched+"/"+events.size()+" heads matched by time/position; "
                        +service.downs.size()+" DOWNs including safe hold resumptions; "+holds+" holds, peak="+service.peak
                        +"; "+service.audit.events+" AOSP events; no invalid indices or duplicate pointer IDs");
                }
                passed++;
            } catch (RuntimeException e) {System.out.println("FAIL chart "+i+": "+e);}
        }
        if(passed!=3)throw new AssertionError("Only "+passed+" of 3 reference charts passed");
        System.out.println("PASS all 3 full reference charts; host input simulation, not a device run");
    }
}
