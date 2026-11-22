package io.github.phiscript.record;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

/** Keep a real FD so the producer's pre-publication fsync is exercised. */
public class RecordingFiles {
    static final ArrayList<RecordingFiles> created = new ArrayList<>();
    public final Uri uri;
    public final ParcelFileDescriptor descriptor;
    final File file;
    private final RandomAccessFile handle;
    Boolean valid;
    public RecordingFiles(Context context) throws IOException {
        file = File.createTempFile("recording-output", ".mp4");
        uri = Uri.fromFile(file);
        handle = new RandomAccessFile(file,"rw");
        descriptor = new ParcelFileDescriptor(handle.getFD());
        created.add(this);
    }
    public void finish(boolean success) {
        if (valid != null) throw new AssertionError("double finalization");
        valid = success;
        try { handle.close(); } catch (IOException e) { throw new AssertionError(e); }
        if (!file.delete()) throw new AssertionError("could not remove host scratch file");
    }
}
