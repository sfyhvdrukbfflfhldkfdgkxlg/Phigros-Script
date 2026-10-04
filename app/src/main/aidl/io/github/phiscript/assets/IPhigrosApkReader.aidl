package io.github.phiscript.assets;
import android.os.ParcelFileDescriptor;
/** Only installed Phigros; reliable pipes avoid large Binder transactions. */
interface IPhigrosApkReader {
    ParcelFileDescriptor listEntries(long versionCode, long lastUpdateTime) = 0;
    ParcelFileDescriptor readAsset(String entry, int maxBytes, long versionCode, long lastUpdateTime) = 1;
    void destroy() = 16777114;
}
