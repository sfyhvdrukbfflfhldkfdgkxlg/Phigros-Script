package io.github.phiscript.root;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.*;
import android.widget.TextView;
import android.view.animation.LinearInterpolator;
import android.view.animation.DecelerateInterpolator;
import io.github.phiscript.RuntimeState;

/** Lifecycle/animation-policy host model; no Android pixels or rendering claim. */
public final class UiMotionRegression {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void linear(View view,String path){
        ok(!view.motion.startedInterpolators.isEmpty(),path+" did not animate");
        for(android.animation.TimeInterpolator value:view.motion.startedInterpolators)ok(value instanceof LinearInterpolator,path+" retained a default/nonlinear interpolator");
    }
    public static void main(String[] args){
        Context c=new Context();ValueAnimator.enabled=true;RuntimeState.INSTANCE.getRunning().set(false);
        View v=new View(c);v.motion.setInterpolator(new DecelerateInterpolator());UiMotion.enter(v,2);ok(v.motion.starts==1&&v.getAlpha()==0,"entry must start once");linear(v,"entry");
        v.detach();ok(v.getAlpha()==1&&v.getTranslationY()==0&&v.getScaleX()==1,"detached entry not reset");
        ValueAnimator.enabled=false;View reduced=new View(c);UiMotion.enter(reduced,0);ok(reduced.motion.starts==0&&reduced.getAlpha()==1,"system animation scale zero must be immediate");
        ValueAnimator.enabled=true;RuntimeState.INSTANCE.getRunning().set(true);View playing=new View(c);UiMotion.enter(playing,0);ok(playing.motion.starts==0,"animation started during playback");RuntimeState.INSTANCE.getRunning().set(false);
        RuntimeState.INSTANCE.getActiveTouchStop().set(new Object());ok(!UiMotion.enabled(),"touch transport must also suppress animations");RuntimeState.INSTANCE.getActiveTouchStop().set(null);
        TextView state=new TextView(c);state.motion.setInterpolator(new DecelerateInterpolator());UiMotion.state(state,"Ready");linear(state,"state alpha");int count=state.motion.starts;UiMotion.state(state,new String("Ready"));ok(state.textWrites==1&&state.motion.starts==count,"unchanged ticker state was rebound");
        ViewGroup parent=new ViewGroup(c);View details=new View(c);details.width=0;details.setVisibility(View.GONE);parent.addView(details);
        UiMotion.toggle(details,true);ok(UiMotion.expanded(details)&&details.getVisibility()==View.VISIBLE,"first GONE expansion failed");ValueAnimator.finishAll();ok(details.getLayoutParams().height==-2&&details.getVisibility()==View.VISIBLE,"expanded content fixed to obsolete height");
        UiMotion.toggle(details,false);details.detach();ValueAnimator.finishAll();ok(!UiMotion.expanded(details)&&details.getVisibility()==View.GONE&&details.getLayoutParams().height==-2,"collapse after detach leaked animation/end callback");
        UiMotion.toggle(details,true);UiMotion.toggle(details,false);UiMotion.toggle(details,true);ValueAnimator.finishAll();ok(details.getVisibility()==View.VISIBLE&&UiMotion.expanded(details),"rapid toggle ended in stale state");
        for(ValueAnimator animator:ValueAnimator.all)ok(animator.interpolator instanceof LinearInterpolator,"expand/collapse/rapid-toggle animator was not explicitly linear");
        int beforeReduced=ValueAnimator.all.size();ValueAnimator.enabled=false;UiMotion.toggle(details,false);ok(details.getVisibility()==View.GONE&&ValueAnimator.all.size()==beforeReduced,"reduced-motion collapse was delayed or allocated an animation");
        View press=new View(c);UiMotion.bindPress(press);press.touch.onTouch(press,new MotionEvent(MotionEvent.ACTION_DOWN));ok(press.motion.starts==0,"press ignored reduced-motion setting");
        ValueAnimator.enabled=true;press.motion.setInterpolator(new DecelerateInterpolator());press.touch.onTouch(press,new MotionEvent(MotionEvent.ACTION_DOWN));linear(press,"press down");
        press.motion.setInterpolator(new DecelerateInterpolator());press.touch.onTouch(press,new MotionEvent(MotionEvent.ACTION_UP));linear(press,"press up");
        press.motion.setInterpolator(new DecelerateInterpolator());press.touch.onTouch(press,new MotionEvent(MotionEvent.ACTION_CANCEL));linear(press,"press cancel");ok(press.motion.targetX==1&&press.motion.targetY==1,"cancelled press remained scaled");
        ViewGroup tree=new ViewGroup(c);View child=new View(c);tree.addView(child);child.setAlpha(.1f);UiMotion.cancelTree(tree);ok(child.getAlpha()==1,"subtree animation not reset");
        android.app.AlertDialog dialog=new android.app.AlertDialog();UiMotion.show(dialog);ok(dialog.shown&&dialog.getWindow().getDecorView().motion.starts==1&&dialog.getWindow().animations==0,"dialog entry not attached to actual shown dialog or duplicate window animation enabled");linear(dialog.getWindow().getDecorView(),"dialog entry");
        System.out.println("PASS "+checks+" native UI motion lifecycle model checks: explicit linear interpolation for entry/press/state/expansion/dialog, system motion policy, playback exclusion, coalesced state, press cancel, rapid expansion and detach; no renderer claim.");
    }
}
