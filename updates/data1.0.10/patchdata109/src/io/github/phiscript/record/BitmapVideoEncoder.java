package io.github.phiscript.record;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.view.Surface;
import java.nio.ByteBuffer;

/** Surface AVC encoder. The historic name is retained for existing cross-DEX audits. */
final class BitmapVideoEncoder {
    private RecordingFiles output;
    private MediaCodec codec;
    private MediaMuxer muxer;
    private Surface surface;
    private int track=-1,frames;
    private boolean muxing,ended;
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    BitmapVideoEncoder(Context c,int width,int height)throws Exception{
        try{
            String encoder=encoder(width,height);
            if(encoder==null)throw new IllegalStateException("设备不支持 1080p60 AVC 录屏，未降为低帧率");
            MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE,RecordingPolicy.BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE,RecordingPolicy.FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            if(Build.VERSION.SDK_INT>=29)format.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);
            codec=MediaCodec.createByCodecName(encoder);codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface=codec.createInputSurface();codec.start();
            output=new RecordingFiles(c);muxer=new MediaMuxer(output.descriptor.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }catch(Exception e){close(false);throw e;}
    }
    private static String encoder(int width,int height){
        MediaCodecInfo[] codecs=new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
        for(int pass=0;pass<2;pass++)for(MediaCodecInfo candidate:codecs){
            if(!candidate.isEncoder())continue;
            if(Build.VERSION.SDK_INT>=29&&pass==0&&!candidate.isHardwareAccelerated())continue;
            if(Build.VERSION.SDK_INT<29&&pass==0&&(candidate.getName().startsWith("OMX.google.")||candidate.getName().startsWith("c2.android.")))continue;
            try{
                MediaCodecInfo.CodecCapabilities caps=candidate.getCapabilitiesForType("video/avc");boolean surface=false;
                for(int color:caps.colorFormats)if(color==MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)surface=true;
                MediaCodecInfo.VideoCapabilities video=caps.getVideoCapabilities();
                if(surface&&video!=null&&video.areSizeAndRateSupported(width,height,RecordingPolicy.FPS)&&video.getBitrateRange().contains(RecordingPolicy.BIT_RATE))return candidate.getName();
            }catch(IllegalArgumentException ignored){}
        }
        return null;
    }
    Surface inputSurface(){return surface;}
    void drain(boolean finish)throws Exception{
        long deadline=android.os.SystemClock.elapsedRealtime()+3000;
        while(true){
            int index=codec.dequeueOutputBuffer(info,finish?10000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER){if(!finish)return;if(android.os.SystemClock.elapsedRealtime()>deadline)throw new IllegalStateException("录屏封装超时");continue;}
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(muxing)throw new IllegalStateException("编码格式重复改变");track=muxer.addTrack(codec.getOutputFormat());muxer.start();muxing=true;continue;}
            if(index<0)continue;
            try{
                if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
                if(info.size>0){if(!muxing)throw new IllegalStateException("编码头未就绪");ByteBuffer buffer=codec.getOutputBuffer(index);if(buffer==null)throw new IllegalStateException("编码缓冲区不可用");buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer.writeSampleData(track,buffer,info);frames++;}
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)return;
            }finally{codec.releaseOutputBuffer(index,false);}
        }
    }
    void close(boolean save){
        if(ended)return;ended=true;boolean valid=false;
        if(save&&codec!=null)try{codec.signalEndOfInputStream();drain(true);valid=muxing&&frames>1;}catch(Exception e){io.github.phiscript.RuntimeState.INSTANCE.log("录屏未完成封装："+e.getMessage());}
        if(muxer!=null){try{if(muxing)muxer.stop();}catch(Exception e){valid=false;}try{muxer.release();}catch(Exception ignored){}muxer=null;}
        if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}codec=null;}
        if(surface!=null){surface.release();surface=null;}
        if(output!=null){output.finish(valid);output=null;}
    }
}
