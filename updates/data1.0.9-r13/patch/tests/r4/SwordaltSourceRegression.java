package io.github.phiscript.assets;
import java.io.*;import java.net.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;import java.util.zip.*;
import android.content.Context;import org.json.*;import io.github.phiscript.engine.*;
public final class SwordaltSourceRegression {
 static int checks,requests;static byte[] response;static boolean refreshing=false;static JSONObject full,example;static final AtomicBoolean cancel=new AtomicBoolean();
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 interface Work{void run()throws Exception;}static void fails(Work work,String m)throws Exception{boolean b=false;try{work.run();}catch(Exception expected){b=true;}ok(b,m);}
 static byte[] bytes(String p)throws Exception{return Files.readAllBytes(Paths.get(p));}
 static byte[] zip(String[] names,byte[][] values)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){for(int i=0;i<names.length;i++){z.putNextEntry(new ZipEntry(names[i]));z.write(values[i]);z.closeEntry();}}return b.toByteArray();}
 static byte[] route(URL u)throws Exception{
  if(!refreshing)return response;
  String p=u.getPath();if(p.endsWith("/branches/chart"))return new JSONObject().put("commit",new JSONObject().put("sha",full.getString("chartCommit"))).toString().getBytes("UTF-8");
  if(p.endsWith("/branches/info"))return new JSONObject().put("commit",new JSONObject().put("sha",full.getString("infoCommit"))).toString().getBytes("UTF-8");
  if(p.contains("/git/trees/"))return bytes("analysis/swordalt/chart-tree.json");
  if(p.endsWith("/info.tsv"))return bytes("analysis/swordalt/info.tsv");if(p.endsWith("/difficulty.tsv"))return bytes("analysis/swordalt/difficulty.tsv");if(p.endsWith("/version.txt"))return bytes("analysis/swordalt/version.txt");throw new AssertionError("unexpected catalog endpoint "+p);
 }
 public static void main(String[] args)throws Exception{
  Context.root=Files.createTempDirectory("swordalt-source").toFile();Context c=new Context();full=SwordaltSource.saved(c);JSONArray entries=full.getJSONArray("entries");
  ok(entries.length()==1057,"full catalog count");int visible=0,noisy=0,totalAreas=0;for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(e.getBoolean("siteVisible"))visible++;if(e.getInt("noiseCount")>0){noisy++;totalAreas+=e.getInt("noiseCount");}if(e.getString("path").equals("Ametrine.GRYSCLMIssionary.0/IN.json"))example=e;}
  ok(visible==1038&&noisy==25&&totalAreas==17782,"catalog noise and variant counts");ChartLibrary library=ExternalCharts.fromSwordaltCatalog(c,full);ok(library.getLocations().size()==1057,"independent adapter indexes all original charts");ok(library.getSourceLabel().contains("Swordalt"),"independent source label");
  JSONObject duplicate=new JSONObject(full.toString());duplicate.getJSONArray("entries").put(example);fails(()->SwordaltSource.validate(duplicate),"duplicate catalog path rejected");
  JSONObject unsafe=new JSONObject(full.toString());unsafe.getJSONArray("entries").getJSONObject(0).put("path","../IN.json");fails(()->SwordaltSource.validate(unsafe),"unsafe catalog path rejected");
  byte[] original=bytes("analysis/swordalt/raw-charts/"+example.getString("path"));ok(SwordaltSource.blobHash(original).equals(example.getString("sha1")),"real Git blob hash");
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){requests++;final byte[] body;try{body=route(u);}catch(Exception e){throw new RuntimeException(e);}return new HttpURLConnection(u){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}public long getContentLengthLong(){return body.length;}public String getContentType(){return "application/json";}public InputStream getInputStream(){return new ByteArrayInputStream(body);}};}}:null);
  response=original;Chart chart=SwordaltSource.loadChart(c,full,example,cancel);ok(BlockAreaSet.count(chart)==24,"all real Ametrine noise areas retained after download");ok(requests==1,"one selected chart request");File cache=new File(SwordaltSource.cacheDirectory(c),SwordaltSource.key(full,example)+".json");ok(Arrays.equals(original,Files.readAllBytes(cache.toPath())),"original JSON saved byte-for-byte");
  Chart again=SwordaltSource.loadChart(c,full,example,cancel);ok(requests==1&&BlockAreaSet.count(again)==24,"verified imported chart reusable without network");
  byte[] damaged=original.clone();damaged[0]='!';Files.write(cache.toPath(),damaged);SwordaltSource.loadChart(c,full,example,cancel);ok(requests==2&&Arrays.equals(original,Files.readAllBytes(cache.toPath())),"corrupt cache re-downloaded and repaired");
  Files.write(cache.toPath(),damaged);response="{\"msg\":\"error\"}".getBytes("UTF-8");fails(()->SwordaltSource.loadChart(c,full,example,cancel),"wrong download hash fails without playing corrupt cache");response=original;
  cancel.set(true);int before=requests;fails(()->SwordaltSource.loadChart(c,full,example,cancel),"cancelled selection stops");ok(requests==before,"no request after cancel");cancel.set(false);
  JSONObject one=new JSONObject(full.toString()).put("entries",new JSONArray().put(example));byte[] index=one.toString().getBytes("UTF-8");byte[] pack=zip(new String[]{"swordalt-catalog.json","charts/"+example.getString("path")},new byte[][]{index,original});
  JSONObject imported=SwordaltSource.importPack(c,new ByteArrayInputStream(pack),cancel);ok(imported.getJSONArray("entries").length()==1,"offline pack imported");ok(SwordaltSource.saved(c).getJSONArray("entries").length()==1,"offline catalog persisted");before=requests;ok(BlockAreaSet.count(SwordaltSource.loadChart(c,imported,example,cancel))==24&&requests==before,"offline import plays with noise without network");
  byte[] incomplete=zip(new String[]{"swordalt-catalog.json"},new byte[][]{index});fails(()->SwordaltSource.importPack(c,new ByteArrayInputStream(incomplete),cancel),"incomplete pack rejected");ok(SwordaltSource.saved(c).getJSONArray("entries").length()==1,"failed pack retains old catalog");
  byte[] bad=zip(new String[]{"swordalt-catalog.json","charts/"+example.getString("path")},new byte[][]{index,damaged});fails(()->SwordaltSource.importPack(c,new ByteArrayInputStream(bad),cancel),"offline hash mismatch rejected");
  byte[] traversal=zip(new String[]{"swordalt-catalog.json","../escape.json"},new byte[][]{index,original});fails(()->SwordaltSource.importPack(c,new ByteArrayInputStream(traversal),cancel),"archive traversal rejected");
  cancel.set(true);fails(()->SwordaltSource.importPack(c,new ByteArrayInputStream(pack),cancel),"offline cancellation");cancel.set(false);
  SwordaltSource.saveCatalog(c,full);refreshing=true;JSONObject updated=SwordaltSource.refresh(c,cancel);refreshing=false;ok(updated.getJSONArray("entries").length()==1057,"refresh builds full pinned catalog");int sites=0,noise=0;for(int i=0;i<updated.getJSONArray("entries").length();i++){JSONObject e=updated.getJSONArray("entries").getJSONObject(i);if(e.getBoolean("siteVisible"))sites++;if(e.getInt("noiseCount")>0)noise++;}ok(sites==1038&&noise==25,"refresh preserves known matching noise metadata");
  ok(ExternalCharts.deleteCacheFiles(c,Collections.singletonList(cache))==0&&!cache.exists(),"settings can delete this source cache");ok(new File(SwordaltSource.directory(c),"catalog.json").exists(),"cache deletion retains source catalog");
  before=requests;SwordaltSource.loadChart(c,full,example,cancel);ok(requests==before+1,"deleted chart downloads again");
  System.out.println("PASS Swordalt source "+checks+" assertions: catalog, originals, noise, cache, offline import, refresh, deletion, errors and cancellation");
 }
}
