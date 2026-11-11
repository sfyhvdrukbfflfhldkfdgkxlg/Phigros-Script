package io.github.phiscript.root;

import io.github.phiscript.GamePackages;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The root subprocess has no Activity or SharedPreferences. Scan both known clients. */
public final class PhiraChartRoots {
    private PhiraChartRoots() {}
    public static List<File> roots(int user)throws IOException{
        if(user<0||user>999)throw new IllegalArgumentException("Android 用户编号无效");
        List<File> result=new ArrayList<>();
        for(String pkg:new String[]{GamePackages.PHIRA,GamePackages.PHIRA_PRO}){
            for(String base:new String[]{"/data/user/"+user+"/"+pkg,"/data/user_de/"+user+"/"+pkg,"/storage/emulated/"+user+"/Android/data/"+pkg,"/data/media/"+user+"/Android/data/"+pkg}){
                File parent=new File(base).getCanonicalFile();
                for(String relative:new String[]{"files/data/charts","files/charts","data/charts"}){
                    File candidate=new File(parent,relative).getCanonicalFile();
                    if(candidate.getPath().startsWith(parent.getPath()+File.separator))result.add(candidate);
                }
            }
        }
        return result;
    }
}
