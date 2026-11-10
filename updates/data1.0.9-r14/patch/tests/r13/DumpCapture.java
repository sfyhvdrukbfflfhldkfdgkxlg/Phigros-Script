import java.io.File;
import com.android.tools.smali.baksmali.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
public final class DumpCapture {
    public static void main(String[] args) throws Exception {
        MultiDexContainer container = DexFileFactory.loadDexContainer(new File(args[0]),new Opcodes(26,0));
        BaksmaliOptions options = new BaksmaliOptions();
        options.localsDirective = true;
        Baksmali.disassembleDexFile(container.getEntry("classes5.dex").getDexFile(), new File(args[1]), 1, options);
    }
}
