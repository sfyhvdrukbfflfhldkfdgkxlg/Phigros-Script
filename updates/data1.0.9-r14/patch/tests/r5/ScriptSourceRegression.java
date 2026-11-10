package io.github.phiscript.assets;
import java.io.*;import java.net.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.*;
import android.content.Context;import org.json.*;import io.github.phiscript.engine.*;

public final class ScriptSourceRegression {
 static int checks,requests;static byte[] response;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 interface Job{void run()throws Exception;}static void rejects(Job job,String m)throws Exception{try{job.run();throw new AssertionError(m);}catch(IOException|IllegalArgumentException expected){checks++;}}
 static byte[] pack(JSONObject catalog,String path,byte[] script)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){z.putNextEntry(new ZipEntry("swordalt-catalog.json"));z.write(catalog.toString().getBytes("UTF-8"));z.closeEntry();z.putNextEntry(new ZipEntry("charts/"+path));z.write(script);z.closeEntry();}return b.toByteArray();}
 public static void main(String[] args)throws Exception{
  Context.root=Files.createTempDirectory("script-source").toFile();Context c=new Context();JSONObject rawCatalog=SwordaltSource.saved(c);String path="Ametrine.GRYSCLMIssionary.0/IN.json";JSONObject raw= SwordaltSource.entry(rawCatalog,path);
  byte[] original=Files.readAllBytes(Paths.get("analysis/swordalt/raw-charts",path));byte[] script=PgrScriptConverter.convert(original,raw,rawCatalog);
  JSONObject row=new JSONObject(raw.toString()).put("sourceSha1",raw.getString("sha1")).put("sourceBytes",original.length).put("sha1",SwordaltSource.blobHash(script)).put("bytes",script.length);
  JSONObject catalog=new JSONObject(rawCatalog.toString()).put("phiscriptSwordaltScripts",1).put("entries",new JSONArray().put(row));
  SwordaltSource.validate(catalog);ok(SwordaltSource.scripts(catalog),"script directory recognized");ok(!SwordaltSource.key(catalog,row).equals(SwordaltSource.key(rawCatalog,raw)),"script and raw caches isolated");
  ok(Arrays.equals(script,PgrScriptConverter.convert(original,row,catalog)),"conversion stable after manifest size/hash replacement");
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){requests++;ok(u.getPath().contains(rawCatalog.optString("chartCommit")),"fallback uses pinned original");return new HttpURLConnection(u){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}public long getContentLengthLong(){return response.length;}public String getContentType(){return "application/json";}public InputStream getInputStream(){return new ByteArrayInputStream(response);}};}}:null);
  response=original;Chart first=SwordaltSource.loadChart(c,catalog,row,null);ok(BlockAreaSet.count(first)==24,"download/convert keeps noise");ok(requests==1,"one original request");
  File cache=new File(SwordaltSource.cacheDirectory(c),SwordaltSource.key(catalog,row)+".json");ok(Arrays.equals(script,Files.readAllBytes(cache.toPath())),"Android repair conversion equals exported script bytes");
  SwordaltSource.loadChart(c,catalog,row,null);ok(requests==1,"cached script works offline");
  byte[] bad=script.clone();bad[0]='!';Files.write(cache.toPath(),bad);SwordaltSource.loadChart(c,catalog,row,null);ok(requests==2&&Arrays.equals(script,Files.readAllBytes(cache.toPath())),"corrupt script repaired");
  ExternalCharts.deleteCacheFiles(c,Collections.singletonList(cache));SwordaltSource.loadChart(c,catalog,row,null);ok(requests==3,"deleted script re-downloaded and converted");
  SwordaltSource.importPack(c,new ByteArrayInputStream(pack(catalog,path,script)),null);int before=requests;Chart imported=SwordaltSource.loadChart(c,SwordaltSource.saved(c),row,null);ok(BlockAreaSet.count(imported)==24&&requests==before,"script ZIP loads without network");
  JSONObject invalid=new JSONObject(catalog.toString());invalid.getJSONArray("entries").getJSONObject(0).put("sourceSha1","invalid");rejects(()->SwordaltSource.validate(invalid),"invalid original hash rejected");
  Files.delete(cache.toPath());response="{}".getBytes("UTF-8");rejects(()->SwordaltSource.loadChart(c,catalog,row,null),"wrong original cannot produce a script");ok(!cache.exists(),"bad source never committed");
  PhiraImportData decoded=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(script),"IN.json");ok(decoded.getTitle().equals(raw.getString("title"))&&decoded.getLevel().startsWith("IN"),"single script includes importable title/difficulty");ok(BlockAreaSet.count(Chart.Companion.parse(decoded.getNormalizedJson()))==24,"single Phira/Phigros import keeps noise");
  System.out.println("PASS native script source "+checks+" assertions: deterministic conversion, pinned recovery, offline import, isolated caches, corruption, deletion and single-file import");
 }
}
