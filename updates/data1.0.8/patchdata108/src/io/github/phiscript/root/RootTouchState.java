package io.github.phiscript.root;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure state machine: validate a whole frame before emitting any Android input. */
public final class RootTouchState {
    public interface Sink { void send(long downTime, long time, int action, List<Pointer> pointers) throws Exception; }
    public static final class Sample {
        public final long key; public final float x,y;
        public Sample(long key,float x,float y) { this.key=key;this.x=x;this.y=y; }
    }
    public static final class Pointer {
        public final long key; public final int id; public float x,y;
        Pointer(Sample s,int id) { key=s.key;this.id=id;x=s.x;y=s.y; }
    }
    private final Sink sink;
    private final int width,height;
    private final List<Pointer> active=new ArrayList<>();
    private long downTime,lastTime;
    private int nextPointerId;
    public RootTouchState(Sink sink,int width,int height) {
        if(width<2||height<2||width>32768||height>32768) throw new IllegalArgumentException("屏幕尺寸无效");
        this.sink=sink;this.width=width;this.height=height;
    }
    public synchronized void apply(long time,List<Sample> samples) throws Exception {
        if(time<lastTime||samples.size()>10) throw new IllegalArgumentException("触控时间或数量无效");
        Map<Long,Sample> wanted=new LinkedHashMap<>();
        for(Sample s:samples) {
            if(!Float.isFinite(s.x)||!Float.isFinite(s.y)||s.x<0||s.x>=width||s.y<0||s.y>=height
                    ||wanted.put(s.key,s)!=null) throw new IllegalArgumentException("触控帧坐标或编号无效");
        }
        lastTime=time;
        boolean moved=false;
        for(Pointer p:active) {
            Sample s=wanted.get(p.key);if(s!=null) {moved|=p.x!=s.x||p.y!=s.y;p.x=s.x;p.y=s.y;}
        }
        if(moved&&!active.isEmpty())emit(time,2);
        // UP uses the old pointer array; remove only after the event was sent.
        for(int i=active.size()-1;i>=0;i--) if(!wanted.containsKey(active.get(i).key)) {
            emit(time,active.size()==1?1:6|(i<<8));active.remove(i);
        }
        Set<Long> present=new HashSet<>();for(Pointer p:active) present.add(p.key);
        for(Sample s:samples) if(!present.contains(s.key)) {
            boolean[] used=new boolean[10];for(Pointer p:active) used[p.id]=true;
            // The double-pause menu gesture deliberately reuses its pointer;
            // Unity UI click counting can be per pointer. Chart heads rotate.
            int id=s.key==-100&&!used[0]?0:nextPointerId;while(used[id])id=(id+1)%10;if(s.key!=-100)nextPointerId=(id+1)%10;
            if(active.isEmpty()) downTime=time;
            active.add(new Pointer(s,id));
            emit(time,active.size()==1?0:5|((active.size()-1)<<8));
        }
        // DOWN/POINTER_DOWN already includes every continuing coordinate. Do
        // not immediately replace a fresh head with a redundant same-time MOVE.
        // Moving old fingers were emitted before any new DOWN above.
    }
    private void emit(long time,int action) throws Exception {
        sink.send(downTime,time,action,new ArrayList<>(active));
    }
    public synchronized void cancel(long time) throws Exception {
        try {if(!active.isEmpty())emit(Math.max(time,lastTime),3);}
        finally {active.clear();}
    }
    public synchronized int count(){return active.size();}
}
