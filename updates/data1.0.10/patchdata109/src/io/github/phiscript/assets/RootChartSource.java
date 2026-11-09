package io.github.phiscript.assets;

import android.content.Context;
import io.github.phiscript.root.RootProcess;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

/** Only APK assets belonging to the PackageManager's installed Phigros snapshot. */
public final class RootChartSource implements ChartSource {
    private final Context context;
    public RootChartSource(Context context){this.context=context.getApplicationContext();}
    @Override public PackageSnapshot inspect(){
        try{return InstalledApksKt.packageSnapshot(context);}
        catch(Exception e){throw new IllegalStateException("未找到已安装的 Phigros",e);}
    }
    private void unchanged(PackageSnapshot snapshot){if(!snapshot.equals(inspect()))throw new IllegalStateException("Phigros 已更新，请重新一键导入");}
    @Override public List<String> entries(PackageSnapshot snapshot){
        unchanged(snapshot);
        try{
            List<String> args=new ArrayList<>();args.add("entries");args.addAll(snapshot.getPaths());
            JSONArray a=new JSONArray(new String(RootProcess.capture(context,16777216,args.toArray(new String[0])),"UTF-8"));
            List<String> result=new ArrayList<>();for(int i=0;i<a.length();i++)result.add(a.getString(i));unchanged(snapshot);return result;
        }catch(Exception e){throw new IllegalStateException("Root 无法读取 Phigros 资源索引",e);}
    }
    @Override public byte[] read(PackageSnapshot snapshot,String entry,int maxBytes){
        unchanged(snapshot);ApkFiles.INSTANCE.validateRead(entry,maxBytes);
        try{
            List<String> args=new ArrayList<>();args.add("read");args.add(entry);args.add(String.valueOf(maxBytes));args.addAll(snapshot.getPaths());
            byte[] data=RootProcess.capture(context,maxBytes,args.toArray(new String[0]));unchanged(snapshot);return data;
        }catch(Exception e){throw new IllegalStateException("Root 无法读取 Phigros 谱面资源",e);}
    }
}
