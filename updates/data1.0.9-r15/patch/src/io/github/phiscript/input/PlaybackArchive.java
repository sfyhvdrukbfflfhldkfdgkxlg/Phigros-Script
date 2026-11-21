package io.github.phiscript.input;

import static io.github.phiscript.i18n.AdditionalEnglish.tr;

import io.github.phiscript.engine.BlockAreaSet;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Viewport;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** One atomic generation containing the playback timeline and its matching pixel tracks. */
final class PlaybackArchive {
    private static final int MAGIC = 0x50545041; // PTPA
    private static final int VERSION = 1;
    private static final int MAX_TIMELINE = 24 * 1024 * 1024;
    private static final int FINGERPRINT_LENGTH = 64;
    // magic, version, writeUTF(SHA-256 hex), timeline length, timeline checksum, track length
    private static final int HEADER_SIZE = 4 + 4 + 2 + FINGERPRINT_LENGTH + 4 + 32 + 4;

    final byte[] timeline;
    final int trackLength;

    private PlaybackArchive(byte[] timeline, int trackLength) {
        this.timeline = timeline;
        this.trackLength = trackLength;
    }

    static PlaybackArchive compile(Chart chart, Viewport viewport, int trackLength) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(new OutputStream() {
            private void checkSpace(int count) throws IOException {
                if (count > MAX_TIMELINE - buffer.size()) throw new IOException(tr("预计算文件超过容量"));
            }
            @Override public void write(int value) throws IOException {
                checkSpace(1);
                buffer.write(value);
            }
            @Override public void write(byte[] data, int offset, int count) throws IOException {
                checkSpace(count);
                buffer.write(data, offset, count);
            }
        });
        PlaybackTimeline.write(out, chart, viewport);
        BlockAreaSet.writeCompiled(out, chart);
        validateLengths(buffer.size(), trackLength);
        return new PlaybackArchive(buffer.toByteArray(), trackLength);
    }

    long size() {
        return (long)HEADER_SIZE + timeline.length + trackLength;
    }

    PlaybackArchive withTrackLength(int length) throws IOException {
        validateLengths(timeline.length, length);
        return new PlaybackArchive(timeline, length);
    }

    void write(OutputStream target, String fingerprint, byte[] tracks) throws IOException {
        DataOutputStream out = new DataOutputStream(target);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeUTF(fingerprint);
        out.writeInt(timeline.length);
        out.write(checksum(timeline));
        out.writeInt(trackLength);
        out.write(timeline);
        out.write(tracks);
    }

    static PlaybackArchive readHeader(DataInputStream in, long length, String fingerprint) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != VERSION || !in.readUTF().equals(fingerprint)
                || fingerprint.length() != FINGERPRINT_LENGTH) throw new IOException(tr("谱面预计算版本或指纹不匹配"));
        int timelineLength = in.readInt();
        byte[] expected = new byte[32];
        in.readFully(expected);
        int trackLength = in.readInt();
        validateLengths(timelineLength, trackLength);
        if (length != (long)HEADER_SIZE + timelineLength + trackLength) throw new IOException(tr("预计算文件大小无效"));
        byte[] timeline = new byte[timelineLength];
        in.readFully(timeline);
        if (!MessageDigest.isEqual(expected, checksum(timeline))) throw new IOException(tr("预计算文件校验失败"));
        return new PlaybackArchive(timeline, trackLength);
    }

    Chart chart() throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(timeline));
        Chart chart = PlaybackTimeline.read(in);
        BlockAreaSet.readCompiled(in, chart);
        if (in.read() != -1) throw new IOException(tr("预计算文件含多余数据"));
        return chart;
    }

    private static void validateLengths(int timelineLength, int trackLength) throws IOException {
        if (timelineLength <= 0 || timelineLength > MAX_TIMELINE || trackLength < 64
                || trackLength > PreparedTrajectory.MAX_BYTES) throw new IOException(tr("预计算文件大小无效"));
    }

    private static byte[] checksum(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }
}
