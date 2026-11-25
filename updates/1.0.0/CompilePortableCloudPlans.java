package io.github.phiscript.input;

import io.github.phiscript.assets.SharedChartImport;
import io.github.phiscript.engine.BlockAreaSet;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.engine.Viewport;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.List;
import java.util.zip.*;

/** Host tool compiled against the same release classes as the Android playback reader. */
public final class CompilePortableCloudPlans {
    private static final Viewport VIEW = new Viewport(0, 0, 1920, 1080);
    private static final TouchBounds BOUNDS = new TouchBounds(0, 0, 1919, 1079);
    private static final GestureProfile PROFILE = GestureProfile.read(null);
    private static final String SIGNATURE = "gesture-noise-v2|true|true|0x1.18p5|0x1.8p2|0x1.f4p10|4|12|true|true";

    public static void main(String[] args) throws Exception {
        Path out = Files.createDirectories(Paths.get(args[1]));
        List<String> rows = Files.readAllLines(Paths.get(args[2]), StandardCharsets.UTF_8);
        try (ZipFile zip = new ZipFile(args[0])) {
            JSONArray entries = new JSONObject(new String(read(zip, "swordalt-catalog.json"), StandardCharsets.UTF_8)).getJSONArray("entries");
            for (String row : rows) {
                JSONObject entry = entries.getJSONObject(Integer.parseInt(row));
                String hash = entry.getString("sha1");
                Path target = out.resolve(hash + ".ptg.gz");
                byte[] raw = read(zip, "charts/" + entry.getString("path"));
                MessageDigest blob = MessageDigest.getInstance("SHA-1");
                blob.update(("blob " + raw.length + "\0").getBytes(StandardCharsets.US_ASCII));
                if (entry.getLong("bytes") != raw.length || !hash.equals(hex(blob.digest(raw)))) throw new IOException("Source checksum " + hash);
                long start = System.nanoTime();
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
                String fingerprint = hex(digest);
                TapMod.reload();
                if (!TapMod.supportsCloudRouting() || TapMod.noiseLookBackMs()!=4 || TapMod.noiseLookAheadMs()!=12) throw new IOException("Nonstandard host settings");
                if (Files.isRegularFile(target) && reusable(target, fingerprint)) continue;
                Chart chart = SharedChartImport.parseJson(new String(raw, StandardCharsets.UTF_8));
                if (BlockAreaSet.count(chart) != entry.getInt("noiseCount")) throw new IOException("Noise count " + hash);
                TapMod.preparationSalt(chart, ByteBuffer.wrap(digest).getLong());
                GesturePlan plan = ContactPlanner.build(chart, VIEW, BOUNDS, PROFILE);
                PreparedTrajectory trajectory = PreparedTrajectory.prepare(chart, plan, PROFILE, VIEW, BOUNDS, null);
                byte[] tracks = portable(PreparedTrajectory.encode(plan, trajectory, fingerprint));
                PlaybackArchive archive = PlaybackArchive.compile(chart, VIEW, tracks.length);
                Path temp = Files.createTempFile(out, hash, ".partial");
                try {
                    try (GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                        archive.write(gzip, fingerprint, tracks);
                    }
                    try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(temp))))) {
                        PlaybackArchive restored = PlaybackArchive.readHeader(input, archive.size(), fingerprint);
                        Chart standalone = restored.chart();
                        GesturePlan decoded = PreparedTrajectory.readCloud(standalone, PROFILE, VIEW, BOUNDS, input, restored.trackLength, fingerprint, VIEW, BOUNDS, null);
                        if (input.read() != -1 || decoded.preparedSampleCount() != trajectory.sampleCount() || standalone.getNotes().size() != chart.getNotes().size()
                                || BlockAreaSet.count(standalone) != BlockAreaSet.count(chart)) throw new IOException("Round trip " + hash);
                        GesturePlan.discardPrepared(standalone);
                    }
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temp);
                }
                System.out.println(row + " " + hash + " notes=" + chart.getNotes().size() + " noise=" + BlockAreaSet.count(chart)
                    + " samples=" + trajectory.sampleCount() + " ms=" + ((System.nanoTime() - start) / 1000000));
            }
        }
    }

    // prepare() leaves delay, drift and handset insets to installation.
    // The public PTG header distinguishes that portable dataset from a finalized device cache.
    private static byte[] portable(byte[] encoded) throws Exception {
        ByteArrayInputStream raw=new ByteArrayInputStream(encoded);
        DataInputStream in=new DataInputStream(raw);
        ByteArrayOutputStream body=new ByteArrayOutputStream(encoded.length);
        DataOutputStream out=new DataOutputStream(body);
        out.writeInt(in.readInt());out.writeInt(in.readInt());out.writeUTF(in.readUTF());
        if (!TapMod.preparationSignature().equals(in.readUTF()))throw new IOException("Unexpected generator settings");
        out.writeUTF(SIGNATURE);
        out.write(encoded,encoded.length-raw.available(),raw.available()-32);
        byte[] serialized=body.toByteArray();
        out.write(MessageDigest.getInstance("SHA-256").digest(serialized));
        return body.toByteArray();
    }

    private static boolean reusable(Path path, String fingerprint) {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
             DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
            file.seek(file.length() - 4);
            long size = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            PlaybackArchive archive = PlaybackArchive.readHeader(input, size, fingerprint);
            PreparedTrajectory.readCloud(archive.chart(), PROFILE, VIEW, BOUNDS, input, archive.trackLength, fingerprint, VIEW, BOUNDS, null);
            return input.read() == -1;
        } catch (IOException invalid) {
            return false;
        }
    }

    private static byte[] read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() < 1 || entry.getSize() > 128L * 1024 * 1024) throw new IOException("Invalid entry " + name);
        try (DataInputStream in = new DataInputStream(zip.getInputStream(entry))) {
            byte[] result = new byte[(int)entry.getSize()];
            in.readFully(result);
            return result;
        }
    }
    private static String hex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) text.append(alphabet[(b & 255) >> 4]).append(alphabet[b & 15]);
        return text.toString();
    }
}
