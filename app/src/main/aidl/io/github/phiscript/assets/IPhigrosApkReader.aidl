package io.github.phiscript.assets;
import android.os.ParcelFileDescriptor;
/** Fixed read-only APK operations and an explicit, bounded self-diagnostic capture. */
interface IPhigrosApkReader {
    ParcelFileDescriptor listEntries(long versionCode, long lastUpdateTime) = 0;
    ParcelFileDescriptor readAsset(String entry, int maxBytes, long versionCode, long lastUpdateTime) = 1;
    String collectAccessibilityDiagnostics() = 2;
    void destroy() = 16777114;
}
