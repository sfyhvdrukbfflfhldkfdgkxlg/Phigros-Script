package io.github.phiscript.capture;
import io.github.phiscript.AppSettings;
public final class RetryTimingRegression {
 static int checks;static void ok(boolean c,String m){checks++;if(!c)throw new AssertionError(m);}
 interface Call{void run();}static void rejects(Call c,String m){boolean rejected=false;try{c.run();}catch(IllegalArgumentException e){rejected=true;}ok(rejected,m);}
 public static void main(String[] a){
  AppSettings.values.clear();AppSettings prefs=new AppSettings(new android.content.Context());
  AppSettings.values.put("phigros_auto_start_offset_ms","999999");AppSettings.values.put("phigros_note_clock_offset_ms","-999999");AppSettings.values.put("captureLag","500");AppSettings.values.put("touchOffset","500");
  ok(PhigrosRetryClock.readDelay(prefs)==0,"one default retry delay; legacy offsets ignored");
  for(long at:new long[]{0,100000,1000000000})for(int delay:new int[]{0,700,5000})for(double offset:new double[]{-.5,0,.125,2}){
   long expected=PhiraRestartClock.INSTANCE.epoch(at,delay,offset,0,0);
   ok(PhigrosRetryClock.epoch(at,delay,offset)==expected,"same epoch calculation as Phira");
  }
  ok(PhigrosRetryClock.epoch(100000,700,.25)==100950,"positive chart offset applied once");
  ok(PhigrosRetryClock.epoch(100000,700,-.25)==100700,"negative offset follows Phira clamping");
  AppSettings.values.put(PhigrosRetryClock.KEY,"1234");ok(PhigrosRetryClock.readDelay(prefs)==1234,"saved retry delay");
  rejects(()->PhigrosRetryClock.epoch(-1,700,0),"invalid retry receipt");rejects(()->PhigrosRetryClock.epoch(0,-1,0),"negative delay");rejects(()->PhigrosRetryClock.epoch(0,5001,0),"oversized delay");rejects(()->PhigrosRetryClock.epoch(0,700,Double.NaN),"NaN offset");
  for(String value:new String[]{"-1","5001","abc"}){AppSettings.values.put(PhigrosRetryClock.KEY,value);rejects(()->PhigrosRetryClock.readDelay(prefs),"invalid stored delay");}
  System.out.println("PASS retry timing "+checks+" assertions (Phira parity, one delay, no legacy offsets)");
 }
}
