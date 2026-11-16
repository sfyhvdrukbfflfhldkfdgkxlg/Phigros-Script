package io.github.phiscript.engine;

import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Independent pre-optimization sequential anchor reference, using JSON values
 * and linear scans rather than production Track/value/prefix implementations. */
public final class NoiseGeometryCacheRegression {
    private static int checks;private static volatile double sink;
    private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private static void near(double actual,double expected,String why){ok(Math.abs(actual-expected)<=Math.max(2e-10,Math.abs(expected)*2e-12),why+": "+actual+" != "+expected);}
    private static JSONObject point(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    private static final class Key {
        double t,x,y,ax,ay;int ex,ey;
        Key(JSONObject row,String property,double dx,double dy,double cx,double cy)throws Exception{
            t=row.getDouble("time");JSONObject p=row.optJSONObject(property),a=row.optJSONObject("anchor");x=property.equals("rotation")?row.getDouble(property):p.optDouble("x",dx);y=property.equals("rotation")?x:p.optDouble("y",dy);ax=a==null?cx:a.getDouble("x");ay=a==null?cy:a.getDouble("y");ex=row.optInt("easeTypeX",row.optInt("easeType",0));ey=row.optInt("easeTypeY",row.optInt("easeType",0));
        }
    }
    private static final class Reference {
        final double cx,cy;final Key[] scale,rotate,move;
        Reference(JSONObject area)throws Exception{
            JSONObject a=area.getJSONObject("bottomLeftPercentage"),b=area.getJSONObject("topRightPercentage");cx=(a.getDouble("x")+b.getDouble("x"))/2;cy=(a.getDouble("y")+b.getDouble("y"))/2;
            scale=keys(area.optJSONArray("scaleEvents"),"scale",1,1);rotate=keys(area.optJSONArray("rotateEvents"),"rotation",0,0);move=keys(area.optJSONArray("moveEvents"),"endPosition",cx,cy);
        }
        Key[] keys(JSONArray rows,String property,double dx,double dy)throws Exception{List<Key> result=new ArrayList<>();if(rows!=null)for(int i=0;i<rows.length();i++)result.add(new Key(rows.getJSONObject(i),property,dx,dy,cx,cy));result.sort(Comparator.comparingDouble(k->k.t));return result.toArray(new Key[0]);}
        int last(Key[] keys,double t){int at=-1;while(at+1<keys.length&&keys[at+1].t<=t)at++;return at;}
        double value(Key[] keys,double t,boolean x,double fallback){int i=last(keys,t);if(i<0)return fallback;double from=x?keys[i].x:keys[i].y;if(i+1==keys.length)return from;double f=(t-keys[i].t)/(keys[i+1].t-keys[i].t);return from+((x?keys[i+1].x:keys[i+1].y)-from)*ease(f,x?keys[i].ex:keys[i].ey);}
        double ratio(double a,double b){return Math.abs(b)<1e-38?1:a/b;}
        double[] center(double t,double aspect){
            double x=cx,y=cy;int last=last(scale,t);
            for(int i=0;i<=last&&i+1<scale.length;i++){Key k=scale[i];double sx=i<last?scale[i+1].x:value(scale,t,true,1),sy=i<last?scale[i+1].y:value(scale,t,false,1);x=k.ax+(x-k.ax)*ratio(sx,k.x);y=k.ay+(y-k.ay)*ratio(sy,k.y);}
            last=last(rotate,t);for(int i=0;i<=last&&i+1<rotate.length;i++){Key k=rotate[i];double delta=(i<last?rotate[i+1].x:value(rotate,t,true,0))-k.x,angle=Math.toRadians(delta),dx=(x-k.ax)*aspect,dy=y-k.ay;x=k.ax+(dx*Math.cos(angle)-dy*Math.sin(angle))/aspect;y=k.ay+dx*Math.sin(angle)+dy*Math.cos(angle);}
            return new double[]{x+value(move,t,true,cx)-cx,y+value(move,t,false,cy)-cy};
        }
        double[] local(double x,double y,double t,double aspect){double sx=Math.abs(value(scale,t,true,1)),sy=Math.abs(value(scale,t,false,1));if(sx<1e-6||sy<1e-6)return null;double[] c=center(t,aspect);double dx=(x-c[0])*aspect,dy=y-c[1],angle=Math.toRadians(value(rotate,t,true,0));return new double[]{cx+(dx*Math.cos(angle)+dy*Math.sin(angle))/aspect/sx,cy+(-dx*Math.sin(angle)+dy*Math.cos(angle))/sy};}
    }
    private static double raw(double p,int type){if(type==13)return 0;if(type==14)return 1;if(p<=0)return 0;if(p>=1)return 1;if(type==0)return p;int power=(type-1)/3+2,mode=(type-1)%3;return mode==0?Math.pow(p,power):mode==1?1-Math.pow(1-p,power):p<.5?Math.pow(2*p,power)/2:1-Math.pow(2-2*p,power)/2;}
    private static double ease(double p,int type){if(type==13)return 0;if(type==14)return 1;double q=Math.max(0,Math.min(1,p))*100;int step=(int)q;if(step==100)return 1;return raw(step/100.0,type)+(raw((step+1)/100.0,type)-raw(step/100.0,type))*(q-step);}
    private static JSONObject area(int count,long seed)throws Exception{
        Random random=new Random(seed);JSONArray scale=new JSONArray(),rotate=new JSONArray(),move=new JSONArray();double time=-.4;
        for(int i=0;i<count;i++){
            if(i%11!=0)time+=.01+random.nextDouble()*.03;
            double sx=i%17==0?0:i%19==0?1e-40:(random.nextBoolean()?1:-1)*(.15+random.nextDouble()*2.2),sy=i%23==0?0:(random.nextBoolean()?1:-1)*(.25+random.nextDouble()*2);
            scale.put(new JSONObject().put("time",time).put("scale",point(sx,sy)).put("anchor",point(random.nextDouble()*2-.5,random.nextDouble()*2-.5)).put("easeTypeX",i%15).put("easeTypeY",(i+5)%15));
            rotate.put(new JSONObject().put("time",time+.003).put("rotation",random.nextDouble()*1080-540).put("anchor",point(random.nextDouble()*2-.5,random.nextDouble()*2-.5)).put("easeType",(i+2)%15));
            move.put(new JSONObject().put("time",time-.005).put("endPosition",point(random.nextDouble(),random.nextDouble())).put("easeTypeX",(i+4)%15).put("easeTypeY",(i+8)%15));
        }
        return new JSONObject().put("bottomLeftPercentage",point(.2,.3)).put("topRightPercentage",point(.65,.85)).put("appearTime",-10).put("enableTime",-10).put("disableTime",10000).put("disappearTime",10000).put("scaleEvents",scale).put("rotateEvents",rotate).put("moveEvents",move);
    }
    private static Object production(JSONObject area)throws Exception{Object key=new Object();BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(key);Field map=BlockAreaSet.class.getDeclaredField("MAP");map.setAccessible(true);Object set=((Map<?,?>)map.get(null)).get(key);Field normal=BlockAreaSet.class.getDeclaredField("normal");normal.setAccessible(true);return ((Object[])normal.get(set))[0];}
    private static Method method(Object instance,String name,Class<?>... types)throws Exception{Method m=instance.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static void geometry()throws Exception{
        Random random=new Random(99231);double[] aspects={.25,.75,1,16.0/9,1000.0/581,2.2,4};
        for(int size:new int[]{0,1,2,9,73}){
            JSONObject json=area(size,765+size);Object actual=production(json);Reference ref=new Reference(json);Method center=method(actual,"center",double.class,double.class),local=method(actual,"local",double.class,double.class,double.class,double.class);
            List<Double> times=new ArrayList<>(Arrays.asList(-2.0,-.4,-.397,0.0,4.0));for(Key k:ref.scale){times.add(k.t);times.add(Math.nextDown(k.t));times.add(Math.nextUp(k.t));}for(Key k:ref.rotate){times.add(k.t);times.add(Math.nextDown(k.t));times.add(Math.nextUp(k.t));}for(int i=0;i<300;i++)times.add(-.5+random.nextDouble()*Math.max(2,size*.04));Collections.shuffle(times,random);
            for(double aspect:aspects)for(double t:times){double[] expected=ref.center(t,aspect),got=(double[])center.invoke(actual,t,aspect);near(got[0],expected[0],"center X n="+size);near(got[1],expected[1],"center Y n="+size);double x=random.nextDouble()*3-1,y=random.nextDouble()*3-1;expected=ref.local(x,y,t,aspect);got=(double[])local.invoke(actual,x,y,t,aspect);ok((got==null)==(expected==null),"zero signed scale visibility");if(got!=null){near(got[0],expected[0],"local X");near(got[1],expected[1],"local Y");}}
            Field cache=actual.getClass().getDeclaredField("geometries");cache.setAccessible(true);ok(((Object[])cache.get(actual)).length==96,"exact-time snapshot budget changed");
        }
    }
    private static void performance()throws Exception{
        JSONObject json=area(5000,8842);Object actual=production(json);Reference ref=new Reference(json);Method center=method(actual,"center",double.class,double.class);double aspect=1000.0/581;
        double end=ref.rotate[ref.rotate.length-1].t;
        for(int i=0;i<250;i++)center.invoke(actual,end-i*.00013,aspect);
        long begin=System.nanoTime();double sum=0;for(int i=0;i<1800;i++)sum+=((double[])center.invoke(actual,end-i*.000011,aspect))[0];long optimized=System.nanoTime()-begin;
        begin=System.nanoTime();for(int i=0;i<1800;i++)sum+=ref.center(end-i*.000011,aspect)[0];long reference=System.nanoTime()-begin;sink=sum;
        for(int i=0;i<24;i++){double t=end-i*.003;double[] expected=ref.center(t,aspect),got=(double[])center.invoke(actual,t,aspect);near(got[0],expected[0],"long-track X");near(got[1],expected[1],"long-track Y");}
        ok(optimized<reference,"prefix queries slower than sequential 5000-key reference");
        Field prefixes=actual.getClass().getDeclaredField("rotationPrefixes");prefixes.setAccessible(true);ok(((Object[])prefixes.get(actual)).length==2,"unbounded per-aspect prefix cache");
        System.out.println("5000-key trajectory: 1800 unique exact-time queries cached="+(optimized/1e6)+" ms, independent sequential="+(reference/1e6)+" ms");
    }
    public static void main(String[] args)throws Exception{geometry();performance();System.out.println("PASS "+checks+" geometry-prefix checks against independent sequential JSON reference: arbitrary times/aspects, signed and zero scales, changing anchors, duplicate key times, easing, bounded exact snapshots and complex-track speed.");}
}
