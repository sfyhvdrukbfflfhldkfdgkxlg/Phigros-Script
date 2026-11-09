package io.github.phiscript.root;
import android.app.Activity;import android.content.Context;import android.util.AtomicFile;
import io.github.phiscript.RuntimeState;import io.github.phiscript.assets.PhiraLibrary;
import java.io.*;import java.nio.file.*;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicBoolean;

public final class IncrementalImportRegression {
 static int checks;static byte[] valid;static Path root;static Activity activity;static Class<?> type;static Method chart;static Constructor<?> constructor;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Object operation()throws Exception{return constructor.newInstance(activity);}
 static File file(String name,int offset)throws Exception{
  Path p=root.resolve(name+".pez");String json=new String(valid,"UTF-8");json=json.replaceFirst("\\\"time\\\"\\s*:\\s*32","\"time\":"+(32+offset));
  try(java.util.zip.ZipOutputStream out=new java.util.zip.ZipOutputStream(Files.newOutputStream(p))){
   out.putNextEntry(new java.util.zip.ZipEntry("info.yml"));out.write(("name: "+name+"\nlevel: IN 15\nchart: chart.json\n").getBytes("UTF-8"));out.closeEntry();
   out.putNextEntry(new java.util.zip.ZipEntry("chart.json"));out.write(json.getBytes("UTF-8"));out.closeEntry();
  }return p.toFile();
 }
 static void accept(Object op,File f)throws Exception{
  try{chart.invoke(op,f,f.getName());}catch(InvocationTargetException e){Throwable t=e.getCause();if(t instanceof Exception)throw (Exception)t;throw (Error)t;}
 }
 static AtomicBoolean cancelled(Object op)throws Exception{Field f=type.getDeclaredField("cancelled");f.setAccessible(true);return (AtomicBoolean)f.get(op);}
 static void durable(int count)throws Exception{
  PhiraLibrary saved=PhiraLibrary.Companion.restore(activity);
  ok(saved!=null&&saved.getEntries().size()==count,"completed chart not durable before next pipe record: expected "+count+", got "+(saved==null?-1:saved.getEntries().size()));
  ok(RuntimeState.INSTANCE.getPhiraLibrary()!=null&&RuntimeState.INSTANCE.getPhiraLibrary().getEntries().size()==count,"runtime library not published after individual commit");
  for(Object entry:saved.getEntries()){
   String id=(String)entry.getClass().getMethod("getId").invoke(entry);
   ok(!saved.load(id).getNotes().isEmpty(),"persisted chart bytes cannot be restored");
  }
 }
 public static void main(String[] args)throws Exception{
  root=Files.createTempDirectory("phi-incremental-import-");Context.root=root.toFile();activity=new Activity();valid=Files.readAllBytes(Paths.get("patch/tests/fixtures/pgr_v3_multihold.json"));
  type=Class.forName("io.github.phiscript.root.RootPhiraImport$Operation");constructor=type.getDeclaredConstructor(Activity.class);constructor.setAccessible(true);chart=type.getDeclaredMethod("chart",File.class,String.class);chart.setAccessible(true);
  Object op=operation();File first=file("first.json",1);accept(op,first);ok(!first.exists(),"staged chart retained after saving");durable(1);
  File bad=root.resolve("bad.json").toFile();Files.write(bad.toPath(),"not a chart".getBytes("UTF-8"));accept(op,bad);ok(!bad.exists(),"invalid staging file leaked");durable(1);
  AtomicFile.failNext=true;accept(op,file("failed-commit.json",2));durable(1);
  accept(op,file("after-failure.json",3));durable(2);
  RuntimeState.onLog=text->{if(text.startsWith("解析 Phira")){try{cancelled(op).set(true);}catch(Exception e){throw new AssertionError(e);}PhiraImportControl.cancel();}};
  boolean aborted=false;try{accept(op,file("cancel-before-commit.json",4));}catch(java.util.concurrent.CancellationException|InterruptedException e){aborted=true;}finally{RuntimeState.onLog=null;Thread.interrupted();}
  ok(aborted,"decode cancellation swallowed");durable(2);
  Object next=operation();AtomicFile.afterFinish=()->{try{cancelled(next).set(true);}catch(Exception e){throw new AssertionError(e);}PhiraImportControl.cancel();};
  accept(next,file("cancel-after-commit.json",5));durable(3);
  aborted=false;try{accept(next,file("must-not-import.json",6));}catch(InterruptedException e){aborted=true;}finally{Thread.interrupted();}
  ok(aborted,"next chart imported after cancellation");durable(3);
  System.out.println("PASS "+checks+" real Root consumer/native PhiraLibrary disk checks: first chart saved before next record, invalid chart/failed commit isolation, restart restoration, cancellation before and just after atomic commit, completed runtime library retained. Android file/URI transport model only.");
 }
}
