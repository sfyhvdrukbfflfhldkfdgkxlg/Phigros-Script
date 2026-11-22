package io.github.phiscript.record;

import android.content.Context;
import android.content.ContentResolver;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaExtractor.Sample;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import java.io.FileDescriptor;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import android.media.MediaExtractor;
import android.media.MediaExtractor.Track;

/** Isolate the production PCM-to-codec loop from the OS capture and hardware encoder. */
public class GameAudioInputRegression {
    private static Object field(GameRecordingAudio session, String name) throws Exception {
        Field field = GameRecordingAudio.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(session);
    }
    private static void run(int channels, boolean silent, int publishResult) throws Exception {
        long epoch = 9000000000L;
        Constructor<GameRecordingAudio> constructor = GameRecordingAudio.class.getDeclaredConstructor(
                Context.class, MediaProjection.class, long.class, int.class);
        constructor.setAccessible(true);
        GameRecordingAudio session = constructor.newInstance(new Context(), null, epoch, 10001);
        AudioRecord capture = new AudioRecord(); capture.channels = channels;
        capture.silent = silent; capture.firstFrameNs = epoch + 200000000;
        capture.stop = session::stopCapture;
        FileDescriptor output = new FileDescriptor();
        MediaMuxer muxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            Class<?> api = Class.forName("io.github.phiscript.record.GameRecordingAudio$Api29");
            Method encode = api.getDeclaredMethod("encode", GameRecordingAudio.class, AudioRecord.class,
                    MediaCodec.class, MediaMuxer.class, int.class);
            encode.setAccessible(true);
            encode.invoke(null, session, capture, new MediaCodec(), muxer, channels);
            List<Sample> samples = MediaMuxer.output.get(output).get(0).samples;
            long[] frames = {128, 1024, 64};
            long submitted = 0;
            if (samples.size() != frames.length) throw new AssertionError("unexpected sample count");
            for (int i = 0; i < frames.length; i++) {
                Sample sample = samples.get(i);
                if (sample.pts != submitted * 1000000 / 48000 || sample.data.length != frames[i] * 2 * channels)
                    throw new AssertionError("short read or channel count disrupted frame clock");
                submitted += frames[i];
            }
            if (!(Boolean)field(session, "complete") || (Boolean)field(session, "audible") == silent
                    || (Long)field(session, "offsetUs") != 200000L) throw new AssertionError("invalid track state");
        } finally {
            muxer.release(); MediaMuxer.output.clear();
        }
        Field temp = GameRecordingAudio.class.getDeclaredField("temporary");
        temp.setAccessible(true);
        File audio = File.createTempFile("game-audio-input", ".m4a");
        temp.set(session,audio);
        RecordingFiles original = new RecordingFiles(new Context());
        Track picture = new Track(); picture.format.mime = "video/avc"; picture.format.durationUs = 1000000;
        Track sound = new Track(); sound.format.mime = "audio/mp4a-latm";
        picture.samples.add(new Sample(0,1,new byte[]{1}));
        sound.samples.add(new Sample(0,1,new byte[]{2}));
        MediaExtractor.inputs.put(original.descriptor.getFileDescriptor(),picture);
        MediaExtractor.inputs.put(audio.getPath(),sound);
        ContentResolver.result = publishResult;
        ContentResolver.calls = 0;
        try {
            session.finish(original,true);
            boolean replaced = !silent && publishResult == 1;
            if (!Boolean.valueOf(!replaced).equals(original.valid) || audio.exists())
                throw new AssertionError("lost original video or leaked scratch audio");
            if (ContentResolver.calls != (silent ? 0 : 1) || RecordingFiles.created.size() != (silent ? 1 : 2))
                throw new AssertionError("unexpected publication");
            if (!silent && !Boolean.valueOf(replaced).equals(RecordingFiles.created.get(1).valid))
                throw new AssertionError("unconfirmed replacement survived");
        } finally {
            MediaExtractor.inputs.clear(); MediaMuxer.output.clear(); RecordingFiles.created.clear();
        }
    }
    public static void main(String[] args) throws Exception {
        run(2, false, 1); run(1, false, 0); run(2, false, -1); run(2, true, 1);
        System.out.println("PASS game capture PCM: mono/stereo, short reads, hardware clock offset, EOS, silence and original video recovery on MediaStore publication failure");
    }
}
