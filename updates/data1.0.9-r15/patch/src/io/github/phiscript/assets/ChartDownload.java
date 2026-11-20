package io.github.phiscript.assets;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;
import static io.github.phiscript.root.UiDesign.words;
import java.net.*;import java.io.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Bounded HTTPS transport; no auth/captcha bypass and no unverified offline fallback. */
public final class ChartDownload {
    public interface Progress{void update(long bytes,long total);}
    static void check(AtomicBoolean cancel)throws InterruptedIOException{if(Thread.currentThread().isInterrupted()||cancel!=null&&cancel.get())throw new InterruptedIOException(tr("下载已取消"));}
    public static byte[] get(String link,int limit,AtomicBoolean cancel,Progress progress)throws IOException{return request(link,limit,cancel,progress,false);}
    public static byte[] request(String link,int limit,AtomicBoolean cancel,Progress progress,boolean post)throws IOException{
        return request(link,limit,cancel,progress,post,-1,0);
    }
    /** Read one independently checksummed gzip member, including on mirrors that ignore Range. */
    static byte[] range(String link,long start,int length,AtomicBoolean cancel)throws IOException{
        if(start<0||length<=0||start+length>32L*1024*1024||start+length<start)
            throw new IOException(words("云端谱面范围无效","Invalid cloud chart range"));
        return request(link,length,cancel,null,false,start,length);
    }
    private static byte[] request(String link,int limit,AtomicBoolean cancel,Progress progress,boolean post,long start,int length)throws IOException{
        URL u=new URL(link);Set<String> seen=new HashSet<>();int retries=0;
        boolean driveRange=start>=0&&"drive.usercontent.google.com".equals(u.getHost());
        for(int hop=0;hop<7;hop++){
            check(cancel);if(!"https".equalsIgnoreCase(u.getProtocol())||u.getUserInfo()!=null)throw new IOException(tr("只支持 HTTPS 下载链接"));
            if(!seen.add(u.toExternalForm()))throw new IOException(tr("下载链接循环跳转"));
            HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(driveRange?5000:15000);c.setReadTimeout(driveRange?5000:15000);
            c.setRequestProperty("User-Agent","Mozilla/5.0 PhigrosScript/1.0.10");c.setRequestProperty("Accept-Encoding","identity");
            if(start>=0)c.setRequestProperty("Range","bytes="+start+"-"+(start+length-1));
            if(u.getHost().endsWith("feijipan.com")){c.setRequestProperty("Referer","https://share.feijipan.com/");c.setRequestProperty("Origin","https://share.feijipan.com");}
            if(post){c.setRequestMethod("POST");c.setDoOutput(true);c.getOutputStream().close();}
            try{
                int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException(tr("重定向缺少地址"));u=new URL(u,loc);if(status==303||status==302)post=false;continue;}
                if(!driveRange&&(status==502||status==503||status==504)&&retries++<2){
                    seen.remove(u.toExternalForm());for(int pause=0;pause<5*retries;pause++){check(cancel);try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException(tr("下载已取消"));}}continue;
                }
                if(status!=200&&!(start>=0&&status==206))throw new IOException(tr("下载失败 HTTP ")+status+tr("，请检查链接或稍后重试"));
                long total=c.getContentLengthLong();
                String type=c.getContentType();if(type!=null&&type.toLowerCase(Locale.ROOT).contains("text/html"))throw new IOException(tr("返回的是网页，请使用谱面直链或小飞机分享链接；登录/验证码请在网盘中处理"));
                if(start>=0){
                    try(InputStream in=c.getInputStream()){
                        return readRange(in,status,c.getHeaderField("Content-Range"),total,start,length,cancel);
                    }
                }
                if(total>limit)throw new IOException(tr("下载文件超过大小限制"));
                ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){check(cancel);if(out.size()+(long)n>limit)throw new IOException(tr("下载文件超过大小限制"));out.write(b,0,n);if(progress!=null)progress.update(out.size(),total);}}
                check(cancel);if(total>=0&&out.size()!=total)throw new EOFException(tr("谱面下载不完整"));if(out.size()==0)throw new IOException(tr("下载文件为空"));return out.toByteArray();
            }finally{c.disconnect();}
        }
        throw new IOException(tr("下载跳转过多"));
    }
    static byte[] readRange(InputStream in,int status,String contentRange,long total,long start,int length,AtomicBoolean cancel)throws IOException{
        long end=start+length-1;
        if(status==206){
            String prefix="bytes "+start+"-"+end+"/";
            if(contentRange==null||!contentRange.startsWith(prefix)||total>=0&&total!=length)
                throw new IOException(words("云端谱面分段不匹配","Cloud chart range mismatch"));
            String shardSize=contentRange.substring(prefix.length());
            if(!"*".equals(shardSize)){
                try{if(Long.parseLong(shardSize)<=end)throw new NumberFormatException();}
                catch(NumberFormatException invalid){throw new IOException(words("云端文件大小无效","Invalid cloud shard size"));}
            }
        }else if(status==200){
            if(total>=0&&total<=end)throw new EOFException(tr("谱面下载不完整"));
            byte[] skip=new byte[16384];
            for(long left=start;left>0;){check(cancel);int n=in.read(skip,0,(int)Math.min(skip.length,left));if(n<0)throw new EOFException(tr("谱面下载不完整"));left-=n;}
        }else throw new IOException(tr("下载失败 HTTP ")+status);
        byte[] result=new byte[length];int done=0;
        while(done<length){check(cancel);int n=in.read(result,done,Math.min(16384,length-done));if(n<0)throw new EOFException(tr("谱面下载不完整"));done+=n;}
        check(cancel);return result;
    }
}
