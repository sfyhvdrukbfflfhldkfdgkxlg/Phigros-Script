package io.github.phiscript.assets;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.view.WindowManager;
import android.view.WindowMetrics;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.BlockAreaSet;
import io.github.phiscript.engine.Chart;
import io.github.phiscript.input.GesturePlan;
import io.github.phiscript.input.PreparedChartStore;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.json.*;

/** Offline installation and playback through the actual APK. Run in its own JVM to intercept every HTTP request. */
public final class OfflineCloudPackRegression {
    private static final String INDEX = "phigros-cloud-plans-v1.json";
    private enum Fault { TRUNCATE, CHANGE_BYTE, CANCEL }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static InputStream faulty(Path file, Fault fault, AtomicBoolean cancel) throws IOException {
        long size = Files.size(file);
        return new FilterInputStream(Files.newInputStream(file)) {
            private long pos;
            @Override public int read(byte[] bytes, int off, int len) throws IOException {
                if (fault == Fault.TRUNCATE) {
                    long available = size - 5 - pos;
                    if (available <= 0) return -1;
                    len = (int)Math.min(len, available);
                }
                int count = super.read(bytes, off, len);
                if (count > 0) {
                    if (fault == Fault.CHANGE_BYTE && pos + count == size) bytes[off + count - 1] ^= 1;
                    pos += count;
                    if (fault == Fault.CANCEL && pos >= 1024 * 1024) cancel.set(true);
                }
                return count;
            }
            @Override public int read() throws IOException {
                byte[] value = new byte[1];
                return read(value, 0, 1) < 0 ? -1 : value[0] & 255;
            }
        };
    }

    private static String sha(MessageDigest digest) {
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }

