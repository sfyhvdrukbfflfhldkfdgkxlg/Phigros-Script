package io.github.phiscript.input;

import io.github.phiscript.assets.NoiseWindowReader;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.TouchEvent;
import java.util.Map;
import java.util.WeakHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Active noise windows forbid optional contact reuse, independent of backend. */
public final class NoiseGate {
    private static final Map<Chart,long[][]> WINDOWS=new WeakHashMap<>();
    private NoiseGate(){}
    public static Chart attach(Chart chart,JSONObject root)throws JSONException {
        JSONArray source=NoiseWindowReader.read(root);long[][] windows=new long[source.length()][2];
        for(int i=0;i<windows.length;i++){JSONArray p=source.getJSONArray(i);windows[i][0]=(long)Math.floor(p.getDouble(0)*1000);windows[i][1]=(long)Math.ceil(p.getDouble(1)*1000);}
        synchronized(WINDOWS){if(windows.length==0)WINDOWS.remove(chart);else WINDOWS.put(chart,windows);}
        GesturePlan.invalidate(chart);return chart;
    }
    static boolean intersects(Chart chart,long from,long to){
        long[][] windows;synchronized(WINDOWS){windows=WINDOWS.get(chart);}if(windows==null)return false;
        long a=Math.min(from,to),b=Math.max(from,to);int lo=0,hi=windows.length;
        while(lo<hi){int m=(lo+hi)>>>1;if(windows[m][1]<=a)lo=m+1;else hi=m;}
        return lo<windows.length&&windows[lo][0]<=b;
    }
    static boolean isolate(Chart chart,TouchEvent event){return intersects(chart,event.getStartMs(),event.getEndMs());}
    static long leadStart(Chart chart,TouchEvent event,long proposed){return intersects(chart,proposed,event.getEndMs())?event.getStartMs():proposed;}
}
