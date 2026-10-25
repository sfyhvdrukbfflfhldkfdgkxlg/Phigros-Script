package io.github.phiscript.input;

import io.github.phiscript.engine.*;
import java.util.*;

/** Eight sustained contacts for a proven dense yellow section; no synthetic taps. */
final class DenseDragPlanner {
    static final int FINGERS=8;
    // Deliberately narrower than the measured Phigros yellow strip (+/- .21 H).
    static final float RADIUS=.10f;
    static final Viewport REF=new Viewport(0,0,1350,900);
    static final class Pin {
        final float x,y;
        Pin(float x,float y){this.x=x;this.y=y;}
        Point point(Viewport v,TouchBounds b){return new Point(b.x(v.getLeft()+x*v.getWidth()),b.y(v.getTop()+y*v.getHeight()));}
    }
    final Set<Note> covered=Collections.newSetFromMap(new IdentityHashMap<Note,Boolean>());
    final IdentityHashMap<TouchEvent,Pin> pins=new IdentityHashMap<>();
    int sections;
    static long hit(Note n){return Math.round(n.getTimeSeconds()*1000);}
    static DenseDragPlanner plan(Chart chart){
        DenseDragPlanner result=new DenseDragPlanner();List<Note> yellows=new ArrayList<>();
        for(Note n:chart.getNotes())if(n.getType()==2)yellows.add(n);
        if(yellows.size()<8)return result;
        List<TouchEvent> other=new ArrayList<>();for(TouchEvent e:ChordPlanner.forChart(chart,false,false))if(e.getNote().getType()!=2)other.add(e);
        for(int at=0;at<yellows.size();){
            int end=at+1;while(end<yellows.size()&&hit(yellows.get(end))-hit(yellows.get(end-1))<=300)end++;
            List<Note> block=yellows.subList(at,end);at=end;
            if(!dense(block))continue;
            long start=Math.max(0,hit(block.get(0))-260),finish=hit(block.get(block.size()-1))+70;
            // Establish the fingers outside nearby Tap/Hold head windows. Idle
            // contact never substitutes for the fresh DOWN those notes require.
            long earliest=start-1000;
            boolean moved;
            do{moved=false;for(TouchEvent e:other){int kind=e.getNote().getType();long head=hit(e.getNote());
                if((kind==1||kind==3)&&head>=start-220&&head<=start+240){start=head-241;moved=true;break;}
            }}while(moved&&start>=earliest);
            if(start<0||start<earliest||!room(other,start,finish))continue;
            Pin[] layout=null;
            for(int mode=0;mode<4;mode++){
                Pin[] candidate=layout(mode);boolean valid=true;
                for(Note n:block){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("手势规划已取消");if(!covers(chart,n,candidate)){valid=false;break;}}
                if(valid){layout=candidate;break;}
            }
            if(layout==null)continue; // Moving/rotated/outside layouts keep exact paths.
            result.sections++;result.covered.addAll(block);
            for(int i=0;i<FINGERS;i++)result.pins.put(new TouchEvent(block.get(i%block.size()),start,finish),layout[i]);
        }
        return result;
    }
    private static boolean dense(List<Note> notes){
        if(notes.size()<8)return false;int first=0,same=0;long previous=Long.MIN_VALUE;
        for(int i=0;i<notes.size();i++){
            long t=hit(notes.get(i));same=t==previous?same+1:1;previous=t;if(same>=8)return true;
            while(first<i&&t-hit(notes.get(first))>250)first++;
            if(i-first+1>=24)return true;
        }return false;
    }
    private static Pin[] layout(int mode){
        Pin[] p=new Pin[FINGERS];int cols=mode==0?8:mode==1?1:mode==2?4:2,rows=8/cols;
        for(int i=0;i<8;i++)p[i]=new Pin((i%cols+.5f)/cols,(i/cols+.5f)/rows);
        return p;
    }
    static boolean covers(Chart chart,Note n,Pin[] pins){
        // A single finger must cover the whole small timing interval, not a
        // different finger at each sample. This also handles animated normals.
        for(Pin pin:pins){boolean safe=true;for(int dt:new int[]{-32,0,32}){
            double time=Math.max(0,hit(n)+dt)/1000.0;Point a=chart.position(n,time,REF,false),normal=chart.judgeNormal(n,time,REF);
            if(a==null||normal==null||!REF.contains(a)){safe=false;break;}
            double length=Math.hypot(normal.getX(),normal.getY());
            double along=length<.001?Double.POSITIVE_INFINITY:Math.abs((pin.x*REF.getWidth()-a.getX())*normal.getY()-(pin.y*REF.getHeight()-a.getY())*normal.getX())/length;
            if(!Double.isFinite(along)||along>REF.getHeight()*RADIUS){safe=false;break;}
        }if(safe)return true;}return false;
    }
    private static boolean room(List<TouchEvent> other,long start,long end){
        List<long[]> changes=new ArrayList<>();for(TouchEvent e:other)if(e.getEndMs()>start&&e.getStartMs()<end){changes.add(new long[]{Math.max(start,e.getStartMs()),1});changes.add(new long[]{Math.min(end,e.getEndMs()),-1});}
        changes.sort((a,b)->a[0]==b[0]?Long.compare(a[1],b[1]):Long.compare(a[0],b[0]));
        int live=0;for(long[] c:changes){live+=c[1];if(live>10-FINGERS)return false;}return true;
    }
}