    private static void checkStore(Path file, JSONArray shards) throws Exception {
        byte[] block = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(file)) {
            for (int i = 0; i < shards.length(); i++) {
                JSONObject shard = shards.getJSONObject(i);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long remaining = shard.getLong("bytes");
                while (remaining > 0) {
                    int count = input.read(block, 0, (int)Math.min(remaining, block.length));
                    check(count > 0, "Truncated installed shard " + i);
                    digest.update(block, 0, count);
                    remaining -= count;
                }
                check(sha(digest).equals(shard.getString("sha256")), "Installed shard differs: " + i);
            }
            check(input.read() == -1, "Unexpected bytes after the indexed shards");
        }
    }

    private static void checkRandomReads(Context ctx, String hash, JSONObject index) throws Exception {
        JSONObject entries = index.getJSONObject("entries");
        JSONArray shards = index.getJSONArray("shards");
        JSONObject[] selected = new JSONObject[shards.length()];
        for (Iterator<String> it = entries.keys(); it.hasNext();) {
            JSONObject plan = entries.getJSONObject(it.next());
            int number = plan.getInt("shard");
            if (selected[number] == null || selected[number].getLong("offset") < plan.getLong("offset"))
                selected[number] = plan;
        }
        for (int i = selected.length - 1; i >= 0; i--) {
            JSONObject plan = selected[i];
            check(plan != null, "No indexed chart in shard " + i);
            byte[] data = OfflineCloudPlans.read(ctx, hash, shards, i, plan.getLong("offset"),
                plan.getInt("length"), plan.getString("sha256"), new AtomicBoolean(false));
            check(data != null && data.length == plan.getInt("length")
                && PreparedChartStore.digest(data).equals(plan.getString("sha256")), "Wrong chart range in shard " + i);
        }
    }

    private static void loadSong(Context ctx) throws Exception {
        JSONObject catalog;
        try (InputStream input = ctx.getAssets().open(SwordaltSource.ASSET)) {
            catalog = new JSONObject(new String(ExternalCharts.read(input, 4 * 1024 * 1024), StandardCharsets.UTF_8));
        }
        catalog.put("phiscriptCloudPlans", 1);
        ChartLibrary library = ExternalCharts.fromSwordaltCatalog(ctx, catalog);
        String id = ExternalCharts.hash("DesultorySignals.technoplanet.0/AT.json");
        for (ChartLocation row : library.getLocations()) {
            if ("AT".equals(row.getDifficulty()) && (id.equals(row.getAssetKey()) || id.equals(row.getBundleEntry()))) {
                Chart chart = ExternalCharts.loadSelected(library, row.getSongId(), row.getDifficulty(), new AtomicBoolean(false));
                check(chart.getNotes().size() == 2026 && BlockAreaSet.count(chart) == 160, "Incomplete Desultory Signals AT");
                return;
            }
        }
        throw new AssertionError("Desultory Signals AT missing from the catalog");
    }

    private static long preparedFiles(Path root) throws IOException {
        try (Stream<Path> all = Files.walk(root)) {
            return all.filter(path -> path.toString().endsWith(".ptg")).count();
        }
    }

    public static void main(String[] args) throws Exception {
        Path pack = Paths.get(args[1]);
        check(Files.size(pack) == 433403067L, "Expected the published touch ZIP");
        URL.setURLStreamHandlerFactory(protocol -> {
            if (!"https".equals(protocol) && !"http".equals(protocol)) return null;
            return new URLStreamHandler() {
                @Override protected URLConnection openConnection(URL url) { throw new AssertionError("Unexpected network request: " + url); }
            };
        });
        Path root = Files.createTempDirectory("offline-cloud-pack-");
        Context.root = root.toFile();
        AppSettings.values.clear();
        AppSettings.values.put("phiraAutomaticViewport", "true");
        AppSettings.smart = AppSettings.catchDrags = AppSettings.flicks = AppSettings.combos = true;
        AppSettings.edges = AppSettings.inward = true;
        try (ZipFile apk = new ZipFile(args[0])) {
            final AssetManager assets = new AssetManager() {
                @Override public InputStream open(String name) throws IOException {
                    return apk.getInputStream(apk.getEntry("assets/" + name));
                }
            };
            Context ctx = new Context() {
                @Override public AssetManager getAssets() { return assets; }
                @Override public Object getSystemService(String name) {
                    if (!WINDOW_SERVICE.equals(name)) return super.getSystemService(name);
                    return new WindowManager() {
                        @Override public WindowMetrics getMaximumWindowMetrics() {
                            return new WindowMetrics() {
                                @Override public Rect getBounds() { return new Rect() {
                                    @Override public int width() { return 1920; }
                                    @Override public int height() { return 1080; }
                                }; }
                            };
                        }
                    };
                }
            };
            byte[] raw;
            try (InputStream input = assets.open(INDEX)) { raw = ExternalCharts.read(input, 4 * 1024 * 1024); }
            String hash = PreparedChartStore.digest(raw);
            JSONObject index = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            JSONArray shards = index.getJSONArray("shards");
            check(shards.length() == 33 && index.getJSONObject("entries").length() == 1065, "Incomplete signed index");
            Path directory = ctx.getFilesDir().toPath().resolve("phigros-offline-plans-v1");
            Path store = directory.resolve(hash + ".bin");
            Path staging = directory.resolve(hash + ".importing");
            try {
                OfflineCloudPlans.install(ctx, new ByteArrayInputStream(new byte[256]), new AtomicBoolean(false));
                throw new AssertionError("Accepted a different file on fresh install");
            } catch (IOException expected) {
                check(!Files.exists(store) && !Files.exists(staging), "Invalid first import created a store");
            }
            try (InputStream input = Files.newInputStream(pack)) { OfflineCloudPlans.install(ctx, input, new AtomicBoolean(false)); }
            check(!Files.exists(staging) && Files.size(store) == 433111854L, "Incomplete committed pack");
            checkStore(store, shards);
            for (Fault fault : Fault.values()) {
                AtomicBoolean cancel = new AtomicBoolean(false);
                try (InputStream input = faulty(pack, fault, cancel)) {
                    OfflineCloudPlans.install(ctx, input, cancel);
                    throw new AssertionError("Accepted " + fault);
                } catch (IOException expected) {
                    check((expected instanceof InterruptedIOException) == (fault == Fault.CANCEL), "Wrong failure for " + fault);
                    check(!Files.exists(staging) && Files.exists(store), "Failed import changed active store");
                }
            }
            checkStore(store, shards);
            checkRandomReads(ctx, hash, index);
            check(preparedFiles(root) == 0, "Per-chart cache was already warm");
            loadSong(ctx);
            check(preparedFiles(root) > 0, "Cold playback did not persist chart tracks");
            GesturePlan.clearPrepared();
            Files.move(store, directory.resolve("saved-pack.bin"));
            loadSong(ctx);
            System.out.println("PASS offline ZIP: 1065 charts, all 33 shard digests and random reads, corrupt/truncated/cancelled replacement, network-blocked cold and cached playback (2026 notes / 160 noise areas).");
        } finally {
            try (Stream<Path> all = Files.walk(root)) {
                Path[] files = all.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
                for (Path file : files) Files.deleteIfExists(file);
            }
        }
    }
}
