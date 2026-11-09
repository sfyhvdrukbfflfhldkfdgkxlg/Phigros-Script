package io.github.phiscript.capture;

import android.graphics.Bitmap;
import android.graphics.RectF;
import io.github.phiscript.SessionSettings;
import io.github.phiscript.engine.Viewport;
import io.github.phiscript.vision.GameplayDetector;
import io.github.phiscript.vision.NormalizedPoint;
import io.github.phiscript.vision.PhiraPauseDetector;

/** Uses the actual Phira menu glyph detector, settings and gameplay pause detector. */
public final class SharedPhiraControls {
    private final Viewport viewport;
    private final PhiraPauseDetector menu;
    private final GameplayDetector game;
    private final PhigrosHudProbe hud;
    private final NormalizedPoint retry;
    public SharedPhiraControls(SessionSettings settings,int width,int height){
        if(width<=height||height<=0)throw new IllegalArgumentException("请使用横屏游戏画面");
        RectF configured=settings.getViewport();
        viewport=settings.getPhiraAutomaticViewport()?PhiraViewport.INSTANCE.bounds(width,height,16.0/9.0,false):
            new Viewport(configured.left*width,configured.top*height,configured.width()*width,configured.height()*height);
        RectF view=new RectF(viewport.getLeft()/width,viewport.getTop()/height,
            (viewport.getLeft()+viewport.getWidth())/width,(viewport.getTop()+viewport.getHeight())/height);
        RectF roi=new RectF(settings.getPhiraPauseRoi());
        if(settings.getPhiraAutomaticViewport()&&roi.left==0&&roi.top==0&&roi.right==.18f&&roi.bottom==.24f)
            roi=new RectF(view.left,view.top,view.left+view.width()*.18f,view.top+view.height()*.24f);
        retry=settings.getPhiraRestartPoint();
        menu=new PhiraPauseDetector(view,retry);game=new GameplayDetector(roi);hud=new PhigrosHudProbe(roi);
    }
    public boolean menu(Bitmap bitmap){return menu.visible(bitmap);}
    public NormalizedPoint pause(Bitmap bitmap,long capturedAt){
        GameplayDetector.Hit hit=game.observe(bitmap,capturedAt);
        return hit==null?null:new NormalizedPoint(hit.getPauseX(),hit.getPauseY());
    }
    public void resetGame(){game.reset();}
    public boolean hud(Bitmap bitmap){return hud.observe(bitmap)!=null;}
    public NormalizedPoint retry(){return retry;}
    public Viewport viewport(){return viewport;}
}
