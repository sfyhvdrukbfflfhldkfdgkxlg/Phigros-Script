package io.github.phiscript.root;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

/** One moving glass lens under the two native, focusable tab buttons. */
final class GlassTabs extends LinearLayout {
    private final Drawable lens;
    private int selected=-1;
    private float lensX;
    private ValueAnimator slide;

    GlassTabs(Context context){
        super(context);
        lens=new GlassSurface(context,18,true);
        setWillNotDraw(false);
    }

    void select(int index,boolean animate){
        if(index==selected)return;
        stopSlide();
        selected=index;
        View tab=getChildAt(index);
        if(!isLaidOut() || tab==null)return;
        lens.setBounds(0,0,tab.getWidth(),tab.getHeight());
        if(!animate || !UiMotion.enabled()){
            lensX=tab.getLeft();
            invalidate();
            return;
        }
        slide=ValueAnimator.ofFloat(lensX,tab.getLeft());
        slide.setInterpolator(new LinearInterpolator());
        slide.setDuration(180);
        slide.addUpdateListener(a->{lensX=(Float)a.getAnimatedValue();invalidate();});
        slide.start();
    }

    private void stopSlide(){
        if(slide!=null){slide.cancel();slide=null;}
    }
    void settle(){
        stopSlide();
        if(selected>=0 && isLaidOut()){lensX=getChildAt(selected).getLeft();invalidate();}
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);
        if(selected<0)return;
        View tab=getChildAt(selected);
        if(changed || lens.getBounds().width()!=tab.getWidth() || lens.getBounds().height()!=tab.getHeight()){
            stopSlide();
            lensX=tab.getLeft();
            lens.setBounds(0,0,tab.getWidth(),tab.getHeight());
        }
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(selected<0)return;
        int save=canvas.save();
        canvas.translate(lensX,getChildAt(selected).getTop());
        lens.draw(canvas);
        canvas.restoreToCount(save);
    }
    @Override protected void onDetachedFromWindow(){
        settle();
        super.onDetachedFromWindow();
    }
}
