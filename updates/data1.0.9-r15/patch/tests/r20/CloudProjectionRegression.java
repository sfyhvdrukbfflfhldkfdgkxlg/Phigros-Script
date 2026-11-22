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
    private static byte[] legacyFixture(byte[] encoded)throws Exception{
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(encoded,0,encoded.length-32));
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(body);
        out.writeInt(input.readInt());out.writeInt(input.readInt());out.writeUTF(input.readUTF());
        input.readUTF();out.writeUTF(PreparedChartStore.LEGACY_CLOUD_SETTINGS);
        byte[] remaining=new byte[input.available()];input.readFully(remaining);out.write(remaining);
        byte[] checksum=java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray());
        out.write(checksum);return body.toByteArray();
    }
    private static void stricterCloudGuard(byte[] reference,Chart chart,long head)throws Exception{
        byte[] legacy=legacyFixture(reference);
        AppSettings.values.put("tap_random_delay_ms","0");AppSettings.values.put("tap_screen_drift_permille","0");
        double open=(head+19)/1000.,close=(head+20)/1000.;
        JSONObject flash=new JSONObject().put("bottomLeftPercentage",xy(-.1,-.1)).put("topRightPercentage",xy(1.1,1.1))
                .put("enableTime",open+chart.getOffsetSeconds()).put("disableTime",close+chart.getOffsetSeconds());
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(flash)));BlockAreaSet.bind(chart);
        for(String guard:new String[]{"0","16","24"}){
            AppSettings.values.put("tap_noise_clock_guard_ms",guard);TapMod.reload();
            PreparedChartStore.checkCloudGeometry(PROFILE,PHONE,TapMod.preparationSignature());
            GesturePlan loaded=map(chart,legacy,PHONE,SAFE);TouchEvent first=loaded.events.get(0);
            boolean present=true;
            try{loaded.preparedPoint(first,head,null,head);}catch(TapMod.NoSafePoint blocked){present=false;}
            ok(present==!guard.equals("24"),"cloud ignored selected guard "+guard);
        }
        AppSettings.values.put("tap_vertical_permille","20");TapMod.reload();
        rejects(()->PreparedChartStore.checkCloudGeometry(PROFILE,PHONE,TapMod.preparationSignature()));
    }
    private static void legacyEdgeClearance()throws Exception{
        AppSettings.values.put("tap_random_delay_ms","0");
        AppSettings.values.put("tap_screen_drift_permille","0");
        TapMod.reload();
        Chart chart=Chart.Companion.fromNative$app_debug(Arrays.asList(note(1,1,.5,.5,.5)),0.0,
                (n,t,v)->new Point(v.getWidth()*.5f,v.getHeight()*.5f),(n,t,v)->new Point(0,1));
        GesturePlan original=GesturePlan.prepareForImport(chart,PROFILE,REFERENCE,FULL,null);
        TouchEvent event=original.events.get(0);
        long head=event.getStartMs();
        Point authored=original.preparedPoint(event,head,null,head);
        double edge=(authored.getX()-REFERENCE.getHeight()*.018)/REFERENCE.getWidth();
        JSONObject area=new JSONObject().put("bottomLeftPercentage",xy(0,0)).put("topRightPercentage",xy(edge,1))
                .put("enableTime",0).put("disableTime",1);
        BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(chart);
        ok(TapMod.clearAt(chart,REFERENCE,authored,head/1000.0)&&BlockAreaSet.nearNoise(chart,REFERENCE,authored,head/1000.0),"invalid edge fixture");
        GesturePlan migrated=map(chart,legacyFixture(original.encodePrepared(KEY)),REFERENCE,FULL);
        Point repaired=migrated.preparedPoint(migrated.events.get(0),head,null,head);
        ok(!BlockAreaSet.nearNoise(chart,REFERENCE,repaired,head/1000.0),"cloud tap still grazes the lone edge");
        ok(Math.hypot(repaired.getX()-authored.getX(),repaired.getY()-authored.getY())<=REFERENCE.getHeight()/30,
                "legacy contact moved beyond its local correction range");
        GesturePlan reopened=GesturePlan.decodePrepared(chart,PROFILE,REFERENCE,FULL,migrated.encodePrepared(KEY),KEY);
        TouchEvent tap=reopened.events.get(0);
        ok(reopened.stationaryTap(tap,repaired,head,head+24),"certified stationary tap lost on cache reopen");
        ok(!reopened.stationaryTap(tap,repaired,head,tap.getEndMs()+1),"certificate extended past the stored release");
        ok(!reopened.stationaryTap(tap,new Point(repaired.getX()+1,repaired.getY()),head,head+24),
                "different pixel accepted as a stationary tap");
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
        byte[] corrupt=encoded.clone();corrupt[corrupt.length-1]^=1;
        rejects(()->map(standalone,corrupt,PHONE,SAFE));
        legacyEdgeClearance();
        stricterCloudGuard(encoded,standalone,reference.events.get(0).getStartMs());
        System.out.println("PASS cloud projection: saved tap/hold/flick/drag samples, accessibility pixels, ultrawide letterbox, noise, settings, corruption and calibration offset");
    }
}
