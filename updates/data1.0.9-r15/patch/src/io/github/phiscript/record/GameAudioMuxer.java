package io.github.phiscript.record;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Copy AVC and AAC samples without re-encoding. The caller owns the pending destination. */
final class GameAudioMuxer {
    private GameAudioMuxer() {}

    private static MediaFormat select(MediaExtractor extractor,String prefix) throws IOException {
        for(int i=0;i<extractor.getTrackCount();i++){
            MediaFormat format=extractor.getTrackFormat(i);
            if(format.getString(MediaFormat.KEY_MIME).startsWith(prefix)){
                extractor.selectTrack(i);
                return format;
            }
        }
        throw new IOException("Missing "+prefix+" track");
    }

    static void merge(FileDescriptor video,String audioPath,FileDescriptor destination,long audioOffsetUs) throws IOException {
        MediaExtractor picture=new MediaExtractor();
        MediaExtractor sound=new MediaExtractor();
        MediaMuxer output=null;
        try {
            picture.setDataSource(video);
            sound.setDataSource(audioPath);
            MediaFormat videoFormat=select(picture,"video/");
            MediaFormat audioFormat=select(sound,"audio/mp4a-latm");
            long firstVideo=picture.getSampleTime(),firstAudio=sound.getSampleTime();
            long duration=videoFormat.containsKey(MediaFormat.KEY_DURATION)?videoFormat.getLong(MediaFormat.KEY_DURATION):0;
            if(firstVideo<0 || firstAudio<0 || audioOffsetUs<0 || duration<=audioOffsetUs)
                throw new IOException("The audio and video timelines do not overlap");
            output=new MediaMuxer(destination,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if(videoFormat.containsKey(MediaFormat.KEY_ROTATION))output.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION));
            int v=output.addTrack(videoFormat),a=output.addTrack(audioFormat);
            output.start();
            ByteBuffer data=ByteBuffer.allocateDirect(256*1024);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            boolean hasAudio=false;
            for(;;){
                long videoTime=picture.getSampleTime(),audioTime=sound.getSampleTime();
                long audioPts=audioTime-firstAudio+audioOffsetUs;
                boolean canWriteAudio=audioTime>=0 && audioPts<duration;
                if(videoTime<0 && !canWriteAudio)break;
                boolean useAudio=canWriteAudio && (videoTime<0 || audioPts<videoTime-firstVideo);
                MediaExtractor source=useAudio?sound:picture;
                long bytes=source.getSampleSize();
                if(bytes<=0 || bytes>16*1024*1024)throw new IOException("Invalid encoded media sample");
                if(bytes>data.capacity())data=ByteBuffer.allocateDirect((int)bytes);
                data.clear();
                int size=source.readSampleData(data,0);
                if(size!=bytes)throw new IOException("Incomplete encoded media sample");
                int flags=(source.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0;
                info.set(0,size,useAudio?audioPts:videoTime-firstVideo,flags);
                output.writeSampleData(useAudio?a:v,data,info);
                if(useAudio)hasAudio=true;
                source.advance();
            }
            if(!hasAudio)throw new IOException("No audio sample fits the recording");
            output.stop();
        } finally {
            try { if(output!=null)output.release(); }
            finally { try { sound.release(); } finally { picture.release(); } }
        }
    }
}
