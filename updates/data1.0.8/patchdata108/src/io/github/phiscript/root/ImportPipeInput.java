package io.github.phiscript.root;

import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Poll the private pipe instead of entering an uninterruptible read awaiting su. */
public final class ImportPipeInput extends InputStream {
    private final Process process;private final InputStream input;private final AtomicBoolean cancelled;private final Supplier<String> timeout;
    public ImportPipeInput(Process process,AtomicBoolean cancelled,Supplier<String> timeout){this.process=process;this.input=process.getInputStream();this.cancelled=cancelled;this.timeout=timeout;}
    @Override public int read()throws IOException{byte[] b=new byte[1];int n=read(b,0,1);return n<0?-1:b[0]&255;}
    @Override public int read(byte[] bytes,int offset,int length)throws IOException{
        if(offset<0||length<0||length>bytes.length-offset)throw new IndexOutOfBoundsException();if(length==0)return 0;
        while(true){
            if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消 Phira 导入");
            String problem=timeout.get();if(problem!=null)throw new IOException(problem);
            int available=input.available();if(available>0)return input.read(bytes,offset,Math.min(available,length));
            if(!process.isAlive())return -1;
            try{Thread.sleep(12);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("已取消 Phira 导入");}
        }
    }
    @Override public void close()throws IOException{input.close();}
}
