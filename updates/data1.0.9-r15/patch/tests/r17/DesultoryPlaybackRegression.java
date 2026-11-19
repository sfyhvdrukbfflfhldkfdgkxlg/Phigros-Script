package io.github.phiscript.input;

import android.accessibilityservice.GestureDescription;
import android.os.SystemClock;
import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.InputBackend;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Optional stress regression. Supply pinned original PGR JSON files; no chart payload is bundled.
 * Checks actual Android constant-arc-length Path playback at every wall millisecond
 * against a separate native-noise reference (including official inset/parity).
 */
public final class DesultoryPlaybackRegression {
    static final Viewport V=new Viewport(0,0,1000,600);static final long EPOCH=100000;
    static final class Track {
        final double[] t,x,y;final int[] ex,ey;final double dx,dy;
        Track(JSONArray list,String property,double dx,double dy)throws Exception{
            this.dx=dx;this.dy=dy;int n=list==null?0:list.length();t=new double[n];x=new double[n];y=new double[n];ex=new int[n];ey=new int[n];
            for(int i=0;i<n;i++){JSONObject a=list.getJSONObject(i);t[i]=a.getDouble("time");ex[i]=a.optInt("easeTypeX",a.optInt("easeType",0));ey[i]=a.optInt("easeTypeY",a.optInt("easeType",0));if(property.equals("rotation")){x[i]=y[i]=a.optDouble(property,dx);}else{JSONObject p=a.getJSONObject(property);x[i]=p.getDouble("x");y[i]=p.getDouble("y");}}
        }
        int at(double stamp){int lo=0,hi=t.length;while(lo<hi){int m=(lo+hi)>>>1;if(t[m]<=stamp)lo=m+1;else hi=m;}return lo-1;}
        double value(double stamp,boolean horizontal){int i=at(stamp);if(i<0)return horizontal?dx:dy;double[] a=horizontal?x:y;if(i+1==t.length)return a[i];double p=(stamp-t[i])/(t[i+1]-t[i]);return a[i]+(a[i+1]-a[i])*eased(p,(horizontal?ex:ey)[i]);}
    }
    static double sampleEase(double p,int ease){if(ease==13)return 0;if(ease==14)return 1;if(ease==0)return p;int power=(ease-1)/3+2,mode=(ease-1)%3;return mode==0?Math.pow(p,power):mode==1?1-Math.pow(1-p,power):p<.5?Math.pow(2*p,power)/2:1-Math.pow(2-2*p,power)/2;}
    static double eased(double p,int type){double q=Math.max(0,Math.min(1,p))*100;int i=(int)q;if(i==100)return sampleEase(1,type);return sampleEase(i/100.0,type)+(sampleEase((i+1)/100.0,type)-sampleEase(i/100.0,type))*(q-i);}
    static final class Area {
        final double l,r,b,t,on,off,cx,cy;final boolean sub;final Track move,scale,rotate;final double[][] sa,ra;
        Area(JSONObject a)throws Exception{
            JSONObject bl=a.getJSONObject("bottomLeftPercentage"),tr=a.getJSONObject("topRightPercentage");l=Math.min(bl.getDouble("x"),tr.getDouble("x"));r=Math.max(bl.getDouble("x"),tr.getDouble("x"));b=Math.min(bl.getDouble("y"),tr.getDouble("y"));t=Math.max(bl.getDouble("y"),tr.getDouble("y"));cx=(l+r)/2;cy=(b+t)/2;
            on=Math.max(a.optDouble("appearTime",0),a.optDouble("enableTime",0));off=Math.min(a.optDouble("disableTime",0),a.optDouble("disappearTime",0));sub=a.optBoolean("isSubtract",false);
            move=new Track(a.optJSONArray("moveEvents"),"endPosition",cx,cy);scale=new Track(a.optJSONArray("scaleEvents"),"scale",1,1);rotate=new Track(a.optJSONArray("rotateEvents"),"rotation",0,0);sa=anchors(a.optJSONArray("scaleEvents"));ra=anchors(a.optJSONArray("rotateEvents"));
        }
        double[][] anchors(JSONArray a)throws Exception{double[][] result=new double[a==null?0:a.length()][2];for(int i=0;i<result.length;i++){JSONObject p=a.getJSONObject(i).optJSONObject("anchor");result[i][0]=p==null?cx:p.getDouble("x");result[i][1]=p==null?cy:p.getDouble("y");}return result;}
        boolean contains(double x,double y,double stamp,boolean inset){
            double sx=scale.value(stamp,true),sy=scale.value(stamp,false);if(Math.abs(sx)<1e-8||Math.abs(sy)<1e-8)return false;
            double centerX=cx*1000,centerY=cy*600;int last=scale.at(stamp);
            for(int k=0;k<=last&&k+1<scale.t.length;k++){double nextX=k<last?scale.x[k+1]:sx,nextY=k<last?scale.y[k+1]:sy;centerX=sa[k][0]*1000+(centerX-sa[k][0]*1000)*(Math.abs(scale.x[k])<1e-38?1:nextX/scale.x[k]);centerY=sa[k][1]*600+(centerY-sa[k][1]*600)*(Math.abs(scale.y[k])<1e-38?1:nextY/scale.y[k]);}
            last=rotate.at(stamp);double rotation=rotate.value(stamp,true);
            for(int k=0;k<=last&&k+1<rotate.t.length;k++){double a=Math.toRadians((k<last?rotate.x[k+1]:rotation)-rotate.x[k]),dx=centerX-ra[k][0]*1000,dy=centerY-ra[k][1]*600;centerX=ra[k][0]*1000+dx*Math.cos(a)-dy*Math.sin(a);centerY=ra[k][1]*600+dx*Math.sin(a)+dy*Math.cos(a);}
            centerX+=(move.value(stamp,true)-cx)*1000;centerY+=(move.value(stamp,false)-cy)*600;
            double angle=Math.toRadians(rotation),dx=x-centerX,dy=y-centerY,localX=dx*Math.cos(angle)+dy*Math.sin(angle),localY=-dx*Math.sin(angle)+dy*Math.cos(angle);
            double margin=inset?(sub?30:-30):0;double hx=Math.max(0,(r-l)*1000*Math.abs(sx)/2+margin),hy=Math.max(0,(t-b)*600*Math.abs(sy)/2+margin);
            return Math.abs(localX)<=hx&&Math.abs(localY)<=hy;
        }
    }
    static final class Reference {
        final Area[] areas;final double offset;
        Reference(JSONObject raw)throws Exception{JSONArray list=raw.getJSONArray("blockAreaList");areas=new Area[list.length()];for(int i=0;i<areas.length;i++)areas[i]=new Area(list.getJSONObject(i));offset=raw.optDouble("offset",0);}
        boolean blocked(float screenX,float screenY,double time){double x=screenX,y=600-screenY,stamp=time+offset;boolean full=false,inset=false;int fs=0,ins=0;for(Area a:areas){if(stamp<a.on||stamp>=a.off)continue;boolean f=a.contains(x,y,stamp,false),i=a.contains(x,y,stamp,true);if(a.sub){if(f)fs++;if(i)ins++;}else{full|=f;inset|=i;}}return (full^((fs&1)==1))&&(inset^((ins&1)==1));}
    }
    static float[] arc(List<float[]> points,double fraction){double total=0;for(int i=1;i<points.size();i++)total+=Math.hypot(points.get(i)[0]-points.get(i-1)[0],points.get(i)[1]-points.get(i-1)[1]);double left=total*fraction;for(int i=1;i<points.size();i++){float[] a=points.get(i-1),b=points.get(i);double length=Math.hypot(b[0]-a[0],b[1]-a[1]);if(length>0&&left<=length){double f=left/length;return new float[]{(float)(a[0]+(b[0]-a[0])*f),(float)(a[1]+(b[1]-a[1])*f)};}left-=length;}return points.get(points.size()-1);}
    public static void main(String[] args)throws Exception{
        if(args.length==0)throw new IllegalArgumentException("Supply pinned Desultory Signals original AT/IN PGR JSON paths");
        boolean random=false;int inputStart=0;String clockGuard="0";
        while(inputStart<args.length && args[inputStart].startsWith("--")){
            String option=args[inputStart++];
            if(option.equals("--random"))random=true;
            else if(option.startsWith("--clock-guard="))clockGuard=option.substring("--clock-guard=".length());
            else throw new IllegalArgumentException("Unknown option: "+option);
        }
        AppSettings.values.put("tap_noise_clock_guard_ms",clockGuard);
        AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=AppSettings.edges=AppSettings.inward=true;AppSettings.values.put("tap_noise_avoid","true");AppSettings.values.put("tapmod_enabled",Boolean.toString(random));TapMod.init(new android.content.Context());InputBackend.rootEnabled=false;
        for(int input=inputStart;input<args.length;input++){String filename=args[input];byte[] raw=Files.readAllBytes(Paths.get(filename));JSONObject json=new JSONObject(new String(raw,StandardCharsets.UTF_8));Reference ref=new Reference(json);Chart chart=SharedChartImport.parseJson(new String(raw,StandardCharsets.UTF_8));
            if(BlockAreaSet.count(chart)!=ref.areas.length)throw new AssertionError("metadata lost before player");
            SystemClock.now=EPOCH;TouchService service=new TouchService();long[] samples={0},violations={0};double[] firstViolation={-1};
            long preparationBegan=System.nanoTime();service.prepareChart(chart,V);long preparationMs=(System.nanoTime()-preparationBegan)/1000000;
            GesturePlan planned=GesturePlan.obtain(chart,GestureProfile.read(service),V,TouchSafety.read(service,1000,600,V).bounds,false);
            int preparedSamples=-1;
            try{preparedSamples=((Number)GesturePlan.class.getMethod("preparedSampleCount").invoke(planned)).intValue();if(preparedSamples<=0)throw new AssertionError("dense preparation was bypassed");}
            catch(NoSuchMethodException olderRelease){/* R17-R19 do not expose prepared paths. */}
            service.after=g->{long duration=0;for(GestureDescription.StrokeDescription s:g.strokes)duration=Math.max(duration,s.start+s.duration);long base=SystemClock.now-duration-2-EPOCH;
                for(GestureDescription.StrokeDescription s:g.strokes)for(long ms=0;ms<=s.duration;ms++){float[] p=arc(s.path.points,ms/(double)s.duration);double time=(base+s.start+ms)/1000.0;samples[0]++;if(ref.blocked(p[0],p[1],time)){violations[0]++;if(firstViolation[0]<0){firstViolation[0]=time;System.out.println("FIRST_BLOCKED "+filename+" time="+time+" x="+p[0]+" y="+p[1]);}}}
            };
            ChartMultiTouchPlayer.play(service,chart,EPOCH,V,new AtomicBoolean(),s->{if(s.contains("输入统计")||s.startsWith("噪域提交复核"))System.out.println(s);return kotlin.Unit.INSTANCE;},new AtomicBoolean(),new AtomicBoolean());
            System.out.println("REPLAY "+Paths.get(filename).getFileName()+" random="+random+" clockGuardMs="+clockGuard+" notes="+chart.getNotes().size()+" unavailableNotes="+planned.unavailableNotes+" planContacts="+planned.events.size()+" preparedSamples="+preparedSamples+" preflightMs="+preparationMs+" areas="+ref.areas.length+" duration="+chart.getDurationSeconds()+" sends="+service.sends+" peak="+service.peak+" DOWN="+service.downs.size()+" arcSamples="+samples[0]+" blocked="+violations[0]+" released="+service.roots.isEmpty());
            if(violations[0]!=0||service.frameworkFailed||!service.roots.isEmpty())throw new AssertionError("actual accessibility trajectory entered native noise or leaked contacts");
        }
    }
}
