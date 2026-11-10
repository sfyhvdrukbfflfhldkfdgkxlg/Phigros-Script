package io.github.phiscript.capture;
public final class RecognitionOverlay {
 public static final class Choice {private final long token;private final boolean start;public Choice(long token,boolean start){this.token=token;this.start=start;}public long getToken(){return token;}public boolean getStart(){return start;}}
 private long token;public void status(String text){}public long hide(){return android.os.SystemClock.uptimeMillis();}
 public void offer(long token,String title,String level,boolean preview){this.token=token;SessionTrace.offers++;if(preview)SessionTrace.stop.set(true);}
 public Choice consumeChoice(){if(token==0)return null;Choice choice=new Choice(token,!SessionTrace.ending.equals("decline"));token=0;if(!choice.getStart())SessionTrace.stop.set(true);return choice;}
}
