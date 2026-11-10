package io.github.phiscript.input;

import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.TouchEvent;
import java.util.ArrayList;
import java.util.List;

/** Both Phira planners consume just the selected quiet interval on this worker. */
public final class NoiseWindows {
    private static final ThreadLocal<List<TouchEvent>> selected=new ThreadLocal<>();
    private NoiseWindows(){}
    public static List<TouchEvent> current(){
        List<TouchEvent> window=selected.get();
        return window==null?null:new ArrayList<>(window);
    }
    static GesturePlan quiet(Chart chart,GestureProfile profile,boolean bridge,List<TouchEvent> window){
        selected.set(window);
        try{return GesturePlan.individual(chart,profile,bridge,java.util.Collections.emptySet());}
        finally{selected.remove();}
    }
}
