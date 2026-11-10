import java.io.File;
import java.nio.file.*;
import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;

/** Disassemble actual APK instructions for the shared-session parity audit. */
public final class DumpSession {
 public static void main(String[] args)throws Exception{
  MultiDexContainer container=DexFileFactory.loadDexContainer(new File(args[0]),new Opcodes(26,0));
  DexBackedDexFile dex=container.getEntry("classes5.dex").getDexFile();File directory=new File(args[1]+".dir");directory.mkdirs();
  Baksmali.disassembleDexFile(dex,directory,1,new BaksmaliOptions());
  Files.copy(directory.toPath().resolve("io/github/phiscript/capture/PhiraSessionEngine.smali"),Paths.get(args[1]),StandardCopyOption.REPLACE_EXISTING);
 }
}
