import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;

public class HostFrames {
    public static void main(String[] args) throws Exception {
        String name = "io/github/phiscript/gesturecapture/GestureTrace.class";
        try (ZipFile input = new ZipFile(args[0])) {
            ClassReader reader = new ClassReader(input.getInputStream(input.getEntry(name)));
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            reader.accept(writer, ClassReader.SKIP_FRAMES);
            Path path = Paths.get(args[1]).resolve(name);
            Files.createDirectories(path.getParent());
            Files.write(path, writer.toByteArray());
        }
    }
}
