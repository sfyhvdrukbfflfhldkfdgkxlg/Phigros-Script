package io.github.phiscript.root;
import io.github.phiscript.assets.PhiraImportDecoder;import java.io.*;import java.util.concurrent.CancellationException;
public final class NativeParserCancelRegression {
 static int reads;public static void main(String[] args)throws Exception{InputStream input=new InputStream(){public int read(){throw new AssertionError();}public int read(byte[] b,int off,int len){if(++reads>4)throw new AssertionError("parser read after cancellation");java.util.Arrays.fill(b,off,off+len,(byte)' ');if(reads==1){byte[] p="{\"formatVersion\":3,".getBytes();System.arraycopy(p,0,b,off,p.length);}PhiraImportControl.cancel();return len;}};
  boolean cancelled=false;try(PhiraImportControl.Session session=PhiraImportControl.begin()){try{PhiraImportDecoder.INSTANCE.decode(input,"cancel.json");}catch(CancellationException expected){cancelled=true;}}
  if(!cancelled||reads>1||Thread.currentThread().isInterrupted()||PhiraImportControl.isBusy())throw new AssertionError("packaged parser cancellation failed");System.out.println("PASS actual packaged PhiraImportDecoder cancellation after first input block, session release and executor reuse.");}
}
