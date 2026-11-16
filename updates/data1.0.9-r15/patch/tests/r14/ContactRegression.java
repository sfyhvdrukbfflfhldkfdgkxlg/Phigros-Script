package io.github.phiscript.input;
import io.github.phiscript.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.*;
import android.os.SystemClock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

public final class ContactRegression {
    static final Viewport V=new Viewport(0,0,1000,600);
    static final GestureProfile PROFILE=new GestureProfile(true,70,.12f,true,true,true);
    static int checks;
    static void ok(boolean test,String msg){checks++;if(!test)throw new AssertionError(msg);}
    static Note n(int id,int type,double t,double end,double x,int line){return new Note(id,type,t,end,x,1,0,true,line);}
    static Chart chart(JSONArray areas,List<Note> notes)throws Exception{
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",areas));
        return Chart.Companion.fromNative$app_debug(notes,0,
            (n,t,v)->new Point((float)(v.getLeft()+n.getPositionX()*v.getWidth()),v.getTop()+v.getHeight()*(n.getLineIndex()%2==0?.25f:.75f)),
            (n,t,v)->new Point(0,1));
    }
    static JSONObject rect(double l,double b,double r,double t,double on,double off)throws Exception{
        return CoreTest.area(l,b,r,t).put("appearTime",on).put("enableTime",on).put("disableTime",off).put("disappearTime",off);
    }
    static void clear(){AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=true;AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new android.content.Context());}
    static List<Note> bank(int columns,int beats,double spread) {
        List<Note> notes=new ArrayList<>();
        for(int t=0;t<beats;t++)for(int k=0;k<columns;k++){
            double x=.5+(((k+.5)/columns)-.5)*spread,when=.50+t*.08;
            notes.add(n(notes.size()+1,2,when,when,x,k));
        }
        return notes;
    }
    static TouchService play(Chart c,boolean root)throws Exception{
        TouchService service=new TouchService();SystemClock.now=100000;InputBackend.rootEnabled=root;
        ChartMultiTouchPlayer.play(service,c,100000,V,new AtomicBoolean(),s->kotlin.Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());return service;
    }
    static int peak(TouchService s,boolean root){return root?RootClient.last.peak:s.peak;}
    static int heads(TouchService s,boolean root){return root?RootClient.last.heads.size():s.downs.size();}
    static void verifyBank(boolean root,int size,double spread,int expected)throws Exception{
        List<Note> notes=bank(size,3,spread);Chart c=chart(new JSONArray(),notes);
        TouchService s=play(c,root);
        ok(peak(s,root)==expected,"bank physical peak "+peak(s,root)+" expected "+expected+" root="+root);
        ok(heads(s,root)==expected,"bank must hold its fingers across beats, actual DOWNs="+heads(s,root));
        for(Note n:notes){
            boolean caught=false;
            if(root){
                for(float[] p:RootClient.last.samples)if(Math.abs(p[0]-100000-ContactPlanner.hit(n))<=14 && Math.abs(p[2]-n.getPositionX()*1000)<=100)caught=true;
            }else{
                for(long[] d:s.downs)if(Math.abs(Float.intBitsToFloat((int)d[2])-n.getPositionX()*1000)<=100)caught=true;
            }
            ok(caught,"uncovered yellow "+n.getId());
        }
    }
    static void geometry(boolean root)throws Exception{
        JSONArray mask=new JSONArray().put(rect(.46,-1,.54,2,.2,.9));
        Chart c=chart(mask,Arrays.asList(n(1,1,.40,.40,.5,0),n(2,2,.60,.60,.5,1),n(3,4,.82,.82,.5,0)));
        TouchService s=play(c,root);List<long[]> ds=root?RootClient.last.heads:s.downs;
        ok(ds.size()>=2,"zone notes lost "+root);
        for(long[] e:ds){
            float x=Float.intBitsToFloat((int)e[2]),y=Float.intBitsToFloat((int)e[3]);
            ok(!BlockAreaSet.covers(c,x,y,0,0,1000,600,(e[1]-100000)/1000.0),"DOWN inside noise");
            ok(Math.abs(x-500)<=99,"escaped outside judgement strip");
        }
        if(root){for(float[] p:RootClient.last.samples)ok(!BlockAreaSet.covers(c,p[2],p[3],0,0,1000,600,(p[0]-100000)/1000.0),"root MOVE inside noise");}
        else for(android.accessibilityservice.GestureDescription g:s.history)for(android.accessibilityservice.GestureDescription.StrokeDescription st:g.strokes)
            for(float[] p:st.path.points)ok(p[0]<460||p[0]>540,"accessibility path inside static strip");
    }
    static void shortHandoff()throws Exception {
        TouchService service=new TouchService();
        GestureChannel channel=new GestureChannel(service);
        GestureBatch<Integer> first=new GestureBatch<>();
        first.add(1,null,GestureChannel.path(150,150,151,150),0,8,true,151,150);
        try {
            channel.send(first);
            GestureBatch<Integer> next=new GestureBatch<>();
            next.add(1,first.next.get(1),GestureChannel.path(151,150,152,150),0,1,true,152,150);
            next.add(2,null,GestureChannel.path(320,300,320,300),2,3,true,320,300);
            channel.send(next);
            channel.finish(next.next.values());
            ok(service.peak==2 && service.downs.size()==2 && service.roots.isEmpty(),"late new DOWN overlaps short continuing leg");
        }finally{channel.close();}
    }
    static void movingEdge()throws Exception {
        Note note=n(5,2,.5,.5,.5,0);
        Chart incoming=chart(new JSONArray().put(rect(.46,-1,.54,2,.501,.7)),Arrays.asList(note));
        Point p=TapMod.withinBounds(incoming,note,.5,V,new Point(500,150),new TouchBounds(20,20,980,580));
        ok(!BlockAreaSet.covers(incoming,p.getX(),p.getY(),0,0,1000,600,.504),"dispatch delay moves DOWN into noise");
        Chart thin=chart(new JSONArray().put(rect(.5006,-1,.5009,2,0,1)),Arrays.asList(note));
        boolean released=false;
        try{TapMod.segment(thin,V,new Point(495,150),new Point(505,150),500,504);}
        catch(TapMod.NoSafePoint expected){released=true;}
        ok(released,"sub-pixel noise between two adjacent sample positions");
    }
    public static void main(String[] args)throws Exception{
        clear();
        if(args.length!=0){
            Chart c=chart(new JSONArray(),bank(18,3,1));System.out.println("baseline planned "+GesturePlan.obtain(c,PROFILE).events.size()+" contacts for 18 full-width yellows");return;
        }
        for(boolean root:new boolean[]{false,true}){
            verifyBank(root,18,1,6);
            verifyBank(root,9,.035,1);
            List<Note> split=new ArrayList<>();for(int k=0;k<8;k++)split.add(n(k,2,.5,.5,k%2==0?.18:.82,k));
            TouchService s=play(chart(new JSONArray(),split),root);ok(peak(s,root)==2 && heads(s,root)==2,"two remote bands");
            Chart held=chart(new JSONArray(),Arrays.asList(n(1,3,.1,.85,.48,0),n(2,2,.25,.25,.51,1),n(3,4,.48,.48,.50,1),n(4,2,.48,.48,.50,1)));
            s=play(held,root);ok(heads(s,root)==1,"hold must catch cross-line red and yellow; heads="+heads(s,root));
            boolean moved=false;
            if(root){for(float[] p:RootClient.last.samples)if(Math.abs(p[3]-150)>17)moved=true;}
            else for(android.accessibilityservice.GestureDescription g:s.history)for(android.accessibilityservice.GestureDescription.StrokeDescription st:g.strokes)
                for(float[] p:st.path.points)if(Math.abs(p[1]-150)>17)moved=true;
            ok(moved,"red must emit a swipe on the held finger");
            geometry(root);
        }
        shortHandoff();movingEdge();
        Chart hole=chart(new JSONArray().put(rect(0,0,1,1,0,2)).put(rect(.488,.103,.512,.111,0,2).put("isSubtract",true)),bank(1,1,0));
        Point q=TapMod.withinBounds(hole,hole.getNotes().get(0),.5,V,new Point(500,150),new TouchBounds(30,30,970,570));
        ok(q.getY()>532&&q.getY()<539&&!BlockAreaSet.covers(hole,q.getX(),q.getY(),0,0,1000,600,.5),"narrow subtractive window beyond old escape radius");
        System.out.println("PASS "+checks+" checks. Both input backends: one/two/six physical fingers; cross-line hold/drag/flick reuse, actual swipe displacement, safe DOWN and MOVE, narrow subtractive window.");
    }
}
