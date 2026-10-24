package io.github.phiscript.record;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import io.github.phiscript.AppSettings;
import io.github.phiscript.RuntimeState;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Publish only successfully finalized videos. No broad storage or microphone permission. */
public final class RecordingFiles implements AutoCloseable {
    final Context context;
    final Uri uri;
    final String name;
    final ParcelFileDescriptor descriptor;
    private boolean completed;
    private final File fallback;
    public RecordingFiles(Context c) throws IOException {
        context=c.getApplicationContext();
        name="Phigros_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+".mp4";
        File file=null;Uri target=null;ParcelFileDescriptor fd=null;
        try {
            if(Build.VERSION.SDK_INT>=29){
                ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,name);
                values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/PhigrosScript");
                values.put(MediaStore.Video.Media.IS_PENDING,1);
                target=context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
                if(target==null)throw new IOException("无法创建相册视频");
                fd=context.getContentResolver().openFileDescriptor(target,"rw");
            }else{
                File dir=new File(context.getFilesDir(),"recordings");
                if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建录屏目录");
                file=new File(dir,name);fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_TRUNCATE);
                target=new Uri.Builder().scheme("content").authority(context.getPackageName()+".recordings").appendPath(name).build();
            }
            if(fd==null)throw new IOException("无法打开录屏输出");
        }catch(Exception e){
            if(fd!=null)try{fd.close();}catch(Exception ignored){}
            if(file!=null)file.delete();else if(target!=null)context.getContentResolver().delete(target,null,null);
            throw new IOException("创建录屏失败："+e.getMessage(),e);
        }
        fallback=file;uri=target;descriptor=fd;
    }
    public synchronized void finish(boolean valid){
        if(completed)return;completed=true;
        try{descriptor.close();}catch(Exception ignored){}
        if(valid){
            try{
                if(Build.VERSION.SDK_INT>=29){ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.IS_PENDING,0);context.getContentResolver().update(uri,values,null,null);}
                AppSettings prefs=new AppSettings(context);prefs.save("recording_last_uri",uri.toString());prefs.save("recording_last_name",name);
                RuntimeState.INSTANCE.log("录屏已保存："+(fallback==null?"Movies/PhigrosScript/":"应用录屏目录/")+name);
            }catch(Exception e){RuntimeState.INSTANCE.log("录屏保存在 "+uri+"，刷新相册失败："+e.getMessage());}
        }else{
            try{if(fallback!=null)fallback.delete();else context.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}
        }
    }
    public void close(){finish(false);}
    public static Intent view(Context c){
        String value=new AppSettings(c).text("recording_last_uri","");
        if(value.isEmpty())throw new IllegalStateException("尚无已完成的录屏；请先完成一次演奏");
        return new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(value),"video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
