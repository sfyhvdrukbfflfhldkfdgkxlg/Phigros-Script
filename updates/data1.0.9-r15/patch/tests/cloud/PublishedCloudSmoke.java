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
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;
import org.json.JSONObject;

/** Opt-in live smoke test of the same chart-selection entry point used by the overlay. */
public final class PublishedCloudSmoke {
    public static void main(String[] args) throws Exception {
        Context.root = Files.createTempDirectory("published-cloud-").toFile();
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
            Context context = new Context() {
                @Override public AssetManager getAssets() { return assets; }
                @Override public Object getSystemService(String name) {
                    if (!WINDOW_SERVICE.equals(name)) return super.getSystemService(name);
                    return new WindowManager() {
                        @Override public WindowMetrics getMaximumWindowMetrics() {
                            return new WindowMetrics() {
                                @Override public Rect getBounds() {
                                    return new Rect() {
                                        @Override public int width() { return 2400; }
                                        @Override public int height() { return 1080; }
                                    };
                                }
                            };
                        }
                    };
                }
            };
            JSONObject catalog;
            try (InputStream stream = assets.open(SwordaltSource.ASSET)) {
                catalog = new JSONObject(new String(ExternalCharts.read(stream, 4 * 1024 * 1024), StandardCharsets.UTF_8));
            }
            catalog.put("phiscriptCloudPlans", 1);
            ChartLibrary library = ExternalCharts.fromSwordaltCatalog(context, catalog);
            String id = ExternalCharts.hash("DesultorySignals.technoplanet.0/AT.json");
            ChartLocation selected = null;
            for (ChartLocation row : library.getLocations()) {
                if ("AT".equals(row.getDifficulty()) && (id.equals(row.getAssetKey()) || id.equals(row.getBundleEntry()))) selected = row;
            }
            if (selected == null) throw new AssertionError("Desultory Signals AT missing from signed catalog");
            AtomicBoolean cancel = new AtomicBoolean(false);
            for (int pass = 0; pass < 2; pass++) {
                Chart chart = ExternalCharts.loadSelected(library, selected.getSongId(), selected.getDifficulty(), cancel);
                if (chart.getNotes().size() != 2026 || BlockAreaSet.count(chart) != 160)
                    throw new AssertionError("Incomplete published timeline or noise geometry");
                if (pass == 0) {
                    GesturePlan.clearPrepared();
                    URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
                        @Override protected URLConnection openConnection(URL url) {
                            throw new AssertionError("Unexpected network access on warm playback: " + url.getHost());
                        }
                    });
                }
            }
            System.out.println("PASS published cloud chart: 2026 notes and 160 noise areas; cold download and offline cache reopen");
        }
    }
}
