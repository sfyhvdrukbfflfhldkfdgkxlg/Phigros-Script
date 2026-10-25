package io.github.phiscript.input;

import io.github.phiscript.engine.*;
import java.util.*;

/** Plans hit-time groups, then keeps one time-parametrized path per real contact. */
final class UnifiedGesturePlanner {
    static final Viewport REF=new Viewport(0,0,1350,900);
    static final TouchBounds AREA=new TouchBounds(0,0,1349,899);
    static long hit(TouchEvent e){return Math.round(e.getNote().getTimeSeconds()*1000);}
    static int type(TouchEvent e){return e.getNote().getType();}
    static boolean track(TouchEvent a,TouchEvent b){return a.getNote().getLineIndex()==b.getNote().getLineIndex()&&a.getNote().getAbove()==b.getNote().getAbove();}
    static Point anchor(Chart c,TouchEvent e,long t,Viewport v){Point p=c.position(e.getNote(),Math.max(0,t)/1000.0,v,false);if(p==null)throw new IllegalArgumentException("无法计算混合手势落点");return p;}
    static double distance(Point a,Point b){return Math.hypot(a.getX()-b.getX(),a.getY()-b.getY());}
    static final class Leg {
        final TouchEvent node;long from,to;final float begin,end;
        Leg(TouchEvent n,long from,long to,float begin,float end){node=n;this.from=from;this.to=to;this.begin=begin;this.end=end;}
    }
    static final class Contact {
        final List<TouchEvent> nodes=new ArrayList<>();final List<Leg> legs=new ArrayList<>();
        long start,end;boolean flick;TouchEvent firstRed;
        Contact(TouchEvent e,boolean bridge,GestureProfile p){nodes.add(e);start=type(e)==2&&p.catchDrags&&bridge?Math.min(e.getStartMs(),hit(e)-100):e.getStartMs();end=e.getEndMs();}
        TouchEvent last(){return nodes.get(nodes.size()-1);}
        void add(TouchEvent e){nodes.add(e);end=Math.max(end,e.getEndMs());}
        Point offset(Chart c,Leg leg,long t,float amount,Viewport v,TouchBounds bounds,boolean[] bounded){
            Point p=anchor(c,leg.node,t,v);float x=bounds.x(p.getX()),y=bounds.y(p.getY());if(x!=p.getX()||y!=p.getY())bounded[0]=true;
            if(amount==0||firstRed==null)return new Point(x,y);
            Point normal=c.judgeNormal(firstRed.getNote(),Math.max(0,hit(firstRed))/1000.0,v),origin=anchor(c,firstRed,hit(firstRed),v);
            float nx=normal.getX(),ny=normal.getY(),length=(float)Math.hypot(nx,ny);if(length<.001f){nx=0;ny=-1;length=1;}nx/=length;ny/=length;
            if(nx*((bounds.left+bounds.right)*.5f-origin.getX())+ny*((bounds.top+bounds.bottom)*.5f-origin.getY())<0){nx=-nx;ny=-ny;}
            float[] end=along(bounds,x,y,nx,ny,v.getHeight()*.1f);
            return new Point(x+(end[0]-x)*amount,y+(end[1]-y)*amount);
        }
        Point point(Chart c,long t,Viewport v,TouchBounds bounds,boolean[] bounded){
            int lo=0,hi=legs.size();while(lo<hi){int m=(lo+hi)>>>1;if(legs.get(m).from<=t)lo=m+1;else hi=m;}
            if(lo==0){Leg first=legs.get(0);return offset(c,first,t,first.begin,v,bounds,bounded);}
            Leg a=legs.get(lo-1);float progress=a.to<=a.from?1:clamp((t-a.from)/(float)(a.to-a.from));
            Point p=offset(c,a,t,a.begin+(a.end-a.begin)*progress,v,bounds,bounded);
            if(t>a.to&&lo<legs.size()){Leg b=legs.get(lo);Point q=offset(c,b,t,b.begin,v,bounds,bounded);return mix(p,q,(t-a.to)/(float)Math.max(1,b.from-a.to));}
            return p;
        }
    }
    static float clamp(float value){return Math.max(0,Math.min(1,value));}
    static Point mix(Point a,Point b,float t){t=clamp(t);return new Point(a.getX()+(b.getX()-a.getX())*t,a.getY()+(b.getY()-a.getY())*t);}
    /** Follow the judge normal, keeping parallel lanes apart and the entire stroke inside. */
    static float[] along(TouchBounds b,float x,float y,float nx,float ny,float length){
        float room=length;if(nx>0)room=Math.min(room,(b.right-x)/nx);else if(nx<0)room=Math.min(room,(b.left-x)/nx);
        if(ny>0)room=Math.min(room,(b.bottom-y)/ny);else if(ny<0)room=Math.min(room,(b.top-y)/ny);
        if(room<length*.45f){nx=-nx;ny=-ny;room=length;if(nx>0)room=Math.min(room,(b.right-x)/nx);else if(nx<0)room=Math.min(room,(b.left-x)/nx);if(ny>0)room=Math.min(room,(b.bottom-y)/ny);else if(ny<0)room=Math.min(room,(b.top-y)/ny);}
        return new float[]{b.x(x+nx*Math.max(0,room)),b.y(y+ny*Math.max(0,room))};
    }
    private static double cost(Chart c,Contact prior,TouchEvent next,GestureProfile p,boolean bridge){
        TouchEvent a=prior.last();int x=type(a),y=type(next);long gap=hit(next)-hit(a);if(!track(a,next)||gap<0||!bridge&&next.getStartMs()>prior.end)return 1e12;
        long limit;
        if(x==1){if(!p.combineShots)return 1e12;limit=160;}
        else if(x==2&&y==2){if(!p.connectDrags&&!p.catchDrags)return 1e12;limit=p.catchDrags?1200:p.gapMs;}
        else if(x==4&&y==4){if(!p.connectFlicks||gap<32)return 1e12;limit=300;}
        else {if(!p.combineShots)return 1e12;limit=300;}
        if(gap>limit||gap==0&&x!=1)return 1e12;
        Point from=anchor(c,a,hit(a),REF),to=anchor(c,next,hit(next),REF);double d=distance(from,to);
        if(d>p.distance*900||d>Math.max(x==1&&gap==0?8:0,gap*1.8))return 1e12;
        if(x==1&&gap>0&&d>Math.max(0,gap-12)*1.8)return 1e12;
        return d+gap*.01+(x==2&&y==2?-200:x==1?0:2);
    }
    /** Rectangular Hungarian assignment: an incoming chord never steals the same finger twice. */
    private static int[] assign(Chart chart,List<Contact> live,List<TouchEvent> notes,GestureProfile p,boolean bridge){
        int n=Math.min(64,notes.size()),m=live.size()+n;int[] answer=new int[notes.size()];Arrays.fill(answer,-1);if(n==0||live.isEmpty())return answer;
        double[][] a=new double[n][m];for(int i=0;i<n;i++)for(int j=0;j<m;j++)a[i][j]=j<live.size()?cost(chart,live.get(j),notes.get(i),p,bridge):1e6;
        double[] u=new double[n+1],v=new double[m+1];int[] match=new int[m+1],way=new int[m+1];
        for(int i=1;i<=n;i++){match[0]=i;int j0=0;double[] min=new double[m+1];Arrays.fill(min,Double.POSITIVE_INFINITY);boolean[] used=new boolean[m+1];
            do {used[j0]=true;int i0=match[j0],j1=0;double delta=Double.POSITIVE_INFINITY;
                for(int j=1;j<=m;j++)if(!used[j]){double cur=a[i0-1][j-1]-u[i0]-v[j];if(cur<min[j]){min[j]=cur;way[j]=j0;}if(min[j]<delta){delta=min[j];j1=j;}}
                for(int j=0;j<=m;j++)if(used[j]){u[match[j]]+=delta;v[j]-=delta;}else min[j]-=delta;j0=j1;
            }while(match[j0]!=0);
            do {int j1=way[j0];match[j0]=match[j1];j0=j1;}while(j0!=0);
        }
        for(int j=1;j<=m;j++)if(match[j]>0&&j-1<live.size()&&a[match[j]-1][j-1]<1e6)answer[match[j]-1]=j-1;
        // A crossing bridge is not forced merely to save a finger.
        for(int i=0;i<n;i++)if(answer[i]>=0)for(int j=0;j<i;j++)if(answer[j]>=0){
            Contact x=live.get(answer[i]),y=live.get(answer[j]);long start=Math.max(hit(x.last()),hit(y.last())),end=hit(notes.get(i));
            Point xa=anchor(chart,x.last(),start,REF),xb=anchor(chart,notes.get(i),end,REF),ya=anchor(chart,y.last(),start,REF),yb=anchor(chart,notes.get(j),end,REF);
            if(distance(xb,yb)<.1||end<=start)continue;
            double dx=xa.getX()-ya.getX(),dy=xa.getY()-ya.getY(),vx=(xb.getX()-xa.getX())-(yb.getX()-ya.getX()),vy=(xb.getY()-xa.getY())-(yb.getY()-ya.getY());
            double q=vx*vx+vy*vy,t=q<.001?0:Math.max(0,Math.min(1,-(dx*vx+dy*vy)/q));
            if(t>.05&&t<.95&&Math.hypot(dx+vx*t,dy+vy*t)<4)answer[i]=-1;
        }
        return answer;
    }
    static GesturePlan build(Chart chart,GestureProfile p,boolean bridge,Set<Note> skipped){
        List<TouchEvent> source=ChordPlanner.forChart(chart,false,false);source.removeIf(e->skipped.contains(e.getNote()));source.sort((a,b)->{int n=Long.compare(hit(a),hit(b));return n!=0?n:Integer.compare(a.getNote().getId(),b.getNote().getId());});
        List<Contact> all=new ArrayList<>(),live=new ArrayList<>();List<TouchEvent> holds=new ArrayList<>(),activeHolds=new ArrayList<>();for(TouchEvent e:source)if(type(e)==3)holds.add(e);int holdAt=0,covered=0;
        for(int at=0;at<source.size();){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("手势规划已取消");int end=at+1;long time=hit(source.get(at));while(end<source.size()&&hit(source.get(end))==time)end++;
            final long now=time;live.removeIf(c->now-hit(c.last())>(type(c.last())==1?160:type(c.last())==4?300:p.catchDrags?1200:p.gapMs));activeHolds.removeIf(e->e.getEndMs()<=now);while(holdAt<holds.size()&&holds.get(holdAt).getStartMs()<=now)activeHolds.add(holds.get(holdAt++));
            List<TouchEvent> colors=new ArrayList<>(),red=new ArrayList<>();for(int i=at;i<end;i++)if(type(source.get(i))==4)red.add(source.get(i));
            for(int i=at;i<end;i++){
                TouchEvent e=source.get(i);int kind=type(e);
                if(kind==1||kind==3){Contact c=new Contact(e,bridge,p);all.add(c);if(kind==1&&live.size()<128)live.add(c);continue;}
                if(kind==2){boolean caught=false;Point point=anchor(chart,e,time,REF);
                    if(p.catchDrags)for(TouchEvent h:activeHolds)if(h.getEndMs()>time+8&&distance(point,anchor(chart,h,time,REF))<.5){caught=true;break;}
                    if(!caught&&p.combineShots)for(TouchEvent r:red)if(track(e,r)&&distance(point,anchor(chart,r,time,REF))<.5){caught=true;break;}
                    // Exact coincident yellows may share contact; separate positions stay separate.
                    if(!caught)for(TouchEvent other:colors)if(type(other)==2&&track(e,other)&&distance(point,anchor(chart,other,time,REF))<.001&&ChordPlanner.sameTrack(chart,e.getNote(),other.getNote(),e.getStartMs(),e.getEndMs(),REF)){caught=true;break;}
                    if(caught){covered++;continue;}
                }
                colors.add(e);
            }
            colors.sort((a,b)->Integer.compare(type(b),type(a)));int[] links=assign(chart,live,colors,p,bridge);List<Contact> added=new ArrayList<>();
            for(int i=0;i<colors.size();i++){
                TouchEvent e=colors.get(i);if(links[i]>=0)live.get(links[i]).add(e);else{Contact c=new Contact(e,bridge,p);all.add(c);added.add(c);}
            }
            for(Contact c:added)if(live.size()<128)live.add(c);at=end;
        }
        List<Contact> finished=new ArrayList<>();for(Contact c:all)finish(chart,c,p,bridge,finished);
        List<TouchEvent> events=new ArrayList<>();IdentityHashMap<TouchEvent,Contact> paths=new IdentityHashMap<>();int groups=0,notes=0;
        for(Contact c:finished){TouchEvent e=new TouchEvent(c.nodes.get(0).getNote(),c.start,c.end);events.add(e);paths.put(e,c);if(c.nodes.size()>1){groups++;notes+=c.nodes.size();}}
        events.sort((a,b)->{int order=Long.compare(a.getStartMs(),b.getStartMs());return order!=0?order:Integer.compare(a.getNote().getId(),b.getNote().getId());});
        return GesturePlan.mixed(events,paths,groups,notes,covered);
    }
    private static Leg leg(TouchEvent e,Leg previous,TouchEvent next){
        long time=hit(e);if(type(e)!=4)return new Leg(e,time,time+(type(e)==1?12:8),0,0);
        long span=24;if(previous!=null&&type(previous.node)==4)span=Math.min(span,Math.max(8,(time-hit(previous.node)-8)/2));if(next!=null&&type(next)==4)span=Math.min(span,Math.max(8,(hit(next)-time-8)/2));
        float begin=previous!=null&&type(previous.node)==4?previous.end:0;Leg leg=new Leg(e,time-span,time+span,begin,1-begin);
        if(previous!=null&&type(previous.node)==1&&leg.from<previous.to){leg.from=previous.to;leg.to=Math.max(leg.to,leg.from+32);}
        return leg;
    }
    private static void finish(Chart chart,Contact original,GestureProfile p,boolean bridge,List<Contact> out){
        Contact current=null;Leg prior=null;
        for(int i=0;i<original.nodes.size();i++){
            TouchEvent e=original.nodes.get(i);TouchEvent next=i+1<original.nodes.size()?original.nodes.get(i+1):null;Leg candidate=leg(e,prior,next);
            if(current!=null){
                Point a=current.offset(chart,prior,prior.to,prior.end,REF,AREA,new boolean[1]);if(type(e)==4&&current.firstRed==null)current.firstRed=e;
                Point b=current.offset(chart,candidate,candidate.from,candidate.begin,REF,AREA,new boolean[1]);long travel=candidate.from-prior.to;double d=distance(a,b);
                if(type(prior.node)==1&&hit(e)==hit(prior.node)&&travel<Math.ceil(d/1.8)){long shift=(long)Math.ceil(d/1.8)-travel;candidate.from+=shift;candidate.to+=shift;travel+=shift;}
                if(travel<0||d>Math.max(.01,travel*1.8)){
                    out.add(current);current=null;prior=null;candidate=leg(e,null,next);
                }
            }
            if(current==null){current=new Contact(e,bridge,p);current.nodes.clear();}
            current.add(e);if(type(e)==4){current.flick=true;if(current.firstRed==null)current.firstRed=e;}current.legs.add(candidate);current.end=Math.max(current.end,candidate.to+8);prior=candidate;
        }
        if(current!=null)out.add(current);
    }
}
