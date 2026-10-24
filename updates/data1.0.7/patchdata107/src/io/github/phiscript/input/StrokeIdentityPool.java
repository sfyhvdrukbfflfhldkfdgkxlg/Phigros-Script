package io.github.phiscript.input;

import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.graphics.Path;
import java.lang.reflect.Field;
import java.util.*;

/** Recycle only fully released framework IDs; a new finger never takes a live ID. */
final class StrokeIdentityPool {
    private final Field field;final boolean supported;
    private Map<Object,Integer> active=new LinkedHashMap<>(),staged;private final List<Integer> ids=new ArrayList<>();
    private int nextFree;
    StrokeIdentityPool(){
        Field f=null;boolean works=false;
        try {f=StrokeDescription.class.getDeclaredField("mId");f.setAccessible(true);Path path=new Path();path.moveTo(1,1);StrokeDescription probe=new StrokeDescription(path,0,1,false);int id=f.getInt(probe);f.setInt(probe,id);works=f.getInt(probe)==id;}
        catch(Exception|LinkageError denied){f=null;}
        field=f;supported=works;
    }
    int available(){return GestureChannel.POINTER_LIMIT-active.size();}
    void begin(){staged=new LinkedHashMap<>(active);}
    void apply(Object key,StrokeDescription description,StrokeDescription previous){
        if(!supported)return;
        Integer id=staged.get(key);
        try {
            if(previous!=null){int continuingId=field.getInt(previous);if(!active.containsValue(continuingId))throw new IllegalStateException("无障碍续接触点 ID 丢失");id=continuingId;staged.put(key,id);}
            if(id==null){
                // Give a fresh press another free physical ID where possible.
                // Immediate UP/DOWN reuse can otherwise fall in the same game frame.
                if(ids.size()<GestureChannel.POINTER_LIMIT){id=field.getInt(description);ids.add(id);nextFree=ids.size()%GestureChannel.POINTER_LIMIT;}
                else for(int i=0;i<ids.size();i++){int index=(nextFree+i)%ids.size();Integer old=ids.get(index);if(!staged.containsValue(old)){id=old;nextFree=(index+1)%ids.size();break;}}
                if(id==null)throw new IllegalStateException("没有已释放的触点 ID");
                staged.put(key,id);
            }
            field.setInt(description,id);if(field.getInt(description)!=id)throw new IllegalAccessException("stroke identity write refused");
        }catch(ReflectiveOperationException e){throw new IllegalStateException("系统不支持保持无障碍触点编号，请切换 Root 触控",e);}
    }
    void accepted(Set<?> continuing){if(!supported)return;staged.keySet().retainAll(continuing);active=staged;staged=null;if(active.isEmpty())ids.clear();}
    void discard(){staged=null;}
    void clear(){active.clear();ids.clear();staged=null;nextFree=0;}
}
