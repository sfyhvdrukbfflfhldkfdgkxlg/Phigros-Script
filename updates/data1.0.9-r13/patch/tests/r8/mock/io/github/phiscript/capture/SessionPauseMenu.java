package io.github.phiscript.capture;
import android.graphics.Bitmap;import android.graphics.RectF;import io.github.phiscript.SessionSettings;import io.github.phiscript.assets.SessionChartSource;import io.github.phiscript.vision.NormalizedPoint;
/** Camera evidence double; production glyph/pixel integration is tested separately in r9. */
public final class SessionPauseMenu {
 private boolean phigros;private NormalizedPoint configured;
 private static Object field(Object owner,String name)throws Exception{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 public static SessionPauseMenu forSession(Object owner,Object selection)throws Exception{SessionPauseMenu menu=new SessionPauseMenu();menu.phigros=((SessionChartSource)field(owner,"library")).isPhigros();menu.configured=((SessionSettings)field(owner,"settings")).getPhiraRestartPoint();return menu;}
 public static RectF phigrosPauseRegion(Object owner)throws Exception{return ((SessionChartSource)field(owner,"library")).isPhigros()?new RectF(((SessionSettings)field(owner,"settings")).getPhiraPauseRoi()):null;}
 public boolean visible(Bitmap bitmap){return bitmap.paused;}
 public NormalizedPoint retryPoint(){return phigros&&SessionTrace.ending.equals("detected-retry")?new NormalizedPoint(.56f,.47f):configured;}
}
