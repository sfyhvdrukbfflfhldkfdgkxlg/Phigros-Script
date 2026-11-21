package io.github.phiscript.engine;

import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Exact-key hash eviction is checked against independent analytic transforms,
 * including a deliberately wrapping collision cluster larger than the ring. */
public final class NoiseGeometryLookupRegression {
    private static int checks;
    private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void near(double a,double b,String why){ok(Math.abs(a-b)<2e-12,why+": "+a+" != "+b);}
    private static JSONObject xy(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    private static Method method(Class<?> c,String name,Class<?>... types)throws Exception{Method m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object area()throws Exception{
        JSONObject rectangle=new JSONObject().put("bottomLeftPercentage",xy(.2,.3)).put("topRightPercentage",xy(.8,.7)).put("appearTime",0).put("enableTime",0).put("disableTime",10).put("disappearTime",10);
        rectangle.put("scaleEvents",new JSONArray().put(new JSONObject().put("time",0).put("scale",xy(1,1)).put("anchor",xy(.2,.4))).put(new JSONObject().put("time",10).put("scale",xy(2,.5)).put("anchor",xy(.9,.1))));
        rectangle.put("rotateEvents",new JSONArray().put(new JSONObject().put("time",0).put("rotation",0).put("anchor",xy(.3,.6))).put(new JSONObject().put("time",10).put("rotation",90).put("anchor",xy(.1,.9))));
        rectangle.put("moveEvents",new JSONArray().put(new JSONObject().put("time",0).put("endPosition",xy(.5,.5))).put(new JSONObject().put("time",10).put("endPosition",xy(.6,.3))));
        Object key=new Object();BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(rectangle)));BlockAreaSet.bind(key);
        Object set=((Map<?,?>)field(BlockAreaSet.class,"MAP").get(null)).get(key);return ((Object[])field(BlockAreaSet.class,"normal").get(set))[0];
    }
    private static double[] reference(double x,double y,double time,double aspect){
        double f=time/10,sx=1+f,sy=1-f*.5,cx=.2+(.5-.2)*sx,cy=.4+(.5-.4)*sy,angle=Math.toRadians(f*90),cos=Math.cos(angle),sin=Math.sin(angle);
        double dx=(cx-.3)*aspect,dy=cy-.6,centerX=.3+(dx*cos-dy*sin)/aspect+f*.1,centerY=.6+dx*sin+dy*cos-f*.2;
        dx=(x-centerX)*aspect;dy=y-centerY;return new double[]{centerX,centerY,.5+(dx*cos+dy*sin)/aspect/sx,.5+(-dx*sin+dy*cos)/sy};
    }
    public static void main(String[] args)throws Exception{
        Object area=area();Class<?> type=area.getClass();Method hash=method(type,"geometryHash",long.class,long.class),center=method(type,"center",double.class,double.class),local=method(type,"local",double.class,double.class,double.class,double.class),covers=method(type,"covers",double.class,double.class,double.class,double.class,double.class),geometry=method(type,"geometry",double.class,double.class);
        Random random=new Random(382516);List<double[]> collision=new ArrayList<>();double[] aspects={.25,.75,1,16.0/9,4};
        while(collision.size()<192){double t=.001+random.nextDouble()*9.998,aspect=aspects[random.nextInt(aspects.length)];if((Integer)hash.invoke(null,Double.doubleToLongBits(t),Double.doubleToLongBits(aspect))==250)collision.add(new double[]{t,aspect});}
        for(int round=0;round<12;round++){
            Collections.shuffle(collision,random);
            for(double[] query:collision){
                double t=query[0],aspect=query[1],x=random.nextDouble()*2-.5,y=random.nextDouble()*2-.5;double[] expected=reference(x,y,t,aspect),actual=(double[])center.invoke(area,t,aspect);
                near(actual[0],expected[0],"collision center X");near(actual[1],expected[1],"collision center Y");actual=(double[])local.invoke(area,x,y,t,aspect);
                near(actual[0],expected[2],"collision local X");near(actual[1],expected[3],"collision local Y");
                boolean blocked=expected[2]>=.2-1e-8&&expected[2]<=.8+1e-8&&expected[3]>=.3-1e-8&&expected[3]<=.7+1e-8;
                ok((Boolean)covers.invoke(area,x,y,t,aspect,0.)==blocked,"fused covers changed local-space coverage");
                Object exact=geometry.invoke(area,t,aspect),adjacent=geometry.invoke(area,Math.nextUp(t),Math.nextUp(aspect));
                ok(field(exact.getClass(),"time").getLong(exact)==Double.doubleToLongBits(t),"exact timestamp was aliased");
                ok(field(adjacent.getClass(),"time").getLong(adjacent)==Double.doubleToLongBits(Math.nextUp(t)),"adjacent timestamp was rounded");
                ok(field(adjacent.getClass(),"aspect").getLong(adjacent)==Double.doubleToLongBits(Math.nextUp(aspect)),"adjacent aspect was rounded");
            }
            Object[] ring=(Object[])field(type,"geometries").get(area);int[] lookup=(int[])field(type,"geometryLookup").get(area);ok(ring.length==96&&lookup.length==256,"bounded cache allocation changed");
            Set<Integer> slots=new HashSet<>();for(int slot:lookup)if(slot!=0)ok(slot>0&&slot<=96&&slots.add(slot),"hash slot duplicated or outside retained ring");ok(slots.size()==96,"ring eviction lost a lookup entry");
        }
        // Compare the fused boolean path directly with the preserved original
        // local-array operations at transformed epsilon boundaries as well.
        for(double aspect:aspects)for(double t:new double[]{0,.1,3.17,9.9}){
            double f=t/10,sx=1+f,sy=1-f*.5,angle=Math.toRadians(f*90),cos=Math.cos(angle),sin=Math.sin(angle);double[] centerAt=reference(0,0,t,aspect);
            for(double lx:new double[]{.2-2e-8,.2-1e-8,.2,.5,.8,.8+1e-8,.8+2e-8})for(double ly:new double[]{.3-2e-8,.3-1e-8,.3,.5,.7,.7+1e-8,.7+2e-8}){
                double dx=(lx-.5)*aspect*sx,dy=(ly-.5)*sy,x=centerAt[0]+(dx*cos-dy*sin)/aspect,y=centerAt[1]+dx*sin+dy*cos;
                double[] before=(double[])local.invoke(area,x,y,t,aspect);boolean legacy=before[0]>=.2-1e-8&&before[0]<=.8+1e-8&&before[1]>=.3-1e-8&&before[1]<=.7+1e-8;
                ok((Boolean)covers.invoke(area,x,y,t,aspect,0.)==legacy,"fused cover differed at transformed epsilon boundary");
            }
        }
        System.out.println("PASS "+checks+" exact geometry lookup checks: wrapping hash collisions, repeated 96-entry eviction, five aspects, adjacent-double keys, independent transform/local coverage and fixed memory bounds.");
    }
}
