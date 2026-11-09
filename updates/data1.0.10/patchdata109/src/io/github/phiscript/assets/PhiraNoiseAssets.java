package io.github.phiscript.assets;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Associates an external native noise list only with its selected chart. */
public final class PhiraNoiseAssets {
    private PhiraNoiseAssets(){}
    public static boolean isSidecarName(String path){
        String name=path.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
        return name.equals("blockarealist.json")||name.endsWith(".blockarealist.json");
    }
    public static PhiraImportData withSidecar(PhiraImportData data,Map<String,byte[]> files,String selected)throws JSONException{
        if(isSidecarName(selected))throw new IllegalArgumentException("噪域列表不能作为谱面文件");
        // The importer has already validated every archive path. Compare full
        // directory-qualified names, never a filename from another folder.
        selected=PhiraImportDecoder.INSTANCE.safeName$app_debug(selected,false);
        JSONObject normalized=ChartJsonReader.read(data.getNormalizedJson());
        byte[] raw=files.get(selected);
        if(raw!=null){String text=new String(raw,StandardCharsets.UTF_8).trim();if(text.startsWith("\ufeff"))text=text.substring(1).trim();
            if(text.startsWith("{")){JSONObject source=ChartJsonReader.read(text);if(source.has("blockAreaList")||source.has("BlockAreaList"))return data;}}
        if(normalized.has("blockAreaList")||normalized.has("BlockAreaList"))return data;
        int slash=selected.lastIndexOf('/');String dir=slash<0?"":selected.substring(0,slash+1),name=selected.substring(slash+1);
        int dot=name.lastIndexOf('.');String stem=dot<0?name:name.substring(0,dot);
        String path=unique(files,dir+stem+".blockAreaList.json");
        if(path==null)path=unique(files,dir+"blockAreaList.json");
        if(path==null)return data; // A sidecar named for a different chart is not ours.
        String text=new String(files.get(path),StandardCharsets.UTF_8).trim();if(text.startsWith("\ufeff"))text=text.substring(1).trim();
        JSONArray list;
        if(text.startsWith("["))list=new JSONArray(text);
        else{JSONObject document=ChartJsonReader.read(text);list=document.optJSONArray("blockAreaList");if(list==null)list=document.getJSONArray("BlockAreaList");}
        JSONArray external=NoiseWindowReader.read(new JSONObject().put("blockAreaList",list));
        normalized.put("phiscriptNoiseWindows",NoiseWindowReader.merge(NoiseWindowReader.read(normalized),external));
        return new PhiraImportData(data.getTitle(),data.getLevel(),normalized.toString(),data.getForceAspectRatio(),data.getAspectRatio());
    }
    private static String unique(Map<String,byte[]> files,String expected){
        String match=null;for(String path:files.keySet())if(path.equalsIgnoreCase(expected)){
            if(match!=null)throw new IllegalArgumentException("噪域列表路径大小写冲突："+expected);match=path;}
        return match;
    }
}
