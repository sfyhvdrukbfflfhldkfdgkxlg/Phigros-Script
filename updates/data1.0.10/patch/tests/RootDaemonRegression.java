package io.github.phiscript.root;

import android.hardware.input.InputManagerGlobal;
import java.io.*;

public final class RootDaemonRegression {
    static void frame(DataOutputStream out,long...keys)throws Exception{
        out.writeInt(keys.length);for(long key:keys){out.writeLong(key);out.writeFloat(100+key%10*50);out.writeFloat(300);}
    }
    public static void main(String[]args)throws Exception{
        android.os.Process.uid=1000;boolean refused=false;
        try{RootDaemon.main(new String[]{"input"});}catch(SecurityException e){refused=true;}
        if(!refused)throw new AssertionError("non-root worker accepted");
        android.os.Process.uid=0;
        InputStream originalIn=System.in;PrintStream originalOut=System.out;
        ByteArrayOutputStream input=new ByteArrayOutputStream();DataOutputStream request=new DataOutputStream(input);
        request.writeInt(1000);request.writeInt(600);
        frame(request,1,2);frame(request,1,2,3,4,5);frame(request,1,2);frame(request,6,7);request.writeInt(-1);
        ByteArrayOutputStream reply=new ByteArrayOutputStream();
        try{System.setIn(new ByteArrayInputStream(input.toByteArray()));System.setOut(new PrintStream(reply));RootDaemon.main(new String[]{"input"});}
        finally{System.setIn(originalIn);System.setOut(originalOut);}
        DataInputStream ack=new DataInputStream(new ByteArrayInputStream(reply.toByteArray()));
        if(ack.readInt()!=RootDaemon.MAGIC)throw new AssertionError("root handshake");
        for(int i=0;i<4;i++)if(ack.readByte()!=1)throw new AssertionError("root ack");
        if(ack.available()!=0||InputManagerGlobal.downs!=7||InputManagerGlobal.ups!=5||InputManagerGlobal.cancels!=1||!InputManagerGlobal.active.isEmpty())throw new AssertionError("root protocol cleanup/counts");
        System.out.println("PASS real RootDaemon.main binary handshake, four acknowledged frames and reflected MotionEvent conversion (mock Android manager)");
        System.out.println("PASS uid != 0 rejected before input startup; EOF/close releases all active pointers with CANCEL");
        System.out.println("Host protocol test only; does not execute su or Android Binder injection.");
    }
}
