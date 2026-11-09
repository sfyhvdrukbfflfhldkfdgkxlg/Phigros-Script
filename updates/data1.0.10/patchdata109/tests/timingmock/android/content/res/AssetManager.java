package android.content.res;
import java.io.*;
public final class AssetManager {
 public InputStream open(String name)throws IOException{return new FileInputStream(new File("patchdata109/assets",name));}
}
