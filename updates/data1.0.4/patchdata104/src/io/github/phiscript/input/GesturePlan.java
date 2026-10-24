package io.github.phiscript.input;

import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Note;
import io.github.phiscript.engine.Point;
import io.github.phiscript.engine.TouchEvent;
import io.github.phiscript.engine.Viewport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Each connected DRAG run is one contact with its own time/position anchors. */
public final class GesturePlan {
    private static final Viewport REFERENCE=new Viewport(0,0,1350,900);
    private static final Map<Chart,Cached> CACHE=new WeakHashMap<>();
    private static final class Cached {GestureProfile profile;GesturePlan plan;Cached(GestureProfile p,GesturePlan plan){profile=p;this.plan=plan;}}
    private static final class Trace {
        final List<TouchEvent> nodes=new ArrayList<>();long start,end;
        Trace(TouchEvent event){nodes.add(event);start=event.getStartMs();end=event.getEndMs();}
        TouchEvent last(){return nodes.get(nodes.size()-1);}
    }
    private final IdentityHashMap<TouchEvent,Trace> traces=new IdentityHashMap<>();
    public final List<TouchEvent> events;
    public final int connectedGroups,connectedNotes,coveredDrags;
    private GesturePlan(List<TouchEvent> events,IdentityHashMap<TouchEvent,Trace> traces,int groups,int notes){this(events,traces,groups,notes,0);}
    private GesturePlan(List<TouchEvent> events,IdentityHashMap<TouchEvent,Trace> traces,int groups,int notes,int covered){this.events=java.util.Collections.unmodifiableList(events);this.traces.putAll(traces);connectedGroups=groups;connectedNotes=notes;coveredDrags=covered;}
    public static GesturePlan obtain(Chart chart,GestureProfile profile){
        synchronized(CACHE){Cached old=CACHE.get(chart);if(old!=null&&old.profile.equals(profile))return old.plan;}
        GesturePlan plan=build(chart,profile,true);
        try{ChordPlanner.checkOverlap(plan.events,10);}catch(IllegalStateException capacity){
            // Reduce artificial overlap from idle-gap bridges when possible.
            // This is a planning fallback, not a chart-admission check: a chart
            // is never rejected here for its estimated independent-contact count.
            if(profile.connectDrags||profile.catchDrags)plan=build(chart,profile,false);
        }
        synchronized(CACHE){if(CACHE.size()>8)CACHE.clear();CACHE.put(chart,new Cached(profile,plan));}
        return plan;
    }
    /** Prepare/cache gestures before the clock; does not reject estimated contact counts. */
    public static void preflight(Chart chart){
        obtain(chart,GestureProfile.read(TouchService.Companion.getCurrent()));
    }
    private static GesturePlan build(Chart chart,GestureProfile profile,boolean bridge){
        List<TouchEvent> source=ChordPlanner.forChart(chart,!profile.connectDrags&&!profile.catchDrags);
        if(!profile.connectDrags&&!profile.catchDrags)return new GesturePlan(source,new IdentityHashMap<>(),0,0);
        List<TouchEvent> holds=new ArrayList<>(),liveHolds=new ArrayList<>();int holdCursor=0,covered=0;
        if(profile.catchDrags)for(TouchEvent event:source)if(event.getNote().getType()==3)holds.add(event);
        List<Trace> all=new ArrayList<>();Map<Long,List<Trace>> tracks=new HashMap<>();int iterations=0;
        for(TouchEvent event:source){
            if((iterations++&1023)==0&&Thread.currentThread().isInterrupted())throw new IllegalStateException("手势规划已取消");
            Note note=event.getNote();
            if(note.getType()!=2){all.add(new Trace(event));continue;}
            long hit=Math.round(note.getTimeSeconds()*1000);
            if(profile.catchDrags){
                while(holdCursor<holds.size()&&holds.get(holdCursor).getStartMs()<=hit)liveHolds.add(holds.get(holdCursor++));
                boolean caught=false;Point target=chart.position(note,Math.max(0,hit)/1000.0,REFERENCE,false);
                for(Iterator<TouchEvent> it=liveHolds.iterator();it.hasNext();){TouchEvent held=it.next();if(held.getEndMs()<=hit){it.remove();continue;}
                    Point p=chart.position(held.getNote(),Math.max(0,hit)/1000.0,REFERENCE,false);
                    if(target!=null&&p!=null&&Math.hypot(target.getX()-p.getX(),target.getY()-p.getY())<=.5){caught=true;break;}
                }
                if(caught){covered++;continue;}
                // One early pickup per run. Subsequent yellow notes are caught by MOVE/hold.
                if(bridge)event=new TouchEvent(note,Math.min(event.getStartMs(),hit-100),event.getEndMs());
            }
            long key=((long)note.getLineIndex()<<1)|(note.getAbove()?1L:0L);
            List<Trace> active=tracks.get(key);if(active==null){active=new ArrayList<>();tracks.put(key,active);}
            Trace best=null;double bestDistance=Double.POSITIVE_INFINITY;
            for(Iterator<Trace> it=active.iterator();it.hasNext();){
                Trace trace=it.next();TouchEvent prior=trace.last();long priorHit=Math.round(prior.getNote().getTimeSeconds()*1000),gap=hit-priorHit;
                if(gap>(profile.catchDrags?1200:profile.gapMs)){it.remove();continue;}
                if(gap<0||!bridge&&event.getStartMs()>trace.end)continue;
                double seconds=Math.max(0,hit)/1000.0;Point a=chart.position(prior.getNote(),seconds,REFERENCE,false),b=chart.position(note,seconds,REFERENCE,false);
                if(a==null||b==null)continue;double distance=Math.hypot(a.getX()-b.getX(),a.getY()-b.getY());
                if(gap==0){
                    if(distance>.01||!ChordPlanner.sameTrack(chart,prior.getNote(),note,event.getStartMs(),Math.max(trace.end,event.getEndMs()),REFERENCE))continue;
                }else if(distance>profile.distance*900||distance>gap*1.8)continue;
                if(distance<bestDistance){best=trace;bestDistance=distance;}
            }
            if(best==null){best=new Trace(event);all.add(best);if(active.size()<128)active.add(best);}
            else{best.nodes.add(event);best.end=Math.max(best.end,event.getEndMs());}
        }
        List<TouchEvent> result=new ArrayList<>();IdentityHashMap<TouchEvent,Trace> traces=new IdentityHashMap<>();int groups=0,notes=0;
        for(Trace trace:all){
            TouchEvent first=trace.nodes.get(0),contact=first;
            if(trace.nodes.size()>1){contact=new TouchEvent(first.getNote(),trace.start,trace.end);traces.put(contact,trace);groups++;notes+=trace.nodes.size();}
            result.add(contact);
        }
        result.sort((a,b)->{int c=Long.compare(a.getStartMs(),b.getStartMs());return c!=0?c:Integer.compare(a.getNote().getId(),b.getNote().getId());});
        return new GesturePlan(result,traces,groups,notes,covered);
    }
    public Point point(Chart chart,TouchEvent contact,long time,Viewport viewport,int[] screen,boolean[] bounded){
        Trace trace=traces.get(contact);if(trace==null)return ChartMultiTouchPlayer.point(chart,contact,time,viewport,screen,bounded);
        // Binary search makes even a very long drag chain cheap to sample.
        int lo=0,hi=trace.nodes.size();
        while(lo<hi){int mid=(lo+hi)>>>1;if(Math.round(trace.nodes.get(mid).getNote().getTimeSeconds()*1000)<=time)lo=mid+1;else hi=mid;}
        int index=Math.max(0,lo-1);TouchEvent a=trace.nodes.get(index);double seconds=Math.max(0,time)/1000.0;Point p=chart.position(a.getNote(),seconds,viewport,false);
        if(index+1<trace.nodes.size()){
            TouchEvent b=trace.nodes.get(index+1);long from=Math.round(a.getNote().getTimeSeconds()*1000),to=Math.round(b.getNote().getTimeSeconds()*1000);
            float ratio=to<=from?1:Math.max(0,Math.min(1,(time-from)/(float)(to-from)));
            Point next=chart.position(b.getNote(),seconds,viewport,false);
            if(p==null||next==null)throw new IllegalArgumentException("无法计算连续拖键轨迹");
            p=new Point(p.getX()+(next.getX()-p.getX())*ratio,p.getY()+(next.getY()-p.getY())*ratio);
        }
        if(p==null)throw new IllegalArgumentException("无法计算连续拖键落点");
        float x=GestureChannel.clamp(p.getX(),Math.max(0,viewport.getLeft()),Math.min(screen[0]-1,Math.nextDown(viewport.getRight())));
        float y=GestureChannel.clamp(p.getY(),Math.max(0,viewport.getTop()),Math.min(screen[1]-1,Math.nextDown(viewport.getBottom())));
        if(x!=p.getX()||y!=p.getY())bounded[0]=true;
        return new Point(x,y);
    }
}
