package io.github.phiscript.root;

import android.content.Context;
import android.os.SystemClock;
import java.util.*;

/** Host transport only; replaces su, not the production planner/state machine. */
public final class RootClient implements AutoCloseable {
    public static RootClient last;
    public static int failAt=-1;
    public static Runnable after;
    public final List<long[]> heads=new ArrayList<>();
    public final Map<Long,Integer> starts=new HashMap<>();
    public final List<float[]> samples=new ArrayList<>();
    public int frames,peak,cancels;public boolean closed;
    public final Set<Integer> active=new HashSet<>();
    final RootTouchState state;
    public RootClient(Context context,int width,int height){last=this;
        state=new RootTouchState((down,time,action,points)->{
            int kind=action&255,index=action>>8;Set<Integer> current=new HashSet<>();
            for(RootTouchState.Pointer p:points)if(!current.add(p.id)||p.id<0||p.id>=10)throw new AssertionError("bad root pointer ID");
            if(points.isEmpty()||points.size()>10||index>=points.size())throw new AssertionError("bad root count/index");
            if(kind==0||kind==5){RootTouchState.Pointer p=points.get(index);
                if(!active.add(p.id))throw new AssertionError("duplicate root DOWN");
                starts.put(p.key,starts.getOrDefault(p.key,0)+1);heads.add(new long[]{p.key,time,Float.floatToIntBits(p.x),Float.floatToIntBits(p.y)});
                if(!current.equals(active))throw new AssertionError("root DOWN pointer set");}
            else if(kind==1||kind==6){if(!current.equals(active)||!active.remove(points.get(index).id))throw new AssertionError("bad root UP");}
            else if(kind==2){if(!current.equals(active))throw new AssertionError("root MOVE pointer set");}
            else if(kind==3){if(!current.equals(active))throw new AssertionError("root CANCEL pointer set");active.clear();cancels++;}
            else throw new AssertionError("unknown root action");peak=Math.max(peak,current.size());
        },width,height);
    }
    public void frame(List<RootTouchState.Sample> samples)throws Exception{
        if(++frames==failAt)throw new IllegalStateException("forced root transport rejection");
        for(RootTouchState.Sample sample:samples)this.samples.add(new float[]{SystemClock.now,(float)sample.key,sample.x,sample.y});
        state.apply(SystemClock.uptimeMillis(),samples);if(after!=null)after.run();
    }
    @Override public void close(){closed=true;try{state.cancel(SystemClock.uptimeMillis());}catch(Exception e){throw new AssertionError(e);}}
}
