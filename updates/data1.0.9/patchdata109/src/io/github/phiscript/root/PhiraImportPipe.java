package io.github.phiscript.root;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** App side writes its own cache, avoiding root-owned files and SELinux relabeling. */
public final class PhiraImportPipe {
    public interface Consumer {default void ready(){}void chart(File file,String label)throws Exception;void warning(String message);}
    public static final class Result {public int roots,charts,warnings;}
    private PhiraImportPipe(){}
    public static Result receive(InputStream stream,File workspace,Consumer consumer)throws Exception{
        if(!workspace.isDirectory()&&!workspace.mkdirs())throw new IOException("无法创建导入缓存");
        DataInputStream in=new DataInputStream(new BufferedInputStream(stream,65536));
        if(in.readInt()!=PhiraExport.MAGIC||in.readInt()!=PhiraExport.VERSION)throw new IOException("Root 谱库通道握手失败");
        consumer.ready();
        Result result=new Result();int index=0;
        while(true){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            int kind=in.readUnsignedByte();
            if(kind==0){result.roots=in.readInt();int declared=in.readInt();result.warnings=in.readInt();if(declared!=result.charts)throw new IOException("谱库传输数量不一致");return result;}
            if(kind==2){consumer.warning(in.readUTF());continue;}
            if(kind==3)continue; // Exporter heartbeat while walking folders.
            if(kind!=1||index>=10000)throw new IOException("谱库通道记录无效或超限");
            String label=in.readUTF();int count=in.readInt();if(count<1||count>PhiraExport.MAX_FILES)throw new IOException("谱包文件数无效");
            File target=new File(workspace,String.format(Locale.ROOT,"%05d.pez",index++));boolean complete=false;
            try{
                long total=0;Set<String> names=new HashSet<>();
                try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))){
                    for(int i=0;i<count;i++){
                        String name=PhiraExport.safeName(in.readUTF());long size=in.readLong();
                        if(!names.add(name)||size<0||size>PhiraExport.MAX_FILE||(total+=size)>PhiraExport.MAX_PACKAGE)throw new IOException("谱包路径重复或体积超限");
                        zip.putNextEntry(new ZipEntry(name));PhiraExport.copy(in,zip,size);zip.closeEntry();
                    }
                }
                complete=true;result.charts++;consumer.chart(target,label);
            }finally{if(!complete)target.delete();}
        }
    }
}
