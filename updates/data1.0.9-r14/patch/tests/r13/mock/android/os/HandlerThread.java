package android.os;
public class HandlerThread extends Thread {
    final Looper looper=new Looper();public static int closed;
    public HandlerThread(String name){super(name);}public void start(){}public Looper getLooper(){return looper;}
    public boolean quitSafely(){closed++;return true;}
}
