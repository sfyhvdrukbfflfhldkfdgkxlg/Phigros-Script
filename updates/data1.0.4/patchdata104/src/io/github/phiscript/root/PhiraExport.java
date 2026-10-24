package io.github.phiscript.root;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Root side: chart resources only, sent over a private pipe; never writes game data. */
public final class PhiraExport {
    public static final int MAGIC=0x50484933,VERSION=1,MAX_FILES=4096;
    public static final long MAX_FILE=64L*1024*1024,MAX_PACKAGE=128L*1024*1024;
    private final DataOutputStream out;
    private final PrintStream pipe;private long nextPulse;
    private final Set<String> seen=new HashSet<>();
    private int visited,exported,warnings,roots;private boolean emitting;
    private PhiraExport(OutputStream output){out=new DataOutputStream(new BufferedOutputStream(output,65536));pipe=output instanceof PrintStream?(PrintStream)output:null;}
    private void pulse()throws IOException{if(System.nanoTime()<nextPulse)return;nextPulse=System.nanoTime()+500000000L;out.writeByte(3);out.flush();checkPipe();}
    private void checkPipe()throws IOException{if(pipe!=null&&pipe.checkError())throw new IOException("谱库导入已取消，停止 Root 读取");}
    public static List<File> roots(int user)throws IOException{
        if(user<0||user>999)throw new IllegalArgumentException("Android 用户编号无效");
        List<File> result=new ArrayList<>();
        for(String base:new String[]{"/data/user/"+user+"/org.flos.phira","/data/user_de/"+user+"/org.flos.phira","/storage/emulated/"+user+"/Android/data/org.flos.phira","/data/media/"+user+"/Android/data/org.flos.phira"}){
            File parent=new File(base).getCanonicalFile();
            for(String relative:new String[]{"files/data/charts","files/charts","data/charts"}){
                File candidate=new File(parent,relative).getCanonicalFile();
                if(candidate.getPath().startsWith(parent.getPath()+File.separator))result.add(candidate);
            }
        }return result;
    }
    public static void write(List<File> roots,OutputStream stream)throws IOException{
        PhiraExport exporter=new PhiraExport(stream);exporter.out.writeInt(MAGIC);exporter.out.writeInt(VERSION);exporter.out.flush();
        Set<String> unique=new HashSet<>();
        for(File root:roots){File canonical=root.getCanonicalFile();if(!canonical.isDirectory()||!unique.add(canonical.getPath()))continue;exporter.roots++;exporter.walk(canonical,canonical,0);}
        exporter.out.writeByte(0);exporter.out.writeInt(exporter.roots);exporter.out.writeInt(exporter.exported);exporter.out.writeInt(exporter.warnings);exporter.out.flush();
    }
    private static boolean chart(String name){String s=name.toLowerCase(Locale.ROOT);return s.endsWith(".json")||s.endsWith(".pec")||s.endsWith(".pgr")||s.endsWith(".rpe")||s.endsWith(".pbc");}
    private static boolean metadata(String name){return name.equalsIgnoreCase("info.yml")||name.equalsIgnoreCase("info.yaml")||name.equalsIgnoreCase("info.txt");}
    private static boolean archive(String name){String s=name.toLowerCase(Locale.ROOT);return s.endsWith(".pez")||s.endsWith(".zip");}
    public static String safeName(String name)throws IOException{
        String normalized=name.replace('\\','/');
        while(normalized.startsWith("./"))normalized=normalized.substring(2);
        if(normalized.isEmpty()||normalized.startsWith("/")||normalized.indexOf('\0')>=0||normalized.matches("^[A-Za-z]:.*"))throw new IOException("谱包路径无效");
        for(String part:normalized.split("/",-1))if(part.isEmpty()||part.equals(".."))throw new IOException("谱包路径越界");
        return normalized;
    }
    private void walk(File root,File directory,int depth)throws IOException{
        pulse();
        if(depth>48)throw new IOException("Phira 谱库目录嵌套过深");
        if(++visited>250000)throw new IOException("Phira 谱库目录超过扫描范围，请分批导入");
        File[] files=directory.listFiles();if(files==null){warn("目录无法读取："+directory.getName());return;}Arrays.sort(files);
        File info=null;
        for(File f:files)if(f.isFile()&&!Files.isSymbolicLink(f.toPath())&&metadata(f.getName())){info=f;break;}
        if(info!=null){
            try{directory(directory,info);}catch(IOException e){if(emitting)throw e;warn(directory.getName()+"："+e.getMessage());}
            return;
        }
        for(File file:files){
            pulse();
            if(++visited>250000)throw new IOException("Phira 谱库文件数量超过扫描范围，请分批导入");
            if(Files.isSymbolicLink(file.toPath()))continue;
            File canonical=file.getCanonicalFile();if(!canonical.getPath().startsWith(root.getPath()+File.separator))continue;
            if(canonical.isDirectory())walk(root,canonical,depth+1);
            else if(archive(file.getName())){
                try{zip(canonical);}catch(IOException e){if(emitting)throw e;warn(file.getName()+"："+e.getMessage());}
            }else if(chart(file.getName())){
                try{sendFiles(canonical.getPath(),canonical.getParentFile(),Collections.singletonList(canonical));}catch(IOException e){if(emitting)throw e;warn(file.getName()+"："+e.getMessage());}
            }
        }
    }
    private static String declaredChart(byte[] info){
        String text=new String(info,java.nio.charset.StandardCharsets.UTF_8);
        for(String line:text.split("\\r?\\n")){
            String value=line.trim();if(value.startsWith("chart:"))value=value.substring(6).trim();else if(value.regionMatches(true,0,"Chart:",0,6))value=value.substring(6).trim();else continue;
            if(value.length()>1&&(value.charAt(0)=='\"'||value.charAt(0)=='\'')){
                char quote=value.charAt(0);int end=value.lastIndexOf(quote);if(end>0)value=value.substring(1,end).replace("''","'");
            }else{int comment=value.indexOf(" #");if(comment>=0)value=value.substring(0,comment).trim();}
            return value;
        }return null;
    }
    private void directory(File root,File info)throws IOException{
        if(!seen.add(info.getCanonicalPath()))return;
        List<File> selected=new ArrayList<>();selected.add(info);collectCharts(root,root,selected,0);
        if(info.length()>1024*1024)throw new IOException("信息文件过大");
        String declared=declaredChart(Files.readAllBytes(info.toPath()));
        if(declared!=null&&!declared.isEmpty()){
            File target=new File(root,safeName(declared)).getCanonicalFile();
            if(!target.getPath().startsWith(root.getCanonicalPath()+File.separator)||Files.isSymbolicLink(target.toPath()))throw new IOException("chart 路径越界");
            if(target.isFile()&&!selected.contains(target))selected.add(target);
        }
        if(selected.size()<2)throw new IOException("未找到可读取谱面文件");
        sendFiles(root.getPath(),root,selected);
    }
    private void collectCharts(File root,File folder,List<File> selected,int depth)throws IOException{
        pulse();
        if(depth>32||selected.size()>MAX_FILES)throw new IOException("单个谱包目录过大");
        File[] files=folder.listFiles();if(files==null)throw new IOException("谱包目录无法读取");Arrays.sort(files);
        for(File file:files){if(Files.isSymbolicLink(file.toPath()))continue;File f=file.getCanonicalFile();if(!f.getPath().startsWith(root.getCanonicalPath()+File.separator))continue;if(f.isDirectory())collectCharts(root,f,selected,depth+1);else if(chart(f.getName())&&!selected.contains(f))selected.add(f);}
    }
    private void sendFiles(String label,File root,List<File> files)throws IOException{
        if(files.size()>MAX_FILES)throw new IOException("单个谱包文件数超限");long total=0;
        for(File file:files){if(!file.isFile()||!file.canRead()||file.length()>MAX_FILE)throw new IOException("谱面文件无法读取或超过 64 MiB");total+=file.length();}
        if(total>MAX_PACKAGE)throw new IOException("单个谱包超过 128 MiB");
        begin(label,files.size());
        for(File file:files){String relative=root.toPath().relativize(file.toPath()).toString();out.writeUTF(safeName(relative));out.writeLong(file.length());try(InputStream in=new FileInputStream(file)){sendBytes(in,file.length());}}
        out.flush();exported++;emitting=false;
    }
    private void zip(File file)throws IOException{
        if(!seen.add(file.getCanonicalPath()))return;
        try(ZipFile zip=new ZipFile(file)){
            List<ZipEntry> entries=new ArrayList<>(),infos=new ArrayList<>();Enumeration<? extends ZipEntry> iterator=zip.entries();
            while(iterator.hasMoreElements()){ZipEntry e=iterator.nextElement();if(e.isDirectory())continue;String path=safeName(e.getName());if(entries.size()>100000)throw new IOException("压缩谱包条目过多");entries.add(e);String base=path.substring(path.lastIndexOf('/')+1);if(metadata(base))infos.add(e);}
            if(infos.isEmpty()){for(ZipEntry e:entries)if(chart(e.getName()))sendZip(file.getPath()+"!/"+e.getName(),zip,Collections.singletonList(e),"");return;}
            for(ZipEntry info:infos){
                String name=safeName(info.getName()),prefix=name.substring(0,name.lastIndexOf('/')+1);List<ZipEntry> selected=new ArrayList<>();selected.add(info);
                if(info.getSize()<0||info.getSize()>1024*1024)throw new IOException("压缩包信息文件过大");
                byte[] meta;try(InputStream input=zip.getInputStream(info)){ByteArrayOutputStream bytes=new ByteArrayOutputStream();copy(input,bytes,info.getSize());meta=bytes.toByteArray();}
                String target=declaredChart(meta);if(target!=null&&!target.isEmpty())target=prefix+safeName(target);
                for(ZipEntry e:entries)if(!e.equals(info)&&safeName(e.getName()).startsWith(prefix)&&(chart(e.getName())||e.getName().equals(target)))selected.add(e);
                if(selected.size()>1)sendZip(file.getPath()+"!/"+prefix,zip,selected,prefix);
            }
        }
    }
    private void sendZip(String label,ZipFile zip,List<ZipEntry> entries,String prefix)throws IOException{
        if(entries.size()>MAX_FILES)throw new IOException("压缩谱包文件数超限");long total=0;
        for(ZipEntry e:entries){if(e.getSize()<0||e.getSize()>MAX_FILE)throw new IOException("压缩谱面大小无效或超限");total+=e.getSize();}
        if(total>MAX_PACKAGE)throw new IOException("解压后谱面总量超过 128 MiB");
        begin(label,entries.size());
        for(ZipEntry e:entries){out.writeUTF(safeName(e.getName()).substring(prefix.length()));out.writeLong(e.getSize());try(InputStream in=zip.getInputStream(e)){sendBytes(in,e.getSize());}}
        out.flush();exported++;emitting=false;
    }
    private void sendBytes(InputStream in,long size)throws IOException{byte[] bytes=new byte[65536];long remaining=size;while(remaining>0){int n=in.read(bytes,0,(int)Math.min(remaining,bytes.length));if(n<0)throw new EOFException("谱面在读取时被更改");out.write(bytes,0,n);out.flush();checkPipe();remaining-=n;}}
    private void begin(String label,int count)throws IOException{checkPipe();if(exported>=10000)throw new IOException("谱库超过 10000 份，请分批导入");emitting=true;out.writeByte(1);out.writeUTF(label);out.writeInt(count);}
    private void warn(String text)throws IOException{warnings++;out.writeByte(2);out.writeUTF(text.length()>1000?text.substring(0,1000):text);out.flush();}
    static void copy(InputStream in,OutputStream out,long size)throws IOException{byte[] buffer=new byte[65536];long remaining=size;while(remaining>0){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消 Phira 导入");int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException("谱面在读取时被更改");out.write(buffer,0,n);remaining-=n;}}
}
