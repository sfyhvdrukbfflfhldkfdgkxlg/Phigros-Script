package io.github.phiscript.assets;

import android.content.Context;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.vision.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;
import org.json.*;

/** API fixtures and the real retained PEZ parser/library; no account credentials. */
public final class OnlinePhiraRegression {
    private static int checks;private static void ok(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private static JSONObject metadata(int id,String name,String level)throws Exception{return new JSONObject().put("id",id).put("name",name).put("level",level).put("charter","Public charter").put("file",OnlinePhiraLibrary.API+"/files/fixture-"+id);}
    private static byte[] json(Object value){return value.toString().getBytes(StandardCharsets.UTF_8);}
    private static OcrLabels labels(String title,String level){return new OcrLabels(Collections.singletonList(title),Collections.singletonList(level),Collections.emptyList(),false,false,null);}
    private static byte[] pack()throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip.putNextEntry(new ZipEntry("chart.json"));zip.write(Files.readAllBytes(new File("patch/tests/r17/fixtures/pgr_v3_multihold.json").toPath()));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("info.txt"));zip.write("#\nName: Online Test\nLevel: IN Lv.13\nChart: chart.json\n".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }return out.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        ok(OnlinePhiraLibrary.id("#8825")==8825,"number parser");
        for(String bad:new String[]{"0","-1","8825.0","https://example.com/1","9999999999"}){boolean rejected=false;try{OnlinePhiraLibrary.id(bad);}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"invalid number accepted: "+bad);}
        JSONObject entry=metadata(8825,"Online Test","IN Lv.13");final int[] queries={0};
        OnlinePhiraLibrary api=new OnlinePhiraLibrary((url,limit,stop)->{
            queries[0]++;try{
                if(url.contains("/chart?"))return json(new JSONObject().put("count",2).put("results",new JSONArray().put(entry).put(metadata(8826,"Other Song","IN Lv.13"))));
                return json(entry);
            }catch(Exception e){throw new IOException(e);}
        });
        ok(api.fetch(8825,new AtomicBoolean()).id==8825,"ID fetch metadata");
        OnlinePhiraLibrary.Search results=api.search("Online Test",new AtomicBoolean());ok(results.complete&&results.entries.size()==2,"server pagination");
        PhiraCandidate matched=OnlinePhiraSession.unique(labels("Online Test","IN Lv.13"),results);ok(matched!=null&&matched.getId().equals("phira-online:8825"),"OCR exact title/difficulty did not select online metadata");
        ok(OnlinePhiraSession.unique(labels("Online Test","AT Lv.13"),results)==null,"wrong difficulty guessed");
        List<OnlinePhiraLibrary.Entry> duplicate=Arrays.asList(OnlinePhiraLibrary.parse(entry),OnlinePhiraLibrary.parse(metadata(8827,"Online Test","IN Lv.13")));
        ok(OnlinePhiraSession.unique(labels("Online Test","IN Lv.13"),new OnlinePhiraLibrary.Search(duplicate,true))==null,"duplicate name/difficulty guessed");
        ok(OnlinePhiraSession.unique(labels("Online Test","IN Lv.13"),new OnlinePhiraLibrary.Search(results.entries,false))==null,"truncated result guessed uniqueness");
        for(String url:new String[]{"http://phira.5wyxi.com/files/x","https://evil.test/files/x",OnlinePhiraLibrary.API+"/files/",OnlinePhiraLibrary.API+"/files/../chart"}){boolean rejected=false;try{OnlinePhiraLibrary.parse(metadata(1,"A","IN").put("file",url));}catch(IllegalArgumentException expected){rejected=true;}ok(rejected,"invalid file origin/path accepted");}
        Context.root=Files.createTempDirectory("r17-phira-online").toFile();Context context=new Context();byte[] pack=pack();
        OnlinePhiraLibrary importer=new OnlinePhiraLibrary((url,limit,stop)->url.contains("/files/")?pack:json(entry));
        PhiraEntry imported=importer.importEntry(context,OnlinePhiraLibrary.parse(entry),new AtomicBoolean());
        PhiraLibrary local=RuntimeState.INSTANCE.getPhiraLibrary();ok(local!=null&&local.getEntries().size()==1,"online package not persisted into real library");
        Chart chart=local.load(imported.getId(),imported.getLevel());ok(chart.getNotes().size()==7,"online import did not produce actual chart");
        PhiraLibrary restored=PhiraLibrary.Companion.restore(context);ok(restored!=null&&restored.load(imported.getId(),imported.getLevel()).getNotes().size()==7,"online chart failed library restart");
        ok(SessionChartSource.phiraOnline(context,null,new AtomicBoolean()).isEmptyForSession()==false,"empty online library could not reach OCR");
        System.out.println("PASS "+checks+" Phira online-library checks: official API shape, ID and OCR matching, ambiguity/truncation rejection, bounded file origin, actual PEZ import and persistent local chart.");
        if(args.length>0&&args[0].equals("--live")){OnlinePhiraLibrary.Entry live=new OnlinePhiraLibrary().fetch(8825,new AtomicBoolean());ok(live.id==8825,"live official metadata");System.out.println("PASS live public Phira API #8825: "+live.title+" / "+live.level);}
    }
}
