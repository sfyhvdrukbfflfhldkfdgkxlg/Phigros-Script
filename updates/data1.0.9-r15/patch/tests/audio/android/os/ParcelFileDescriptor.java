package android.os;

import java.io.FileDescriptor;

public class ParcelFileDescriptor {
    private final FileDescriptor descriptor;
    public ParcelFileDescriptor(FileDescriptor descriptor) { this.descriptor = descriptor; }
    public FileDescriptor getFileDescriptor() { return descriptor; }
}
