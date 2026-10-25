package io.github.phiscript.record;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** A bounded single-thread bitmap -> GLES -> AVC encoder. Shares OCR capture, not its token. */
final class BitmapVideoEncoder {
    private RecordingFiles output;
    private MediaCodec codec;
    private MediaMuxer muxer;
    private Surface surface;
    private EGLDisplay egl=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private int program,texture,track=-1,width,height,frames;
    private long first=-1,last=-1;
    private boolean muxing,ended;
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    private FloatBuffer vertices;
    BitmapVideoEncoder(Context c,int w,int h)throws Exception{
        try{
            int[] size=RecordingPolicy.size(w,h);width=size[0];height=size[1];
            MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE,4000000);format.setInteger(MediaFormat.KEY_FRAME_RATE,30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            codec=MediaCodec.createEncoderByType("video/avc");codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface=codec.createInputSurface();codec.start();
            output=new RecordingFiles(c);muxer=new MediaMuxer(output.descriptor.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            egl=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] versions=new int[2];
            if(!EGL14.eglInitialize(egl,versions,0,versions,1))throw new IllegalStateException("EGL 初始化失败");
            int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,0x3142,1,EGL14.EGL_NONE};
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
            if(!EGL14.eglChooseConfig(egl,attrs,0,configs,0,1,count,0)||count[0]<1)throw new IllegalStateException("无可用录屏 EGL 配置");
            context=EGL14.eglCreateContext(egl,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            window=EGL14.eglCreateWindowSurface(egl,configs[0],surface,new int[]{EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(egl,window,window,context))throw new IllegalStateException("无法绑定编码 Surface");
            int vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; attribute vec2 t; varying vec2 uv; void main(){gl_Position=vec4(p,0.,1.);uv=t;}");
            int fs=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; varying vec2 uv; uniform sampler2D image; void main(){gl_FragColor=texture2D(image,uv);}");
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);
            int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
            GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);if(ok[0]==0)throw new IllegalStateException("录屏着色器链接失败");
            int[] textures=new int[1];GLES20.glGenTextures(1,textures,0);texture=textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(new float[]{-1,-1,0,1,1,-1,1,1,-1,1,0,0,1,1,1,0}).position(0);
        }catch(Exception e){close(false);throw e;}
    }
    private static int shader(int type,String source){
        int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] status=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0);
        if(status[0]==0){GLES20.glDeleteShader(shader);throw new IllegalStateException("录屏着色器编译失败");}return shader;
    }
    void frame(Bitmap bitmap,long captured)throws Exception{
        if(first<0)first=captured;
        long timestamp=Math.max(last+1,(captured-first)*1000);last=timestamp;
        drain(false);GLES20.glViewport(0,0,width,height);GLES20.glClearColor(0,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        float scale=Math.min((float)width/bitmap.getWidth(),(float)height/bitmap.getHeight());
        int w=Math.max(1,(int)(bitmap.getWidth()*scale)),h=Math.max(1,(int)(bitmap.getHeight()*scale));GLES20.glViewport((width-w)/2,(height-h)/2,w,h);
        GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
        int p=GLES20.glGetAttribLocation(program,"p"),t=GLES20.glGetAttribLocation(program,"t");
        vertices.position(0);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(p);
        vertices.position(2);GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(t);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)throw new IllegalStateException("录屏绘制失败");
        EGLExt.eglPresentationTimeANDROID(egl,window,timestamp*1000);
        if(!EGL14.eglSwapBuffers(egl,window))throw new IllegalStateException("录屏提交失败");
        drain(false);
    }
    private void drain(boolean finish)throws Exception{
        long deadline=android.os.SystemClock.elapsedRealtime()+3000;
        while(true){
            int index=codec.dequeueOutputBuffer(info,finish?10000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER){if(!finish)return;if(android.os.SystemClock.elapsedRealtime()>deadline)throw new IllegalStateException("录屏封装超时");continue;}
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(muxing)throw new IllegalStateException("编码格式重复改变");track=muxer.addTrack(codec.getOutputFormat());muxer.start();muxing=true;continue;}
            if(index<0)continue;
            try{
                if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
                if(info.size>0){if(!muxing)throw new IllegalStateException("编码头未就绪");ByteBuffer buffer=codec.getOutputBuffer(index);buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer.writeSampleData(track,buffer,info);frames++;}
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)return;
            }finally{codec.releaseOutputBuffer(index,false);}
        }
    }
    void close(boolean save){
        if(ended)return;ended=true;boolean valid=false;
        if(save&&codec!=null)try{codec.signalEndOfInputStream();drain(true);valid=muxing&&frames>1;}catch(Exception ignored){}
        if(muxer!=null){try{if(muxing)muxer.stop();}catch(Exception e){valid=false;}try{muxer.release();}catch(Exception ignored){}muxer=null;}
        if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}codec=null;}
        if(egl!=EGL14.EGL_NO_DISPLAY){
            if(context!=EGL14.EGL_NO_CONTEXT){if(texture!=0)GLES20.glDeleteTextures(1,new int[]{texture},0);if(program!=0)GLES20.glDeleteProgram(program);}
            EGL14.eglMakeCurrent(egl,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(egl,window);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(egl,context);
            EGL14.eglReleaseThread();EGL14.eglTerminate(egl);
        }
        if(surface!=null)surface.release();if(output!=null)output.finish(valid);
    }
}
