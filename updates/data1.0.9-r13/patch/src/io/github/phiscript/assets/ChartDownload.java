package io.github.phiscript.assets;
import java.net.*;import java.io.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Bounded HTTPS transport; no auth/captcha bypass and no unverified offline fallback. */
public final class ChartDownload {
    public interface Progress{void update(long bytes,long total);}
    static void check(AtomicBoolean cancel)throws InterruptedIOException{if(Thread.currentThread().isInterrupted()||cancel!=null&&cancel.get())throw new InterruptedIOException("下载已取消");}
    public static byte[] get(String link,int limit,AtomicBoolean cancel,Progress progress)throws IOException{return request(link,limit,cancel,progress,false);}
    public static byte[] request(String link,int limit,AtomicBoolean cancel,Progress progress,boolean post)throws IOException{
        URL u=new URL(link);Set<String> seen=new HashSet<>();int retries=0;
        for(int hop=0;hop<7;hop++){
            check(cancel);if(!"https".equalsIgnoreCase(u.getProtocol())||u.getUserInfo()!=null)throw new IOException("只支持 HTTPS 下载链接");
            if(!seen.add(u.toExternalForm()))throw new IOException("下载链接循环跳转");
            HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent","Mozilla/5.0 PhigrosScript/1.0.10");c.setRequestProperty("Accept-Encoding","identity");
            if(u.getHost().endsWith("feijipan.com")){c.setRequestProperty("Referer","https://share.feijipan.com/");c.setRequestProperty("Origin","https://share.feijipan.com");}
            if(post){c.setRequestMethod("POST");c.setDoOutput(true);c.getOutputStream().close();}
            try{
                int status=c.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("重定向缺少地址");u=new URL(u,loc);if(status==303||status==302)post=false;continue;}
                if((status==502||status==503||status==504)&&retries++<2){
                    seen.remove(u.toExternalForm());for(int pause=0;pause<5*retries;pause++){check(cancel);try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("下载已取消");}}continue;
                }
                if(status!=200)throw new IOException("下载失败 HTTP "+status+"，请检查链接或稍后重试");
                long total=c.getContentLengthLong();if(total>limit)throw new IOException("下载文件超过大小限制");
                String type=c.getContentType();if(type!=null&&type.toLowerCase(Locale.ROOT).contains("text/html"))throw new IOException("返回的是网页，请使用谱面直链或小飞机分享链接；登录/验证码请在网盘中处理");
                ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){check(cancel);if(out.size()+(long)n>limit)throw new IOException("下载文件超过大小限制");out.write(b,0,n);if(progress!=null)progress.update(out.size(),total);}}
                check(cancel);if(total>=0&&out.size()!=total)throw new EOFException("谱面下载不完整");if(out.size()==0)throw new IOException("下载文件为空");return out.toByteArray();
            }finally{c.disconnect();}
        }
        throw new IOException("下载跳转过多");
    }
}
