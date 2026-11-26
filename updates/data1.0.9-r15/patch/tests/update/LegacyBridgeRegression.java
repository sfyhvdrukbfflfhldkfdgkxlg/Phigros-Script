package io.github.phiscript.update;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

/** Run with UpdateCatalog from the actual installed APK and the anonymous GitHub releases response. */
public final class LegacyBridgeRegression {
    private static final String BASE = "https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/";

    private static JSONObject find(JSONArray releases, String tag) {
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (tag.equals(release.getString("tag_name"))) return release;
        }
        return null;
    }

    private static JSONObject apk(JSONObject release, String name) {
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (name.equals(asset.getString("name"))) return asset;
        }
        throw new AssertionError("Missing " + name + " on " + release.getString("tag_name"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException(
            "Expected GitHub releases JSON, installed version, bridge tag, target version and --simulate or --published");
        byte[] raw = Files.readAllBytes(Paths.get(args[0]));
        if (raw.length > 1048576) throw new AssertionError("Payload too large for the old fetcher");
        JSONArray feed = new JSONArray(new String(raw, StandardCharsets.UTF_8));
        String installed = args[1], bridge = args[2], target = args[3];
        String filename = "PhigrosScript_" + target + ".apk";
        String url = BASE + "download/" + bridge + "/" + filename;
        JSONObject formal = find(feed, target);
        if (formal == null) throw new AssertionError("Canonical release missing from the first page");
        JSONObject original = apk(formal, filename), entry = find(feed, bridge);
        JSONArray before = new JSONArray();
        for (int i = 0; i < feed.length(); i++) {
            if (!bridge.equals(feed.getJSONObject(i).getString("tag_name"))) before.put(feed.get(i));
        }
        if ("--simulate".equals(args[4])) {
            JSONObject copy = new JSONObject(original.toString()).put("browser_download_url", url);
            entry = new JSONObject().put("tag_name", bridge).put("html_url", BASE + "tag/" + bridge)
                .put("draft", false).put("prerelease", false).put("assets", new JSONArray().put(copy));
            feed = new JSONArray().put(entry);
            for (int i = 0; i < before.length() && feed.length() < 30; i++) feed.put(before.get(i));
        } else if (!"--published".equals(args[4])) {
            throw new IllegalArgumentException("Expected --simulate or --published");
        }
        if (entry == null || entry.getBoolean("prerelease") || entry.getBoolean("draft"))
            throw new AssertionError("Missing stable compatibility release");
        JSONObject copy = apk(entry, filename);
        if (entry.getJSONArray("assets").length() != 1 || !url.equals(copy.getString("browser_download_url"))
                || !"uploaded".equals(copy.getString("state")) || copy.getLong("size") <= 0
                || copy.getLong("size") != original.getLong("size")
                || !original.getString("digest").matches("sha256:[0-9a-f]{64}")
                || !copy.getString("digest").equals(original.getString("digest")))
            throw new AssertionError("Bridge must serve the same canonical APK");
        UpdateCatalog.Release previous = UpdateCatalog.latest(before.toString(), installed);
        UpdateCatalog.Release actual = UpdateCatalog.latest(feed.toString(), installed);
        if (installed.startsWith("data")) {
            if ((previous != null && target.equals(previous.version)) || actual == null
                    || !bridge.equals(actual.version) || !url.equals(actual.apk))
                throw new AssertionError("Old client did not select the compatibility APK");
        } else if (actual == null ? previous != null : previous == null
                || !actual.version.equals(previous.version) || !actual.apk.equals(previous.apk)
                || actual.version.startsWith("data")) {
            throw new AssertionError("Compatibility release changed numbered client selection");
        }
        System.out.println("PASS " + installed + "  without bridge=" + (previous == null ? "none" : previous.version)
            + "  with bridge=" + (actual == null ? "none" : actual.version + " -> " + actual.apk));
    }
}
