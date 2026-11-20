package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import io.github.phiscript.engine.*;
import java.util.*;

/** Reserve fresh heads first. DRAG checks an occupied judgement strip; FLICK needs movement. */
final class ContactPlanner {
    static final int LIMIT=6;
    private static final int RED_DOWN_MS=12,RED_TAIL_MS=16;
    static float radius(Viewport v){return Math.min(v.getWidth()*.098f,v.getHeight()*.20f);}
    static long hit(Note n){return Math.round(n.getTimeSeconds()*1000);}
    static final class Anchor {
        final Note note;
        final float dx,dy;
        final boolean pin;
        Anchor(Note n,float dx,float dy,boolean pin){this.note=n;this.dx=dx;this.dy=dy;this.pin=pin;}
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
            free=n.getType()==3?end:n.getType()==1?start+12:start;
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
        return TapMod.withinBounds(chart,r.anchor(t).note,t/1000.0,view,r.at(chart,t,view,bounds),bounds);
    }
    private boolean catchHeld(Note n,long time){
        for(Route r:live){
            if(time<r.start||time>=r.end)continue;
            try{
                if(covered(n,time,actual(r,time))){
                    r.retain(Math.min(time+5,r.end-1));shared++;return true;
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
    private boolean mobileEligible(Route r,long time,long end,boolean red){long free=Math.max(r.free,r.pulse),gap=time-free;return gap>=0&&gap<=(red?360:1200)&&room(r,r.end,end,!red)&&(r.end>=time||(red?profile.connectFlicks||profile.combineShots:profile.connectDrags||profile.catchDrags));}
    private boolean mayMobile(long time,long end,boolean red){for(Route r:live)if(mobileEligible(r,time,end,red))return true;return false;}
    private GesturePlan schedule(){
        List<TouchEvent> events=ChordPlanner.forChart(chart,false,false);
        events.sort((a,b)->{int t=Long.compare(hit(a.getNote()),hit(b.getNote()));return t!=0?t:Integer.compare(a.getNote().getId(),b.getNote().getId());});
        for(TouchEvent e:events){
            Note n=e.getNote();if(n.getType()!=1 && n.getType()!=3)continue;
            Route r=new Route(n,new Anchor(n),e.getStartMs(),e.getEndMs());
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
        try{origin=TapMod.withinBounds(chart,base.note,start/1000.0,view,raw,bounds);}
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
                if(Math.hypot(b.getX()-a.getX(),b.getY()-a.getY())<Math.hypot(dx,dy)*.88)continue;
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
                    r.keys.put(approach,base);r.keys.put(start,stroke[0]);r.keys.put(end,stroke[1]);r.keys.put(end+RED_TAIL_MS,base);
                    r.flick=true;r.pulse=end+RED_TAIL_MS;r.uses++;return;
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
                r=create(n,stroke[0],down,up);
            }else{extend(r,up);r.uses++;}
            r.keys.put(start,stroke[0]);r.keys.put(end,stroke[1]);r.flick=true;r.free=end;r.pulse=end;return;
        }
        missed++;
    }
    private void drags(List<Note> pending,long hit){
        for(int delay:new int[]{0,20,40,60}){
            long t=hit+delay;
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
                    Route reuse=mobile(a,t,t+16,false);
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
                    long start=t-16;
                    if(!room(null,start,t+16,true))start=t;
                    if(!room(null,start,t+16,true)){deferred.add(pending.remove(0));continue;}
                    selected=create(best.note,best,start,t+16);
                }else{if(selected.head.getType()!=2)yellow.reserve(selected.end,t+16);extend(selected,t+16);selected.uses++;}
                selected.keys.put(t,best);selected.keys.put(t+8,best);selected.free=t+8;
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
