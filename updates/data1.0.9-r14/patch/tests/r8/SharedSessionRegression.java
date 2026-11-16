package io.github.phiscript.capture;

import android.content.Context;import android.graphics.RectF;import android.net.Uri;
import io.github.phiscript.*;import io.github.phiscript.assets.*;import io.github.phiscript.engine.*;import io.github.phiscript.input.*;import io.github.phiscript.root.*;import io.github.phiscript.vision.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.atomic.*;import java.util.zip.*;import org.json.*;

/** Executes actual final-APK Phira sessions against deterministic camera and touch transports. */
public final class SharedSessionRegression {
 static int checks,traces,requests;static PhiraLibrary phira;static ChartLibrary phigros;static byte[] response;static Context context;static String fixture;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 interface Task{void run()throws Exception;}static void fails(Task task,String m)throws Exception{boolean failed=false;try{task.run();}catch(Exception e){failed=true;}ok(failed,m);}
 static File pez(Context c,String name)throws Exception{
  File file=File.createTempFile("session-",".pez",c.getCacheDir());try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(file))){
   z.putNextEntry(new ZipEntry("chart.json"));z.write(fixture.getBytes(StandardCharsets.UTF_8));z.closeEntry();
   z.putNextEntry(new ZipEntry("info.txt"));z.write(("#\nName: "+name+"\nLevel: IN\nChart: chart.json\nOffset: 0.125\nAspectRatio: 1.5\nForceAspectRatio: true\n").getBytes(StandardCharsets.UTF_8));z.closeEntry();
  }return file;
 }
 static SessionSettings settings(boolean pgr,boolean manual,boolean automatic){
  String id=manual&&!pgr?phira.getEntries().stream().filter(e->e.getTitle().equals("Common Song")).findFirst().get().getId():pgr?"foreign-phira-selection":"";
  return new SessionSettings(new RectF(.1f,.05f,.9f,.95f),new RectF(0,0,1,1),new RectF(0,0,1,1),0,-17,new RectF(0,0,.18f,.24f),120,new NormalizedPoint(.5f,.5f),200,pgr?PlayMode.VISUAL:PlayMode.PHIRA,new NormalizedPoint(.5f,.5f),1380,31,new RectF(0,0,.18f,.24f),automatic,id);
 }
 static ChartLocation common(){for(ChartLocation l:phigros.getLocations())if(l.getAssetKey().equals("Common Song"))return l;throw new AssertionError("common song missing");}
 static void scenario(boolean pgr,boolean root,boolean manual,String ending,boolean automatic,boolean pro)throws Exception{
  SessionTrace.reset(ending,root);RuntimeState.INSTANCE.getActiveTouchStop().set(null);RuntimeState.logs.clear();
  TouchService service=new TouchService();ModeRouting.select(new AppSettings(service),pgr?(manual?3:0):1);
  service.foregroundPackage=pgr?TouchService.GAME_PACKAGE:pro?GamePackages.PHIRA_PRO:TouchService.PHIRA_PACKAGE;
  service.selectGame(service.foregroundPackage);
  RuntimeState.INSTANCE.setLibrary(phigros);RuntimeState.INSTANCE.setPhiraLibrary(pgr?null:phira);
  if(pgr&&manual)SessionChartSource.selectManual(service,phigros,common());
  if(root)RootClient.after=SessionTrace::rootFrame;
  SessionSettings settings=settings(pgr,manual,automatic);boolean preview=ending.equals("preview");Exception failure=null;
  try{if(pgr)new PhigrosAutoEngine(service,new FrameStore(),settings,preview,SessionTrace.stop,new RecognitionOverlay()).run();else new PhiraSessionEngine(new FrameStore(),phira,settings,preview,SessionTrace.stop,new RecognitionOverlay()).run();}
  catch(Exception error){failure=error;}
  finally{RootClient.after=null;}
  if(ending.equals("retry-stuck")){
   ok(failure!=null&&SessionTrace.plays==0,"no playback when retry does not return to gameplay");ok(SessionTrace.commands.equals(Arrays.asList("pause:2","retry:1")),"failed restart clicks retry only once");
  }else if(ending.equals("preview")||ending.equals("decline")){
   if(failure!=null)throw failure;ok(SessionTrace.plays==0&&SessionTrace.commands.isEmpty(),"preview/declined confirmation sends no input");ok(RootClient.created==0,"no Root preparation before user confirmation");
  }else{
   if(failure!=null)throw failure;ok(SessionTrace.offers==1&&SessionTrace.commands.equals(Arrays.asList("pause:2","retry:1")),"one confirmed pause/retry sequence");ok(SessionTrace.plays==1,"exactly one Phira playback entry");
   ok(SessionTrace.chart.getNotes().size()==7&&BlockAreaSet.count(SessionTrace.chart)==1,"source retained chart notes and noise");
   long expected=PhiraRestartClock.INSTANCE.epoch(SessionTrace.receipt,settings.getPhiraRestartDelayMs(),SessionTrace.chart.getOffsetSeconds(),settings.getPhiraGlobalOffsetMs(),settings.getTouchOffsetMs());
   ok(Math.abs(SessionTrace.epoch-expected)<10,"shared retry epoch includes chart/global/touch offsets exactly once");
   Viewport wanted=automatic?PhiraViewport.INSTANCE.bounds(2400,1080,1.5,true):new Viewport(240,54,1920,972);
   ok(Math.abs(SessionTrace.viewport.getLeft()-wanted.getLeft())<.1&&Math.abs(SessionTrace.viewport.getTop()-wanted.getTop())<.1&&Math.abs(SessionTrace.viewport.getWidth()-wanted.getWidth())<.1&&Math.abs(SessionTrace.viewport.getHeight()-wanted.getHeight())<.1,"uses identical Phira viewport and source aspect metadata");
   if(ending.equals("detected-retry"))ok(Math.abs(SessionTrace.retryX-(pgr?.56f:.5f))<.002&&Math.abs(SessionTrace.retryY-(pgr?.47f:.5f))<.002,"Phigros clicks measured retry glyph; Phira retains configured point");
   ok(Math.abs(SessionTrace.pauseLeft-(!pgr&&automatic?wanted.getLeft()/2400:0))<.001,"Phigros pause ROI uses screen space; Phira keeps viewport mapping");
   if(ending.equals("stale"))ok(SessionTrace.staleStopped-SessionTrace.staleBegan>=1500,"shared stale-frame monitor permits Phira's 1.5-second evidence window");
   if(root)ok(SessionTrace.rootConsumed&&RootClient.created==1,"Phira and Phigros reuse the same pre-retry Root channel");
  }
  ok(RuntimeState.INSTANCE.getActiveTouchStop().get()==null,"shared active input ownership released");
  Field owners=SharedChartPlayback.class.getDeclaredField("OWNERS");owners.setAccessible(true);ok(((Map<?,?>)owners.get(null)).isEmpty(),"prepared session ownership released");
  if(root&&!ending.equals("preview")&&!ending.equals("decline"))ok(RootClient.last.closed&&RootClient.last.active.isEmpty(),"Root resources and pointers released");
  if(pgr)ok(RuntimeState.INSTANCE.getPhiraLibrary()==null,"Phigros runs without a Phira runtime library");
  traces++;System.out.println("PASS session "+(pgr?"Phigros":"Phira")+" root="+root+" manual="+manual+" "+ending);
 }
 static void mockNetwork(){URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url){requests++;return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){return 200;}public String getContentType(){return "application/json";}public long getContentLengthLong(){return response.length;}public InputStream getInputStream(){return new ByteArrayInputStream(response);}};}}:null);}
 static byte[] nativeScript(String path)throws Exception{try(ZipFile zip=new ZipFile("count-current/Phigros_Swordalt_All_Scripts.zip");InputStream in=zip.getInputStream(zip.getEntry("charts/"+path));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 static OcrLabels labels(String title,String level){return new OcrLabels(Collections.singletonList(title),Collections.singletonList(level),Collections.emptyList(),false,false,null);}
 static List<PhiraCandidate> candidates(SessionChartSource source){List<PhiraCandidate> result=new ArrayList<>();for(PhiraEntry e:source.getEntries())result.add(new PhiraCandidate(e.getId(),e.getTitle(),e.getLevel(),e.getAliases()));return result;}
 static void adapters()throws Exception{
  new AppSettings(context).save("mode","VISUAL");ModeRouting.select(new AppSettings(context),0);new AppSettings(context).save("phiraChartId",phira.getEntries().get(0).getId());
  SessionChartSource local=SessionChartSource.phigros(context,phigros,new AtomicBoolean());ok(local.selectionId(settings(true,false,true)).isEmpty(),"Phigros automatic source ignores another library's manual selection");
  ok(local.getEntries().get(0).getAspectRatio()==1.5&&local.getEntries().get(0).getForceAspectRatio(),"local source aspect metadata preserved");
  PhiraCandidate found=local.match(labels("Common Song","IN Lv.12"),candidates(local));ok(found!=null&&found.getLevel().equals("IN"),"Phigros source maps rank labels to actual chart entries");
  PhiraEntry common=phira.getEntries().stream().filter(e->e.getTitle().equals("Common Song")).findFirst().get();SessionChartSource original=SessionChartSource.phira(phira);
  ok(original.match(labels("Common Song","IN"),candidates(original)).getId().equals(common.getId()),"Phira original matcher retained");ok(original.match(labels("Common Song","IN Lv.12"),candidates(original))==null,"Phira custom-level strictness retained");
  PhiraCandidate other=local.match(labels("Other Song","IN"),candidates(local));fails(()->local.load(found.getId(),"HD"),"wrong selected difficulty rejected");ok(local.load(found.getId(),"IN").getNotes().size()==7,"local Phigros source loads exact identity");
  SessionChartSource cancelled=SessionChartSource.phigros(context,phigros,new AtomicBoolean(true));fails(()->cancelled.load(found.getId(),"IN"),"source load honors cancellation");
  SessionChartSource.selectManual(context,phigros,common());SessionChartSource manual=SessionChartSource.phigros(context,phigros,new AtomicBoolean());ok(!manual.selectionId(settings(true,false,true)).isEmpty()&&SessionChartSource.hasManual(context,phigros),"own Phigros manual choice persisted separately");
  new AppSettings(context).save(SessionChartSource.MANUAL_ID,"invalid");fails(()->SessionChartSource.phigros(context,phigros,new AtomicBoolean()),"stale manual choice rejected before input");ModeRouting.select(new AppSettings(context),0);
  mockNetwork();JSONObject old=new JSONObject().put("share","eTyQ30l7").put("entries",new JSONArray().put(new JSONObject().put("id","123456").put("name","Common Song - IN [12].json")));
  SessionChartSource cloud=SessionChartSource.phigros(context,ExternalCharts.fromCatalog(context,old),new AtomicBoolean());response=fixture.getBytes(StandardCharsets.UTF_8);PhiraEntry entry=cloud.getEntries().get(0);int before=requests;Chart first=cloud.load(entry.getId(),entry.getLevel()),second=cloud.load(entry.getId(),entry.getLevel());ok(requests==before+2&&first!=second&&BlockAreaSet.count(second)==1,"old cloud source downloads anew and preserves noise through Phira adapter");
  JSONObject catalog=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/assets/"+FeijipanSource.NEW_ASSET)),StandardCharsets.UTF_8));JSONObject selected=null;for(int i=0;i<catalog.getJSONArray("entries").length();i++){JSONObject row=catalog.getJSONArray("entries").getJSONObject(i);if(row.getJSONObject("resource").getString("path").equals("Glaciaxion.SunsetRay.0/EZ.json"))selected=row;}
  response=nativeScript("Glaciaxion.SunsetRay.0/EZ.json");SessionChartSource current=SessionChartSource.phigros(context,ExternalCharts.fromCatalog(context,new JSONObject(catalog.toString()).put("entries",new JSONArray().put(selected))),new AtomicBoolean());entry=current.getEntries().get(0);ok(current.load(entry.getId(),entry.getLevel()).getNotes().size()==66,"new feyajI8g source verifies and loads native script through Phira adapter");response=response.clone();response[5]^=1;final PhiraEntry badEntry=entry;fails(()->current.load(badEntry.getId(),badEntry.getLevel()),"new-source hash mismatch stops shared session before playback");
  JSONObject scripts=new JSONObject(new String(Files.readAllBytes(Paths.get("patch/data-tools/snapshot/feijipan-feyajI8g/manifest.json")),StandardCharsets.UTF_8));JSONObject resource=null;for(int i=0;i<scripts.getJSONArray("entries").length();i++)if(scripts.getJSONArray("entries").getJSONObject(i).getString("path").equals("Glaciaxion.SunsetRay.0/EZ.json"))resource=scripts.getJSONArray("entries").getJSONObject(i);
  scripts.put("entries",new JSONArray().put(resource));Method key=SwordaltSource.class.getDeclaredMethod("key",JSONObject.class,JSONObject.class);key.setAccessible(true);String stem=(String)key.invoke(null,scripts,resource);Files.write(new File(SwordaltSource.cacheDirectory(context),stem+".json").toPath(),nativeScript(resource.getString("path")));
  SessionChartSource swordalt=SessionChartSource.phigros(context,ExternalCharts.fromSwordaltCatalog(context,scripts),new AtomicBoolean());entry=swordalt.getEntries().get(0);ok(swordalt.load(entry.getId(),entry.getLevel()).getNotes().size()==66,"Swordalt source keeps verified offline script reads");
  TouchService service=new TouchService();service.foregroundPackage=GamePackages.PHIRA_PRO;ok(!original.isForeground(),"regular Phira target cannot inject into Pro");service.selectGame(GamePackages.PHIRA_PRO);ok(original.isForeground()&&!local.isForeground(),"Phira Pro belongs only to Phira target");service.foregroundPackage=TouchService.GAME_PACKAGE;service.selectGame(TouchService.GAME_PACKAGE);ok(local.isForeground()&&!original.isForeground(),"Phigros target remains isolated");
  AppSettings prefs=new AppSettings(context);prefs.save("phiraRestartDelay","1234");prefs.save("phigrosRestartDelay","55");
  ModeRouting.select(prefs,0);ok(prefs.snapshot().getPhiraRestartDelayMs()==55&&PhigrosRetryClock.readDelay(prefs)==55,"Phigros reads its own retry delay");
  ModeRouting.select(prefs,1);ok(prefs.snapshot().getPhiraRestartDelayMs()==1234,"Phira retains the existing delay");
 }
 public static void main(String[] args)throws Exception{
  Context.root=Files.createTempDirectory("shared-phira-session").toFile();context=new Context();String base=new String(Files.readAllBytes(Paths.get("upstream/updates/data1.0.9/patch/tests/fixtures/pgr_v3_multihold.json")),StandardCharsets.UTF_8);
  fixture=new JSONObject(base).put("blockAreaList",new JSONArray().put(new JSONObject("{\"bottomLeftPercentage\":{\"x\":0.4,\"y\":0.4},\"topRightPercentage\":{\"x\":0.6,\"y\":0.6},\"appearTime\":0,\"enableTime\":0,\"disableTime\":9,\"disappearTime\":10}"))).toString();
  List<Uri> files=Arrays.asList(Uri.fromFile(pez(context,"Common Song")),Uri.fromFile(pez(context,"Other Song")));phira=PhiraLibrary.Companion.importFiles(context,files,s->kotlin.Unit.INSTANCE);
  Constructor<?> storage=Class.forName("io.github.phiscript.assets.ExternalCharts$Storage").getDeclaredConstructor(Context.class);storage.setAccessible(true);PhiraLibrary imported=PhiraLibrary.Companion.importFiles((Context)storage.newInstance(context),files,s->kotlin.Unit.INSTANCE);
  Method from=ExternalCharts.class.getDeclaredMethod("fromLocal",Context.class,PhiraLibrary.class);from.setAccessible(true);phigros=(ChartLibrary)from.invoke(null,context,imported);
  for(String end:new String[]{"result","pause","stale","focus","dimension","switch","cancel","retry-stuck","preview","decline"})for(boolean pgr:new boolean[]{false,true})scenario(pgr,false,false,end,true,false);
  for(boolean pgr:new boolean[]{false,true}){scenario(pgr,true,false,"pause",true,!pgr);scenario(pgr,false,true,"result",false,false);scenario(pgr,true,false,"preview",true,false);scenario(pgr,true,false,"decline",true,false);}
  for(boolean pgr:new boolean[]{false,true})for(boolean root:new boolean[]{false,true})scenario(pgr,root,false,"detected-retry",true,false);
  adapters();System.out.println("PASS shared Phira session: "+checks+" assertions, "+traces+" actual-engine traces; source isolation, root handoff, confirmation, retry, viewport, offsets, stop/cleanup and every external source");
 }
}
