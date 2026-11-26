package io.github.phiscript.assets;

import static io.github.phiscript.root.UiDesign.words;
import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** One verified local file containing the published shards in the APK index's order. */
final class OfflineCloudPlans {
    static final String NAME="Phigros_Touch_Plans_4.1.0_20261122.zip";
    static final String SHARE_URL="https://share.feijipan.com/s/0hfX6hQd";
    private static final long ZIP_BYTES=433403067L;
    private static final String ZIP_SHA="30cb9ec7604e06b42fb35d8f1b5b928046dcd23059dfb82f1420c35a86ada4e8";

    private OfflineCloudPlans() {}

    private static File file(Context ctx,String indexHash){
        return new File(new File(ctx.getFilesDir(),"phigros-offline-plans-v1"),indexHash+".bin");
    }

    private static final class PackageStream extends DigestInputStream {
        long bytes;
        PackageStream(InputStream in) throws Exception {super(in,MessageDigest.getInstance("SHA-256"));}
        @Override public int read() throws IOException {
            int value=super.read();if(value>=0)count(1);return value;
        }
        @Override public int read(byte[] b,int off,int len) throws IOException {
            int n=super.read(b,off,len);if(n>0)count(n);return n;
        }
        private void count(int n)throws IOException {
            bytes+=n;
            if(bytes>ZIP_BYTES)throw invalid();
        }
    }

    private static IOException invalid(){return new IOException(words("离线触点包不完整或校验失败，请重新选择完整的 ZIP。",
            "The offline touch pack is incomplete or failed verification. Select the complete ZIP again."));}

    private static boolean matches(MessageDigest digest,String hex){
        byte[] bytes=digest.digest();
        if(hex.length()!=2*bytes.length)return false;
        for(int i=0;i<bytes.length;i++){
            int n=(Character.digit(hex.charAt(2*i),16)<<4)|Character.digit(hex.charAt(2*i+1),16);
            if(n!=(bytes[i]&255))return false;
        }
        return true;
    }

    static synchronized void install(Context ctx,InputStream input,AtomicBoolean cancel)throws Exception {
        try(PackageStream raw=new PackageStream(input)){
            ChartDownload.check(cancel);
            String hash=CloudPlanSource.indexHash(ctx);
            JSONArray shards=CloudPlanSource.index(ctx).getJSONArray("shards");
            File target=file(ctx,hash),dir=target.getParentFile();
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException(words("无法创建离线触点目录","Could not create the offline touch directory"));
            File staging=new File(dir,hash+".importing");
            Map<String,Integer> numbers=new HashMap<>();long[] offsets=new long[shards.length()+1];
            for(int i=0;i<shards.length();i++){
                long size=shards.getJSONObject(i).getLong("bytes");
                if(size<1||size>32L*1024*1024)throw invalid();
                numbers.put(String.format(Locale.ROOT,"phigros-plans-v1-%03d.bin",i),i);
                offsets[i+1]=offsets[i]+size;
            }
            try{
                try(RandomAccessFile out=new RandomAccessFile(staging,"rw");
                    ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw,65536))){
                    out.setLength(0);
                    Set<String> seen=new HashSet<>();byte[] buffer=new byte[65536];ZipEntry entry;
                    while((entry=zip.getNextEntry())!=null){
                        ChartDownload.check(cancel);
                        String name=entry.getName();Integer number=numbers.get(name);
                        if(entry.isDirectory()||!seen.add(name)||(number==null&&!CloudPlanSource.INDEX.equals(name)))throw invalid();
                        String expected=hash;long limit=4*1024*1024;
                        if(number!=null){
                            JSONObject shard=shards.getJSONObject(number);
                            expected=shard.getString("sha256");limit=shard.getLong("bytes");out.seek(offsets[number]);
                        }
                        MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;int n;
                        while((n=zip.read(buffer))!=-1){
                            ChartDownload.check(cancel);size+=n;if(size>limit)throw invalid();
                            digest.update(buffer,0,n);if(number!=null)out.write(buffer,0,n);
                        }
                        if((number!=null&&size!=limit)||!matches(digest,expected))throw invalid();
                        zip.closeEntry();
                    }
                    if(seen.size()!=shards.length()+1||out.length()!=offsets[shards.length()])throw invalid();
                    // The ZIP reader can stop at the central directory. Include every remaining byte in the package hash.
                    while(raw.read(buffer)!=-1)ChartDownload.check(cancel);
                    if(raw.bytes!=ZIP_BYTES||!matches(raw.getMessageDigest(),ZIP_SHA))throw invalid();
                    out.getFD().sync();
                }
                ChartDownload.check(cancel);
                Files.move(staging.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            }finally{staging.delete();}
        }
    }

    static byte[] read(Context ctx,String indexHash,JSONArray shards,int number,long offset,int length,String sha,AtomicBoolean cancel)throws Exception {
        File target=file(ctx,indexHash);
        if(!target.isFile())return null;
        long size=shards.getJSONObject(number).getLong("bytes");
        if(length<=0||offset<0||offset>size-length)throw invalid();
        long start=offset;
        for(int i=0;i<number;i++)start+=shards.getJSONObject(i).getLong("bytes");
        byte[] result=new byte[length];
        try(RandomAccessFile in=new RandomAccessFile(target,"r")){
            in.seek(start);
            for(int done=0;done<length;){
                ChartDownload.check(cancel);int n=in.read(result,done,Math.min(65536,length-done));
                if(n<0)throw invalid();done+=n;
            }
        }
        if(!io.github.phiscript.input.PreparedChartStore.digest(result).equals(sha))throw invalid();
        return result;
    }
}
