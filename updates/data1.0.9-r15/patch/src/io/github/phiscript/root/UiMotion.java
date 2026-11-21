package io.github.phiscript.root;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import io.github.phiscript.RuntimeState;

/** UI-only linear motion. No frame loop, playback callback or background timer. */
public final class UiMotion {
    private static final int LIFE=0x7f0f7b01,ANIMATOR=0x7f0f7b02,EXPANDED=0x7f0f7b03;
    private UiMotion(){}
    public static boolean enabled(){
        return ValueAnimator.areAnimatorsEnabled()&&!RuntimeState.INSTANCE.getRunning().get()
            &&RuntimeState.INSTANCE.getActiveTouchStop().get()==null;
    }
    private static void lifecycle(View view){
        if(Boolean.TRUE.equals(view.getTag(LIFE)))return;
        view.setTag(LIFE,true);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){cancel(v);}
        });
    }
    public static void cancel(View view){
        Object old=view.getTag(ANIMATOR);
        if(old instanceof Animator){((Animator)old).removeAllListeners();((Animator)old).cancel();view.setTag(ANIMATOR,null);}
        view.animate().cancel();view.setAlpha(1);view.setTranslationY(0);view.setScaleX(1);view.setScaleY(1);
        if(view.getTag(EXPANDED) instanceof Boolean){
            ViewGroup.LayoutParams p=view.getLayoutParams();if(p!=null){p.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(p);}
            view.setVisibility(expanded(view)?View.VISIBLE:View.GONE);
        }
    }
    public static void cancelTree(View view){
        cancel(view);if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)cancelTree(g.getChildAt(i));}
    }
    public static void enter(View view,int index){
        lifecycle(view);
        if(!enabled()){cancel(view);return;}
        view.setAlpha(0);view.setTranslationY(UiDesign.dp(view.getContext(),12));
        view.animate().alpha(1).translationY(0).setStartDelay(Math.min(5,Math.max(0,index))*35L)
            .setDuration(260).setInterpolator(new LinearInterpolator()).start();
    }
    /** Keep the inactive page attached so its controls and scroll position survive tab switches. */
    public static void switchPage(View previous,View next){
        if(previous!=null){cancel(previous);previous.setVisibility(View.GONE);}
        next.setVisibility(View.VISIBLE);enter(next,0);
    }
    public static void bindPress(View view){
        lifecycle(view);
        view.setOnTouchListener((v,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN&&v.isEnabled()){
                if(enabled())v.animate().scaleX(.985f).scaleY(.985f).setStartDelay(0).setDuration(90).setInterpolator(new LinearInterpolator()).start();
            }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(enabled())v.animate().scaleX(1).scaleY(1).setStartDelay(0).setDuration(150).setInterpolator(new LinearInterpolator()).start();
                else{v.setScaleX(1);v.setScaleY(1);}
            }
            return false;
        });
    }
    public static boolean expanded(View view){return Boolean.TRUE.equals(view.getTag(EXPANDED));}
    public static void toggle(View view,boolean open){
        lifecycle(view);Object prior=view.getTag(ANIMATOR);
        if(prior instanceof Animator){((Animator)prior).removeAllListeners();((Animator)prior).cancel();}
        view.setTag(EXPANDED,open);
        ViewGroup.LayoutParams p=view.getLayoutParams();if(p==null)return;
        int width=view.getWidth();
        if(width==0&&view.getParent() instanceof View){View parent=(View)view.getParent();width=parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight();}
        if(!enabled()||width<=0){p.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(p);view.setVisibility(open?View.VISIBLE:View.GONE);view.setAlpha(1);return;}
        int from=view.getVisibility()==View.VISIBLE?view.getHeight():0;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int to=open?view.getMeasuredHeight():0;view.setVisibility(View.VISIBLE);
        ValueAnimator animator=ValueAnimator.ofInt(from,to);view.setTag(ANIMATOR,animator);
        animator.setDuration(200);animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a->{p.height=(Integer)a.getAnimatedValue();view.setLayoutParams(p);});
        animator.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){
            view.setTag(ANIMATOR,null);p.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(p);view.setVisibility(open?View.VISIBLE:View.GONE);
        }});animator.start();
    }
    /** Coalesces ticker updates; motion only occurs on an actual idle UI change. */
    public static void state(TextView view,CharSequence value){
        if(TextUtils.equals(view.getText(),value))return;
        view.setText(value);
        if(enabled()&&view.isAttachedToWindow()){lifecycle(view);view.setAlpha(.65f);view.animate().alpha(1).setStartDelay(0).setDuration(160).setInterpolator(new LinearInterpolator()).start();}
    }
    public static AlertDialog show(AlertDialog dialog){
        if(dialog.getWindow()!=null)dialog.getWindow().setWindowAnimations(0);
        dialog.show();if(dialog.getWindow()!=null)enter(dialog.getWindow().getDecorView(),0);return dialog;
    }
}
