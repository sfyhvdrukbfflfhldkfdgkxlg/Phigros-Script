package io.github.phiscript.capture;
import io.github.phiscript.engine.*;import io.github.phiscript.vision.*;import java.util.*;import org.json.*;
public final class CourseClockRegression {
    private static int checks;private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static JSONArray constant(double value)throws Exception{return new JSONArray().put(new JSONArray().put(new JSONObject().put("time",0).put("value",value).put("tween",new JSONObject().put("id",0))));}
    private static Chart chart()throws Exception{
        JSONArray notes=new JSONArray();notes.put(new JSONObject().put("type",1).put("time",1).put("object",new JSONObject().put("x",constant(-.3))));
        notes.put(new JSONObject().put("type",4).put("time",1.5).put("object",new JSONObject().put("x",constant(.3))));
        JSONObject line=new JSONObject().put("object",new JSONObject().put("x",constant(0)).put("y",constant(0)).put("rotation",constant(0))).put("notes",notes);
        return Chart.Companion.parse(new JSONObject().put("phiscriptFormat",1).put("offset",.175).put("lines",new JSONArray().put(line)).toString());
    }
    private static VisualScene scene(long epoch,long at,boolean stationary,boolean wrong,boolean movingLine){
        double elapsed=stationary?0:at-epoch,y=300-(1000-elapsed)*.25,secondY=300-(1500-elapsed)*.17,lineY=movingLine?300+elapsed*.5:300;
        return new VisualScene(1000,600,Collections.singletonList(new VisualJudgeLine(500,lineY,1,0,900,2)),Arrays.asList(new VisualNoteBlob(wrong?2:1,350,y,0,0,0,1),new VisualNoteBlob(4,650,secondY,0,0,0,1)),true);
    }
    private static long observe(PhigrosCourseClock clock,long epoch,boolean stationary,boolean wrong,boolean movingLine){long result=-1;for(int i=0;i<12;i++){long at=epoch+i*40;long next=clock.observe(scene(epoch,at,stationary,wrong,movingLine),at,at);if(next>=0)result=next;}return result;}
    public static void main(String[] args)throws Exception{
        Chart chart=chart();Viewport view=new Viewport(0,0,1000,600);
        for(long epoch:new long[]{10000,60000,110000}){PhigrosCourseClock clock=new PhigrosCourseClock(chart,epoch,1000,600,view);long result=observe(clock,epoch,false,false,false);ok(Math.abs(result-epoch)<=10,"native-script crossing clock: "+result+" != "+epoch);}
        java.io.ByteArrayOutputStream timeline=new java.io.ByteArrayOutputStream();
        io.github.phiscript.input.PlaybackTimeline.write(new java.io.DataOutputStream(timeline),chart,view);
        Chart restored=io.github.phiscript.input.PlaybackTimeline.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(timeline.toByteArray())));
        for(long epoch:new long[]{10000,60000,110000}){
            long result=observe(new PhigrosCourseClock(restored,epoch,1000,600,view),epoch,false,false,false);
            ok(Math.abs(result-epoch)<=10,"standalone course timeline lost calibration at "+epoch);
        }
        ok(observe(new PhigrosCourseClock(chart,10000,1000,600,view),10000,true,false,false)<0,"stationary notes authorized input");
        ok(observe(new PhigrosCourseClock(chart,10000,1000,600,view),10000,false,true,false)<0,"wrong note kinds matched first chord");
        ok(observe(new PhigrosCourseClock(chart,10000,1000,600,view),10000,false,false,true)<0,"rapid judge-line motion treated as stable crossing velocity");
        PhigrosCourseClock stale=new PhigrosCourseClock(chart,10000,1000,600,view);for(int i=0;i<12;i++){long at=10000+i*40;ok(stale.observe(scene(10000,at,false,false,false),at,at+300)<0,"stale capture locked clock");}
        PhigrosCourseClock repeat=new PhigrosCourseClock(chart,10000,1000,600,view);for(int i=0;i<12;i++)ok(repeat.observe(scene(10000,10000,false,false,false),10000,10000)<0,"repeated frame counted as motion");
        System.out.println("PASS "+checks+" course clock checks: native scripts, three independent origins, frozen/stale/wrong notes and moving-line rejection.");
    }
}
