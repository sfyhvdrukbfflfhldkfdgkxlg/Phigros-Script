package io.github.phiscript.record;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import io.github.phiscript.RuntimeState;
import io.github.phiscript.input.TouchService;
import static io.github.phiscript.root.UiDesign.words;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** The game UID's playback is captured with the screen session's projection. No microphone input. */
final class GameRecordingAudio {
    private static final int RATE=48000;
    private final Context context;
    private final long videoEpochNs;
    private final Thread worker;
    private volatile boolean stopping,abandoned;
    private File temporary;
    private boolean complete,audible;
    private long offsetUs;

    private GameRecordingAudio(Context context,MediaProjection projection,long epoch,int uid){
        this.context=context.getApplicationContext();
        videoEpochNs=epoch;
        worker=new Thread(()->{
            try {
                temporary=File.createTempFile("phi-game-audio-", ".m4a",this.context.getCacheDir());
                Api29.capture(this,projection,uid);
            } catch(Exception|OutOfMemoryError error){
                complete=false;
                log(words("游戏声音采集失败，将保留画面。","Game audio capture failed. Keeping the video.")+" "+error.getMessage());
            } finally {
                if(abandoned || !complete)discard();
            }
        },"phi-game-audio");
    }

    static GameRecordingAudio start(Context context,MediaProjection projection,long videoEpochNs){
        if(!RecordingPreferences.gameAudioEnabled(context))return null;
        if(Build.VERSION.SDK_INT<29 || projection==null ||
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            log(words("游戏声音不可用，本次仅录制画面。","Game audio is unavailable. This recording will contain video only."));
            return null;
        }
        try {
            TouchService touch=TouchService.Companion.getCurrent();
            String target=touch==null?null:touch.getTargetPackage();
            if(target==null||target.isEmpty())throw new IllegalStateException("No active game");
            int uid=context.getPackageManager().getApplicationInfo(target,0).uid;
            GameRecordingAudio session=new GameRecordingAudio(context,projection,videoEpochNs,uid);
            session.worker.start();
            return session;
        } catch(Exception|OutOfMemoryError error){
            log(words("无法录制当前游戏声音，本次仅保存画面。","Cannot capture this game's audio. Saving video only.")+" "+error.getMessage());
            return null;
        }
    }

    void stopCapture(){stopping=true;}

