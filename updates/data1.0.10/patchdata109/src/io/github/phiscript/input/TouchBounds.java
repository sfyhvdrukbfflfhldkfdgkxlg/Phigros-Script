package io.github.phiscript.input;

/** Convex physical touch area. Every path sample, including continuation nudges, stays inside. */
public final class TouchBounds {
    public final float left,top,right,bottom;
    public TouchBounds(float left,float top,float right,float bottom){
        if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)
                ||right-left<2||bottom-top<2)throw new IllegalArgumentException("系统边缘避让后没有足够的触控区域，请检查玩法视口");
        this.left=left;this.top=top;this.right=right;this.bottom=bottom;
    }
    public float x(float value){return Math.max(left,Math.min(right,value));}
    public float y(float value){return Math.max(top,Math.min(bottom,value));}
    public TouchBounds intersect(float l,float t,float r,float b){return new TouchBounds(Math.max(left,l),Math.max(top,t),Math.min(right,r),Math.min(bottom,b));}
    /** Move toward the safe area's centre, keeping every sample away from system edges. */
    public float[] flick(float x,float y,float dx,float dy){
        x=x(x);y=y(y);if(!Float.isFinite(dx)||!Float.isFinite(dy))throw new IllegalArgumentException("划键方向无效");
        float length=(float)Math.hypot(dx,dy);if(length<1)length=Math.min(48,Math.min(right-left,bottom-top)*.1f);
        float vx=(left+right)*.5f-x,vy=(top+bottom)*.5f-y;
        float radius=(float)Math.hypot(vx,vy);
        if(radius<.001f){vx=0;vy=-1;radius=1;}
        return new float[]{x(x+vx/radius*length),y(y+vy/radius*length)};
    }
}
