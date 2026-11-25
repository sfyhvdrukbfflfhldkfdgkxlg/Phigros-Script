package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import io.github.phiscript.engine.*;
import java.util.*;

/** Reserve fresh heads first. DRAG checks an occupied judgement strip; FLICK needs movement. */
final class ContactPlanner {
    static final int LIMIT=6;
    private static final int RED_DOWN_MS=20,RED_TAIL_MS=18;
    // Reserve at least one 60 Hz frame for a fresh tap or an occupied drag strip.
    private static final int HEAD_SETTLE_MS=20,DRAG_DWELL_MS=20;
    static float radius(Viewport v){return Math.min(v.getWidth()*.098f,v.getHeight()*.20f);}
    static long hit(Note n){return Math.round(n.getTimeSeconds()*1000);}
    static final class Anchor {
        final Note note;
        final float dx,dy;
        final boolean pin,precise;
        Anchor(Note n,float dx,float dy,boolean pin){this(n,dx,dy,pin,false);}
        Anchor(Note n,float dx,float dy,boolean pin,boolean precise){this.note=n;this.dx=dx;this.dy=dy;this.pin=pin;this.precise=precise;}
        Anchor certified(){return new Anchor(note,dx,dy,pin,true);}
        Anchor(Note n){this(n,0,0,false);}
        Point at(Chart chart,long t,Viewport v,TouchBounds b){
            if(pin)return new Point(b.x(v.getLeft()+v.getWidth()*dx),b.y(v.getTop()+v.getHeight()*dy));
            double seconds=Math.max(0,t)/1000.0;
            Point p=chart.position(note,seconds,v,false);
            if(p==null)throw new IllegalArgumentException(tr("无法计算触点位置"));
            float x=p.getX(),y=p.getY();
            if(dx!=0 || dy!=0){
                Point d=chart.judgeNormal(note,seconds,v);
                double m=Math.hypot(d.getX(),d.getY());
                if(m>1e-7){
                    x+=(d.getY()*dx*radius(v)+d.getX()*dy*v.getHeight())/m;
                    y+=(-d.getX()*dx*radius(v)+d.getY()*dy*v.getHeight())/m;
                }
            }
            if(x<b.left || x>b.right || y<b.top || y>b.bottom){
                Point d=chart.judgeNormal(note,seconds,v);
                double[] span={Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY};
                if(BlockAreaSet.clip(x,d.getX(),b.left,b.right,span) && BlockAreaSet.clip(y,d.getY(),b.top,b.bottom,span)){
                    double nearest=Math.max(span[0],Math.min(0,span[1]));x+=nearest*d.getX();y+=nearest*d.getY();
                }
            }
            return new Point(b.x(x),b.y(y));
        }
    }
    static final class Route {
        final Note head;
        final long start;
        long end,free,pulse=Long.MIN_VALUE;
        int uses=1;
        boolean flick;
        final NavigableMap<Long,Anchor> keys=new TreeMap<>();
        Route(Note n,Anchor a,long start,long end){
            head=n;this.start=start;this.end=end;
            free=n.getType()==3?end:n.getType()==1?Math.min(end,start+HEAD_SETTLE_MS):start;
            keys.put(start,a);keys.put(free,a);
        }
        Anchor anchor(long t){Map.Entry<Long,Anchor> e=keys.floorEntry(t);return (e==null?keys.firstEntry():e).getValue();}
        Point at(Chart c,long t,Viewport v,TouchBounds b){
            Map.Entry<Long,Anchor> a=keys.floorEntry(t);
            if(a==null)a=keys.firstEntry();
            Map.Entry<Long,Anchor> next=keys.higherEntry(a.getKey());
            if(next==null)return a.getValue().at(c,t,v,b);
            return bridgePoint(a.getValue(),a.getKey(),next.getValue(),next.getKey(),t,c,v,b);
        }
        long boundary(long now,long end){Long k=keys.higherKey(now);return k==null?end:Math.min(k,end);}
        void retain(long t){
            // Freeze an already covered judgement before a later note can change the bridge leading into it.
            if(keys.lastKey()<=t){Anchor current=keys.lastEntry().getValue();keys.put(t,current);free=Math.max(free,t);}
        }
    }
    /** Future TAP/HOLD reservations constrain new contacts and extended bridges. */
    private static final class Occupancy {
        final TreeMap<Long,Integer> counts=new TreeMap<>();
        int at(long t){Map.Entry<Long,Integer> e=counts.floorEntry(t);return e==null?0:e.getValue();}
        boolean room(long a,long b,int limit){
            if(a>=b)return true;
            if(at(a)>=limit)return false;
            for(int n:counts.subMap(a,false,b,false).values())if(n>=limit)return false;
            return true;
        }
        void reserve(long a,long b){
            if(a>=b)return;
            counts.put(b,at(b));counts.put(a,at(a));
            for(Map.Entry<Long,Integer> e:counts.subMap(a,true,b,false).entrySet())e.setValue(e.getValue()+1);
        }
    }
    final Chart chart;final Viewport view;final TouchBounds bounds;final GestureProfile profile;
    final List<Route> routes=new ArrayList<>(),live=new ArrayList<>();
    final Occupancy total=new Occupancy(),yellow=new Occupancy();
    final Set<Note> pinned=Collections.newSetFromMap(new IdentityHashMap<Note,Boolean>());
    final IdentityHashMap<Note,Route> heads=new IdentityHashMap<>();
    final NavigableMap<Long,List<Note>> headTimes=new TreeMap<>();
    final Map<Long,List<Note>> dragTimes=new HashMap<>();
    final Map<Long,Boolean> stableChords=new HashMap<>();
    int shared,sections,missed;
    private static final ThreadLocal<long[]> LAST_STATS=new ThreadLocal<>();
    static long[] lastStats(){long[] stats=LAST_STATS.get();return stats==null?new long[6]:stats.clone();}
    private long strokeRequests,strokeComputations,strokeCandidates,bandRequests,bandEvaluations,noiseChecks;
    private long lastRedHit;
    private IdentityHashMap<Note,Map<Span,Anchor[]>> strokeMemo;
    private IdentityHashMap<Note,Map<Long,Band>> bands;
    private static final Anchor[] NO_STROKE=new Anchor[0];
    private static final class Span {
        final long from,to;Span(long from,long to){this.from=from;this.to=to;}
        public int hashCode(){return Long.hashCode(from)*31+Long.hashCode(to);}
        public boolean equals(Object o){return o instanceof Span&&((Span)o).from==from&&((Span)o).to==to;}
    }
    private static final class Band {
        final Point center,direction;final double magnitude;
        Band(Point center,Point direction){this.center=center;this.direction=direction;magnitude=direction==null?0:Math.hypot(direction.getX(),direction.getY());}
        double distance(Point p){
            if(center==null||magnitude<1e-7)return Double.POSITIVE_INFINITY;
            return Math.abs((p.getX()-center.getX())*direction.getY()-(p.getY()-center.getY())*direction.getX())/magnitude;
        }
    }
    ContactPlanner(Chart c,Viewport v,TouchBounds b,GestureProfile p){chart=c;view=v;bounds=b;profile=p;}
    static GesturePlan build(Chart c,Viewport v,TouchBounds b,GestureProfile p){
        Boolean prev=TapMod.planning.get();TapMod.planning.set(true);
        ContactPlanner planner=new ContactPlanner(c,v,b,p);try{return planner.schedule();}
        finally{LAST_STATS.set(new long[]{planner.strokeRequests,planner.strokeComputations,planner.strokeCandidates,planner.bandRequests,planner.bandEvaluations,planner.noiseChecks,planner.lastRedHit});if(prev==null)TapMod.planning.remove();else TapMod.planning.set(prev);}
    }
    private boolean noise(long a,long b){return TapMod.avoids(chart) && BlockAreaSet.overlaps(chart,a,b);}
    private static Point bridgePoint(Anchor from,long start,Anchor to,long end,long t,Chart chart,Viewport view,TouchBounds bounds){
        if(t>=end)return to.at(chart,t,view,bounds);
        Point p=from.at(chart,t,view,bounds);
        if(t<=start||from==to)return p;
        Point q=to.at(chart,t,view,bounds);
        float f=(t-start)/(float)(end-start);
        return new Point(p.getX()+(q.getX()-p.getX())*f,p.getY()+(q.getY()-p.getY())*f);
    }
    /** Only sever an inter-note gesture when its own path crosses active noise. */
    private boolean bridgeClear(Anchor from,long start,Anchor to,long end){
        if(!noise(start-TapMod.noiseLookBackMs(),end+TapMod.noiseLookAheadMs()))return true;
        Point p=from.at(chart,start,view,bounds);
        try{
            if(from.pin&&to.pin||start>=end){
                TapMod.segment(chart,view,p,to.at(chart,end,view,bounds),start,end);
                return true;
            }
            for(long t=start;t<end;){
                long next=Math.min(t+8,end);
                Point q=bridgePoint(from,start,to,end,next,chart,view,bounds);
                TapMod.segment(chart,view,p,q,t,next);
                p=q;t=next;
            }
            return true;
        }catch(TapMod.NoSafePoint blocked){return false;}
    }
    private boolean covered(Note n,long t,Point p){
        bandRequests++;Band band=null;Map<Long,Band> memo=null;if(bands!=null){memo=bands.get(n);if(memo==null){memo=new HashMap<>();bands.put(n,memo);}band=memo.get(t);}
        if(band==null){bandEvaluations++;
        double sec=Math.max(0,t)/1000.0;
        Point center=chart.position(n,sec,view,false),d=chart.judgeNormal(n,sec,view);
        band=new Band(center,d);if(memo!=null)memo.put(t,band);}
        Point center=band.center,d=band.direction;if(center==null||d==null)return false;
        return band.magnitude>1e-7 && Math.abs((p.getX()-center.getX())*d.getY()-(p.getY()-center.getY())*d.getX())<=radius(view)*band.magnitude;
    }
    private boolean room(Route r,long a,long b,boolean isYellow){
        return total.room(a,b,10) && (!(isYellow || r!=null && r.head.getType()==2) || yellow.room(a,b,LIMIT));
    }
    private Route create(Note n,Anchor anchor,long start,long end){
        Route r=new Route(n,anchor,start,end);routes.add(r);live.add(r);total.reserve(start,end);
        if(n.getType()==2)yellow.reserve(start,end);return r;
    }
    private void extend(Route r,long end){
        if(end<=r.end)return;
        total.reserve(r.end,end);if(r.head.getType()==2)yellow.reserve(r.end,end);r.end=end;
    }
    private Point actual(Route r,long t){
        Anchor a=r.anchor(t);
        return TapMod.withinBounds(chart,a.note,t/1000.0,view,r.at(chart,t,view,bounds),bounds,null,0,a.note.getType()==3,!a.precise&&a.note.getType()!=4);
    }
    private boolean catchHeld(Note n,long time){
        for(Route r:live){
            if(time<r.start||time>=r.end)continue;
            long until=Math.min(time+DRAG_DWELL_MS,r.end-1);
            long from=until-DRAG_DWELL_MS;
            if(from<r.start||from<hit(n)-40)continue;
            try{
                if(covered(n,from,actual(r,from)) &&
                        covered(n,from+DRAG_DWELL_MS/2,actual(r,from+DRAG_DWELL_MS/2)) &&
                        covered(n,until,actual(r,until))){
                    r.retain(until);r.uses++;shared++;return true;
                }
            }catch(TapMod.NoSafePoint blocked){/* Check the other active fingers. */}
        }
        return false;
    }
    private Route mobile(Anchor target,long time,long end,boolean red){
        Route best=null;
        long shortestIdle=Long.MAX_VALUE;
        double shortest=Double.POSITIVE_INFINITY;
        Point to=null;
        for(Route r:live){
            long free=Math.max(r.free,r.pulse),gap=time-free;
            if(!mobileEligible(r,time,end,red))continue;
            if(to==null)to=target.at(chart,time,view,bounds);
            Point from=r.at(chart,free,view,bounds);double dist=Math.hypot(from.getX()-to.getX(),from.getY()-to.getY());
            // With a full dispatch interval to travel, even a screen-wide jump
            // is one MOVE. Keep short transitions clear of the preceding hit.
            if(gap<ChordPlanner.CHUNK_MS && dist>Math.max(1,gap*view.getHeight()*.006))continue;
            // Extending an older finger retroactively holds it down through the
            // intervening notes. Prefer the finger that just played them.
            long idle=Math.max(0,time-r.end);
            if((idle<shortestIdle || idle==shortestIdle && dist<shortest)
                    &&bridgeClear(r.keys.lastEntry().getValue(),r.keys.lastKey(),target,time)){
                shortestIdle=idle;shortest=dist;best=r;
            }
        }
        return best;
    }
    private boolean mobileEligible(Route r,long time,long end,boolean red){
        long gap=time-Math.max(r.free,r.pulse);
        // A released blue head only links to a nearby special note when tap
        // combinations are enabled. Long drag-chain gaps apply to drags themselves.
        if(r.uses==1 && r.head.getType()==1 && time>r.end &&
                (!profile.combineShots || time-hit(r.head)>160))return false;
        return gap>=0 && gap<=(red?360:1200) && room(r,r.end,end,!red) &&
                (r.end>=time || (red?profile.connectFlicks||profile.combineShots:profile.connectDrags||profile.catchDrags));
    }
    private boolean mayMobile(long time,long end,boolean red){for(Route r:live)if(mobileEligible(r,time,end,red))return true;return false;}
    private List<TouchEvent> sourceEvents(){
        List<TouchEvent> events=new ArrayList<>();
        PriorityQueue<Long> releases=new PriorityQueue<>();
        for(TouchEvent e:TouchEvent.Companion.forChart(chart)){
            Note n=e.getNote();
            if(n.getType()==1 || n.getType()==3){
                long start=hit(n);
                long end=n.getType()==1?start+48:Math.max(start+1,Math.round(n.getEndSeconds()*1000));
                while(!releases.isEmpty() && releases.peek()<=start)releases.remove();
                // Keep the original HOLD (including native position animation) and a separate
                // DOWN for every head. Use the stacked-hold fallback only under real pressure.
                if(releases.size()>=10)return ChordPlanner.forChart(chart,false,false);
                releases.add(end);
                e=new TouchEvent(n,start,end);
            }
            events.add(e);
        }
        return events;
    }
    private Band band(Note n,long ms){
        double seconds=Math.max(0,ms)/1000.;
        return new Band(chart.position(n,seconds,view,false),chart.judgeNormal(n,seconds,view));
    }
    private int competitors(Point point,List<Band[]> other){
        int count=0;
        // Phira Pro uses X_DIFF_MAX = .23625 in a two-unit-wide world.
        double threshold=view.getWidth()*.119+2;
        for(Band[] strip:other)for(Band pose:strip){
            if(pose.distance(point)<=threshold){count++;break;}
        }
        return count;
    }
    private int chordRisk(Point point,Band[] own,List<Band[]> other){
        int score=0;
        double width=view.getWidth();
        for(Band[] peer:other){
            boolean overlaps=false,canSteal=false;
            for(int i=0;i<peer.length;i++){
                double distance=peer[i].distance(point);
                if(distance>width*.119+2)continue;
                overlaps=true;
                // All distances within the native .13175-unit core have equal
                // rank. A point near the edge may still prefer its own head
                // when the strips cannot be separated within the touch bounds.
                if(distance<=Math.max(width*.065875,own[i].distance(point))+width*.008)canSteal=true;
            }
            if(overlaps)score++;
            if(canSteal)score+=other.size()+1;
        }
        return score;
    }
    private boolean stableChord(List<Note> notes,long start){
        int count=notes.size();
        if(count>6)return false;
        Point[] original=new Point[count];
        try{
            for(int i=0;i<count;i++){
                Note n=notes.get(i);
                original[i]=TapMod.withinBounds(chart,n,start/1000.,view,new Anchor(n).at(chart,start,view,bounds),bounds);
            }
        }catch(TapMod.NoSafePoint blocked){return false;}
        double width=view.getWidth();
        for(long t=start;t<=start+16;t+=8){
            double[][] ranks=new double[count][count];
            for(int j=0;j<count;j++){
                Band band=band(notes.get(j),t);
                for(int i=0;i<count;i++){
                    double distance=band.distance(original[i]);
                    ranks[i][j]=distance>width*.116?Double.POSITIVE_INFINITY:Math.max(distance,width*.065875);
                }
            }
            int all=(1<<count)-1;
            if(!everyOrderMatches(ranks,all,all,width*.006,new HashSet<Integer>()))return false;
        }
        return true;
    }
    private boolean everyOrderMatches(double[][] ranks,int fingers,int notes,double tolerance,Set<Integer> verified){
        if(fingers==0)return true;
        int key=(fingers<<ranks.length)|notes;
        if(verified.contains(key))return true;
        for(int i=0;i<ranks.length;i++){
            if((fingers&(1<<i))==0)continue;
            double best=Double.POSITIVE_INFINITY;
            for(int j=0;j<ranks.length;j++)if((notes&(1<<j))!=0)best=Math.min(best,ranks[i][j]);
            if(!Double.isFinite(best))return false;
            for(int j=0;j<ranks.length;j++){
                if((notes&(1<<j))!=0 && ranks[i][j]<=best+tolerance &&
                    !everyOrderMatches(ranks,fingers^(1<<i),notes^(1<<j),tolerance,verified))return false;
            }
        }
        verified.add(key);
        return true;
    }
    private Anchor specialDown(Anchor target,long start,long arrival){
        if(arrival<=start)return target;
        List<Band[]> heads=new ArrayList<>();
        // A new finger for a drag/flick is still a click to Phira. It can
        // consume an upcoming head before the special note is recognised.
        for(List<Note> notes:headTimes.subMap(start-18,true,start+220,true).values()){
            for(Note n:notes)heads.add(new Band[]{band(n,start),band(n,start+12)});
        }
        if(heads.isEmpty())return target;
        Point origin=target.at(chart,start,view,bounds);
        int initial=competitors(origin,heads);
        if(initial==0)return target;
        int bestCount=initial;double shortest=Double.POSITIVE_INFINITY;
        Anchor best=target;
        for(float normal:new float[]{0,-.18f,.18f,-.36f,.36f,-.56f,.56f})
        for(float lateral:new float[]{0,-.85f,.85f,-1.7f,1.7f}){
            TapMod.checkPlanningCancelled();
            Anchor candidate=new Anchor(target.note,lateral,normal,false);
            Point p=candidate.at(chart,start,view,bounds);
            int count=competitors(p,heads);
            double distance=Math.hypot(p.getX()-origin.getX(),p.getY()-origin.getY());
            if(count>=initial||count>bestCount||(count==bestCount&&distance>=shortest))continue;
            if(!TapMod.clearAt(chart,view,p,start/1000.))continue;
            Anchor fixed=pixel(target.note,p).certified();
            if(!bridgeClear(fixed,start,target,arrival))continue;
            best=fixed;bestCount=count;shortest=distance;
        }
        return best;
    }
    private Anchor distinctHead(Note n,long start,Point nominal){
        List<Note> chord=headTimes.get(start);
        if(chord==null||chord.size()<2)return null;
        // Preserve a complete, order-independent native chord. Moving a HOLD
        // sideways for extra head clearance can steer its tail into moving noise.
        if(stableChords.computeIfAbsent(start,ignored->chord.stream().anyMatch(head->head.getType()==3)&&stableChord(chord,start)))return null;
        List<Band[]> other=new ArrayList<>();
        for(Note peer:chord)if(peer!=n){
            other.add(new Band[]{band(peer,start),band(peer,start+8),band(peer,start+16)});
        }
        Band[] own={band(n,start),band(n,start+8),band(n,start+16)};
        int initial=chordRisk(nominal,own,other);
        if(initial==0)return null;
        int bestCount=initial;double shortest=Double.POSITIVE_INFINITY;
        Anchor best=null;
        for(float normal:new float[]{0,.16f,-.16f,.34f,-.34f,.55f,-.55f,.8f,-.8f})
        for(float lateral:new float[]{0,.45f,-.45f,.84f,-.84f}){
            TapMod.checkPlanningCancelled();
            Anchor proposal=new Anchor(n,lateral,normal,false);
            Point p=proposal.at(chart,start,view,bounds);
            if(chordRisk(p,own,other)>=initial)continue;
            try{p=TapMod.withinBounds(chart,n,start/1000.,view,p,bounds);}
            catch(TapMod.NoSafePoint blocked){continue;}
            boolean accepts=true;
            for(Band strip:own)if(strip.distance(p)>radius(view)){accepts=false;break;}
            if(!accepts)continue;
            int count=chordRisk(p,own,other);
            double travel=Math.hypot(p.getX()-nominal.getX(),p.getY()-nominal.getY());
            if(count>=initial || count>bestCount || (count==bestCount && travel>=shortest))continue;
            // A HOLD retains its original moving judgement axis. The tap only
            // needs this unique, stationary DOWN and can release on the same spot.
            Anchor candidate=n.getType()==3?proposal:pixel(n,p);
            if(n.getType()==1){
                try{TapMod.segment(chart,view,p,p,start,start+48);}
                catch(TapMod.NoSafePoint movingNoise){continue;}
            }
            best=candidate.certified();bestCount=count;shortest=travel;
        }
        return best;
    }
    private Anchor catchChordDrag(Note tap,long start,Anchor head){
        List<Note> drags=dragTimes.get(start);
        if(tap.getType()!=1 || drags==null || headTimes.get(start).size()!=1)return head;
        Point original=head.at(chart,start,view,bounds);
        Band own=band(tap,start);
        List<Band[]> strips=new ArrayList<>();
        for(Note drag:drags)strips.add(new Band[]{band(drag,start),band(drag,start+10),band(drag,start+20)});
        Anchor best=head;int bestCount=0;double shortest=Double.POSITIVE_INFINITY;
        for(float normal:new float[]{0,.12f,-.12f,.24f,-.24f,.36f,-.36f,.52f,-.52f,.75f,-.75f,1,-1})
        for(float lateral:new float[]{0,.4f,-.4f,.72f,-.72f,.92f,-.92f,1.12f,-1.12f}){
            Point p;
            if(normal==0&&lateral==0)p=original;
            else {
                double nx=own.direction.getX()/own.magnitude,ny=own.direction.getY()/own.magnitude;
                p=new Point(bounds.x((float)(original.getX()+nx*normal*view.getHeight()+ny*lateral*radius(view))),
                    bounds.y((float)(original.getY()+ny*normal*view.getHeight()-nx*lateral*radius(view))));
            }
            // Keep the shared DOWN inside the native click strip even when the
            // centre of that strip is forbidden by a noise area.
            if(own.distance(p)>view.getWidth()*.113)continue;
            int count=0;
            for(Band[] strip:strips)if(strip[0].distance(p)<radius(view)*.88 && strip[1].distance(p)<radius(view)*.88 && strip[2].distance(p)<radius(view)*.88)count++;
            double travel=Math.hypot(p.getX()-original.getX(),p.getY()-original.getY());
            if(count==0 || count<bestCount || count==bestCount&&travel>=shortest)continue;
            try{TapMod.segment(chart,view,p,p,start,start+48);}
            catch(TapMod.NoSafePoint blocked){continue;}
            best=pixel(tap,p).certified();bestCount=count;shortest=travel;
            if(travel==0&&count==strips.size())return best;
        }
        return best;
    }
    private Anchor headAnchor(TouchEvent e){
        Note n=e.getNote();
        Anchor follow=new Anchor(n);
        long start=e.getStartMs();
        try{
            Point down=TapMod.withinBounds(chart,n,start/1000.0,view,follow.at(chart,start,view,bounds),bounds);
            Anchor distinct=distinctHead(n,start,down);
            if(distinct!=null)return distinct;
            if(n.getType()!=1)return follow;
            TapMod.segment(chart,view,down,down,start,e.getEndMs());
            return catchChordDrag(n,start,pixel(n,down));
        }catch(TapMod.NoSafePoint movingNoise){return follow;}
    }
    private GesturePlan schedule(){
        List<TouchEvent> events=sourceEvents();
        events.sort((a,b)->{int t=Long.compare(hit(a.getNote()),hit(b.getNote()));return t!=0?t:Integer.compare(a.getNote().getId(),b.getNote().getId());});
        for(TouchEvent event:events){
            Note n=event.getNote();
            if(n.getType()==1 || n.getType()==3)headTimes.computeIfAbsent(hit(n),ignored->new ArrayList<>()).add(n);
            if(n.getType()==2)dragTimes.computeIfAbsent(hit(n),ignored->new ArrayList<>()).add(n);
        }
        for(TouchEvent e:events){
            Note n=e.getNote();if(n.getType()!=1 && n.getType()!=3)continue;
            Anchor head=headAnchor(e);
            Route r=new Route(n,head,e.getStartMs(),e.getEndMs());
            if(n.getType()==3 && head.precise && r.end-r.start>HEAD_SETTLE_MS+32){
                long settled=r.start+HEAD_SETTLE_MS,returnAt=settled+32;
                Anchor follow=new Anchor(n);
                if(bridgeClear(head,settled,follow,returnAt)){
                    r.keys.put(settled,head);
                    r.keys.put(returnAt,follow);
                    r.keys.put(r.end,follow);
                }
            }
            heads.put(n,r);routes.add(r);total.reserve(r.start,r.end);
        }
        fullRuns(events);
        for(int at=0;at<events.size();){
            TapMod.checkPlanningCancelled();
            long t=hit(events.get(at).getNote());int end=at+1;
            while(end<events.size()&&hit(events.get(end).getNote())==t)end++;
            live.removeIf(r->t-r.end>1200);
            for(int i=at;i<end;i++){Route r=heads.get(events.get(i).getNote());if(r!=null)live.add(r);}
            ArrayList<Note> drags=new ArrayList<>();
            for(int i=at;i<end;i++){
                Note n=events.get(i).getNote();
                if(n.getType()==4)red(n,t);
                if(n.getType()==2&&!pinned.contains(n))drags.add(n);
            }
            drags(drags,t);at=end;
        }
        return GesturePlan.routed(routes,shared,pinned.size(),sections,missed);
    }
    private Anchor pixel(Note n,Point p) {
        return new Anchor(n,(p.getX()-view.getLeft())/view.getWidth(),(p.getY()-view.getTop())/view.getHeight(),true);
    }
    private Anchor[] stroke(Anchor base,Note red,long start,long end){
        strokeRequests++;TapMod.checkPlanningCancelled();Map<Span,Anchor[]> memo=null;Span span=null;
        if(strokeMemo!=null&&!base.pin&&base.dx==0&&base.dy==0){memo=strokeMemo.get(base.note);if(memo==null){memo=new HashMap<>();strokeMemo.put(base.note,memo);}span=new Span(start,end);Anchor[] old=memo.get(span);if(old!=null)return old==NO_STROKE?null:old;}
        strokeComputations++;Anchor[] result=computeStroke(base,red,start,end);if(memo!=null)memo.put(span,result==null?NO_STROKE:result);return result;
    }
    private Anchor[] computeStroke(Anchor base,Note red,long start,long end){
        Point raw=base.at(chart,start,view,bounds),origin;
        try{origin=TapMod.withinBounds(chart,base.note,start/1000.0,view,raw,bounds,null,0,false,true);}
        catch(TapMod.NoSafePoint blocked){return null;}
        Point n=chart.judgeNormal(base.note,Math.max(0,start)/1000.0,view);
        float length=(float)Math.hypot(n.getX(),n.getY());
        if(length<.001f)return null;
        float nx=n.getX()/length,ny=n.getY()/length;
        float[][] directions={{nx,ny},{-nx,-ny},{ny,-nx},{-ny,nx}};
        // Construct the swipe at the escaped position. Projecting two blocked endpoints
        // independently can collapse a red note to a zero-length stationary touch.
        for(float size:new float[]{.095f,.065f,.045f})for(float[] d:directions){
            float dx=d[0]*view.getHeight()*size,dy=d[1]*view.getHeight()*size;
            for(float center:new float[]{0,.5f,1}){
                TapMod.checkPlanningCancelled();strokeCandidates++;
                Point a=new Point(bounds.x(origin.getX()-dx*center),bounds.y(origin.getY()-dy*center));
                Point b=new Point(bounds.x(a.getX()+dx),bounds.y(a.getY()+dy));
                double distance=Math.hypot(b.getX()-a.getX(),b.getY()-a.getY());
                // Phira Pro uses a two-unit-wide world and tests speed on the second MOVE.
                // Leave room above its 1.14 units/s threshold after integer pixel rounding.
                if(distance<Math.hypot(dx,dy)*.88 || distance<view.getWidth()*.00065*(end-start)+1)continue;
                boolean clear=true;Point[] samples=new Point[9];long[] times=new long[9];
                for(int step=0;step<=8;step++) {
                    float f=step/8f;long t=start+Math.round((end-start)*f);
                    Point sample=new Point(a.getX()+(b.getX()-a.getX())*f,a.getY()+(b.getY()-a.getY())*f);
                    if(!covered(red,t,sample) || base.note!=red&&!covered(base.note,t,sample)){clear=false;break;}samples[step]=sample;times[step]=t;
                }
                if(!clear)continue;
                // Reject an unreachable judgement band before expensive noise
                // queries. The same nine noise samples and segment still run
                // for every geometrically admissible candidate.
                for(int step=0;step<=8;step++){noiseChecks++;if(!TapMod.clearAt(chart,view,samples[step],times[step]/1000.0)){clear=false;break;}}
                if(!clear)continue;
                try{TapMod.segment(chart,view,a,b,start,end);}
                catch(TapMod.NoSafePoint blocked){continue;}
                return new Anchor[]{pixel(base.note,a),pixel(base.note,b)};
            }
        }
        return null;
    }
    private void red(Note n,long hit){
        lastRedHit=hit;
        strokeMemo=new IdentityHashMap<>();bands=new IdentityHashMap<>();try{redCandidates(n,hit);}finally{strokeMemo=null;bands=null;}
    }
    private static void placeStroke(Route route,Anchor[] stroke,long start,long end){
        Anchor from=stroke[0],to=stroke[1];
        long middle=start+(end-start)/2;
        float f=(middle-start)/(float)(end-start);
        route.keys.put(start,from);
        route.keys.put(middle,new Anchor(from.note,from.dx+(to.dx-from.dx)*f,from.dy+(to.dy-from.dy)*f,true));
        route.keys.put(end,to);
        route.flick=true;
    }
    private void redCandidates(Note n,long hit){
        // Prefer a timely continuing finger. A late reuse must not beat a fresh
        // contact that can start and move during the actual judgement window.
        for(int[] delays:new int[][]{{0,12,-12,24,-24},{36,-36}})
        for(boolean fresh:new boolean[]{false,true})for(int delay:delays)for(int duration:new int[]{48,40,32,24}){
            TapMod.checkPlanningCancelled();
            long start=hit+delay-duration/2,end=start+duration;
            long down=start-RED_DOWN_MS,up=end+RED_TAIL_MS;
            if(!fresh){
                for(Route r:live){
                    long approach=Math.max(r.start,Math.max(r.pulse,start-RED_TAIL_MS));
                    if(r.head.getType()!=3 || approach>=start || Math.round(r.head.getEndSeconds()*1000)<end+RED_TAIL_MS)continue;
                    Anchor base=new Anchor(r.head);Anchor[] stroke=stroke(base,n,start,end);
                    if(stroke==null||!bridgeClear(base,approach,stroke[0],start)||!bridgeClear(stroke[1],end,base,end+RED_TAIL_MS))continue;
                    r.keys.put(approach,base);placeStroke(r,stroke,start,end);r.keys.put(end+RED_TAIL_MS,base);
                    r.pulse=end+RED_TAIL_MS;r.uses++;return;
                }
            }
            // If no route meets mobile's time/capacity/noise prerequisites,
            // every possible own-note stroke would be discarded immediately.
            // Skip that whole search, retaining the same later fresh attempts.
            if(!fresh&&!mayMobile(start,up,true)||fresh&&!room(null,down,up,false))continue;
            Anchor base=new Anchor(n);Anchor[] stroke=stroke(base,n,start,end);
            if(stroke==null)continue;
            Route r=fresh?null:mobile(stroke[0],start,up,true);
            if(r==null){
                if(!fresh||!room(null,down,up,false))continue;
                Point at=stroke[0].at(chart,down,view,bounds);
                try{TapMod.segment(chart,view,at,at,down,start);}
                catch(TapMod.NoSafePoint blocked){continue;}
                r=create(n,specialDown(stroke[0],down,start),down,up);
            }else{extend(r,up);r.uses++;}
            placeStroke(r,stroke,start,end);r.free=end;r.pulse=end;return;
        }
        missed++;
    }
    private void drags(List<Note> pending,long hit){
        for(int delay:new int[]{0,20,40,60}){
            long t=hit+delay;
            long until=t+DRAG_DWELL_MS;
            pending.removeIf(n->catchHeld(n,t) || delay==0 && (catchHeld(n,t-8) || catchHeld(n,t-16)));
            List<Note> deferred=new ArrayList<>();
            while(!pending.isEmpty()){
                Note first=pending.get(0);Anchor best=null;Route selected=null;int score=0;
                for(int j=0;j<Math.min(pending.size(),28);j++)for(float lateral:new float[]{0,.82f,-.82f}){
                    Anchor a=new Anchor(pending.get(j),lateral,0,false);Point p;
                    try{p=TapMod.withinBounds(chart,a.note,t/1000.0,view,a.at(chart,t,view,bounds),bounds);}
                    catch(TapMod.NoSafePoint blocked){continue;}
                    if(!covered(first,t,p))continue;
                    int count=0;for(Note n:pending)if(covered(n,t,p))count++;
                    Route reuse=mobile(a,t,until+1,false);
                    if(best==null || reuse!=null&&selected==null || (reuse==null)==(selected==null)&&count>score){
                        best=a;selected=reuse;score=count;
                    }
                    if(selected!=null&&score==pending.size())break;
                }
                if(best==null){deferred.add(pending.remove(0));continue;}
                if(selected==null){
                    if(delay<40 && (catchHeld(first,t+8) || catchHeld(first,t+16))){
                        pending.remove(0);continue;
                    }
                    long start=t-DRAG_DWELL_MS;
                    if(!room(null,start,until+1,true))start=t;
                    if(!room(null,start,until+1,true)){deferred.add(pending.remove(0));continue;}
                    selected=create(best.note,specialDown(best,start,t),start,until+1);
                }else{if(selected.head.getType()!=2)yellow.reserve(selected.end,until+1);extend(selected,until+1);selected.uses++;}
                selected.keys.put(t,best);selected.keys.put(until,best);selected.free=until;
                Point p=actual(selected,t);int before=pending.size();
                pending.removeIf(n->covered(n,t,p));
                if(before==pending.size())throw new IllegalStateException(tr("黄键分配与实际轨迹不一致"));
                shared+=before-pending.size()-1;
            }
            pending.addAll(deferred);
            if(pending.isEmpty())return;
        }
        missed+=pending.size();
    }
    private void fullRuns(List<TouchEvent> notes){
        for(int i=0;i<notes.size();){
            if(notes.get(i).getNote().getType()!=2){i++;continue;}
            int end=i+1;
            while(end<notes.size()&&notes.get(end).getNote().getType()==2&&
                  hit(notes.get(end).getNote())-hit(notes.get(end-1).getNote())<=250)end++;
            int left=i;boolean dense=false;
            for(int j=i;j<end;j++){
                while(hit(notes.get(j).getNote())-hit(notes.get(left).getNote())>250)left++;
                if(j-left+1>=8){dense=true;break;}
            }
            if(dense)pins(notes.subList(i,end));i=end;
        }
    }
    private void pins(List<TouchEvent> events){
        long start=hit(events.get(0).getNote())-18,end=hit(events.get(events.size()-1).getNote())+18;
        if(noise(start,end)||!total.room(start,end,1))return;
        Note head=events.get(0).getNote();
        for(int columns:new int[]{6,1,3,2}){
            Anchor[] pins=new Anchor[LIMIT];int rows=LIMIT/columns,necessary=0;
            for(int k=0;k<LIMIT;k++)pins[k]=new Anchor(head,(k%columns+.5f)/columns,(k/columns+.5f)/rows,true);
            boolean all=true;
            for(TouchEvent event:events){
                Note n=event.getNote();long t=hit(n);int mask=0;
                for(int k=0;k<LIMIT;k++){
                    Point p=pins[k].at(chart,t,view,bounds);
                    if(covered(n,t,p)&&covered(n,t-8,p)&&covered(n,t+8,p))mask|=1<<k;
                }
                if(mask==0){all=false;break;}
                if(Integer.bitCount(mask)==1)necessary|=mask;
            }
            if(!all||Integer.bitCount(necessary)<5)continue;
            for(int k=0;k<LIMIT;k++){
                routes.add(new Route(events.get(k).getNote(),pins[k],start,end));total.reserve(start,end);yellow.reserve(start,end);
            }
            for(TouchEvent e:events)pinned.add(e.getNote());
            sections++;return;
        }
    }
}
