package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

public final class CloudProjectionRegression {
    private static final Viewport REFERENCE=new Viewport(0,0,1920,1080);
    private static final TouchBounds FULL=new TouchBounds(0,0,1919,1079);
    private static final Viewport PHONE=new Viewport(160,0,1280,720); // 1600x720 landscape, centered play area
    private static final TouchBounds SAFE=new TouchBounds(166,25,1438,685);
    private static final GestureProfile PROFILE=GestureProfile.read(null);
    private static final String KEY=PreparedChartStore.digest(new byte[]{1});
    private static void ok(boolean value,String why){if(!value)throw new AssertionError(why);}
    private interface Work{void run()throws Exception;}
    private static void rejects(Work work)throws Exception{try{work.run();throw new AssertionError("Accepted incompatible cloud data");}catch(IOException expected){}}
    private static Note note(int id,int type,double t,double end,double x){return new Note(id,type,t,end,x,1,0,true,0);}
    private static JSONObject xy(double x,double y)throws JSONException{return new JSONObject().put("x",x).put("y",y);}
    private static GesturePlan map(Chart chart,byte[] tracks,Viewport v,TouchBounds b)throws Exception{
        return PreparedTrajectory.readCloud(chart,PROFILE,REFERENCE,FULL,new ByteArrayInputStream(tracks),tracks.length,KEY,v,b,new AtomicBoolean());
    }
    public static void main(String[] args)throws Exception{
        AppSettings.values.clear();
        AppSettings.smart=AppSettings.catchDrags=AppSettings.flicks=AppSettings.combos=AppSettings.edges=AppSettings.inward=true;
        TapMod.init(new Context());
        String signature=TapMod.preparationSignature();
        PreparedChartStore.checkCloudGeometry(PROFILE,REFERENCE,signature);
        PreparedChartStore.checkCloudGeometry(PROFILE,PHONE,signature);
        rejects(()->PreparedChartStore.checkCloudGeometry(PROFILE,new Viewport(0,0,1600,720),signature));
        rejects(()->PreparedChartStore.checkCloudGeometry(PROFILE,PHONE,signature+"1"));
        rejects(()->PreparedChartStore.checkCloudGeometry(new GestureProfile(false,70,.12f),PHONE,signature));
        // In the reference view all four contacts are clear. Moving the system inset into
        // the strip during the tap makes a naive "scale & clamp" implementation unsafe.
        JSONObject noise=new JSONObject().put("bottomLeftPercentage",xy(.13,.05)).put("topRightPercentage",xy(.17,.95))
                .put("enableTime",0).put("disableTime",2);
        Chart source=Chart.Companion.fromNative$app_debug(Arrays.asList(note(1,1,.2,.2,.04),
                note(2,3,.4,.65,.51),note(3,4,1.1,1.1,.77),note(4,2,2.8,2.8,.6)),.173,
                (n,t,v)->new Point(v.getLeft()+(float)n.getPositionX()*v.getWidth(),v.getTop()+v.getHeight()*.48f),
                (n,t,v)->new Point(0,1));
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(noise)));
        BlockAreaSet.bind(source);
        ok(BlockAreaSet.count(source)==1,"Test fixture must contain one noise area");
        GesturePlan reference=GesturePlan.prepareForImport(source,PROFILE,REFERENCE,FULL,null);
        byte[] encoded=reference.encodePrepared(KEY);
        PlaybackArchive archive=PlaybackArchive.compile(source,REFERENCE,encoded.length);
        // The standalone chart only knows the timeline, compiled noise and course anchors.
        Chart standalone=archive.chart();
        ok(BlockAreaSet.count(standalone)==1&&standalone.getOffsetSeconds()==.173,"Lost compiled noise or nonzero offset");
        GesturePlan mapped=map(standalone,encoded,PHONE,SAFE);
        ok(reference.preparedSampleCount()==mapped.preparedSampleCount(),"Lost samples");
        ok(mapped.events.size()==4&&mapped.events.size()==reference.events.size(),"Lost tap, hold, flick or drag: source="+reference.events.size()+", mapped="+mapped.events.size()+", unavailable="+reference.unavailableNotes);
        for(int i=0;i<mapped.events.size();i++){
            TouchEvent a=reference.events.get(i),b=mapped.events.get(i);
            ok(a.getStartMs()==b.getStartMs()&&a.getEndMs()==b.getEndMs(),"Retimed contact");
            ok(reference.hasFlickPath(a)==mapped.hasFlickPath(b)&&reference.isContinuous(a)==mapped.isContinuous(b),"Changed contact type");
            for(long t=b.getStartMs();t<=b.getEndMs();t++){
                Point p=reference.preparedPoint(a,t,null,t),q=mapped.preparedPoint(b,t,null,t);
                float x=SAFE.x(160+p.getX()*1280f/1920),y=SAFE.y(p.getY()*720f/1080);
                ok(Math.abs(q.getX()-x)<.001&&Math.abs(q.getY()-y)<.001,"Incorrect mapped contact");
                Point endpoint=mapped.preparedAccessibilityEndpoint(b,t,false,t,new GestureChannel.Live(null,-1,-1));
                ok(endpoint.getX()==SAFE.x(Math.round(q.getX()))&&endpoint.getY()==SAFE.y(Math.round(q.getY())),"Wrong accessibility pixel");
            }
        }
        byte[] local=mapped.encodePrepared(KEY);
        GesturePlan.decodePrepared(standalone,PROFILE,PHONE,SAFE,local,KEY);
        TouchBounds cutout=new TouchBounds(160+1280*.15f,25,1438,685);
        GesturePlan clipped=map(standalone,encoded,PHONE,cutout);
        GesturePlan reopened=GesturePlan.decodePrepared(standalone,PROFILE,PHONE,cutout,clipped.encodePrepared(KEY),KEY);
        int blockedSamples=0,clearSamples=0;
        for(TouchEvent event:reopened.events){
            for(long t=event.getStartMs();t<=event.getEndMs();t++){
                try{
                    Point q=reopened.preparedPoint(event,t,null,t);
                    ok(TapMod.clearAt(standalone,PHONE,q,t/1000.0),"Reopened unsafe clipped sample");
                    clearSamples++;
                }catch(TapMod.NoSafePoint expected){blockedSamples++;}
            }
        }
        ok(blockedSamples>0&&clearSamples>0,"A blocked inset should suppress only unsafe samples");
        encoded[encoded.length-1]^=1;
        rejects(()->map(standalone,encoded,PHONE,SAFE));
        System.out.println("PASS cloud projection: saved tap/hold/flick/drag samples, accessibility pixels, ultrawide letterbox, noise, settings, corruption and calibration offset");
    }
}
