package io.github.phiscript.assets;

import io.github.phiscript.engine.Chart;
import io.github.phiscript.input.ImportCache;
import org.json.JSONObject;
import org.json.JSONException;

public final class SharedChartImport {
    private SharedChartImport() {}
    public static Chart parseJson(String text){
        try{return PhiraChartParser.INSTANCE.parse(text,0.0);}
        finally{ImportCache.clear();ImportCache.takeChart();}
    }
    public static void preserveAreas(JSONObject root,JSONObject normalized)throws JSONException{
        if(root.has("blockAreaList"))normalized.put("blockAreaList",root.get("blockAreaList"));
    }
}
