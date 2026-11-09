package android.util;
import java.io.*;import java.nio.file.*;
/** Filesystem transport model; production PhiraLibrary owns transaction ordering. */
public final class AtomicFile {
 public static boolean failNext;public static Runnable afterFinish;
 private final File base,pending;
 public AtomicFile(File file){base=file;pending=new File(file+".new");}
 public File getBaseFile(){return base;}
 public FileInputStream openRead()throws IOException{return new FileInputStream(base);}
 public FileOutputStream startWrite()throws IOException{return new FileOutputStream(pending);}
 public void finishWrite(FileOutputStream stream)throws IOException{
  stream.getFD().sync();stream.close();
  if(failNext){failNext=false;throw new IOException("forced index commit failure");}
  Files.move(pending.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING);
  if(afterFinish!=null){Runnable r=afterFinish;afterFinish=null;r.run();}
 }
 public void failWrite(FileOutputStream stream){try{stream.close();}catch(Exception ignored){}pending.delete();}
}
