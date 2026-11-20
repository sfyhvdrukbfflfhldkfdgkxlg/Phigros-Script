package io.github.phiscript.assets;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CloudRangeRegression {
    private static final byte[] SHARD={10,20,30,40,50,60};
    private static byte[] read(int status,String range,long total,byte[] data,boolean cancelled)throws IOException{
        return ChartDownload.readRange(new ByteArrayInputStream(data),status,range,total,2,3,new AtomicBoolean(cancelled));
    }
    private interface Work{void run()throws IOException;}
    private static void rejects(Work work)throws IOException{try{work.run();throw new AssertionError("Invalid range accepted");}catch(IOException expected){}}
    public static void main(String[] args)throws IOException{
        byte[] member={30,40,50};
        if(!Arrays.equals(member,read(206,"bytes 2-4/6",3,member,false))||
                !Arrays.equals(member,read(200,null,6,SHARD,false))||
                !Arrays.equals(member,read(200,null,-1,SHARD,false)))throw new AssertionError("Incorrect member returned");
        rejects(()->read(206,"bytes 1-3/6",3,member,false));
        rejects(()->read(206,"bytes 2-4/4",3,member,false));
        rejects(()->read(206,"bytes 2-4/6",2,member,false));
        rejects(()->read(200,null,3,member,false));
        rejects(()->read(200,null,-1,member,false));
        rejects(()->read(200,null,6,SHARD,true));
        System.out.println("PASS cloud downloads: byte range, full-shard fallback, malformed range, truncation and cancellation");
    }
}
