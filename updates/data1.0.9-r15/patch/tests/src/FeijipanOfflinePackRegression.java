package io.github.phiscript.assets;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Supply the published offline ZIP and a fresh cache directory. */
public class FeijipanOfflinePackRegression {
    private interface Action { void run() throws Exception; }

    private static void rejects(Action action) throws Exception {
        try { action.run(); }
        catch (IOException expected) { return; }
        throw new AssertionError("Accepted a cancelled or invalid package");
    }

    static void checkShareLinks() {
        for (String url:new String[]{OfflineCloudPlans.SHARE_URL,FeijipanOfflinePack.SHARE_URL}) {
            for (String suffix:new String[]{"","/","/?source=android#download","?from=share"}) {
                if (!url.equals(SwordaltLibraryUi.publishedFeijipanShare(java.net.URI.create(url+suffix))))
                    throw new AssertionError("Missed published share: "+url+suffix);
            }
        }
        for (String url:new String[]{"https://share.feijipan.com/s/feyajI8g","https://share.feijipan.com/s/0hfX6hQd-extra",
                "https://example.com/s/0hfX6hQd","http://share.feijipan.com/s/sqfXebbe"}) {
            if (SwordaltLibraryUi.publishedFeijipanShare(java.net.URI.create(url))!=null)
                throw new AssertionError("Unexpected published share: "+url);
        }
    }

    public static void main(String[] args) throws Exception {
        checkShareLinks();
        Path zip = Paths.get(args[0]);
        Context.root = Files.createDirectory(Paths.get(args[1])).toFile();
        Context context = new Context();
        AtomicBoolean active = new AtomicBoolean();

        rejects(() -> FeijipanOfflinePack.verify(new ByteArrayInputStream(new byte[]{1}), active));
        try (InputStream input = Files.newInputStream(zip)) {
            rejects(() -> FeijipanOfflinePack.verify(input, new AtomicBoolean(true)));
        }
        try (InputStream input = new FilterInputStream(Files.newInputStream(zip)) {
            boolean changed;
            @Override public int read(byte[] buffer) throws IOException {
                int count = in.read(buffer);
                if (!changed && count > 0) { buffer[0] ^= 1; changed = true; }
                return count;
            }
        }) {
            rejects(() -> FeijipanOfflinePack.verify(input, active));
        }
        try (InputStream input = Files.newInputStream(zip)) {
            rejects(() -> SwordaltSource.importPack(context,input,active,"different catalog fingerprint"));
        }
        File[] prior = SwordaltSource.cacheDirectory(context).listFiles();
        if (prior == null || prior.length != 0) throw new AssertionError("Rejected catalog wrote chart data");

        JSONObject catalog = FeijipanOfflinePack.install(context,Uri.fromFile(zip.toFile()),active);
        JSONArray charts = catalog.getJSONArray("entries");
        if (charts.length()!=1065 || SwordaltSource.saved(context).getJSONArray("entries").length()!=charts.length())
            throw new AssertionError("Incomplete installed catalog");
        for (int i=0;i<charts.length();i++) {
            JSONObject row = charts.getJSONObject(i);
            File file = new File(SwordaltSource.cacheDirectory(context),SwordaltSource.key(catalog,row)+".json");
            if (file.length()!=row.getLong("bytes") || !row.getString("sha1").equals(SwordaltSource.blobHash(Files.readAllBytes(file.toPath()))))
                throw new AssertionError("Bad installed chart " + row.getString("path"));
        }
        System.out.println("PASS full Feijipan ZIP, 1065 installed chart hashes, cancellation, truncation, same-size corruption and catalog replacement");
    }
}
