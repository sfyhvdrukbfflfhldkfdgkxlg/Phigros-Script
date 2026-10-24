package io.github.phiscript.assets;

import android.content.Context;
import java.io.*;
import java.util.*;

/** Music-relative facts from a pinned public chart snapshot, never device latency. */
public final class FirstNoteCatalog {
    public static final String ASSET="phigros-first-notes-v1.tsv";
    private static volatile FirstNoteCatalog cached;
    private final Map<String,Entry> rows;
    public final String gameVersion;
    public static final class Entry {
        public final String songId,difficulty,sourceBlob;
        public final long firstChartUs,offsetUs,firstMusicUs,lastMusicEndUs;
        public final int notes;
        Entry(String[] v){
            songId=v[0];difficulty=v[1];firstChartUs=Long.parseLong(v[2]);offsetUs=Long.parseLong(v[3]);
            firstMusicUs=Long.parseLong(v[4]);notes=Integer.parseInt(v[5]);lastMusicEndUs=Long.parseLong(v[6]);sourceBlob=v[7];
            if(songId.isEmpty()||songId.length()>512||difficulty.isEmpty()||difficulty.length()>24||firstChartUs<0
                    ||Math.abs(firstChartUs+offsetUs-firstMusicUs)>1||notes<1||notes>1000000||lastMusicEndUs<firstMusicUs
                    ||!sourceBlob.matches("[0-9a-f]{40}"))throw new IllegalArgumentException("首键基准数据无效");
        }
    }
    private FirstNoteCatalog(Map<String,Entry> rows,String version){this.rows=Collections.unmodifiableMap(rows);gameVersion=version;}
    public static String canonical(String id){return id.endsWith(".0")?id.substring(0,id.length()-2):id;}
    private static String key(String id,String difficulty){return canonical(id)+"\n"+difficulty.toUpperCase(Locale.ROOT);}
    public Entry find(String id,String difficulty){return rows.get(key(id,difficulty));}
    public List<Entry> entries(){return new ArrayList<>(rows.values());}
    public static FirstNoteCatalog get(Context context)throws IOException{
        FirstNoteCatalog result=cached;
        if(result==null)synchronized(FirstNoteCatalog.class){
            result=cached;if(result==null)try(InputStream in=context.getAssets().open(ASSET)){cached=result=read(in);}
        }return result;
    }
    public static FirstNoteCatalog read(InputStream input)throws IOException{
        Map<String,Entry> entries=new LinkedHashMap<>();String version="",line;boolean schema=false;int bytes=0;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(input,"UTF-8"))){
            while((line=reader.readLine())!=null){
                bytes+=line.length();if(bytes>2097152||line.length()>2048)throw new IOException("首键基准超过大小限制");
                if(line.equals("# phigros-first-notes-v1")){schema=true;continue;}
                if(line.startsWith("# game_version=")){version=line.substring(15);continue;}
                if(line.startsWith("#")||line.isEmpty())continue;
                String[] values=line.split("\t",-1);if(values.length!=8)throw new IOException("首键基准列数错误");
                try{Entry entry=new Entry(values);if(entries.put(key(entry.songId,entry.difficulty),entry)!=null)throw new IOException("首键基准重复");}
                catch(IllegalArgumentException e){throw new IOException("首键基准解析失败",e);}
                if(entries.size()>4096)throw new IOException("首键基准数量超限");
            }
        }
        if(!schema||version.isEmpty()||entries.isEmpty())throw new IOException("缺少首键基准版本");
        return new FirstNoteCatalog(entries,version);
    }
}
