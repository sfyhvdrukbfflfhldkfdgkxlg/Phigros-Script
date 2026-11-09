package io.github.phiscript.assets;

import io.github.phiscript.root.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Uses real PhiraImportData, bounded native decoder and Root package transport. */
public final class NoiseAssetsRegression {
    static int checks;static boolean nativeHook;static byte[] chart;
    static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static byte[] utf(String text){return text.getBytes(StandardCharsets.UTF_8);}
    static byte[] area(double a,double b)throws Exception{return utf(new JSONArray().put(new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",0).put("y",0)).put("topRightPercentage",new JSONObject().put("x",1).put("y",1)).put("appearTime",0).put("enableTime",a).put("disableTime",b).put("disappearTime",10)).toString());}
    static PhiraImportData raw(byte[] source)throws Exception{return PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(source),"chart.json");}
    static byte[] zip(Map<String,byte[]> files)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(bytes)){for(Map.Entry<String,byte[]> e:files.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}return bytes.toByteArray();}
    static PhiraImportData decode(Map<String,byte[]> files,String selected)throws Exception{
        PhiraImportData data=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(zip(files)),"fixture.pez");
        return nativeHook?data:PhiraNoiseAssets.withSidecar(data,files,selected);
    }
    static JSONArray spans(PhiraImportData data)throws Exception{return NoiseWindowReader.read(new JSONObject(data.getNormalizedJson()));}
    static void assertSpan(PhiraImportData data,double a,double b,String why)throws Exception{JSONArray list=spans(data);ok(list.length()==1&&list.getJSONArray(0).getDouble(0)==a&&list.getJSONArray(0).getDouble(1)==b,why);}
    static void association()throws Exception{
        Map<String,byte[]> files=new LinkedHashMap<>();files.put("wrapper/info.yml",utf("name: Noise\nlevel: IN 15\nchart: nested/chart.json\n"));files.put("wrapper/nested/chart.json",chart);files.put("wrapper/nested/chart.blockAreaList.json",area(.6,1));files.put("wrapper/nested/blockAreaList.json",area(2,3));files.put("unrelated/chart.blockAreaList.json",area(4,5));files.put("wrapper/nested/other.blockAreaList.json",area(5,6));
        assertSpan(decode(files,"wrapper/nested/chart.json"),.6,1,"named chart sidecar lost or merged with another directory/difficulty");
        files.remove("wrapper/nested/chart.blockAreaList.json");assertSpan(decode(files,"wrapper/nested/chart.json"),2,3,"generic same-directory sidecar lost");
        files.remove("wrapper/nested/blockAreaList.json");ok(spans(decode(files,"wrapper/nested/chart.json")).length()==0,"another chart's sidecar was adopted");
        files.put("wrapper/nested/chart.blockAreaList.json",area(.6,1));files.put("wrapper/nested/CHART.BLOCKAREALIST.JSON",area(2,3));boolean rejected=false;try{decode(files,"wrapper/nested/chart.json");}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"ambiguous case sidecars silently chose one");files.remove("wrapper/nested/CHART.BLOCKAREALIST.JSON");
        JSONObject embedded=new JSONObject(new String(chart,StandardCharsets.UTF_8)).put("blockAreaList",new JSONArray());files.put("wrapper/nested/chart.json",utf(embedded.toString()));ok(spans(decode(files,"wrapper/nested/chart.json")).length()==0,"explicit empty embedded list did not override sidecar");
        files.put("wrapper/nested/chart.json",chart);
        Map<String,byte[]> bare=new LinkedHashMap<>();bare.put("chart.json",chart);bare.put("chart.blockAreaList.json",area(.6,1));assertSpan(decode(bare,"chart.json"),.6,1,"sidecar was mistaken for a second chart in metadata-free zip");
        ok(PhiraNoiseAssets.isSidecarName("nested/BlockAreaList.JSON")&&PhiraNoiseAssets.isSidecarName("nested/a.blockarealist.json")&&!PhiraNoiseAssets.isSidecarName("a.json"),"sidecar classifier");
        rejected=false;try{PhiraNoiseAssets.withSidecar(raw(chart),bare,"../chart.json");}catch(IllegalArgumentException e){rejected=true;}ok(rejected,"sidecar association bypassed path guard");
    }
    static void embedded()throws Exception{
        JSONArray nativeList=new JSONArray(new String(area(1.4,1.8),StandardCharsets.UTF_8));
        JSONObject pgr=new JSONObject(new String(chart,StandardCharsets.UTF_8)).put("blockAreaList",nativeList);
        JSONObject rpe=new JSONObject().put("BPMList",new JSONArray().put(new JSONObject().put("startTime",new JSONArray("[0,0,1]")).put("bpm",120)))
            .put("META",new JSONObject().put("name","Noise").put("level","IN 15").put("offset",175))
            .put("judgeLineList",new JSONArray().put(new JSONObject().put("eventLayers",new JSONArray()).put("notes",new JSONArray().put(new JSONObject().put("type",4).put("startTime",new JSONArray("[4,0,1]")).put("endTime",new JSONArray("[4,0,1]")).put("positionX",0).put("above",1)))));
        JSONObject next=new JSONObject().put("bottomLeft",new JSONObject().put("x",-100).put("y",-100)).put("topRight",new JSONObject().put("x",100).put("y",100))
            .put("appearTime",new JSONArray("[0,0,1]")).put("disappearTime",new JSONArray("[8,0,1]"))
            .put("activeIntervals",new JSONArray().put(new JSONObject().put("startTime",new JSONArray("[2,0,1]")).put("endTime",new JSONArray("[4,0,1]"))));
        JSONObject nextRpe=new JSONObject(rpe.toString()).put("blockAreaList",new JSONArray().put(next));
        JSONObject nativeRpe=new JSONObject(rpe.toString()).put("blockAreaList",nativeList);
        JSONObject nativeChart=RpeCompatibilityNormalizer.normalize(rpe);nativeChart.remove("phiscriptNoiseWindows");nativeChart.put("blockAreaList",nativeList);
        JSONObject[] sources={pgr,nativeRpe,nextRpe,nativeChart};String[] names={"PGR native embedded","RPE native embedded","RPE Next embedded","phiscript native embedded"};
        for(int i=0;i<sources.length;i++){
            Map<String,byte[]> files=new LinkedHashMap<>();files.put("chart.json",utf(sources[i].toString()));files.put("chart.blockAreaList.json",area(5,6));
            assertSpan(decode(files,"chart.json"),i==2?1:1.4,i==2?2:1.8,names[i]+" metadata was dropped or overwritten by sidecar");
            assertSpan(raw(files.get("chart.json")),i==2?1:1.4,i==2?2:1.8,names[i]+" direct JSON import lost noise metadata");
        }
    }
    static void write(Path path,byte[] bytes)throws Exception{Files.createDirectories(path.getParent());Files.write(path,bytes);}
    static void transport(Path root,Path stage,int expected)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();PhiraExport.write(Collections.singletonList(root.toFile()),bytes);
        final int[] count={0};PhiraImportPipe.Result result=PhiraImportPipe.receive(new ByteArrayInputStream(bytes.toByteArray()),stage.toFile(),new PhiraImportPipe.Consumer(){public void warning(String s){throw new AssertionError(s);}public void chart(File file,String label)throws Exception{
            count[0]++;Map<String,byte[]> files=new LinkedHashMap<>();String selected=null;try(ZipFile zip=new ZipFile(file)){Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();try(InputStream in=zip.getInputStream(e)){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)b.write(block,0,n);files.put(e.getName(),b.toByteArray());if(e.getName().endsWith("chart.json"))selected=e.getName();}}}
            ok(files.keySet().stream().anyMatch(PhiraNoiseAssets::isSidecarName),"Root exporter omitted associated sidecar");ok(!label.toLowerCase(Locale.ROOT).endsWith("blockarealist.json"),"Root exporter emitted sidecar as standalone chart");assertSpan(decode(files,selected),.6,1,"Root directory/archive transfer lost noise metadata");
        }});ok(result.charts==expected&&count[0]==expected,"sidecars changed exported chart count");
    }
    static void root()throws Exception{
        Path temp=Files.createTempDirectory("phi-noise-assets-");Path raw=temp.resolve("raw"),meta=temp.resolve("meta"),archive=temp.resolve("archive");
        write(raw.resolve("chart.json"),chart);write(raw.resolve("chart.blockAreaList.json"),area(.6,1));write(raw.resolve("other.blockAreaList.json"),area(5,6));transport(raw,temp.resolve("stage-raw"),1);
        write(meta.resolve("info.yml"),utf("name: Noise\nlevel: IN 15\nchart: nested/chart.json\n"));write(meta.resolve("nested/chart.json"),chart);write(meta.resolve("nested/chart.blockAreaList.json"),area(.6,1));transport(meta,temp.resolve("stage-meta"),1);
        Map<String,byte[]> zip=new LinkedHashMap<>();zip.put("nested/chart.json",chart);zip.put("nested/chart.blockAreaList.json",area(.6,1));zip.put("nested/other.blockAreaList.json",area(5,6));write(archive.resolve("no-info.pez"),zip(zip));transport(archive,temp.resolve("stage-archive"),1);
    }
    public static void main(String[] args)throws Exception{nativeHook=args.length>0&&args[0].equals("--native");chart=Files.readAllBytes(Paths.get("patch/tests/fixtures/pgr_v3_multihold.json"));association();embedded();root();System.out.println("PASS "+checks+" noise asset checks: real decoder/Root pipe, raw-directory and metadata-free archives, exact chart sidecar association, generic fallback, PGR/RPE Next/native embedded and empty priority, ambiguity/path guards and original chart counts; native hook="+nativeHook+".");}
}
