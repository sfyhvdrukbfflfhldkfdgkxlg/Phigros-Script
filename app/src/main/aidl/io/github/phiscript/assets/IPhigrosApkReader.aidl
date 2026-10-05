package io.github.phiscript.assets;
import android.os.ParcelFileDescriptor;
/** Fixed APK reads, bounded diagnostics, and explicit one-shot activation of this app's service. */
interface IPhigrosApkReader {
    ParcelFileDescriptor listEntries(long versionCode, long lastUpdateTime) = 0;
    ParcelFileDescriptor readAsset(String entry, int maxBytes, long versionCode, long lastUpdateTime) = 1;
    String collectAccessibilityDiagnostics() = 2;
    String enableSelfAccessibility() = 3;
    void destroy() = 16777114;
}
