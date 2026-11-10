package android.content;
import java.io.File;
public class Context{
 public static final String WINDOW_SERVICE="window"; public static final int MODE_PRIVATE=0;
 public static File root=new File("patch/tests/temp");
 private static final java.util.Map<String,MemoryPreferences> preferences=new java.util.HashMap<>();
 public SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,n->new MemoryPreferences());}
 public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}
 public Context getApplicationContext(){return this;}
 public File getFilesDir(){File f=new File(root,"files");f.mkdirs();return f;}
 public File getCacheDir(){File f=new File(root,"cache");f.mkdirs();return f;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public Object getSystemService(String name){return new android.view.WindowManager(){};}
 public ContentResolver getContentResolver(){return new ContentResolver();}
}
