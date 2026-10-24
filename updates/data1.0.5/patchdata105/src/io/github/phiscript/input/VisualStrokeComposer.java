package io.github.phiscript.input;

import java.util.*;

/** Reuses real DOWNs across nearby detected blue/yellow/red patterns on both backends. */
final class VisualStrokeComposer {
    private static final class Stroke {
        long id,born,last,until,fromAt,toAt;float x,y,fromX,fromY,toX,toY;int flicks;boolean drag;
        void sample(long now){float t=toAt<=fromAt?1:Math.max(0,Math.min(1,(now-fromAt)/(float)(toAt-fromAt)));x=fromX+(toX-fromX)*t;y=fromY+(toY-fromY)*t;}
    }
    private final GestureProfile profile;private final TouchBounds bounds;
    private final List<Stroke> strokes=new ArrayList<>();private final Map<Long,Stroke> aliases=new HashMap<>();
    private long nextId=Long.MIN_VALUE;
    VisualStrokeComposer(GestureProfile profile,TouchBounds bounds){this.profile=profile;this.bounds=bounds;}
    private Stroke create(VisualTouch t,long now,int w,int h){
        Stroke s=new Stroke();s.id=nextId++;s.born=s.last=now;s.until=now+160;s.fromAt=s.toAt=now;
        s.x=s.fromX=s.toX=bounds.x(t.getX()*w);s.y=s.fromY=s.toY=bounds.y(t.getY()*h);strokes.add(s);return s;
    }
    private Stroke nearby(VisualTouch t,long now,int w,int h,boolean flick,Set<Stroke> used){
        Stroke best=null;double distance=Double.POSITIVE_INFINITY;
        for(Stroke s:strokes){
            if(s.drag||used.contains(s)||s.until<now||now<s.toAt||(!flick&&s.flicks>0))continue;
            if(s.flicks==0&&!profile.combineShots||s.flicks>0&&!profile.connectFlicks)continue;
            long gap=now-s.last;if(gap>(s.flicks==0?160:300)||s.flicks>0&&gap<30)continue;
            double d=Math.hypot(s.x-t.getX()*w,s.y-t.getY()*h);
            if(d<=profile.distance*h&&d<distance){best=s;distance=d;}
        }return best;
    }
    VisualTouchDemand compose(VisualTouchDemand input,long now,int w,int h){
        for(Stroke s:strokes)s.sample(now);strokes.removeIf(s->!s.drag&&s.until<now);
        List<VisualTouch> holds=new ArrayList<>(),shots=new ArrayList<>();Set<Stroke> used=new HashSet<>(),drags=new HashSet<>();
        // Process blue heads first regardless of classifier list order. Every new blue
        // creates a new pointer, even when another sustained contact is nearby.
        for(VisualTouch t:input.getShots())if(t.getKind()==VisualTouchKind.TAP&&profile.combineShots)create(t,now,w,h);
        for(VisualTouch t:input.getShots()){
            if(t.getKind()==VisualTouchKind.TAP&&profile.combineShots)continue;
            if(t.getKind()!=VisualTouchKind.FLICK||!profile.connectFlicks&&!profile.combineShots){shots.add(t);continue;}
            Stroke s=nearby(t,now,w,h,true,used);if(s==null)s=create(t,now,w,h);
            used.add(s);s.sample(now);s.fromX=s.x;s.fromY=s.y;s.fromAt=s.flicks==0?Math.max(now,s.born+18):now;s.toAt=s.fromAt+48;
            float x=bounds.x(t.getX()*w),y=bounds.y(t.getY()*h);float[] target=bounds.flick(x,y,t.getFlickDx()*w,t.getFlickDy()*h);
            s.toX=(s.flicks&1)==0?target[0]:x;s.toY=(s.flicks&1)==0?target[1]:y;s.flicks++;s.last=now;s.until=now+(profile.connectFlicks?300:100);
        }
        for(VisualTouch t:input.getHolds()){
            if(t.getKind()!=VisualTouchKind.DRAG||!profile.combineShots){holds.add(t);continue;}
            Stroke s=aliases.get(t.getId());if(s!=null&&!strokes.contains(s)){aliases.remove(t.getId());s=null;}
            if(s==null){s=nearby(t,now,w,h,false,used);if(s!=null){s.drag=true;aliases.put(t.getId(),s);}}
            if(s==null){holds.add(t);continue;}
            drags.add(s);used.add(s);
            if(now<s.born+18){s.fromX=s.x;s.fromY=s.y;s.fromAt=s.born+18;s.toAt=s.fromAt+16;s.toX=bounds.x(t.getX()*w);s.toY=bounds.y(t.getY()*h);}
            else {s.x=s.fromX=s.toX=bounds.x(t.getX()*w);s.y=s.fromY=s.toY=bounds.y(t.getY()*h);s.fromAt=s.toAt=now;}
            s.until=now+60;s.last=now;
        }
        strokes.removeIf(s->s.drag&&!drags.contains(s));aliases.values().removeIf(s->!strokes.contains(s));
        // Artificial gaps yield to fresh heads; live movements and actual holds retain priority.
        for(Iterator<Stroke> it=strokes.iterator();it.hasNext()&&holds.size()+shots.size()+strokes.size()>GestureChannel.POINTER_LIMIT;){Stroke s=it.next();if(!used.contains(s)&&now>=s.toAt&&now>s.born)it.remove();}
        for(Stroke s:strokes){s.sample(now);holds.add(new VisualTouch(s.id,VisualTouchKind.HOLD,s.x/w,s.y/h,0,0));}
        return new VisualTouchDemand(holds,shots);
    }
}
