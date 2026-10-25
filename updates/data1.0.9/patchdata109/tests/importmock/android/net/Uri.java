package android.net;
import java.io.File;
public final class Uri {
 public final File file;
 private Uri(File file){this.file=file;}
 public static Uri fromFile(File file){return new Uri(file);}
 public String getLastPathSegment(){return file.getName();}
 public String getScheme(){return "file";}
 public String getPath(){return file.getPath();}
 public String toString(){return file.toURI().toString();}
}
