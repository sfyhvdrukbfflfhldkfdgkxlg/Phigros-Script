package io.github.phiscript.record;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Surface;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.input.TouchService;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** All GL and codec work belongs to this thread, never to OCR or touch scheduling. */
final class ProjectionVideoBridge {
    private final HandlerThread thread=new HandlerThread("phi-video-surface");
    private final Handler handler;
    private EGLDisplay egl=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLConfig config;
    private EGLSurface pbuffer=EGL14.EGL_NO_SURFACE,ocr=EGL14.EGL_NO_SURFACE,video=EGL14.EGL_NO_SURFACE;
    private SurfaceTexture texture;
    private Surface input;
    private Surface sink;
    private VirtualDisplay display;
    private int width,height,density,program,textureId;
    private int p,t,m;
    private final float[] transform=new float[16];
    private FloatBuffer vertices;
    private volatile boolean closed,attempted;
    private boolean haveFrame;
    private BitmapVideoEncoder encoder;
    private long started,frame;
    private Runnable recordingFailure;
    ProjectionVideoBridge(int width,int height,Surface sink){
        this.width=width;this.height=height;this.sink=sink;thread.start();handler=new Handler(thread.getLooper());
        try{call(()->initialize());}catch(RuntimeException e){close();throw e;}
    }
    Surface inputSurface(){attempted=true;return input;}
    boolean displayWasAttempted(){return attempted;}
    void display(VirtualDisplay value,int dpi){display=value;density=dpi;}
    boolean owns(VirtualDisplay value){return !closed&&value==display;}
    void onRecordingFailure(Runnable listener){recordingFailure=listener;}
    private void initialize(){
        egl=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
        if(!EGL14.eglInitialize(egl,version,0,version,1))throw new IllegalStateException("EGL 初始化失败");
        int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,0x3142,1,EGL14.EGL_NONE};
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        if(!EGL14.eglChooseConfig(egl,attrs,0,configs,0,1,count,0)||count[0]<1)throw new IllegalStateException("无可用录屏 EGL 配置");
        config=configs[0];context=EGL14.eglCreateContext(egl,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        pbuffer=EGL14.eglCreatePbufferSurface(egl,config,new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);current(pbuffer);
        int vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; attribute vec2 t; uniform mat4 matrix; varying vec2 uv; void main(){gl_Position=vec4(p,0.,1.);uv=(matrix*vec4(t,0.,1.)).xy;}");
        int fs=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 uv; uniform samplerExternalOES image; void main(){gl_FragColor=texture2D(image,uv);}");
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
        int[] status=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);if(status[0]==0)throw new IllegalStateException("录屏着色器链接失败");
        p=GLES20.glGetAttribLocation(program,"p");t=GLES20.glGetAttribLocation(program,"t");m=GLES20.glGetUniformLocation(program,"matrix");
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);textureId=ids[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();vertices.put(new float[]{-1,-1,0,0,1,-1,1,0,-1,1,0,1,1,1,1,1}).position(0);
        texture=new SurfaceTexture(textureId);int[] size=RecordingPolicy.captureSize(width,height);texture.setDefaultBufferSize(size[0],size[1]);
        texture.setOnFrameAvailableListener(value->incoming(),handler);input=new Surface(texture);if(sink!=null)ocr=window(sink);
    }
    private static int shader(int type,String source){
        int value=GLES20.glCreateShader(type);GLES20.glShaderSource(value,source);GLES20.glCompileShader(value);int[] status=new int[1];GLES20.glGetShaderiv(value,GLES20.GL_COMPILE_STATUS,status,0);
        if(status[0]==0){GLES20.glDeleteShader(value);throw new IllegalStateException("录屏着色器编译失败");}return value;
    }
    private EGLSurface window(Surface target){
        EGLSurface value=EGL14.eglCreateWindowSurface(egl,config,target,new int[]{EGL14.EGL_NONE},0);
        if(value==EGL14.EGL_NO_SURFACE)throw new IllegalStateException("无法绑定画面输出 Surface");return value;
    }
    private void current(EGLSurface target){if(!EGL14.eglMakeCurrent(egl,target,target,context))throw new IllegalStateException("无法绑定录屏 EGL");}
    private void incoming(){
        if(closed)return;
        try{current(pbuffer);texture.updateTexImage();texture.getTransformMatrix(transform);haveFrame=true;if(ocr!=EGL14.EGL_NO_SURFACE)draw(ocr,width,height,false,-1);}
        catch(RuntimeException e){fallback(e);}
    }
    private void draw(EGLSurface target,int outWidth,int outHeight,boolean black,long presentationNs){
        current(target);GLES20.glViewport(0,0,outWidth,outHeight);GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if(!black&&haveFrame){
            float scale=Math.min((float)outWidth/width,(float)outHeight/height);int w=Math.max(1,Math.round(width*scale)),h=Math.max(1,Math.round(height*scale));
            GLES20.glViewport((outWidth-w)/2,(outHeight-h)/2,w,h);GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
            vertices.position(0);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(p);vertices.position(2);GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(t);
            GLES20.glUniformMatrix4fv(m,1,false,transform,0);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        }
        if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)throw new IllegalStateException("录屏绘制失败");
        if(presentationNs>=0)EGLExt.eglPresentationTimeANDROID(egl,target,presentationNs);
        if(!EGL14.eglSwapBuffers(egl,target))throw new IllegalStateException("录屏画面提交失败");
    }
    void startRecording(Context owner){handler.post(()->{
        if(closed||encoder!=null)return;
        try{beginRecording(owner);}
        catch(Exception e){fallback(new IllegalStateException("1080p60 录屏失败："+e.getMessage(),e));}
    });}
    void startRecordingSync(Context owner){call(()->{
        if(closed||encoder!=null)throw new IllegalStateException("录屏已停止或已经开始");
        try{beginRecording(owner);}catch(Exception e){endVideo(false);throw new IllegalStateException(e.getMessage(),e);}
    });}
    private void beginRecording(Context owner)throws Exception{
        int[] size=RecordingPolicy.size(width,height);encoder=new BitmapVideoEncoder(owner,size[0],size[1]);video=window(encoder.inputSurface());frame=0;started=SystemClock.uptimeMillis();handler.post(tick);RuntimeState.INSTANCE.log("演奏录屏已开始（1080p60，无声）");
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(closed||encoder==null)return;
        try{
            TouchService service=TouchService.Companion.getCurrent();boolean black=service==null||!service.getGameForeground();
            int[] size=RecordingPolicy.size(width,height);encoder.drain(false);draw(video,size[0],size[1],black,RecordingPolicy.frameTimeNs(frame));encoder.drain(false);frame++;
            long due=started+RecordingPolicy.frameDueMs(frame);
            if(SystemClock.uptimeMillis()-due>1000)throw new IllegalStateException("设备无法保持 1080p60 编码速度");
            handler.postAtTime(this,due);
        }catch(Exception e){endVideo(false);RuntimeState.INSTANCE.log("1080p60 录屏失败："+e.getMessage());if(recordingFailure!=null)recordingFailure.run();}
    }};
    void finishRecording(){handler.post(()->endVideo(true));}
    private void endVideo(boolean save){
        handler.removeCallbacks(tick);
        if(egl!=EGL14.EGL_NO_DISPLAY&&context!=EGL14.EGL_NO_CONTEXT)try{current(pbuffer);}catch(RuntimeException ignored){}
        if(video!=EGL14.EGL_NO_SURFACE){EGL14.eglDestroySurface(egl,video);video=EGL14.EGL_NO_SURFACE;}
        if(encoder!=null){encoder.close(save);encoder=null;}
    }
    void resize(int w,int h,int dpi){call(()->{
        if(closed){display.resize(w,h,dpi);return;}
        if((w>=h)!=(width>=height)&&encoder!=null){endVideo(true);RuntimeState.INSTANCE.log("画面方向已改变，已保存本段录屏");}
        width=w;height=h;density=dpi;haveFrame=false;int[] size=RecordingPolicy.captureSize(w,h);texture.setDefaultBufferSize(size[0],size[1]);display.resize(size[0],size[1],dpi);
    });}
    void sink(Surface value){call(()->{
        if(closed){display.setSurface(value);return;}
        current(pbuffer);if(ocr!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(egl,ocr);sink=value;ocr=window(value);
    });}
    private void fallback(RuntimeException failure){
        RuntimeState.INSTANCE.log("录屏分流失败，恢复原截图："+failure.getMessage());
        closed=true;
        try{current(pbuffer);if(ocr!=EGL14.EGL_NO_SURFACE){EGL14.eglDestroySurface(egl,ocr);ocr=EGL14.EGL_NO_SURFACE;}}catch(RuntimeException ignored){}
        try{if(display!=null){display.resize(width,height,density);display.setSurface(sink);}}catch(RuntimeException ignored){}
        release(false);thread.quitSafely();if(recordingFailure!=null)recordingFailure.run();
    }
    void close(){close(true);}
    void close(boolean save){if(closed)return;closed=true;handler.post(()->{release(save);thread.quitSafely();});}
    private void release(boolean save){
        endVideo(save);
        if(texture!=null){texture.setOnFrameAvailableListener(null);texture.release();texture=null;}if(input!=null){input.release();input=null;}
        if(egl!=EGL14.EGL_NO_DISPLAY){
            if(context!=EGL14.EGL_NO_CONTEXT)try{current(pbuffer);if(textureId!=0)GLES20.glDeleteTextures(1,new int[]{textureId},0);if(program!=0)GLES20.glDeleteProgram(program);}catch(RuntimeException ignored){}
            EGL14.eglMakeCurrent(egl,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(ocr!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(egl,ocr);if(pbuffer!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(egl,pbuffer);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(egl,context);EGL14.eglReleaseThread();EGL14.eglTerminate(egl);egl=EGL14.EGL_NO_DISPLAY;
        }
    }
    private void call(Runnable task){
        CountDownLatch done=new CountDownLatch(1);AtomicReference<RuntimeException> error=new AtomicReference<>();
        if(!handler.post(()->{try{task.run();}catch(RuntimeException e){error.set(e);}finally{done.countDown();}}))throw new IllegalStateException("录屏线程已结束");
        try{if(!done.await(4,TimeUnit.SECONDS))throw new IllegalStateException("录屏 Surface 准备超时");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("录屏 Surface 准备已取消",e);}
        if(error.get()!=null)throw error.get();
    }
}
