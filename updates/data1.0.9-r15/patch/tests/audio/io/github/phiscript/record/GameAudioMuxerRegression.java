package io.github.phiscript.record;

import android.media.MediaExtractor;
import android.media.MediaExtractor.Sample;
import android.media.MediaExtractor.Track;
import android.media.MediaMuxer;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** Runs the application's actual remuxer. The Android boundary provides timestamped encoded packets. */
public final class GameAudioMuxerRegression {
    private static Track track(String mime, long duration) {
        Track t = new Track();
        t.format.mime = mime; t.format.durationUs = duration;
        t.format.fps = mime.startsWith("video/") ? 30 : 0;
        return t;
    }
    private static void add(Track t, long pts, int flags, byte[] data) {
        t.samples.add(new Sample(pts, flags, data));
        t.format.maxSize = Math.max(t.format.maxSize, data.length);
    }
    private static List<Track> merge(Track video, Track audio, long offsetUs) throws Exception {
        FileDescriptor in = new FileDescriptor(), out = new FileDescriptor();
        MediaExtractor.inputs.put(in, video);
        MediaExtractor.inputs.put("audio.mp4", audio);
        try {
            GameAudioMuxer.merge(in, "audio.mp4", out, offsetUs);
            return MediaMuxer.output.get(out);
        } finally {
            MediaExtractor.inputs.clear();
            MediaMuxer.output.clear();
            check(MediaMuxer.live == 0 && MediaExtractor.live == 0, "media resource leak");
        }
    }
    private static Track find(List<Track> tracks, String mime) {
        for (Track t : tracks) if (mime.equals(t.format.mime)) return t;
        throw new AssertionError("missing output " + mime);
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void verifyPackets(Track source, Track result, long firstUs) {
        check(!result.samples.isEmpty() && result.samples.size() <= source.samples.size(), "bad output count");
        long sourceStart = source.samples.get(0).pts;
        for (int i = 0; i < result.samples.size(); i++) {
            Sample input = source.samples.get(i), output = result.samples.get(i);
            check(output.pts == firstUs + input.pts - sourceStart, "changed sample timing");
            check(input.flags == output.flags && Arrays.equals(input.data, output.data), "changed encoded sample");
        }
    }
    private static void packetTests() throws Exception {
        Track v = track("video/avc", 100000), a = track("audio/mp4a-latm", 180000);
        v.format.rotation = 90;
        for (int i = 0; i < 3; i++) add(v, 700000 + i * 33333L, i == 0 ? 1 : 0, new byte[] {(byte)i, 0, 1});
        for (int i = 0; i < 9; i++) add(a, 100000 + i * 21333L, 1, new byte[] {(byte)i, 42});
        List<Track> result = merge(v, a, 20000);
        Track video = find(result, v.format.mime), audio = find(result, a.format.mime);
        check(video.samples.size() == 3, "dropped video");
        check(MediaMuxer.orientation == 90, "dropped source orientation");
        verifyPackets(v, video, 0); verifyPackets(a, audio, 20000);
        int count = audio.samples.size();
        check(count >= 3 && count < a.samples.size() && audio.samples.get(count-1).pts < 100000, "failed to trim audio to video");
        boolean threw = false;
        MediaMuxer.injectFailure = true;
        try { merge(v, a, 0); } catch (Exception expected) { threw = true; }
        finally { MediaMuxer.injectFailure = false; }
        check(threw, "silently accepted a failed merge");
        for (long offset : new long[] {-1,100000}) {
            threw = false;
            try { merge(v, a, offset); } catch (java.io.IOException expected) { threw = true; }
            check(threw, "published a recording with non-overlapping timelines");
        }
    }
    private static Track load(String path) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            String[] header = reader.readLine().split("\\t");
            Track t = track(header[0], Long.parseLong(header[1]));
            t.format.fps = Integer.parseInt(header[2]);
            String row;
            while ((row = reader.readLine()) != null) {
                String[] fields = row.split("\\t");
                add(t, Long.parseLong(fields[0]), Integer.parseInt(fields[1]), Base64.getDecoder().decode(fields[2]));
            }
            return t;
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            packetTests();
            System.out.println("PASS game audio MP4: track formats, start offset, relative packet timing, video integrity, trimmed tail, and resource cleanup on failure");
            return;
        }
        Track v = load(args[0]), a = load(args[1]);
        for (Track t : merge(v, a, Long.parseLong(args[2]))) {
            for (Sample packet : t.samples) {
                System.out.println(t.format.mime + "\t" + packet.pts + "\t" + packet.flags + "\t" + Base64.getEncoder().encodeToString(packet.data));
            }
        }
    }
}