    /** Keep the completed video until its replacement, including the audio track, has finalized. */
    void finish(RecordingFiles video,boolean valid){
        stopCapture();
        RecordingFiles merged=null;
        try {
            worker.join(4500);
            if(worker.isAlive()){
                log(words("音轨结束超时，保留原始画面。","Audio finalization timed out. Keeping the original video."));
                return;
            }
            if(!valid||!complete)return;
            if(!audible){
                log(words("本次未采集到游戏声音，已改为保存无声视频。","No game audio was received. Saving a silent video."));
                return;
            }
            merged=new RecordingFiles(context);
            GameAudioMuxer.merge(video.descriptor.getFileDescriptor(),temporary.getPath(),
                    merged.descriptor.getFileDescriptor(),offsetUs);
            // R15 finish(true) treats a failed MediaStore update as a logging issue.
            // Confirm the new item is public before permitting removal of the source.
            merged.descriptor.getFileDescriptor().sync();
            ContentValues ready=new ContentValues();
            ready.put(MediaStore.Video.Media.IS_PENDING,0);
            if(context.getContentResolver().update(merged.uri,ready,null,null)!=1)
                throw new IOException("MediaStore did not publish the combined video");
            merged.finish(true);
            merged=null;
            valid=false;
            log(words("游戏音轨已写入视频。","Game audio was added to the video."));
        } catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
        } catch(Exception|OutOfMemoryError error){
            log(words("音轨合并失败，保留原始画面。","Could not combine the audio track. Keeping the original video.")+" "+error.getMessage());
        } finally {
            abandoned=true;
            try { if(merged!=null)merged.finish(false); }
            finally { try { if(video!=null)video.finish(valid); } finally { discard(); } }
        }
    }

    private void discard(){File file=temporary;if(file!=null)file.delete();}
    private static void log(String text){RuntimeState.INSTANCE.log(text);}

    /** Isolate playback-capture classes from Android 8 and 9's verifier. */
    private static final class Api29 {
        static void capture(GameRecordingAudio session,MediaProjection projection,int uid) throws Exception {
            AudioPlaybackCaptureConfiguration capture=new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUid(uid)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
            AudioRecord record=null;
            MediaCodec codec=null;
            MediaMuxer muxer=null;
            try {
                int channels=2;
                try { record=open(capture,AudioFormat.CHANNEL_IN_STEREO); }
                catch(IllegalArgumentException|UnsupportedOperationException|IOException stereoUnavailable){
                    channels=1;
                    record=open(capture,AudioFormat.CHANNEL_IN_MONO);
                }
                MediaFormat format=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,RATE,channels);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                format.setInteger(MediaFormat.KEY_BIT_RATE,channels==2?160000:96000);
                codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
                codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();
                muxer=new MediaMuxer(session.temporary.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if(session.stopping)return;
                long origin=System.nanoTime();
                record.startRecording();
                if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IOException("AudioRecord failed to start");
                session.offsetUs=Math.max(0,(origin-session.videoEpochNs)/1000);
                encode(session,record,codec,muxer,channels);
            } finally {
                if(record!=null){
                    try { if(record.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)record.stop(); }catch(RuntimeException ignored){}
                    try { record.release(); }catch(RuntimeException ignored){}
                }
                if(codec!=null){
                    try { codec.stop(); }catch(RuntimeException ignored){}
                    try { codec.release(); }catch(RuntimeException ignored){}
                }
                if(muxer!=null)muxer.release();
            }
        }

        static AudioRecord open(AudioPlaybackCaptureConfiguration capture,int mask) throws IOException {
            int min=AudioRecord.getMinBufferSize(RATE,mask,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IOException("Unsupported PCM format");
            AudioFormat format=new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(mask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
            AudioRecord record=new AudioRecord.Builder().setAudioFormat(format)
                    .setAudioPlaybackCaptureConfig(capture).setBufferSizeInBytes(Math.max(min*2,32768)).build();
            if(record.getState()==AudioRecord.STATE_INITIALIZED)return record;
            record.release();
            throw new IOException("Game audio input unavailable");
        }

        static void encode(GameRecordingAudio session,AudioRecord record,MediaCodec codec,MediaMuxer muxer,int channels) throws IOException {
            int frameBytes=channels*2,input=-1,track=-1,samples=0;
            long frames=0,deadline=0,firstPacketUs=0;
            boolean eos=false,calibrated=false;
            AudioTimestamp timestamp=new AudioTimestamp();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            for(;;){
                if(session.stopping && deadline==0)deadline=SystemClock.elapsedRealtime()+2500;
                if(deadline!=0 && SystemClock.elapsedRealtime()>deadline)throw new IOException("AAC finalization timed out");
                boolean progressed=false;
                if(!eos){
                    if(input<0)input=codec.dequeueInputBuffer(0);
                    if(input>=0){
                        long pts=frames*1000000L/RATE;
                        if(session.stopping){
                            // The encoder never retains an AudioRecord read across shutdown.
                            codec.queueInputBuffer(input,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            input=-1;eos=true;progressed=true;
                        } else {
                            ByteBuffer pcm=codec.getInputBuffer(input);
                            pcm.clear();
                            int len=Math.min(1024,pcm.capacity()/frameBytes)*frameBytes;
                            if(len==0)throw new IOException("AAC input buffer is too small");
                            int bytes=record.read(pcm,len,AudioRecord.READ_NON_BLOCKING);
                            if(bytes<0 && session.stopping)continue;
                            if(bytes<0 || bytes%frameBytes!=0)throw new IOException("PCM read failed: "+bytes);
                            if(bytes>0){
                                if(!session.audible){
                                    pcm.order(ByteOrder.nativeOrder());
                                    for(int i=0;i<bytes;i+=2)if(Math.abs((int)pcm.getShort(i))>1){session.audible=true;break;}
                                }
                                codec.queueInputBuffer(input,0,bytes,pts,0);
                                frames+=bytes/frameBytes;input=-1;progressed=true;
                                if(!calibrated && frames<RATE && record.getTimestamp(timestamp,AudioTimestamp.TIMEBASE_MONOTONIC)==AudioRecord.SUCCESS){
                                    long audioOrigin=timestamp.nanoTime-timestamp.framePosition*1000000000L/RATE;
                                    session.offsetUs=Math.max(0,(audioOrigin-session.videoEpochNs)/1000);
                                    calibrated=true;
                                }
                            }
                        }
                    }
                }
                int out=codec.dequeueOutputBuffer(info,0);
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    if(track>=0)throw new IOException("Unexpected AAC format change");
                    track=muxer.addTrack(codec.getOutputFormat());muxer.start();progressed=true;
                } else if(out>=0){
                    boolean done=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    try {
                        // Some AAC encoders return a negative-PTS priming packet. It
                        // precedes the first PCM sample and MediaMuxer requires PTS >= 0.
                        if(info.size>0 && info.presentationTimeUs>=0 && (info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                            if(track<0)throw new IOException("AAC format missing");
                            if(samples==0)firstPacketUs=info.presentationTimeUs;
                            info.presentationTimeUs-=firstPacketUs;
                            muxer.writeSampleData(track,codec.getOutputBuffer(out),info);samples++;
                        }
                    } finally { codec.releaseOutputBuffer(out,false); }
                    if(done){
                        if(track>=0 && samples>0){muxer.stop();session.offsetUs+=firstPacketUs;session.complete=true;}
                        return;
                    }
                    progressed=true;
                }
                if(!progressed)SystemClock.sleep(3);
            }
        }
    }
}
