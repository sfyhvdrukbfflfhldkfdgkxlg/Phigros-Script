package io.github.phiscript.engine;
import android.content.Context;import io.github.phiscript.AppSettings;import io.github.phiscript.input.TapMod;
import org.json.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;

public final class NativeProjectionRegression {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static JSONArray anim(double start,double a,double end,double b,int easing)throws Exception{return new JSONArray().put(new JSONArray().put(new JSONObject().put("time",start).put("value",a).put("tween",new JSONObject().put("id",easing))).put(new JSONObject().put("time",end).put("value",b).put("tween",new JSONObject().put("id",0))));}
 static JSONObject line(int parent,boolean rotate,double x,double y,double angle)throws Exception{return new JSONObject().put("parent",parent).put("rotWithParent",rotate).put("object",new JSONObject().put("x",anim(0,x,100,x,0)).put("y",anim(0,y,100,y,0)).put("rotation",anim(0,angle,100,angle,0))).put("notes",new JSONArray());}
 static JSONObject note(double t,double x)throws Exception{return new JSONObject().put("type",1).put("time",t).put("object",new JSONObject().put("x",anim(0,x,100,x,0)));}
 static Chart parse(JSONArray lines)throws Exception{return Chart.Companion.parse(new JSONObject().put("phiscriptFormat",1).put("offset",0).put("lines",lines).toString());}
 static Object runtime(Chart c)throws Exception{Field f=Chart.class.getDeclaredField("nativePosition");f.setAccessible(true);Object lambda=f.get(c);for(Class<?> type=lambda.getClass();type!=null&&type!=Object.class;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){field.setAccessible(true);Object v=field.get(lambda);if(v!=null&&v.getClass().getName().equals("io.github.phiscript.engine.PhiraNativeChart"))return v;}throw new AssertionError("native runtime not found");}
 public static void main(String[] args)throws Exception{
  boolean baseline=args.length>0;Context.root=Files.createTempDirectory("native-projection").toFile();Context c=new Context();new AppSettings(c).save("tapmod_enabled","false");TapMod.init(c);
  JSONObject moving=line(-1,false,0,0,0);moving.getJSONObject("object").put("x",anim(10,.2,20,1,2));for(int i=0;i<20;i++)moving.getJSONArray("notes").put(note(i*.5,0));Chart chart=parse(new JSONArray().put(moving));Viewport v=new Viewport(0,0,1920,1080);int misses=0;
  // Phira Pro Anim::now_opt_inner holds the first keyframe before its time.
  for(Note n:chart.getNotes()){Point p=chart.position(n,n.getTimeSeconds(),v,false);double expected=1152,error=Math.abs(p.getX()-expected);if(error>0.23625*v.getWidth()/2)misses++;if(!baseline)ok(error<.001,"pre-start linear animation moved click away from first value");}
  JSONObject layered=line(-1,false,0,0,0);layered.getJSONArray("notes").put(note(1,0));layered.getJSONObject("object").put("x",new JSONArray().put(new JSONArray()).put(anim(0,.5,2,.5,0).getJSONArray(0)));
  Chart layers=parse(new JSONArray().put(layered));double layerX=layers.position(layers.getNotes().get(0),1,v,false).getX();
  if(baseline){ok(misses>=12,"R5 should reproduce majority misses before first keyframe");ok(Math.abs(layerX-1440)>200,"R5 first empty layer discarded later movement");System.out.println("PASS R5 majority-miss reproduction: "+misses+" / 20 pre-start notes outside Phira Pro judgement strip; first-empty-layer error "+Math.abs(layerX-1440)+" pixels");return;}
  ok(Math.abs(layerX-1440)<.001,"valid later animation layer discarded after an empty first layer");
  layered.getJSONObject("object").put("x",new JSONArray().put(anim(0,.5,2,.5,0).getJSONArray(0)).put(new JSONArray()).put(anim(0,.1,2,.1,0).getJSONArray(0)));
  layers=parse(new JSONArray().put(layered));ok(Math.abs(layers.position(layers.getNotes().get(0),1,v,false).getX()-1536)<.001,"intermediate empty layer must not disable additive movement");
  for(int easing:new int[]{0,1,3,4,21,22}){moving.getJSONObject("object").put("x",anim(10,.2,20,1,easing));Chart eased=parse(new JSONArray().put(moving));for(Note n:eased.getNotes())ok(Math.abs(eased.position(n,n.getTimeSeconds(),v,false).getX()-1152)<.001,"pre-start eased track must clamp");}
  Random random=new Random(614);for(int trial=0;trial<100;trial++){
   JSONArray ls=new JSONArray();double[] px=new double[8],py=new double[8],angle=new double[8];int[] parents=new int[8];boolean[] inherit=new boolean[8];
   for(int i=0;i<8;i++){parents[i]=i==0?-1:random.nextInt(i+1)-1;inherit[i]=random.nextBoolean();px[i]=(random.nextDouble()-.5)*.35;py[i]=(random.nextDouble()-.5)*.35;angle[i]=random.nextDouble()*180-90;ls.put(line(parents[i],inherit[i],px[i],py[i],angle[i]));}
   ls.getJSONObject(7).getJSONArray("notes").put(note(1,.1));Chart sample=parse(ls);
   for(float[] dims:new float[][]{{1920,1080},{2400,1080},{2048,1536}}){
    Viewport area=new Viewport(13,17,dims[0],dims[1]);double[] x=new double[8],y=new double[8],r=new double[8];
    // Independent world-space transforms from Phira Pro line/object source; project only at the end.
    double aspect=dims[0]/dims[1];for(int i=0;i<8;i++){double xx=px[i],yy=py[i]/aspect,a=Math.toRadians(angle[i]);int parent=parents[i];if(parent>=0){x[i]=x[parent]+Math.cos(r[parent])*xx-Math.sin(r[parent])*yy;y[i]=y[parent]+Math.sin(r[parent])*xx+Math.cos(r[parent])*yy;r[i]=a+(inherit[i]?r[parent]:0);}else{x[i]=xx;y[i]=yy;r[i]=a;}}
    Point p=sample.position(sample.getNotes().get(0),1,area,false);double ex=13+(1+x[7]+.1*Math.cos(r[7]))*dims[0]/2,ey=17+dims[1]/2-(y[7]+.1*Math.sin(r[7]))*dims[0]/2;
    ok(Math.hypot(p.getX()-ex,p.getY()-ey)<.001,"parent/aspect projection diverged from Phira Pro world-space calculation");
   }
  }
  JSONArray huge=new JSONArray();for(int i=0;i<2000;i++)huge.put(line(-1,false,.1,.1,0));huge.getJSONObject(1999).getJSONArray("notes").put(note(1,0));Chart sparse=parse(huge);Object rt=runtime(sparse);
  for(int i=0;i<100;i++){sparse.position(sparse.getNotes().get(0),i*.001,v,false);sparse.judgeNormal(sparse.getNotes().get(0),i*.001,v);}
  ok(NativeChartProjection.evaluatedLines(rt)==100,"unrelated 1999 effect lines must not be evaluated for one touched line");
  System.out.println("PASS native projection "+checks+" assertions: first-frame clamping, Phira Pro parent/aspect geometry and 2000-line sparse evaluation");
 }
}
