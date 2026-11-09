package android.content;
import android.net.Uri;import java.io.*;
public class ContentResolver {
 public InputStream openInputStream(Uri uri)throws IOException{return new FileInputStream(uri.file);}
 public android.database.Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){return null;}
}
