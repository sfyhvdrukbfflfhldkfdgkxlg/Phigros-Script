package io.github.phiscript.assets;

import static io.github.phiscript.root.UiDesign.words;
import android.content.Context;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.input.PreparedChartStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.json.JSONObject;

/** The APK pins the identity, range and digest of each server-generated touch archive. */
final class CloudPlanSource {
    private static final String INDEX="phigros-cloud-plans-v1.json";
    private static JSONObject manifest;
    private static volatile long driveUnavailableUntil;

    private CloudPlanSource(){}

    private static synchronized JSONObject index(Context context)throws Exception{
        if(manifest!=null)return manifest;
        try(InputStream input=context.getAssets().open(INDEX)){
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            byte[] block=new byte[16384];int n;
            while((n=input.read(block))!=-1){
                if(output.size()+n>4*1024*1024)throw new IOException(words("云端谱库目录过大","Cloud chart index is too large"));
                output.write(block,0,n);
            }
            JSONObject result=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
            if(result.getInt("version")!=1)throw new IOException(words("云端谱库版本不支持","Unsupported cloud chart index"));
            result.getJSONObject("entries");result.getJSONArray("shards");
            manifest=result;
            return result;
        }
    }

    static Chart load(Context context,JSONObject entry,AtomicBoolean cancel)throws Exception{
        ChartDownload.check(cancel);
        JSONObject index=index(context);
        JSONObject plan=index.getJSONObject("entries").optJSONObject(entry.getString("sha1"));
        if(plan==null)throw new IOException(words("这张谱面没有对应的云端触点，请在 Swordalt 谱库准备谱面。",
                "No published touch track matches this chart. Prepare it in the Swordalt library."));
        String fingerprint=plan.getString("fingerprint");
        if(!fingerprint.matches("[0-9a-f]{64}"))throw new IOException(words("云端谱面指纹无效","Invalid cloud chart fingerprint"));
        try{return PreparedChartStore.openPlayback(context,fingerprint,16.0/9,false);}
        catch(IOException unavailable){ChartDownload.check(cancel);}

        PreparedChartStore.checkCloudProfile(context);
        int length=plan.getInt("length"),size=plan.getInt("bytes");
        long offset=plan.getLong("offset");
        String sha=plan.getString("sha256");
        if(size<=0||size>65*1024*1024||!sha.matches("[0-9a-f]{64}"))
            throw new IOException(words("云端谱面校验信息无效","Invalid cloud chart verification data"));
        JSONObject shard=index.getJSONArray("shards").getJSONObject(plan.getInt("shard"));
        RuntimeState.INSTANCE.log(words("下载演奏时间轴与触点：","Downloading timeline and touches: ")+entry.getString("title")+" / "+entry.getString("difficulty"));
        String id=shard.optString("drive");
        byte[] bytes=null;
        long retryAfter=driveUnavailableUntil;
        if(id.matches("[a-zA-Z0-9_-]{10,128}")&&(retryAfter==0||System.nanoTime()-retryAfter>=0)){
            try{
                bytes=download("https://drive.usercontent.google.com/download?export=download&confirm=t&id="+id,offset,length,sha,cancel);
            }catch(IOException failure){
                ChartDownload.check(cancel);
                driveUnavailableUntil=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
                RuntimeState.INSTANCE.log(words("Google 云盘暂时不可用，正在使用 GitHub 镜像。","Google Drive is unavailable. Trying the GitHub mirror."));
            }
        }
        if(bytes==null)bytes=download(shard.getString("github"),offset,length,sha,cancel);
        try(InputStream input=new GZIPInputStream(new ByteArrayInputStream(bytes))){
            Chart chart=PreparedChartStore.installCloud(context,fingerprint,input,size,cancel);
            ChartDownload.check(cancel);
            return chart;
        }
    }

    private static byte[] download(String url,long offset,int length,String sha,AtomicBoolean cancel)throws IOException{
        byte[] bytes=ChartDownload.range(url,offset,length,cancel);
        if(!PreparedChartStore.digest(bytes).equals(sha))throw new IOException(words("云端触点分段校验失败","Cloud touch archive checksum mismatch"));
        return bytes;
    }
}
