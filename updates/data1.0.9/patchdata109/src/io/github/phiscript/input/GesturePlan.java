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
    private final IdentityHashMap<TouchEvent,UnifiedGesturePlanner.Contact> mixed=new IdentityHashMap<>();
    private final IdentityHashMap<TouchEvent,Trace> traces=new IdentityHashMap<>();
    public final List<TouchEvent> events;
    public final int connectedGroups,connectedNotes,coveredDrags;
    private final IdentityHashMap<TouchEvent,DenseDragPlanner.Pin> densePins=new IdentityHashMap<>();
    public int denseDragSections,denseDragNotes;
    private GesturePlan(List<TouchEvent> events,IdentityHashMap<TouchEvent,Trace> traces,int groups,int notes){this(events,traces,groups,notes,0);}
    private GesturePlan(List<TouchEvent> events,IdentityHashMap<TouchEvent,Trace> traces,int groups,int notes,int covered){this.events=java.util.Collections.unmodifiableList(events);this.traces.putAll(traces);connectedGroups=groups;connectedNotes=notes;coveredDrags=covered;}
    public static GesturePlan obtain(Chart chart,GestureProfile profile){
        synchronized(CACHE){Cached old=CACHE.get(chart);if(old!=null&&old.profile.equals(profile))return old.plan;}
        GesturePlan plan=build(chart,profile,true);
        try{ChordPlanner.checkOverlap(plan.events,10);}catch(IllegalStateException capacity){
            // Reduce artificial overlap from idle-gap bridges when possible.
            // This is a planning fallback, not a chart-admission check: a chart
            // is never rejected here for its estimated independent-contact count.
            plan=build(chart,profile,false);
        }
        synchronized(CACHE){if(CACHE.size()>8)CACHE.clear();CACHE.put(chart,new Cached(profile,plan));}
        return plan;
    }
    /** Prepare/cache gestures before the clock; does not reject estimated contact counts. */
    public static void preflight(Chart chart){
        obtain(chart,GestureProfile.read(TouchService.Companion.getCurrent()));
    }
    static GesturePlan mixed(List<TouchEvent> events,IdentityHashMap<TouchEvent,UnifiedGesturePlanner.Contact> paths,int groups,int notes,int covered){GesturePlan p=new GesturePlan(events,new IdentityHashMap<>(),groups,notes,covered);p.mixed.putAll(paths);return p;}
    private static GesturePlan build(Chart chart,GestureProfile profile,boolean bridge){
        DenseDragPlanner dense=bridge?DenseDragPlanner.plan(chart):new DenseDragPlanner();
        GesturePlan ordinary=individual(chart,profile,bridge,dense.covered);
        if(dense.pins.isEmpty())return ordinary;
        List<TouchEvent> events=new ArrayList<>(ordinary.events);events.addAll(dense.pins.keySet());
        events.sort((a,b)->{int c=Long.compare(a.getStartMs(),b.getStartMs());if(c!=0)return c;DenseDragPlanner.Pin x=dense.pins.get(a),y=dense.pins.get(b);if(x!=null&&y!=null){c=Float.compare(x.x,y.x);return c!=0?c:Float.compare(x.y,y.y);}return Integer.compare(a.getNote().getId(),b.getNote().getId());});
        GesturePlan plan=new GesturePlan(events,ordinary.traces,ordinary.connectedGroups,ordinary.connectedNotes,ordinary.coveredDrags+dense.covered.size());
        plan.mixed.putAll(ordinary.mixed);plan.densePins.putAll(dense.pins);plan.denseDragSections=dense.sections;plan.denseDragNotes=dense.covered.size();
        return plan;
    }
    private static GesturePlan individual(Chart chart,GestureProfile profile,boolean bridge,java.util.Set<Note> skipped){
        if(profile.connectFlicks||profile.combineShots)return UnifiedGesturePlanner.build(chart,profile,bridge,skipped);
        List<TouchEvent> source=ChordPlanner.forChart(chart,!profile.connectDrags&&!profile.catchDrags);
        source.removeIf(e->skipped.contains(e.getNote()));
        // Sustained catch even when optional linking is off. Capacity fallback
        // removes only idle lead-in, never turns DRAG into a tap event.
        if(bridge)for(int i=0;i<source.size();i++){TouchEvent e=source.get(i);if(e.getNote().getType()==2)source.set(i,new TouchEvent(e.getNote(),Math.min(e.getStartMs(),Math.round(e.getNote().getTimeSeconds()*1000)-100),e.getEndMs()));}
        source.sort((a,b)->{int order=Long.compare(a.getStartMs(),b.getStartMs());return order!=0?order:Integer.compare(a.getNote().getId(),b.getNote().getId());});
        if(!profile.connectDrags&&!profile.catchDrags&&!profile.connectFlicks&&!profile.combineShots)return new GesturePlan(source,new IdentityHashMap<>(),0,0);
        List<TouchEvent> holds=new ArrayList<>(),liveHolds=new ArrayList<>();int holdCursor=0,covered=0;
        if(profile.catchDrags)for(TouchEvent event:source)if(event.getNote().getType()==3)holds.add(event);
        List<Trace> all=new ArrayList<>();Map<Long,List<Trace>> tracks=new HashMap<>();int iterations=0;
        for(TouchEvent event:source){
            if((iterations++&1023)==0&&Thread.currentThread().isInterrupted())throw new IllegalStateException("手势规划已取消");
            Note note=event.getNote();
            if(note.getType()!=2||!profile.connectDrags&&!profile.catchDrags){all.add(new Trace(event));continue;}
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
        compose(chart,all,profile,bridge);
        List<TouchEvent> result=new ArrayList<>();IdentityHashMap<TouchEvent,Trace> traces=new IdentityHashMap<>();int groups=0,notes=0;
        for(Trace trace:all){
            TouchEvent first=trace.nodes.get(0),contact=first;
            if(trace.nodes.size()>1){contact=new TouchEvent(first.getNote(),trace.start,trace.end);traces.put(contact,trace);groups++;notes+=trace.nodes.size();}
            result.add(contact);
        }
        result.sort((a,b)->{int c=Long.compare(a.getStartMs(),b.getStartMs());return c!=0?c:Integer.compare(a.getNote().getId(),b.getNote().getId());});
        return new GesturePlan(result,traces,groups,notes,covered);
    }
    private static long hit(TouchEvent e){return Math.max(Math.round(e.getNote().getTimeSeconds()*1000),e.getNote().getType()==4?e.getStartMs()+28:Long.MIN_VALUE);}
    private static TouchEvent target(Trace t){return t.nodes.get(t.nodes.get(0).getNote().getType()==1?1:0);}
    private static long track(TouchEvent e){return ((long)e.getNote().getLineIndex()<<1)|(e.getNote().getAbove()?1:0);}
    private static boolean near(Chart chart,TouchEvent a,TouchEvent b,long gap,GestureProfile profile){
        if(track(a)!=track(b)||gap<0)return false;
        double seconds=Math.max(0,hit(b))/1000.0;Point p=chart.position(a.getNote(),seconds,REFERENCE,false),q=chart.position(b.getNote(),seconds,REFERENCE,false);
        if(p==null||q==null)return false;double d=Math.hypot(p.getX()-q.getX(),p.getY()-q.getY());
        return d<=profile.distance*900&&d<=Math.max(12,gap*1.8);
    }
    /** Pair in hit order: a flick's early start must not precede its blue DOWN. */
    private static void compose(Chart chart,List<Trace> all,GestureProfile profile,boolean bridge){
        Map<Long,List<Trace>> candidates=new HashMap<>();
        for(Trace t:all){int type=t.nodes.get(0).getNote().getType();if(type==2||type==4)candidates.computeIfAbsent(track(t.nodes.get(0)),k->new ArrayList<>()).add(t);}
        for(List<Trace> list:candidates.values())list.sort((a,b)->Long.compare(hit(a.nodes.get(0)),hit(b.nodes.get(0))));
        java.util.Set<Trace> removed=java.util.Collections.newSetFromMap(new IdentityHashMap<Trace,Boolean>());
        List<Trace> taps=new ArrayList<>();for(Trace t:all)if(t.nodes.size()==1&&t.last().getNote().getType()==1)taps.add(t);
        taps.sort((a,b)->Long.compare(hit(b.last()),hit(a.last())));
        if(profile.combineShots)for(Trace tap:taps){
            TouchEvent blue=tap.last();List<Trace> list=candidates.get(track(blue));if(list==null)continue;
            int lo=0,hi=list.size();while(lo<hi){int m=(lo+hi)>>>1;if(hit(target(list.get(m)))<hit(blue))lo=m+1;else hi=m;}
            Trace best=null;long bestGap=Long.MAX_VALUE;
            for(int i=lo;i<list.size()&&i<lo+512;i++){Trace t=list.get(i);TouchEvent next=target(t);long gap=hit(next)-hit(blue);if(gap>160)break;
                if(t.nodes.get(0).getNote().getType()==1||!bridge&&tap.end<t.start||!near(chart,blue,next,gap,profile))continue;
                if(gap<bestGap){best=t;bestGap=gap;}}
            if(best!=null){best.nodes.add(0,blue);best.start=tap.start;best.end=Math.max(best.end,tap.end);removed.add(tap);}
        }
        if(profile.connectFlicks)for(List<Trace> list:candidates.values()){
            List<Trace> live=new ArrayList<>();
            for(Trace t:list){TouchEvent first=t.nodes.get(t.nodes.get(0).getNote().getType()==1?1:0);if(first.getNote().getType()!=4)continue;
                Trace best=null;long bestGap=Long.MAX_VALUE;
                for(Iterator<Trace> it=live.iterator();it.hasNext();){Trace prior=it.next();long gap=hit(first)-hit(prior.last());if(gap>300){it.remove();continue;}
                    if(t.nodes.get(0).getNote().getType()==1||gap<=0||!bridge&&t.start>prior.end||!near(chart,prior.last(),first,gap,profile))continue;
                    if(gap<bestGap){best=prior;bestGap=gap;}}
                if(best==null){if(live.size()<128)live.add(t);}else{best.nodes.addAll(t.nodes);best.end=Math.max(best.end,t.end);removed.add(t);}
            }
        }
        all.removeAll(removed);
        for(Trace t:all)if(t.nodes.size()>1&&t.last().getNote().getType()==4)t.end=Math.max(t.end,hit(t.last())+60);
    }
    public long nextBoundary(TouchEvent event,long now,long end){UnifiedGesturePlanner.Contact c=mixed.get(event);if(c!=null){int lo=0,hi=c.legs.size();while(lo<hi){int mid=(lo+hi)>>>1;if(c.legs.get(mid).to<=now)lo=mid+1;else hi=mid;}if(lo<c.legs.size()){UnifiedGesturePlanner.Leg leg=c.legs.get(lo);if(leg.from>now&&leg.from>event.getStartMs())end=Math.min(end,leg.from);else if(leg.to>now)end=Math.min(end,leg.to);}}return end;}
    public boolean isContinuous(TouchEvent event){return mixed.containsKey(event)||traces.containsKey(event)||event.getNote().getType()==2||event.getNote().getType()==3;}
    public boolean hasFlickPath(TouchEvent event){if(mixed.containsKey(event))return mixed.get(event).flick;Trace t=traces.get(event);return t!=null&&t.last().getNote().getType()==4;}
    public Point point(Chart chart,TouchEvent contact,long time,Viewport viewport,int[] screen,boolean[] bounded){
        return point(chart,contact,time,viewport,screen,bounded,new TouchBounds(Math.max(0,viewport.getLeft()),Math.max(0,viewport.getTop()),Math.min(screen[0]-1,Math.nextDown(viewport.getRight())),Math.min(screen[1]-1,Math.nextDown(viewport.getBottom()))));
    }
    public Point point(Chart chart,TouchEvent contact,long time,Viewport viewport,int[] screen,boolean[] bounded,TouchBounds bounds){
        DenseDragPlanner.Pin pin=densePins.get(contact);if(pin!=null)return pin.point(viewport,bounds);
        UnifiedGesturePlanner.Contact path=mixed.get(contact);if(path!=null)return path.point(chart,time,viewport,bounds,bounded);
        Trace trace=traces.get(contact);if(trace==null)return ChartMultiTouchPlayer.point(chart,contact,time,viewport,screen,bounded);
        if(hasFlickPath(contact))return flickPoint(trace,chart,time,viewport,bounds,bounded);
        if(trace.nodes.get(0).getNote().getType()==1&&time<trace.start+18){
            Point p=chart.position(trace.nodes.get(0).getNote(),Math.max(0,time)/1000.0,viewport,false);
            return new Point(bounds.x(p.getX()),bounds.y(p.getY()));
        }
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
    private static long legStart(Trace t,int i){long h=hit(t.nodes.get(i)),span=24;if(i>0&&t.nodes.get(i-1).getNote().getType()==4)span=Math.min(span,Math.max(1,(h-hit(t.nodes.get(i-1)))/2));long start=h-span;
        if(i==1&&t.nodes.get(0).getNote().getType()==1)start=Math.max(start,t.start+18);return Math.max(t.start,start);}
    private static long legEnd(Trace t,int i){long h=hit(t.nodes.get(i)),end=h+24;if(i+1<t.nodes.size())end=Math.min(end,h+Math.max(1,(hit(t.nodes.get(i+1))-h)/2));return Math.max(legStart(t,i)+24,end);}
    private static Point endpoint(Trace t,int i,boolean end,Chart c,long now,Viewport v,TouchBounds bounds,boolean[] bounded){
        TouchEvent e=t.nodes.get(i);Point p=c.position(e.getNote(),Math.max(0,now)/1000.0,v,false);if(p==null)throw new IllegalArgumentException("无法计算连续划键轨迹");
        float x=bounds.x(p.getX()),y=bounds.y(p.getY());if(x!=p.getX()||y!=p.getY())bounded[0]=true;
        int first=t.nodes.get(0).getNote().getType()==1?1:0;
        if(end==((i-first)%2==0)){
            Point anchor=c.position(e.getNote(),Math.max(0,hit(e))/1000.0,v,false);float[] target=bounds.flick(bounds.x(anchor.getX()),bounds.y(anchor.getY()),0,-v.getHeight()*.1f);
            x=bounds.x(x+target[0]-bounds.x(anchor.getX()));y=bounds.y(y+target[1]-bounds.y(anchor.getY()));
        }
        return new Point(x,y);
    }
    private static Point mix(Point a,Point b,float r){r=Math.max(0,Math.min(1,r));return new Point(a.getX()+(b.getX()-a.getX())*r,a.getY()+(b.getY()-a.getY())*r);}
    private static Point flickPoint(Trace t,Chart c,long now,Viewport v,TouchBounds bounds,boolean[] bounded){
        int first=t.nodes.get(0).getNote().getType()==1?1:0,lo=first,hi=t.nodes.size();
        while(lo<hi){int m=(lo+hi)>>>1;if(legStart(t,m)<=now)lo=m+1;else hi=m;}
        if(lo==first){Point dest=endpoint(t,first,false,c,now,v,bounds,bounded);if(first==0)return dest;
            Point blue=c.position(t.nodes.get(0).getNote(),Math.max(0,now)/1000.0,v,false);blue=new Point(bounds.x(blue.getX()),bounds.y(blue.getY()));
            return mix(blue,dest,(now-t.start-18)/(float)Math.max(1,legStart(t,first)-t.start-18));}
        int i=lo-1;long start=legStart(t,i),end=legEnd(t,i);Point a=endpoint(t,i,false,c,now,v,bounds,bounded),b=endpoint(t,i,true,c,now,v,bounds,bounded);
        if(now<=end)return mix(a,b,(now-start)/(float)(end-start));
        if(i+1<t.nodes.size())return mix(b,endpoint(t,i+1,false,c,now,v,bounds,bounded),(now-end)/(float)Math.max(1,legStart(t,i+1)-end));
        return b;
    }
}
