
package android.os; public class HandlerThread extends Thread {
 public HandlerThread(String s){}public synchronized void start(){}
 public Looper getLooper(){return Looper.WORKER;}public boolean quitSafely(){return true;}}
