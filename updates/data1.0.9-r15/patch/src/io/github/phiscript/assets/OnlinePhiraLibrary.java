package io.github.phiscript.assets;

import android.content.Context;
import android.net.Uri;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.root.PhiraImportControl;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import kotlin.Unit;

/** Public read-only Phira API, as used by TeamFlos/phira client.rs/model/chart.rs.
 * No account token is read. Every imported package uses the existing local parser. */
public final class OnlinePhiraLibrary {
    public static final String API="https://phira.5wyxi.com";
    private static final int JSON_LIMIT=2*1024*1024,PACKAGE_LIMIT=128*1024*1024;
    public interface Transport {byte[] get(String url,int limit,AtomicBoolean stop)throws IOException;}
    private static final Transport HTTP=(url,limit,stop)->ChartDownload.get(url,limit,stop,null);
    public static final class Entry {
        public final int id;public final String title,level,charter,file;
        Entry(int id,String title,String level,String charter,String file){this.id=id;this.title=title;this.level=level;this.charter=charter;this.file=file;}
        public String key(){return "phira-online:"+id;}
        public PhiraEntry metadata(){return new PhiraEntry(key(),title,level,Collections.emptyList(),16.0/9,false);}
        public String display(){return "#"+id+"  "+title+" / "+level+"\n谱师："+charter;}
    }
    public static final class Search {public final List<Entry> entries;public final boolean complete;Search(List<Entry> e,boolean c){entries=e;complete=c;}}
    private final Transport transport;
    public OnlinePhiraLibrary(){this(HTTP);}
    public OnlinePhiraLibrary(Transport transport){this.transport=transport;}
    public static int id(String text){
        String n=text.trim();if(n.startsWith("#"))n=n.substring(1);
        if(!n.matches("[1-9][0-9]{0,8}"))throw new IllegalArgumentException("请输入 Phira 谱面正整数编号，例如 8825");
        return Integer.parseInt(n);
    }
    static Entry parse(JSONObject object)throws JSONException{
        int id=object.getInt("id");String title=object.getString("name").trim(),level=object.getString("level").trim(),file=object.getString("file");
        if(id<=0||title.isEmpty()||title.length()>200||level.isEmpty()||level.length()>80||!file.startsWith(API+"/files/")||!file.substring((API+"/files/").length()).matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}"))throw new IllegalArgumentException("Phira 返回了无效谱面元数据");
        return new Entry(id,title,level,object.optString("charter","未知"),file);
    }
    public Entry fetch(int id,AtomicBoolean stop)throws Exception{
        if(id<=0)throw new IllegalArgumentException("谱面编号无效");
        Entry e=parse(new JSONObject(new String(transport.get(API+"/chart/"+id,JSON_LIMIT,stop),StandardCharsets.UTF_8)));
        if(e.id!=id)throw new IllegalArgumentException("Phira 返回的编号不一致");return e;
    }
    public Search search(String title,AtomicBoolean stop)throws Exception{
        title=title.trim();if(title.length()<2||title.length()>200)throw new IllegalArgumentException("请输入 2–200 字符的曲名");
        LinkedHashMap<Integer,Entry> results=new LinkedHashMap<>();int count=0;
        for(int page=1;page<=5;page++){
            String path=API+"/chart?search="+URLEncoder.encode(title,"UTF-8")+"&page="+page+"&pageNum=20&type=-1";
            JSONObject root=new JSONObject(new String(transport.get(path,JSON_LIMIT,stop),StandardCharsets.UTF_8));JSONArray list=root.getJSONArray("results");count=root.getInt("count");
            if(count<0||list.length()>20)throw new IllegalArgumentException("Phira 返回了无效分页");
            for(int i=0;i<list.length();i++){Entry e=parse(list.getJSONObject(i));results.put(e.id,e);}
            if(page*20>=count)return new Search(new ArrayList<>(results.values()),true);
            if(list.length()==0)break;
        }
        return new Search(new ArrayList<>(results.values()),false);
    }
    public PhiraEntry importEntry(Context context,Entry entry,AtomicBoolean stop)throws Exception{
        try(PhiraImportControl.Session session=PhiraImportControl.begin()){
        Entry fresh=fetch(entry.id,stop);
        if(!fresh.title.equals(entry.title)||!fresh.level.equals(entry.level))throw new IllegalStateException("在线谱面的名称或难度已变化，请重新识别或按编号导入");
        byte[] bytes=transport.get(fresh.file,PACKAGE_LIMIT,stop);
        ChartDownload.check(stop);
        PhiraImportData decoded=PhiraImportDecoder.INSTANCE.decode(new ByteArrayInputStream(bytes),"phira-"+fresh.id+".pez");
        File temp=File.createTempFile("phira-online-",".pez",context.getCacheDir());
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}
            ChartDownload.check(stop);
            {
                PhiraLibrary library=PhiraLibrary.Companion.importFiles(context,Collections.singletonList(Uri.fromFile(temp)),s->{RuntimeState.INSTANCE.log(s);return Unit.INSTANCE;});
                RuntimeState.INSTANCE.setPhiraLibrary(library);
                PhiraEntry result=null;
                for(PhiraEntry e:library.getEntries())if(e.getTitle().equals(decoded.getTitle())&&e.getLevel().equals(decoded.getLevel())){if(result!=null)throw new IllegalStateException("导入后谱面仍有歧义");result=e;}
                if(result==null)throw new IllegalStateException("未找到导入后的 Phira 谱面");
                return result;
            }
        }finally{temp.delete();}
        }
    }
}
