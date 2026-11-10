package io.github.phiscript.engine;

import java.lang.reflect.Field;
import java.util.*;

/** Evaluates only the touched line and its ancestors; unrelated effects never delay input. */
public final class NativeChartProjection {
    private static final Map<Object,Projection> CACHE=Collections.synchronizedMap(new WeakHashMap<Object,Projection>());
    private NativeChartProjection(){}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Projection get(Object owner){
        synchronized(CACHE){Projection p=CACHE.get(owner);if(p==null){
            try{p=new Projection((List<NativeLine>)field(owner,"lines"),(Map<Integer,NativeAnim>)field(owner,"noteX"));}
            catch(Exception e){throw new IllegalStateException("无法读取原生谱面坐标",e);}CACHE.put(owner,p);
        }return p;}
    }
    public static Point position(Object owner,Note note,double time,Viewport viewport){return get(owner).position(note,time,viewport);}
    public static Point normal(Object owner,Note note,double time,Viewport viewport){return get(owner).normal(note,time,viewport);}
    public static long evaluatedLines(Object owner){return get(owner).evaluations;}
    public static double unit(double value){return Math.max(0,Math.min(1,value));}

    private static final class Projection {
        final List<NativeLine> lines;final Map<Integer,NativeAnim> noteX;
        final double[] x,y,rotation;final int[] stamps,path;int generation=1;long evaluations;
        double time=Double.NaN;float width=Float.NaN,height=Float.NaN;
        Projection(List<NativeLine> lines,Map<Integer,NativeAnim> noteX){this.lines=lines;this.noteX=noteX;int n=lines.size();x=new double[n];y=new double[n];rotation=new double[n];stamps=new int[n];path=new int[n];}
        void ensure(int index,double at,Viewport v){
            if(!Double.isFinite(at))throw new IllegalArgumentException("谱面时刻无效");
            if(time!=at||width!=v.getWidth()||height!=v.getHeight()){
                time=at;width=v.getWidth();height=v.getHeight();
                if(++generation==0){Arrays.fill(stamps,0);generation=1;}
            }
            int size=0,cursor=index;
            while(cursor>=0&&stamps[cursor]!=generation){if(size>=path.length)throw new IllegalStateException("判定线父子循环");path[size++]=cursor;cursor=lines.get(cursor).getParent();}
            while(size>0){int i=path[--size];NativeLine line=lines.get(i);int parent=line.getParent();
                double px=line.getX().value(at)*width/2.0,py=line.getY().value(at)*height/2.0,angle=line.getRotation().value(at)*Math.PI/180;
                if(parent<0){x[i]=px;y[i]=py;rotation[i]=angle;}
                else {double c=Math.cos(rotation[parent]),s=Math.sin(rotation[parent]);x[i]=x[parent]+c*px-s*py;y[i]=y[parent]+s*px+c*py;rotation[i]=angle+(line.getRotWithParent()?rotation[parent]:0);}
                stamps[i]=generation;evaluations++;
            }
        }
        synchronized Point normal(Note n,double at,Viewport v){ensure(n.getLineIndex(),at,v);double a=rotation[n.getLineIndex()];return new Point((float)-Math.sin(a),(float)-Math.cos(a));}
        synchronized Point position(Note n,double at,Viewport v){
            int i=n.getLineIndex();ensure(i,at,v);double angle=rotation[i],along=noteX.get(n.getId()).value(at)*width/2.0;
            double px=v.getLeft()+width/2.0+x[i]+along*Math.cos(angle),py=v.getTop()+height/2.0-y[i]-along*Math.sin(angle);
            if(!Double.isFinite(px)||!Double.isFinite(py))return null;Point point=new Point((float)px,(float)py);if(v.contains(point))return point;
            double nx=-Math.sin(angle),ny=-Math.cos(angle),margin=Math.min(.5,Math.min(width/8.0,height/8.0));
            double lower=Double.NEGATIVE_INFINITY,upper=Double.POSITIVE_INFINITY;
            double[] origins={px,py},dirs={nx,ny},minimum={v.getLeft()+margin,v.getTop()+margin},maximum={v.getRight()-margin,v.getBottom()-margin};
            for(int axis=0;axis<2;axis++){
                double direction=dirs[axis],origin=origins[axis];
                if(Math.abs(direction)<1e-12){if(origin<minimum[axis]||origin>maximum[axis])return point;}
                else {double a=(minimum[axis]-origin)/direction,b=(maximum[axis]-origin)/direction;lower=Math.max(lower,Math.min(a,b));upper=Math.min(upper,Math.max(a,b));if(lower>upper)return point;}
            }
            double shift=Math.max(lower,Math.min(upper,0));return new Point((float)(px+nx*shift),(float)(py+ny*shift));
        }
    }
}
