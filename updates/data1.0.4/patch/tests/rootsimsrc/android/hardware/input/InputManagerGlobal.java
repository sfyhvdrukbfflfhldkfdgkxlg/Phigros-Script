package android.hardware.input;
import android.view.*;import java.util.*;
public final class InputManagerGlobal {
    static final InputManagerGlobal INSTANCE=new InputManagerGlobal();
    public static final Set<Integer> active=new HashSet<>();public static int downs,ups,moves,cancels;
    public static InputManagerGlobal getInstance(){return INSTANCE;}
    public boolean injectInputEvent(InputEvent input,int mode){
        if(mode!=0)throw new AssertionError("root input should use nonblocking queue acceptance");
        MotionEvent e=(MotionEvent)input;int kind=e.action&255,index=e.action>>8;
        Set<Integer> ids=new HashSet<>();
        if(e.count<=0||e.count>10||index>=e.count||e.source!=4098||e.down>e.time)throw new AssertionError("invalid MotionEvent metadata");
        for(int i=0;i<e.count;i++){
            if(!ids.add(e.properties[i].id)||e.properties[i].id<0||e.properties[i].id>9||e.properties[i].toolType!=1)throw new AssertionError("invalid pointer properties");
            if(!Float.isFinite(e.coords[i].x)||!Float.isFinite(e.coords[i].y))throw new AssertionError("invalid coordinates");
        }
        if(kind==0||kind==5){if(!active.add(e.properties[index].id))throw new AssertionError("duplicate DOWN");downs++;if(!ids.equals(active))throw new AssertionError("DOWN set");}
        else if(kind==1||kind==6){if(!ids.equals(active)||!active.remove(e.properties[index].id))throw new AssertionError("UP set");ups++;}
        else if(kind==2){if(!ids.equals(active))throw new AssertionError("MOVE set");moves++;}
        else if(kind==3){if(!ids.equals(active))throw new AssertionError("CANCEL set");active.clear();cancels++;}
        else throw new AssertionError("unknown action");return true;
    }
}
