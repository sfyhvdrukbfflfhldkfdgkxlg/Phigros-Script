package io.github.phiscript.root;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
import io.github.phiscript.assets.PhiraImportDecoder;import io.github.phiscript.assets.PhiraImportData;
public final class PhiraRootPipeRegression {
 static int checks;static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static byte[] chart;static void put(Path p,byte[] bytes)throws Exception{Files.createDirectories(p.getParent());Files.write(p,bytes);}
 static final class Sink implements PhiraImportPipe.Consumer {
  int records,warnings,parsed;boolean nested;final boolean decode;
  Sink(boolean decode){this.decode=decode;}
  public void warning(String s){warnings++;}
  public void chart(File f,String label)throws Exception{
   records++;try(ZipFile zip=new ZipFile(f)){ok(zip.getEntry("music.ogg")==null,"unneeded music copied");Enumeration<? extends ZipEntry> es=zip.entries();while(es.hasMoreElements()){String n=es.nextElement().getName();PhiraExport.safeName(n);if(n.contains("nested/"))nested=true;}}
   if(decode){try(InputStream in=new FileInputStream(f)){PhiraImportData data=PhiraImportDecoder.INSTANCE.decode(in,f.getName());ok(data.getNormalizedJson().contains("judgeLineList")||data.getNormalizedJson().contains("formatVersion"),"native importer did not decode transferred chart");parsed++;}}
   f.delete();
  }
 }
 static PhiraImportPipe.Result transfer(Path root,Path stage,Sink sink)throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();PhiraExport.write(Collections.singletonList(root.toFile()),bytes);return PhiraImportPipe.receive(new ByteArrayInputStream(bytes.toByteArray()),stage.toFile(),sink);}
 static byte[] header(int kind,String name,long size)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);out.writeInt(PhiraExport.MAGIC);out.writeInt(1);out.writeByte(kind);if(kind==1){out.writeUTF("fixture");out.writeInt(1);out.writeUTF(name);out.writeLong(size);}return b.toByteArray();}
 static void reject(byte[] bytes,Path stage,String why)throws Exception{boolean failed=false;try{PhiraImportPipe.receive(new ByteArrayInputStream(bytes),stage.toFile(),new Sink(false));}catch(Exception e){failed=true;}ok(failed,why);}
 public static void main(String[] args)throws Exception{
  chart=Files.readAllBytes(Paths.get("patch/tests/fixtures/pgr_v3_multihold.json"));Path base=Files.createTempDirectory("phira-root-test-");Path root=base.resolve("charts");
  for(int i=0;i<75;i++){Path dir=root.resolve("pack "+i);put(dir.resolve(i%2==0?"info.yml":"info.yaml"),("name: 谱面 "+i+"\nlevel: IN 15\nchart: 'nested/谱面 chart.json'\n").getBytes("UTF-8"));put(dir.resolve("nested/谱面 chart.json"),chart);put(dir.resolve("music.ogg"),new byte[1024]);}
  Sink many=new Sink(true);PhiraImportPipe.Result result=transfer(root,base.resolve("stage-many"),many);ok(result.charts==75&&many.parsed==75,"64-chart cutoff remains");ok(many.nested,"nested chart path flattened");ok(result.roots==1,"root count incorrect");
  Path zipped=base.resolve("archive-root");Files.createDirectories(zipped);try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(zipped.resolve("same name.pez")))){for(String n:new String[]{"wrapper/info.yml","wrapper/nested/chart.json","wrapper/music.ogg"}){z.putNextEntry(new ZipEntry(n));z.write(n.endsWith(".yml")?"name: Archive\nlevel: IN 15\nchart: nested/chart.json\n".getBytes("UTF-8"):n.endsWith(".json")?chart:new byte[100]);z.closeEntry();}}
  Sink zipSink=new Sink(true);ok(transfer(zipped,base.resolve("stage-zip"),zipSink).charts==1&&zipSink.parsed==1&&zipSink.nested,"archive chart selection/path preservation");
  Path raw=base.resolve("raw-root");put(raw.resolve("standalone.json"),chart);Sink rawSink=new Sink(true);ok(transfer(raw,base.resolve("stage-raw"),rawSink).charts==1&&rawSink.parsed==1,"raw local chart not discovered");
  Path bad=base.resolve("unsafe-root");put(bad.resolve("info.yml"),"chart: ../private.json\n".getBytes("UTF-8"));put(base.resolve("private.json"),"secret".getBytes("UTF-8"));Sink unsafe=new Sink(false);ok(transfer(bad,base.resolve("stage-unsafe"),unsafe).charts==0&&unsafe.warnings==1,"metadata path escaped chart directory");
  Path links=base.resolve("links-root");Files.createDirectories(links);Files.createSymbolicLink(links.resolve("leak.json"),base.resolve("private.json"));Sink symlink=new Sink(false);ok(transfer(links,base.resolve("stage-links"),symlink).charts==0,"symlink outside chart scope was exported");
  Path malformed=base.resolve("malformed");reject(header(1,"../escape",1),malformed,"path traversal accepted");reject(header(1,"chart.json",PhiraExport.MAX_FILE+1),malformed,"oversized record accepted");reject(header(1,"chart.json",10),malformed,"truncated record accepted");reject(new byte[]{1,2,3},malformed,"invalid handshake accepted");
  for(String name:new String[]{"/absolute","x/../escape","C:\\private","a//b","\u0000"}){boolean failed=false;try{PhiraExport.safeName(name);}catch(IOException e){failed=true;}ok(failed,"unsafe ZIP name accepted: "+name);}
  ok(PhiraExport.roots(10).stream().allMatch(f->f.getPath().contains("/10/")),"wrong Android user included");boolean failed=false;try{PhiraExport.roots(-1);}catch(IllegalArgumentException e){failed=true;}ok(failed,"invalid user accepted");
  System.out.println("PASS "+checks+" Root Phira scan/pipe/package checks including 75 real native-decoder imports, nested paths, archives/raw charts, no audio copies, symlink/path bounds, truncation, size guards and Android user paths.");
 }
}
