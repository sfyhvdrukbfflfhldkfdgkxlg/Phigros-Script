package io.github.phiscript.input;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import io.github.phiscript.AppSettings;
import io.github.phiscript.assets.*;
import io.github.phiscript.engine.*;
import io.github.phiscript.root.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.json.*;

/** Exercises the actual decoder, persisted library, native transform and both players. */
public final class NoiseImportRegression {
    static int checks;static final Viewport V=new Viewport(0,0,1000,600);
    static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static JSONObject point(double x,double y)throws Exception{return new JSONObject().put("x",x).put("y",y);}
    static JSONObject rect(double l,double b,double r,double t)throws Exception{return new JSONObject().put("bottomLeftPercentage",point(l,b)).put("topRightPercentage",point(r,t)).put("appearTime",0).put("enableTime",0).put("disableTime",10).put("disappearTime",10);}
    static JSONArray constant(double value)throws Exception{return new JSONArray().put(new JSONArray().put(new JSONObject().put("time",0).put("value",value).put("tween",new JSONObject().put("id",0))));}
    static JSONObject chart()throws Exception{
        JSONObject note=new JSONObject().put("type",1).put("time",.5).put("object",new JSONObject().put("x",constant(0)));
        JSONObject line=new JSONObject().put("object",new JSONObject().put("x",constant(0)).put("y",constant(0)).put("rotation",constant(0))).put("notes",new JSONArray().put(note));
        return new JSONObject().put("phiscriptFormat",1).put("offset",0).put("lines",new JSONArray().put(line));
    }
    static Chart areas(JSONObject... areas)throws Exception{return SharedChartImport.parseJson(chart().put("blockAreaList",new JSONArray(Arrays.asList(areas))).toString());}
    static boolean covered(Chart c,double x,double y,double t){return BlockAreaSet.covers(c,(float)(x*1000),(float)((1-y)*600),0,0,1000,600,t);}
    static byte[] zip(String... entries)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){for(int i=0;i<entries.length;i+=2){z.putNextEntry(new ZipEntry(entries[i]));z.write(entries[i+1].getBytes(StandardCharsets.UTF_8));z.closeEntry();}}return bytes.toByteArray();}
    static void transforms()throws Exception{
        JSONObject motion=rect(.46,.46,.54,.54).put("moveEvents",new JSONArray().put(new JSONObject().put("time",0).put("easeTypeX",1).put("endPosition",point(.5,.5))).put(new JSONObject().put("time",2).put("easeTypeX",0).put("endPosition",point(.9,.5))));
        Chart c=areas(motion);ok(covered(c,.6,.5,1)&&!covered(c,.7,.5,1),"native easing used destination instead of outgoing key");
        JSONObject scaling=rect(.4,.4,.6,.6).put("scaleEvents",new JSONArray().put(new JSONObject().put("time",0).put("anchor",point(1,.5)).put("scale",point(1,1))).put(new JSONObject().put("time",1).put("anchor",point(0,.5)).put("scale",point(2,1))).put(new JSONObject().put("time",2).put("anchor",point(.5,.5)).put("scale",point(4,1))));
        c=areas(scaling);ok(covered(c,0,.5,1)&&covered(c,0,.5,2)&&!covered(c,.5,.5,2),"completed scale anchor delta discarded");
        JSONObject first=rect(.4,.4,.6,.6).put("scaleEvents",new JSONArray().put(new JSONObject().put("time",1).put("anchor",point(0,0)).put("scale",point(2,2))));
        c=areas(first);ok(covered(c,.5,.5,1)&&!covered(c,.1,.1,1),"first scale key incorrectly orbited chart center");
        JSONObject rotating=rect(.49,.49,.51,.51).put("rotateEvents",new JSONArray().put(new JSONObject().put("time",0).put("rotation",0).put("anchor",point(.8,.5))).put(new JSONObject().put("time",1).put("rotation",90).put("anchor",point(.2,.2))).put(new JSONObject().put("time",2).put("rotation",180).put("anchor",point(.5,.5))));
        c=areas(rotating);ok(covered(c,.8,0,1)&&!covered(c,.2,.7,1),"rotation discarded outgoing anchor or aspect");
    }
    static void inversion()throws Exception{
        JSONObject sub=rect(.4,.4,.6,.6).put("isSubtract",true),normal=rect(.4,.4,.6,.6);
        Chart one=areas(sub);ok(covered(one,.5,.5,1)&&BlockAreaSet.overlaps(one,900,1100),"isolated inverted domain was ignored");
        ok(!covered(areas(sub,sub),.5,.5,1),"two inversions did not cancel");
        ok(covered(areas(sub,sub,sub),.5,.5,1),"three inversions must block");
        ok(covered(areas(normal,sub,sub),.5,.5,1),"two subtract overlaps incorrectly opened a hole");
        ok(!covered(areas(normal,sub),.5,.5,1),"single subtract did not open a hole");
    }
    static void persisted()throws Exception{
        Context.root=Files.createTempDirectory("r17-noise-import").toFile();Context ctx=new Context();
        JSONObject zone=rect(.48,-1,.52,2);String side=new JSONObject().put("BlockAreaList",new JSONArray().put(zone)).toString();
        byte[] pack=zip("bundle/song.json",chart().toString(),"bundle/info.yml","name: Noise Persistence\nlevel: IN 12\nchart: song.json\n","bundle/song.blockAreaList.json",side,"other/blockAreaList.json",new JSONObject().put("blockAreaList",new JSONArray().put(rect(0,0,1,1))).toString());
        PhiraImportData decoded=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(pack),"noise.pez");
        ok(new JSONObject(decoded.getNormalizedJson()).getJSONArray("blockAreaList").length()==1,"PEZ sidecar dropped before normalization");
        Path file=ctx.getCacheDir().toPath().resolve("noise.pez");Files.write(file,pack);
        PhiraLibrary lib=PhiraLibrary.Companion.importFiles(ctx,Collections.singletonList(Uri.fromFile(file.toFile())),s->kotlin.Unit.INSTANCE);
        lib=PhiraLibrary.Companion.restore(ctx);String id=lib.getEntries().get(0).getId();
        for(boolean root:new boolean[]{false,true}){
            Chart c=lib.load(id);ok(BlockAreaSet.count(c)==1&&covered(c,.5,.5,.5),"saved/reloaded noise data missing");
            AppSettings.values.put("tapmod_enabled","false");AppSettings.values.put("tap_noise_avoid","true");TapMod.init(ctx);SystemClock.now=100000;InputBackend.rootEnabled=root;
            TouchService service=new TouchService();ChartMultiTouchPlayer.play(service,c,100000,V,new AtomicBoolean(),s->kotlin.Unit.INSTANCE,new AtomicBoolean(),new AtomicBoolean());
            List<long[]> downs=root?RootClient.last.heads:service.downs;ok(downs.size()==1,"sidecar chart did not play exactly one tap");
            for(long[] d:downs){float x=Float.intBitsToFloat((int)d[2]),y=Float.intBitsToFloat((int)d[3]);ok(!BlockAreaSet.covers(c,x,y,0,0,1000,600,(d[1]-100000)/1000.0),"persisted sidecar player touched noise");}
        }
        JSONObject embedded=chart().put("blockAreaList",new JSONArray());
        byte[] chosen=zip("chart.json",embedded.toString(),"info.yml","name: Explicit Empty\nlevel: HD\nchart: chart.json\n","blockAreaList.json",side);
        ok(new JSONObject(PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(chosen),"empty.pez").getNormalizedJson()).getJSONArray("blockAreaList").length()==0,"sidecar overrode explicit embedded list");
    }
    static JSONArray beat(int n)throws Exception{return new JSONArray().put(n).put(0).put(1);}
    static void nextStatic()throws Exception{
        JSONObject area=new JSONObject().put("bottomLeft",point(-67.5,-45)).put("topRight",point(67.5,45)).put("appearTime",beat(0)).put("disappearTime",beat(12)).put("activeIntervals",new JSONArray().put(new JSONObject().put("startTime",beat(4)).put("endTime",beat(8))));
        JSONArray bpm=new JSONArray().put(new JSONObject().put("bpm",120).put("startTime",beat(0))).put(new JSONObject().put("bpm",240).put("startTime",beat(4)));
        JSONObject tap=new JSONObject().put("type",1).put("startTime",beat(1)).put("endTime",beat(1)).put("positionX",0).put("above",1).put("isFake",0).put("speed",1).put("size",1).put("alpha",255).put("yOffset",0);
        JSONObject line=new JSONObject().put("Name","Next test").put("Texture","line.png").put("isCover",1).put("eventLayers",new JSONArray()).put("notes",new JSONArray().put(tap));
        JSONObject json=new JSONObject().put("META",new JSONObject().put("RPEVersion",220).put("offset",0).put("name","Next Static Persistence")).put("judgeLineList",new JSONArray().put(line)).put("BPMList",bpm).put("blockAreaList",new JSONArray().put(area));
        PhiraImportData data=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8)),"next.json");Chart c=SharedChartImport.parseJson(data.getNormalizedJson());
        ok(BlockAreaSet.count(c)==1&&!covered(c,.5,.5,1)&&covered(c,.5,.5,2.5)&&!covered(c,.5,.5,3),"Next static activation failed BPM conversion or ready state");
        JSONObject inverted=new JSONObject(area.toString()).put("isInvert",true);
        json.put("blockAreaList",new JSONArray().put(inverted).put(inverted).put(inverted)).put("noiseAreaOptions",new JSONObject().put("ignoreTripleInversion",true));
        data=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8)),"next-single.json");c=SharedChartImport.parseJson(data.getNormalizedJson());
        ok(!covered(c,.5,.5,2.5),"Next ignoreTripleInversion option lost after normalization");
        Context ctx=new Context();Path saved=ctx.getCacheDir().toPath().resolve("next-single.json");Files.write(saved,json.toString().getBytes(StandardCharsets.UTF_8));
        PhiraLibrary lib=PhiraLibrary.Companion.importFiles(ctx,Collections.singletonList(Uri.fromFile(saved.toFile())),s->kotlin.Unit.INSTANCE);lib=PhiraLibrary.Companion.restore(ctx);
        String id=null;for(PhiraEntry entry:lib.getEntries())if(entry.getTitle().equals("Next Static Persistence"))id=entry.getId();ok(id!=null,"Next chart was not persisted");
        ok(!covered(lib.load(id),.5,.5,2.5),"Next inversion composition flag lost after library save/reload");
        area.put("moveXEvents",new JSONArray().put(new JSONObject().put("startTime",beat(0)).put("endTime",beat(1)).put("start",0).put("end",100)));json.put("blockAreaList",new JSONArray().put(area));boolean refused=false;
        try{PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8)),"next-dynamic.json");}catch(IllegalArgumentException e){refused=e.getMessage().contains("Next");}
        ok(refused,"unsupported Next transform silently dropped noise");
    }
    public static void main(String[] args)throws Exception{AppSettings.values.put("tapmod_enabled","false");TapMod.init(new Context());transforms();inversion();persisted();nextStatic();System.out.println("PASS "+checks+" native-noise and real import/persist/reload/play checks: outgoing easing, accumulated anchors, isolated/overlapping inversion, associated PEZ sidecar, static Next BPM/activation and both input backends.");}
}
