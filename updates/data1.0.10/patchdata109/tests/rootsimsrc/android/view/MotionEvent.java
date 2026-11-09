package android.view;
public final class MotionEvent extends InputEvent {
    public static final class PointerProperties{public int id,toolType;}
    public static final class PointerCoords{public float x,y,pressure,size;}
    public long down,time;public int action,count,source;public PointerProperties[] properties;public PointerCoords[] coords;
    public static MotionEvent obtain(long down,long time,int action,int count,PointerProperties[] properties,
            PointerCoords[] coordinates,int meta,int buttons,float xp,float yp,int device,int edge,int source,int flags){
        MotionEvent e=new MotionEvent();e.down=down;e.time=time;e.action=action;e.count=count;e.source=source;e.properties=properties;e.coords=coordinates;return e;
    }
    public void recycle(){}
}
