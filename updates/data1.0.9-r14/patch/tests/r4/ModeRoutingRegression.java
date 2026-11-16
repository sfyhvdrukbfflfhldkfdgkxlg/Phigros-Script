package io.github.phiscript.assets;
import android.content.*;import android.net.Uri;import io.github.phiscript.*;import io.github.phiscript.root.ModeRouting;import java.util.*;import java.nio.file.*;import java.io.*;import java.util.zip.*;
/** Uses the final APK's real AppSettings and its injected persistence hooks. */
public final class ModeRoutingRegression{
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static File pez(Context c,String title)throws Exception{
  File f=File.createTempFile("routing-",".pez",c.getCacheDir());try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){z.putNextEntry(new ZipEntry("chart.json"));z.write(Files.readAllBytes(Paths.get("upstream/updates/data1.0.9/patch/tests/fixtures/pgr_v3_multihold.json")));z.closeEntry();z.putNextEntry(new ZipEntry("info.txt"));z.write(("#\nName: "+title+"\nLevel: IN 12\nChart: chart.json\n").getBytes("UTF-8"));z.closeEntry();}return f;
 }
 public static void main(String[] args)throws Exception{
  Context.root=Files.createTempDirectory("mode-routing").toFile();Context c=new Context();AppSettings p=new AppSettings(c);SharedPreferences raw=c.getSharedPreferences("settings",0);
  // Reproduce the old persisted state without running the new hooks.
  raw.edit().putString("mode","PHIRA").putString("phigros_library_source","local").putBoolean("phigros_auto_mode",false).apply();
  ok(ModeRouting.needsPhira(p),"legacy local import really routes to Phira before migration");ModeRouting.migrateLegacy(p);ok(ModeRouting.kind(p)==ModeRouting.Kind.PHIGROS_AUTO&&!ModeRouting.needsPhira(p),"legacy Phigros-local state repaired");
  p.save("mode","PHIRA");ok(!p.flag("phigros_auto_mode",true)&&!p.flag("phigros_manual_mode",true),"real mode write clears Phigros flags");ModeRouting.migrateLegacy(p);ok(ModeRouting.kind(p)==ModeRouting.Kind.PHIRA,"later explicit Phira choice survives restart migration");
  for(String game:new String[]{"VISUAL","PHIRA"})for(boolean auto:new boolean[]{false,true})for(boolean manual:new boolean[]{false,true}){
   raw.edit().putString("mode",game).putBoolean("phigros_auto_mode",auto).putBoolean("phigros_manual_mode",manual).apply();ModeRouting.Kind expected=game.equals("PHIRA")?ModeRouting.Kind.PHIRA:manual?ModeRouting.Kind.PHIGROS_MANUAL:auto?ModeRouting.Kind.PHIGROS_AUTO:ModeRouting.Kind.VISUAL;
   ok(ModeRouting.kind(p)==expected,"consistent legacy mode priority");ok(ModeRouting.needsPhira(p)==(expected==ModeRouting.Kind.PHIRA),"Phira library check isolated");ok(ModeRouting.needsPhigros(p)==(expected==ModeRouting.Kind.PHIGROS_AUTO||expected==ModeRouting.Kind.PHIGROS_MANUAL),"Phigros library check isolated");ok(!ModeRouting.autoEngine(p,PlayMode.PHIRA),"captured Phira session cannot dispatch Phigros engine");
   ok(ModeRouting.autoEngine(p,PlayMode.VISUAL)==(expected==ModeRouting.Kind.PHIGROS_AUTO||expected==ModeRouting.Kind.PHIGROS_MANUAL),"both Phigros chart modes use shared session engine");
  }
  for(int index=0;index<4;index++){
   ModeRouting.select(p,index);ok(!(p.flag("phigros_auto_mode",false)&&p.flag("phigros_manual_mode",false)),"new mode flags mutually exclusive");AppSettings restarted=new AppSettings(c);ok(ModeRouting.kind(restarted)==ModeRouting.kind(p),"mode survives settings reload");
  }
  p.save("mode","VISUAL");p.saveFlag("phigros_auto_mode",true);p.saveFlag("phigros_manual_mode",true);ok(!p.flag("phigros_auto_mode",true)&&ModeRouting.kind(p)==ModeRouting.Kind.PHIGROS_MANUAL,"real flag hook keeps manual exclusive");p.saveFlag("phigros_auto_mode",true);ok(!p.flag("phigros_manual_mode",true)&&ModeRouting.kind(p)==ModeRouting.Kind.PHIGROS_AUTO,"real flag hook keeps auto exclusive");
  PhiraLibrary phira=PhiraLibrary.Companion.importFiles(c,Collections.singletonList(Uri.fromFile(pez(c,"True Phira"))),s->kotlin.Unit.INSTANCE);RuntimeState.INSTANCE.setPhiraLibrary(phira);byte[] phiraIndex=Files.readAllBytes(new File(c.getFilesDir(),"phira/index.json").toPath());
  PhiraLibrary phiStorage=PhiraLibrary.Companion.importFiles(new ExternalCharts.Storage(c),Collections.singletonList(Uri.fromFile(pez(c,"True Phigros Local"))),s->kotlin.Unit.INSTANCE);
  java.lang.reflect.Method wrap=ExternalCharts.class.getDeclaredMethod("fromLocal",Context.class,PhiraLibrary.class);wrap.setAccessible(true);ChartLibrary local=(ChartLibrary)wrap.invoke(null,c,phiStorage);
  ok(local.songs().get(0).getTitle().equals("True Phigros Local"),"Phigros source reads its own isolated data");ok(Arrays.equals(phiraIndex,Files.readAllBytes(new File(c.getFilesDir(),"phira/index.json").toPath())),"Phigros import does not overwrite Phira files");
  for(String source:new String[]{"local","cloud","swordalt","apk"}){
   p.save("mode","PHIRA");ExternalCharts.commitPhigros(c,local,source);ok(ModeRouting.kind(p)==ModeRouting.Kind.PHIGROS_AUTO&&!ModeRouting.needsPhira(p),"explicit Phigros source fixes mode: "+source);ok(p.mode()==PlayMode.VISUAL&&p.mode().getGamePackage().equals("com.PigeonGames.Phigros"),"real startup snapshot targets Phigros");ok(source.equals(p.text("phigros_library_source","")),"correct source persisted: "+source);ok(RuntimeState.INSTANCE.getLibrary()==local&&RuntimeState.INSTANCE.getPhiraLibrary()==phira,"libraries remain independent");
   ok(local.load(local.songs().get(0).getId(),"IN").getNotes().size()==7,"Phigros local chart loads without Phira route");
  }
  RuntimeState.INSTANCE.setPhiraLibrary(null);ExternalCharts.commitPhigros(c,local,"local");ok(p.mode()==PlayMode.VISUAL&&!ModeRouting.needsPhira(p)&&local.load(local.songs().get(0).getId(),"IN").getNotes().size()==7,"reported case: Phigros local works with no Phira library");RuntimeState.INSTANCE.setPhiraLibrary(phira);
  p.save("mode","PHIRA");ok(p.mode()==PlayMode.PHIRA&&p.mode().getGamePackage().equals("org.flos.phira"),"real startup snapshot targets Phira");ok(ModeRouting.kind(p)==ModeRouting.Kind.PHIRA&&RuntimeState.INSTANCE.getPhiraLibrary()==phira,"original Phira import/selection mode write remains valid");
  p.save("mode","VISUAL");RuntimeState.INSTANCE.setLibrary(null);RuntimeState.INSTANCE.setPhiraLibrary(null);ok(!ModeRouting.needsPhigros(p)&&!ModeRouting.needsPhira(p),"pure visual needs neither chart library");
  System.out.println("PASS actual-settings routing "+checks+" assertions: legacy migration, mode matrix, shared-preference hooks, isolated storage, all Phigros sources, Phira and visual routes");
 }
}
