package io.github.phiscript.root;

import java.util.*;

public final class RootStateRegression {
    static int checks,downs,ups,moves,cancels,peak;
    static Set<Integer> active=new HashSet<>();static Map<Long,Integer> starts=new HashMap<>();
    static void ok(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static RootTouchState state(){active.clear();starts.clear();downs=ups=moves=cancels=peak=0;
        return new RootTouchState((down,time,action,points)->{
            int kind=action&255,index=action>>8;Set<Integer> current=new HashSet<>();
            for(RootTouchState.Pointer p:points){ok(p.id>=0&&p.id<10,"pointer id");ok(current.add(p.id),"duplicate pointer id");}
            ok(points.size()<=10&&points.size()>0,"pointer count");ok(down<=time,"time order");
            if(kind==0||kind==5){ok(index<points.size(),"DOWN index");RootTouchState.Pointer p=points.get(index);
                ok(!active.contains(p.id),"reused active id");active.add(p.id);downs++;starts.put(p.key,starts.getOrDefault(p.key,0)+1);
                ok(active.equals(current),"DOWN pointer set");if(kind==0)ok(active.size()==1,"ACTION_DOWN count");}
            else if(kind==1||kind==6){ok(index<points.size(),"UP index");ok(active.equals(current),"UP pointer set");
                ok(active.remove(points.get(index).id),"unknown UP");ups++;if(kind==1)ok(active.isEmpty(),"ACTION_UP count");}
            else if(kind==2){ok(active.equals(current),"MOVE pointer set");moves++;}
            else if(kind==3){ok(active.equals(current),"CANCEL pointer set");cancels++;active.clear();}
            else throw new AssertionError("bad action");peak=Math.max(peak,current.size());
        },1000,600);
    }
    static RootTouchState.Sample s(long key){return new RootTouchState.Sample(key,100+(key%10)*60,300);}
    static void reject(RootTouchState state,List<RootTouchState.Sample> points)throws Exception{
        int before=downs+ups+moves+cancels;boolean failed=false;try{state.apply(9000,points);}catch(IllegalArgumentException e){failed=true;}
        ok(failed,"invalid frame accepted");ok(before==downs+ups+moves+cancels,"invalid frame partially emitted");
    }
    public static void main(String[]args)throws Exception{
        RootTouchState t=state();List<RootTouchState.Sample> points=new ArrayList<>(Arrays.asList(s(1),s(2)));t.apply(1,points);
        for(int i=0;i<1000;i++){
            List<RootTouchState.Sample> chord=new ArrayList<>(points);chord.add(s(10000+i*3));chord.add(s(10001+i*3));chord.add(s(10002+i*3));
            t.apply(2+i*2,chord);t.apply(3+i*2,points);
        }
        ok(starts.get(1L)==1&&starts.get(2L)==1,"root holds restarted");ok(peak==5,"three-shot chord peak");
        t.apply(3000,Collections.emptyList());ok(active.isEmpty()&&downs==ups,"unbalanced release");
        System.out.println("PASS 2 continuous holds + 1000 three-finger chords: "+downs+" DOWN, "+ups+" UP; no hold restart");
        t=state();for(int i=0;i<100;i++){points=new ArrayList<>();for(int j=0;j<10;j++)points.add(s(i*10+j));t.apply(i,points);}
        ok(peak==10,"ten-finger peak");t.cancel(100);ok(active.isEmpty()&&cancels==1,"cancel leaves fingers");
        System.out.println("PASS 100 ten-to-ten handoffs: unique IDs and valid UP/DOWN indices");
        t=state();t.apply(1,Arrays.asList(s(1)));reject(t,Arrays.asList(s(1),s(1)));
        reject(t,Arrays.asList(new RootTouchState.Sample(2,Float.NaN,3)));
        reject(t,Arrays.asList(new RootTouchState.Sample(2,1000,3)));
        points=new ArrayList<>();for(int i=0;i<11;i++)points.add(s(i));reject(t,points);
        t.cancel(9001);ok(active.isEmpty(),"final release");
        System.out.println("PASS duplicate IDs, non-finite/offscreen coordinates and >10 fingers rejected atomically");
        System.out.println("ALL "+checks+" ROOT STATE CHECKS PASSED (host only; no su/device execution)");
    }
}
