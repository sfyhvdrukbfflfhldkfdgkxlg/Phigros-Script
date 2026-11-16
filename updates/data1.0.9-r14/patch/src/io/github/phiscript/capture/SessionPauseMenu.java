package io.github.phiscript.capture;

import android.graphics.Bitmap;
import android.graphics.RectF;
import io.github.phiscript.SessionSettings;
import io.github.phiscript.assets.SessionChartSource;
import io.github.phiscript.vision.NormalizedPoint;
import io.github.phiscript.vision.PhiraPauseDetector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Game-specific menu appearance and screen coordinates; session timing remains in Phira. */
public final class SessionPauseMenu {
    private final PhiraPauseDetector phira;
    private final PhigrosMenuGlyphDetector phigros;
    private final NormalizedPoint configured;
    private NormalizedPoint measured,previous;
    private SessionPauseMenu(PhiraPauseDetector phira,PhigrosMenuGlyphDetector phigros,NormalizedPoint configured){this.phira=phira;this.phigros=phigros;this.configured=configured;}
    private static Object field(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    public static SessionPauseMenu forSession(Object owner,Object selection)throws Exception{
        SessionChartSource source=(SessionChartSource)field(owner,"library");
        if(source.isPhigros())return new SessionPauseMenu(null,new PhigrosMenuGlyphDetector(),null);
        Method original=owner.getClass().getDeclaredMethod("phiraPauseMenuFor",selection.getClass());original.setAccessible(true);
        try{return new SessionPauseMenu((PhiraPauseDetector)original.invoke(owner,selection),null,((SessionSettings)field(owner,"settings")).getPhiraRestartPoint());}
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw (Exception)cause;if(cause instanceof Error)throw (Error)cause;throw error;}
    }
    /** Phigros HUD is positioned in physical screen space, including on wide screens. */
    public static RectF phigrosPauseRegion(Object owner)throws Exception{
        if(!((SessionChartSource)field(owner,"library")).isPhigros())return null;
        return new RectF(((SessionSettings)field(owner,"settings")).getPhiraPauseRoi());
    }
    public synchronized boolean visible(Bitmap bitmap){
        if(phira!=null)return phira.visible(bitmap);
        NormalizedPoint point=phigros.locate(bitmap);
        if(point==null){measured=null;previous=null;return false;}
        boolean steady=previous==null||Math.hypot(previous.getX()-point.getX(),previous.getY()-point.getY())<=.018;
        previous=point;measured=steady?point:null;return steady;
    }
    public synchronized NormalizedPoint retryPoint(){
        if(phira!=null)return configured;
        if(measured==null)throw new IllegalStateException("Phigros 三键暂停菜单尚未确认，未点击重试");
        return measured;
    }
}
