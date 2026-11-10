import java.io.*;import java.nio.file.*;import java.util.*;
import brut.androlib.mod.SmaliMod;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
public class Assemble { public static void main(String[]args)throws Exception{DexBuilder b=new DexBuilder(new Opcodes(26,0));try(java.util.stream.Stream<Path>s=Files.walk(Paths.get(args[0]))){Iterator<Path> it=s.filter(p->p.toString().endsWith(".smali")).iterator();int n=0;while(it.hasNext()){File f=it.next().toFile();if(!SmaliMod.assembleSmaliFile(f,b,26))throw new Exception("bad "+f);n++;}System.out.println("assembled "+n+" classes");}b.writeTo(new FileDataStore(new File(args[1])));}}
