package io.github.phiscript.assets;

import io.github.phiscript.RuntimeState;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.concurrent.atomic.AtomicBoolean;

/** The share site's public web-client protocol, for anonymously accessible shares only. */
public final class FeijipanSource {
    public static final String NEW_SHARE="feyajI8g",NEW_ASSET="phigros-cloud-feyajI8g-v1.json";
    private static final String UUID_VALUE=UUID.randomUUID().toString();
    private static final int MAX_FOLDERS=2048,MAX_FILES=10000;
    private static final class Folder {
        final String id,path;
        Folder(String id,String path){this.id=id;this.path=path;}
    }
    static String encode(String text)throws Exception{Cipher c=Cipher.getInstance("AES/ECB/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec("dingHao-disk-app".getBytes(StandardCharsets.UTF_8),"AES"));StringBuilder b=new StringBuilder();for(byte v:c.doFinal(text.getBytes(StandardCharsets.UTF_8)))b.append(String.format(Locale.ROOT,"%02X",v&255));return b.toString();}
    static String query(Map<String,String> p)throws Exception{StringBuilder b=new StringBuilder();for(Map.Entry<String,String> e:p.entrySet()){if(b.length()>0)b.append('&');b.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(e.getValue(),"UTF-8"));}return b.toString();}
    static Map<String,String> common(String share)throws Exception{Map<String,String> p=new LinkedHashMap<>();p.put("devType","6");p.put("devModel","Chrome");p.put("uuid",UUID_VALUE);p.put("extra","2");p.put("timestamp",encode(Long.toString(System.currentTimeMillis())));p.put("shareId",share);return p;}
    static JSONObject api(String path,Map<String,String> p,AtomicBoolean cancel)throws Exception{
        JSONObject j=new JSONObject(new String(ChartDownload.request("https://api.feijipan.com/"+path+"?"+query(p),4*1024*1024,cancel,null,true),StandardCharsets.UTF_8));
        if(j.optInt("code",200)!=200)throw new IOException("网盘目录读取失败："+j.optString("msg","需要登录或验证码"));return j;
    }
    private static String number(JSONObject row,String key)throws IOException{
        String id=row.optString(key,"");if(!id.matches("[0-9]{1,24}"))throw new IOException("网盘文件或目录编号无效");return id;
    }
    private static String segment(JSONObject row)throws IOException{
        String name=row.optString("name","");if(name.isEmpty())name=row.optString("fileName","");if(name.isEmpty())name=row.optString("folderName","");
        if(name.isEmpty()||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\"))throw new IOException("网盘文件或目录名称无效");
        for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))throw new IOException("网盘路径包含控制字符");return name;
    }
    private static void collect(JSONArray rows,String parent,ArrayDeque<Folder> queue,Set<String> ids,Set<String> paths,JSONArray files,AtomicBoolean cancel)throws Exception{
        for(int i=0;i<rows.length();i++){
            ChartDownload.check(cancel);JSONObject row=rows.getJSONObject(i);String name=segment(row),path=parent+name;
            if(!paths.add(path))throw new IOException("网盘目录包含重复路径："+path);
            if(row.optInt("fileType")==2){queue.add(new Folder(number(row,"folderId"),path+"/"));continue;}
            String id=number(row,"fileId");if(!ids.add(id))throw new IOException("网盘文件编号重复");
            files.put(new JSONObject().put("id",id).put("name",name).put("path",path).put("display",row.optString("fileName",name)));
            if(files.length()>MAX_FILES)throw new IOException("分享超过 10000 个文件");
        }
    }
    public static JSONObject catalog(String share,AtomicBoolean cancel)throws Exception{
        if(!share.matches("[A-Za-z0-9]{4,32}"))throw new IllegalArgumentException("无效的小飞机分享编号");
        Map<String,String> p=common(share);p.put("type","0");p.put("offset","1");p.put("limit","110");
        JSONObject root=api("ws/recommend/list",p,cancel);
        if(root.optInt("status",0)!=0)throw new IOException("分享需要提取码或已失效，请先在网盘中检查");
        JSONArray shares=root.getJSONArray("list");if(shares.length()!=1)throw new IOException("未找到唯一的分享目录");
        JSONArray files=new JSONArray();Set<String> folders=new HashSet<>(),ids=new HashSet<>(),paths=new HashSet<>();ArrayDeque<Folder> queue=new ArrayDeque<>();
        collect(shares.getJSONObject(0).getJSONArray("fileList"),"",queue,ids,paths,files,cancel);
        while(!queue.isEmpty()){
            ChartDownload.check(cancel);Folder folder=queue.remove();if(!folders.add(folder.id))throw new IOException("网盘目录重复或循环");
            if(folders.size()>MAX_FOLDERS)throw new IOException("分享子目录超过 2048 个");
            int count=0,total=-1,pages=-1;
            for(int page=1;page<=100;page++){
                p=common(share);p.put("folderId",folder.id);p.put("offset",Integer.toString(page));p.put("limit","110");JSONObject j=api("ws/share/list",p,cancel);JSONArray list=j.getJSONArray("list");
                int expected=j.optInt("total",-1),pageCount=j.optInt("totalPage",expected>=0?(expected+109)/110:1);
                pageCount=Math.max(1,pageCount);
                if(pageCount>100||expected>MAX_FILES+MAX_FOLDERS||page>1&&(expected!=total||pageCount!=pages))throw new IOException("网盘目录分页不完整或发生变化，请重新连接");
                total=expected;pages=pageCount;count+=list.length();collect(list,folder.path,queue,ids,paths,files,cancel);
                if(page>=pages){if(total>=0&&count!=total)throw new IOException("网盘目录分页不完整，请重新连接");break;}
                if(list.length()==0||page==100)throw new IOException("网盘目录分页不完整");
            }
            if(folders.size()%20==0)RuntimeState.INSTANCE.log("读取小飞机目录："+folders.size()+" 个文件夹 / "+files.length()+" 个文件");
        }
        JSONObject manifestFile=null;
        for(int i=0;i<files.length();i++)if("swordalt-catalog.json".equals(files.getJSONObject(i).getString("name"))){if(manifestFile!=null)throw new IOException("分享中有多个谱面清单，请单独分享一个谱库目录");manifestFile=files.getJSONObject(i);}
        JSONObject manifest=null;
        if(manifestFile!=null){byte[] data=download(share,manifestFile.getString("id"),4*1024*1024,cancel,null);manifest=SwordaltSource.validate(new JSONObject(new String(data,StandardCharsets.UTF_8)));}
        ChartDownload.check(cancel);return resolve(share,files,manifest,manifestFile==null?null:manifestFile.getString("path"));
    }
    /** Join the manifest's relative paths to actual files, never to noise sidecars or basenames. */
    static JSONObject resolve(String share,JSONArray files,JSONObject manifest,String manifestPath)throws Exception{
        JSONArray entries=new JSONArray(),missing=new JSONArray();JSONObject result=new JSONObject().put("share",share).put("entries",entries);
        if(manifest==null){
            for(int i=0;i<files.length();i++){JSONObject row=files.getJSONObject(i);if(row.getString("name").matches("(?i)^.+ - (EZ|HD|IN|AT|SP) \\[[^\\]]+\\]\\.(json|pez|zip)$"))entries.put(row);}
        }else{
            SwordaltSource.validate(manifest);if(manifestPath==null||!manifestPath.endsWith("swordalt-catalog.json"))throw new IOException("谱面清单路径无效");
            String prefix=manifestPath.substring(0,manifestPath.length()-"swordalt-catalog.json".length())+"charts/";
            Map<String,JSONObject> byPath=new HashMap<>();Set<String> ids=new HashSet<>();
            for(int i=0;i<files.length();i++){JSONObject row=files.getJSONObject(i);String path=row.getString("path");if(byPath.put(path,row)!=null||!ids.add(row.getString("id")))throw new IOException("网盘文件路径或编号重复");}
            JSONArray expected=manifest.getJSONArray("entries");
            for(int i=0;i<expected.length();i++){
                JSONObject resource=expected.getJSONObject(i),file=byPath.get(prefix+resource.getString("path"));
                if(file==null){missing.put(resource.getString("path"));continue;}
                String name=resource.getString("title")+" - "+resource.getString("difficulty")+" ["+resource.optString("level","?")+"].json";
                entries.put(new JSONObject().put("id",file.getString("id")).put("name",name).put("display",name).put("path",file.getString("path")).put("resource",resource).put("script",SwordaltSource.scripts(manifest)));
            }
            result.put("phiscriptFeijipanCatalog",1).put("manifestPath",manifestPath).put("chartCommit",manifest.getString("chartCommit")).put("version",manifest.optString("version"))
                .put("expectedCharts",expected.length()).put("missing",missing).put("availableCharts",entries.length());
            RuntimeState.INSTANCE.log("小飞机谱库核对：可用 "+entries.length()+" / "+expected.length()+" 张；缺失 "+missing.length()+" 张");
        }
        if(entries.length()==0)throw new IOException("分享中没有可用谱面；请保留 charts/ 目录和 swordalt-catalog.json，或使用带曲名和难度的文件名");
        return result;
    }
    static JSONObject validateResource(JSONObject item)throws Exception{
        JSONObject e=item.getJSONObject("resource");String path=e.getString("path");String[] parts=path.split("/",-1);
        if(parts.length!=2||parts[0].isEmpty()||parts[0].equals(".")||parts[0].equals("..")||path.contains("\\")||!parts[1].matches("(EZ|HD|IN|AT|SP)\\.json")||!parts[1].equals(e.getString("difficulty")+".json"))throw new IOException("谱面清单路径或难度无效");
        String remote=item.getString("path");for(String part:remote.split("/",-1)){if(part.isEmpty()||part.equals(".")||part.equals("..")||part.contains("\\"))throw new IOException("网盘谱面路径无效");for(int n=0;n<part.length();n++)if(Character.isISOControl(part.charAt(n)))throw new IOException("网盘谱面路径包含控制字符");}
        if(!item.getString("path").endsWith("/charts/"+path)&&!item.getString("path").equals("charts/"+path))throw new IOException("网盘谱面位置与清单不一致");
        String title=e.getString("title");if(title.trim().isEmpty()||title.length()>300||!e.getString("sha1").matches("[0-9a-f]{40}")||e.getLong("bytes")<1||e.getLong("bytes")>64L*1024*1024)throw new IOException("谱面清单校验信息无效");return e;
    }
    static void verify(byte[] bytes,JSONObject resource)throws Exception{
        if(bytes.length!=resource.getLong("bytes")||!SwordaltSource.blobHash(bytes).equals(resource.getString("sha1")))throw new IOException("小飞机谱面长度或 Git 数据校验失败，请更新目录后重试");
    }
    public static byte[] download(String share,String file,AtomicBoolean cancel,ChartDownload.Progress progress)throws Exception{return download(share,file,64*1024*1024,cancel,progress);}
    private static byte[] download(String share,String file,int limit,AtomicBoolean cancel,ChartDownload.Progress progress)throws Exception{
        if(!share.matches("[A-Za-z0-9]{4,32}")||!file.matches("[0-9]{1,24}"))throw new IllegalArgumentException("无效分享或谱面编号");long now=System.currentTimeMillis();Map<String,String> p=new LinkedHashMap<>();
        p.put("downloadId",encode(file+"|"));p.put("enable","1");p.put("devType","6");p.put("uuid",UUID_VALUE);p.put("timestamp",encode(Long.toString(now)));p.put("auth",encode(file+"|"+now));p.put("shareId",share);
        byte[] result=ChartDownload.get("https://api.feijipan.com/ws/file/redirect?"+query(p),limit,cancel,progress);
        if(result.length<16384){String text=new String(result,StandardCharsets.UTF_8).trim();if(text.startsWith("{")){JSONObject reply=new JSONObject(text);if(reply.has("code")&&!reply.has("phiscriptFormat")&&!reply.has("judgeLineList"))throw new IOException("网盘返回："+reply.optString("msg","下载未完成")+"（"+reply.optString("code")+"）");}}
        return result;
    }
}
