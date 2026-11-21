package io.github.phiscript.input;

import io.github.phiscript.engine.*;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sampled at import. A chord shares one delay; each finger has its own vertical translation. */
final class ContactVariation {
    static final ContactVariation OFF=new ContactVariation(0,0);
    final int maxDelay;
    final float maxDrift;

    ContactVariation(int maxDelay,float maxDrift){
        this.maxDelay=maxDelay;
        this.maxDrift=maxDrift;
    }

    private static long mix(long value){
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }

    static long chartSeed(Chart chart){
        long seed=0;
        for(Note note:chart.getNotes()){
            seed=mix(seed^note.getId()^Double.doubleToLongBits(note.getTimeSeconds()));
        }
        return seed;
    }

    int delay(long seed,List<TouchEvent> group,NavigableSet<Long> onsets){
        if(maxDelay==0)return 0;
        long start=group.get(0).getStartMs();
        int limit=maxDelay;
        Long next=onsets.higher(start);
        if(next!=null)limit=(int)Math.min(limit,next-start-1);
        for(TouchEvent event:group){
            long end=event.getEndMs();
            // Keep every original handoff possible, including an UP/DOWN on the same tick.
            Long nextDown=onsets.ceiling(end);
            if(nextDown!=null)limit=(int)Math.min(limit,nextDown-end);
            limit=(int)Math.min(limit,Integer.MAX_VALUE-end);
        }
        return (int)((mix(seed^start)>>>1)%(limit+1));
    }

    float drift(long seed,TouchEvent event,float[] y,Viewport view,TouchBounds bounds){
        if(maxDrift==0)return 0;
        long bits=mix(seed^((long)event.getNote().getId()<<32)^event.getStartMs()^0x61ea6d27L);
        float offset=(float)((bits>>>11)*0x1.0p-53*2-1)*maxDrift*view.getHeight()/1000f;
        // Reduce the entire translation at the edge; do not flatten part of a flick.
        for(float point:y){
            if(Float.isFinite(point)){
                offset=offset>=0?Math.min(offset,bounds.bottom-point):Math.max(offset,bounds.top-point);
            }
        }
        return offset;
    }

    static boolean safe(Chart chart,Viewport view,int start,float[] x,float[] y,byte[] connected,
                        int delay,float dy,AtomicBoolean cancel){
        if((delay==0&&dy==0)||!TapMod.avoids(chart))return true;
        long from=(long)start+delay;
        int back=TapMod.noiseLookBackMs(),ahead=TapMod.noiseLookAheadMs();
        if(!BlockAreaSet.overlaps(chart,from-back,from+y.length-1+ahead))return true;
        Point previous=null;
        for(int i=0;i<y.length;i++){
            if((i&255)==0)PreparedTrajectory.cancelled(cancel);
            if(!Float.isFinite(x[i])){previous=null;continue;}
            long time=from+i;
            Point point=new Point(x[i],y[i]+dy);
            if(BlockAreaSet.overlaps(chart,time-1-back,time+ahead)){
                if(!TapMod.clearAt(chart,view,point,time/1000.0))return false;
                if(connected[i]!=0&&previous!=null){
                    try{TapMod.segment(chart,view,previous,point,time-1,time);}
                    catch(TapMod.NoSafePoint blocked){return false;}
                }
            }
            previous=point;
        }
        return true;
    }
}
