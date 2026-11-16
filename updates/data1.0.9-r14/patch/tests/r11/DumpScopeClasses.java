import java.io.File;
import com.android.tools.smali.baksmali.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
/** Compare decoded instructions rather than DEX data-section layout. */
public final class DumpScopeClasses {
    public static void main(String[] args)throws Exception{
        MultiDexContainer container=DexFileFactory.loadDexContainer(new File(args[0]),new Opcodes(26,0));
        BaksmaliOptions options=new BaksmaliOptions();options.localsDirective=true;
        for(int index:new int[]{4,5,6,7,8,11,12}){
            File directory=new File(args[1],Integer.toString(index));directory.mkdirs();
            Baksmali.disassembleDexFile(container.getEntry("classes"+index+".dex").getDexFile(),directory,1,options);
        }
    }
}
