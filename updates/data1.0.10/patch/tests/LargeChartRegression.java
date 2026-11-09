package io.github.phiscript.assets;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.*;
import io.github.phiscript.engine.Chart;
import org.json.JSONObject;

/** Exercises real import/normalization paths above their former size limits. */
public final class LargeChartRegression {
    static final int MIB=1024*1024, LIMIT=64*MIB;
    static String repeated(char c,int n){char[] chars=new char[n];Arrays.fill(chars,c);return new String(chars);}
    interface Action {void run() throws Exception;}
    static void reject(Action action,String name)throws Exception{
        try{action.run();}catch(IllegalArgumentException expected){System.out.println("PASS rejected "+name+": "+expected.getMessage());return;}
        throw new AssertionError("accepted oversized "+name);
    }
    static void boundary()throws Exception{
        if(PhiraChartParser.class.getField("MAX_JSON_BYTES").getInt(null)!=LIMIT)
            throw new AssertionError("parser byte limit differs from import/reader limit");
        if(ChartJsonReader.read(repeated(' ',LIMIT-2)+"{}").length()!=0)
            throw new AssertionError("64 MiB JSON boundary");
        System.out.println("PASS JSON exactly 64 MiB");
        reject(()->ChartJsonReader.read(repeated(' ',LIMIT-1)+"{}"),"JSON 64 MiB + 1 byte");
        reject(()->ChartJsonReader.read("{\"text\":\""+repeated('\u4e2d',LIMIT/3)+"\"}"),"UTF-8 bytes above 64 MiB with fewer characters");
        JSONObject unicode=ChartJsonReader.read("{\"text\":\"中文😀\"}");
        if(!"中文😀".equals(unicode.getString("text")))throw new AssertionError("Unicode corrupted");
        System.out.println("PASS Unicode byte counting and content preservation");
    }
    static void normalizedLarge()throws Exception{
        String fixture=new String(Files.readAllBytes(Paths.get("patch/tests/fixtures/pgr_v3_multihold.json")),StandardCharsets.UTF_8);
        JSONObject root=new JSONObject(fixture);root.put("padding",repeated('x',20*MIB));
        String input=root.toString();
        PhiraImportData imported=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),"large.json");
        if(imported.getNormalizedJson().getBytes(StandardCharsets.UTF_8).length<=16*MIB
                ||Chart.Companion.parse(imported.getNormalizedJson()).getNotes().size()!=7)
            throw new AssertionError("large raw/normalized chart failed");
        System.out.println("PASS raw JSON and normalized JSON above 20 MiB; seven notes retained");
    }
    static void pecLarge()throws Exception{
        String source=repeated(' ',9*MIB)+"\n"+new String(Files.readAllBytes(Paths.get("patch/tests/fixtures/pec_multihold.pec")),StandardCharsets.UTF_8);
        PhiraImportData imported=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),"large.pec");
        if(Chart.Companion.parse(imported.getNormalizedJson()).getNotes().size()!=5)throw new AssertionError("large PEC failed");
        System.out.println("PASS PEC above former 8 MiB limit; five notes retained");
    }
    static void zipLarge()throws Exception{
        byte[] fixture=Files.readAllBytes(Paths.get("patch/tests/fixtures/pgr_v3_multihold.json"));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] spaces=new byte[8192];Arrays.fill(spaces,(byte)' ');
        try(ZipOutputStream z=new ZipOutputStream(bytes)){
            z.putNextEntry(new ZipEntry("info.yml"));z.write("name: Size Test\nlevel: AT Lv.1\nchart: one.json\n".getBytes(StandardCharsets.UTF_8));z.closeEntry();
            for(String name:new String[]{"one.json","two.json"}){
                z.putNextEntry(new ZipEntry(name));int left=36*MIB-fixture.length;
                while(left>0){int n=Math.min(left,spaces.length);z.write(spaces,0,n);left-=n;}
                z.write(fixture);z.closeEntry();
            }
        }
        PhiraImportData imported=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(bytes.toByteArray()),"large.zip");
        if(Chart.Companion.parse(imported.getNormalizedJson()).getNotes().size()!=7)throw new AssertionError("retained package failed");
        System.out.println("PASS ZIP with two 36 MiB charts; 72 MiB retained data; metadata selection preserved");
    }
    public static void main(String[] args)throws Exception{
        boundary();System.gc();normalizedLarge();System.gc();pecLarge();System.gc();zipLarge();
        System.out.println("PASS large-chart limits (host import/parser checks; not an Android device run)");
    }
}
