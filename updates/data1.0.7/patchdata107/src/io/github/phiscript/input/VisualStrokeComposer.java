package io.github.phiscript.input;
import java.util.*;

/** A contact changes roles without changing its ID or teleporting to the next note. */
final class VisualStrokeComposer {
    static final int TAP=1,DRAG=2,FLICK=4;
    private static final class Stroke {
        long id,born,last,until,fromAt,toAt,yellowToken=Long.MIN_VALUE;int kind;
        float x,y,fromX,fromY,toX,toY,axisX,axisY,lastDx,lastDy;
        void sample(long now){float t=toAt<=fromAt?1:Math.max(0,Math.min(1,(now-fromAt)/(float)(toAt-fromAt)));x=fromX+(toX-fromX)*t;y=fromY+(toY-fromY)*t;}
        void move(float x,float y,long from,long to){fromX=this.x;fromY=this.y;toX=x;toY=y;fromAt=from;toAt=to;}
    }
    private final GestureProfile profile;private final TouchBounds bounds;
    private final List<Stroke> strokes=new ArrayList<>();private final Map<Long,Stroke> aliases=new HashMap<>();private long serial=Long.MIN_VALUE;
    VisualStrokeComposer(GestureProfile profile,TouchBounds bounds){this.profile=profile;this.bounds=bounds;}
    private Stroke create(VisualTouch t,long now,int w,int h,int kind){Stroke s=new Stroke();s.id=serial++;s.born=s.last=now;s.until=now+160;s.kind=kind;s.fromAt=s.toAt=now;s.x=s.fromX=s.toX=bounds.x(t.getX()*w);s.y=s.fromY=s.toY=bounds.y(t.getY()*h);strokes.add(s);return s;}
    private double distance(Stroke s,VisualTouch t,int w,int h){return Math.hypot(s.x-t.getX()*w,s.y-t.getY()*h);}
    private Stroke find(VisualTouch t,long now,int w,int h,Set<Stroke> used,boolean red){
        Stroke best=null;double score=Double.POSITIVE_INFINITY;
        for(Stroke s:strokes){
            if(used.contains(s)||now<s.toAt||s.until<now)continue;
            if(s.kind==TAP&&!profile.combineShots||s.kind==FLICK&&!(red?profile.connectFlicks:profile.combineShots)||s.kind==DRAG&&!profile.combineShots)continue;
            long gap=now-s.last;if(gap>(s.kind==TAP?160:s.kind==FLICK?300:1200)||s.kind==FLICK&&red&&gap<32)continue;
            double d=distance(s,t,w,h);if(d>profile.distance*h||d>Math.max(8,(gap-(s.kind==TAP?12:0))*h*.002))continue;
            if(d<score){best=s;score=d;}
        }return best;
    }
    VisualTouchDemand compose(VisualTouchDemand input,long now,int w,int h,Map<Long,Long> tokens){
        for(Stroke s:strokes)s.sample(now);strokes.removeIf(s->s.until<now&&s.kind!=DRAG);aliases.values().removeIf(s->!strokes.contains(s)&&s.kind!=FLICK);
        List<VisualTouch> holds=new ArrayList<>(),shots=new ArrayList<>();Set<Stroke> claimed=new HashSet<>(),currentYellow=new HashSet<>();
        for(VisualTouch t:input.getShots())if(t.getKind()==VisualTouchKind.TAP&&profile.combineShots)create(t,now,w,h,TAP);
        for(VisualTouch t:input.getHolds()){
            if(t.getKind()!=VisualTouchKind.DRAG){holds.add(t);continue;}
            Stroke s=aliases.get(t.getId());Long token=tokens.get(t.getId());
            // Tracker grace must not pull an active red leg back to the old yellow point.
            if(s!=null&&s.kind==FLICK&&(token==null||token==s.yellowToken)){currentYellow.add(s);continue;}
            if(s!=null&&!strokes.contains(s))s=null;
            if(s!=null&&claimed.contains(s))s=null;
            if(s!=null&&s.kind==FLICK&&now<s.toAt){if(distance(s,t,w,h)<=h*.008){if(token!=null)s.yellowToken=token;currentYellow.add(s);continue;}s=null;}
            if(s==null)s=find(t,now,w,h,claimed,false);
            if(s!=null&&distance(s,t,w,h)>h*.12&&s.kind!=DRAG)s=null;
            if(s==null)s=create(t,now,w,h,DRAG);
            float x=bounds.x(t.getX()*w),y=bounds.y(t.getY()*h);double d=Math.hypot(x-s.x,y-s.y);
            if(d>h*.12&&now<s.toAt){s=create(t,now,w,h,DRAG);d=0;}
            if(s.toX!=x||s.toY!=y){long begin=Math.max(now,s.kind==TAP?s.born+12:now),span=Math.max(8,(long)Math.ceil(d/(h*.0025)));s.move(x,y,begin,begin+Math.min(48,span));}
            s.kind=DRAG;if(token!=null){s.yellowToken=token;s.last=now;}s.until=now+1200;aliases.put(t.getId(),s);if(token!=null)claimed.add(s);currentYellow.add(s);
        }
        for(VisualTouch t:input.getShots()){
            if(t.getKind()==VisualTouchKind.TAP&&profile.combineShots)continue;
            if(t.getKind()!=VisualTouchKind.FLICK||!profile.connectFlicks&&!profile.combineShots){shots.add(t);continue;}
            Stroke s=find(t,now,w,h,claimed,true);
            if(s==null&&profile.combineShots)for(Stroke y:currentYellow)if(y.kind==DRAG&&distance(y,t,w,h)<=h*.008){s=y;break;}
            if(s==null)s=create(t,now,w,h,FLICK);
            s.sample(now);float x=bounds.x(t.getX()*w),y=bounds.y(t.getY()*h),nx=s.axisX,ny=s.axisY;
            if(nx==0&&ny==0){nx=t.getFlickDx()*w;ny=t.getFlickDy()*h;float l=(float)Math.hypot(nx,ny);if(l<1){nx=0;ny=-1;l=1;}nx/=l;ny/=l;if(nx*((bounds.left+bounds.right)*.5f-x)+ny*((bounds.top+bounds.bottom)*.5f-y)<0){nx=-nx;ny=-ny;}}
            float[] b=UnifiedGesturePlanner.along(bounds,x,y,nx,ny,h*.1f);
            boolean reverse=s.kind==FLICK&&s.lastDx*nx+s.lastDy*ny>0;float tx=reverse?x:b[0],ty=reverse?y:b[1];
            if(Math.hypot(tx-s.x,ty-s.y)<h*.035){tx=reverse?b[0]:x;ty=reverse?b[1]:y;}
            float dx=tx-s.x,dy=ty-s.y;
            if(s.kind==FLICK&&s.lastDx*s.lastDx+s.lastDy*s.lastDy>1&&dx*s.lastDx+dy*s.lastDy>=0){s=create(t,now,w,h,FLICK);tx=b[0];ty=b[1];dx=tx-s.x;dy=ty-s.y;}
            long start=Math.max(now,s.kind==TAP?s.born+12:now);s.move(tx,ty,start,start+32);s.lastDx=dx;s.lastDy=dy;s.axisX=nx;s.axisY=ny;s.kind=FLICK;s.last=now;s.until=now+(profile.connectFlicks?300:100);claimed.add(s);
        }
        strokes.removeIf(s->s.kind==DRAG&&!currentYellow.contains(s));aliases.values().removeIf(s->!strokes.contains(s)&&s.kind!=FLICK);
        for(Iterator<Stroke> it=strokes.iterator();it.hasNext()&&holds.size()+shots.size()+strokes.size()>GestureChannel.POINTER_LIMIT;){Stroke s=it.next();if(!claimed.contains(s)&&now>=s.toAt&&s.born<now)it.remove();}
        Set<Long> inputs=new HashSet<>();for(VisualTouch t:input.getHolds())if(t.getKind()==VisualTouchKind.DRAG)inputs.add(t.getId());aliases.keySet().retainAll(inputs);
        for(Stroke s:strokes){s.sample(now);holds.add(touch(s,s.x,s.y,w,h));}
        return new VisualTouchDemand(holds,shots);
    }
    private static VisualTouch touch(Stroke s,float x,float y,int w,int h){return new VisualTouch(s.id,VisualTouchKind.HOLD,x/w,y/h,0,0);}
    Map<Long,VisualTouch> preview(long at,int w,int h){Map<Long,VisualTouch> result=new HashMap<>();for(Stroke s:strokes){float r=s.toAt<=s.fromAt?1:Math.max(0,Math.min(1,(at-s.fromAt)/(float)(s.toAt-s.fromAt)));result.put(s.id,touch(s,s.fromX+(s.toX-s.fromX)*r,s.fromY+(s.toY-s.fromY)*r,w,h));}return result;}
    long nextBoundary(long now,long end){for(Stroke s:strokes){if(s.fromAt>now)end=Math.min(end,s.fromAt);if(s.toAt>now)end=Math.min(end,s.toAt);}return end;}
}
