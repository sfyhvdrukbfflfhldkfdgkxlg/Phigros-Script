package io.github.phiscript.assets;

import io.github.phiscript.engine.Chart;
import io.github.phiscript.input.ImportCache;
import org.json.JSONObject;
import org.json.JSONException;

public final class SharedChartImport {
    private SharedChartImport() {}
    public static Chart parseJson(String text){
        Chart chart = parseForImport(text);
        io.github.phiscript.input.PreparedChartStore.bind(chart,text);
        return chart;
    }
    // The importers bind the exact source bytes after validation. Avoid encoding and
    // hashing a second, chart-sized UTF-8 buffer just to replace that binding.
    static Chart parseForImport(String text){
        try { return PhiraChartParser.INSTANCE.parse(text,0.0); }
        finally{ImportCache.clear();ImportCache.takeChart();}
    }
    public static void preserveAreas(JSONObject root,JSONObject normalized)throws JSONException{
        NoiseImportBridge.convertNext(root);
        if(root.has("blockAreaList"))normalized.put("blockAreaList",root.get("blockAreaList"));
        else if(root.has("BlockAreaList"))normalized.put("blockAreaList",root.get("BlockAreaList"));
        if(root.has("phiscriptNoiseSingleInvert"))normalized.put("phiscriptNoiseSingleInvert",root.getBoolean("phiscriptNoiseSingleInvert"));
    }
}
