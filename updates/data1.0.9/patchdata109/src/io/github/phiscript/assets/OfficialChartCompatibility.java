package io.github.phiscript.assets;
import org.json.*;

/** Official PGR v1 differs from v3 in packed judge-line move coordinates only. */
public final class OfficialChartCompatibility {
    private OfficialChartCompatibility(){}
    public static void upgrade(JSONObject root)throws JSONException{
        if(root.has("phiscriptFormat")||root.optInt("formatVersion",3)!=1)return;
        JSONArray lines=root.getJSONArray("judgeLineList");
        for(int i=0;i<lines.length();i++){
            io.github.phiscript.root.PhiraImportControl.check();
            JSONArray moves=lines.getJSONObject(i).optJSONArray("judgeLineMoveEvents");if(moves==null)continue;
            for(int j=0;j<moves.length();j++){
                if((j&1023)==0)io.github.phiscript.root.PhiraImportControl.check();
                JSONObject event=moves.getJSONObject(j);
                if(event.optDouble("endTime",Double.NaN)<event.optDouble("startTime",Double.NaN))continue;
                double start=event.getDouble("start"),end=event.getDouble("end");
                if(!Double.isFinite(start)||!Double.isFinite(end))throw new JSONException("旧版判定线坐标无效");
                event.put("start",Math.floor(start/1000.0)/880.0);event.put("start2",(start%1000.0)/520.0);
                event.put("end",Math.floor(end/1000.0)/880.0);event.put("end2",(end%1000.0)/520.0);
            }
        }
        root.put("formatVersion",3);
    }
}
