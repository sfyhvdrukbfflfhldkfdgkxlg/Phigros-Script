package android.content;
import java.io.File;
public class Context {
 public static File root;
 public Context getApplicationContext(){return this;}
 public File getFilesDir(){File f=new File(root,"files");f.mkdirs();return f;}
 public File getCacheDir(){File f=new File(root,"cache");f.mkdirs();return f;}
 public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}
}
