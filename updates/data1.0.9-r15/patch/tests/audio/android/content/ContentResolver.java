package android.content;

import android.net.Uri;

public class ContentResolver {
    public static int result, calls;
    public int update(Uri uri, ContentValues values, String where, String[] args) {
        if (uri == null || !Integer.valueOf(0).equals(values.data.get("is_pending"))) throw new AssertionError("missing publish flag");
        ++calls;
        if (result < 0) throw new IllegalStateException("injected provider failure");
        return result;
    }
}
