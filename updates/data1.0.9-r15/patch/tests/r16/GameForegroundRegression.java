package io.github.phiscript.input;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.*;
public final class GameForegroundRegression {
 private static int checks;private static final String GAME="com.PigeonGames.Phigros",SELF="io.github.phiscript";
 private static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 private static AccessibilityWindowInfo win(int type,boolean focus,boolean active,String pkg){return new AccessibilityWindowInfo(type,focus,active,pkg);}
 private static void frame(TouchService s,AccessibilityWindowInfo... windows){SystemClock.now+=40;s.windows=Arrays.asList(windows);s.activeRoot=null;}
 public static void main(String[] args){
  TouchService s=new TouchService();s.selectGame(GAME);s.reportedPackage=SELF;
  frame(s,win(1,true,true,SELF));ok(!GameForeground.isForeground(s),"startup own activity accepted as game");
  frame(s,win(3,true,true,"android"));ok(!GameForeground.isForeground(s),"projection consent sheet accepted as gameplay");
  frame(s,win(1,true,true,GAME));ok(GameForeground.isForeground(s),"stale own-package event blocked live game");
  frame(s,win(4,true,true,SELF),win(1,false,true,GAME));ok(GameForeground.isForeground(s),"own focused overlay hid proven active game");
  frame(s,win(4,true,true,SELF));s.reportedPackage=SELF;ok(!GameForeground.isForeground(s),"overlay alone authorized input");
  s.reportedPackage=GAME;ok(!GameForeground.isForeground(s),"overlay without underlying app trusted stale game event");
  frame(s,win(1,true,true,null));s.activeRoot=new AccessibilityNodeInfo(GAME);ok(GameForeground.isForeground(s),"null focused root rejected active-root evidence");ok(s.activeRoot.recycled,"active root was not recycled");
  frame(s,win(1,true,true,GAME));s.reportedPackage=GAME;ok(GameForeground.isForeground(s),"game proof rejected");
  s.reportedPackage="other.app";s.windows=Arrays.asList(win(1,true,true,"other.app"));
  ok(!GameForeground.isForeground(s),"event change did not invalidate 35ms game snapshot");
  frame(s,win(1,true,true,"other.app"));s.reportedPackage=GAME;ok(!GameForeground.isForeground(s),"stale game event overrode actual other app");
  frame(s,win(3,true,true,"com.android.systemui"),win(1,false,true,GAME));ok(!GameForeground.isForeground(s),"system shade failed to block game");
  frame(s,win(4,true,true,"third.party.overlay"),win(1,false,true,GAME));ok(!GameForeground.isForeground(s),"foreign accessibility overlay accepted");
  frame(s);s.reportedPackage=SELF;ok(!GameForeground.isForeground(s),"unknown focus with own event assumed game");
  frame(s);s.activeRoot=new AccessibilityNodeInfo(SELF);s.reportedPackage=GAME;ok(!GameForeground.isForeground(s),"active own app root trusted stale game event");
  s.selectGame("org.flos.phira");s.windows=Arrays.asList(win(1,true,true,GAME));ok(!GameForeground.isForeground(s),"target change did not invalidate foreground snapshot");
  frame(s,win(1,true,true,"org.flos.phira"));ok(GameForeground.isForeground(s),"Phira live foreground rejected");
  System.out.println("PASS "+checks+" live foreground checks: projection sheet, stale events, focused/active overlay windows, empty root, cache invalidation and real app switches.");
 }
}
