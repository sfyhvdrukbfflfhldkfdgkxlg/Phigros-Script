package io.github.phiscript.capture;

/** Physical-pixel bounds keep the status window away from the standard pause/menu controls. */
public final class AutoOverlayBounds {
    public final int width,height,margin,padding,actionWidth,textSize;
    public AutoOverlayBounds(int screenWidth,int screenHeight,float density){
        if(screenWidth<=0||screenHeight<=0||!Float.isFinite(density)||density<=0)throw new IllegalArgumentException("屏幕参数无效");
        width=Math.max(100,Math.min(Math.round(300*density),Math.round(screenWidth*.32f)));
        height=Math.max(48,Math.min(Math.round(64*density),Math.round(screenHeight*.14f)));
        margin=Math.max(4,Math.min(Math.round(8*density),Math.round(screenHeight*.02f)));
        padding=Math.max(2,Math.min(Math.round(4*density),Math.round(screenHeight*.006f)));
        actionWidth=Math.max(40,Math.min(Math.round(56*density),Math.round(width*.30f)));
        textSize=Math.max(10,Math.min(Math.round(12*density),Math.round(screenHeight*.027f)));
    }
}
