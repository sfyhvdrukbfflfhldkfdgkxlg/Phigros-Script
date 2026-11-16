import java.io.File;
import com.android.tools.smali.baksmali.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
/** Read final APK code, including the shared gesture planner and active settings. */
public final class DumpAuditClasses {
    public static void main(String[] args)throws Exception{
        MultiDexContainer container=DexFileFactory.loadDexContainer(new File(args[0]),new Opcodes(26,0));
        BaksmaliOptions options=new BaksmaliOptions();options.localsDirective=true;
        for(int index:new int[]{4,6,8,12}){
            File out=new File(args[1],Integer.toString(index));out.mkdirs();
            Baksmali.disassembleDexFile(container.getEntry("classes"+index+".dex").getDexFile(),out,1,options);
        }
    }
}
