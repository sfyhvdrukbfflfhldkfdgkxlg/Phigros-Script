package io.github.phiscript.assets;

import static io.github.phiscript.root.UiDesign.words;

import android.content.Context;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Identifies the complete offline release before committing charts from the document picker. */
final class FeijipanOfflinePack {
    static final String SHARE_URL = "https://share.feijipan.com/s/sqfXebbe";
    static final String NAME = "Phigros_Charts_4.1.0_20261122.zip";
    static final long SIZE = 364666147L;
    private static final String SHA256 = "22b9e0c4cfca269d299c83fe1044c93f188c374751aa0d1dbfe6d25b2abefd71";
    private static final String CATALOG_SHA256 = "f18750f2b3e2c750602fde4916221200af2e0013179d5edc9500d08aac45b806";

    private FeijipanOfflinePack() {}

    static JSONObject install(Context context, Uri uri, AtomicBoolean cancel) throws Exception {
        try (InputStream document = open(context, uri)) {
            verify(document, cancel);
        }
        ChartDownload.check(cancel);
        try (InputStream document = open(context, uri)) {
            return SwordaltSource.importPack(context, document, cancel, CATALOG_SHA256);
        }
    }

    private static InputStream open(Context context, Uri uri) throws IOException {
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException(words("无法打开所选 ZIP，请重新选择下载的文件。",
                "Could not open the selected ZIP. Choose the downloaded file again."));
        return input;
    }

    static void verify(InputStream input, AtomicBoolean cancel) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        long size = 0;
        while (true) {
            ChartDownload.check(cancel);
            int count = input.read(buffer);
            if (count < 0) break;
            size += count;
            if (size > SIZE) break;
            digest.update(buffer, 0, count);
        }
        ChartDownload.check(cancel);
        if (size != SIZE) throw new IOException(words("ZIP 大小不符。请等待下载完成，然后选择完整的 ",
                "ZIP size mismatch. Wait for the download to finish, then choose the complete ") + NAME);
        byte[] hash = digest.digest();
        for (int i = 0; i < hash.length; i++) {
            if ((hash[i] & 255) != Integer.parseInt(SHA256.substring(i * 2, i * 2 + 2), 16)) {
                throw new IOException(words("ZIP 的 SHA-256 校验失败。请重新下载并选择该文件。",
                        "The ZIP failed its SHA-256 check. Download it again and select the new file."));
            }
        }
    }
}
