package io.github.phiscript.input;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import org.json.*;
/** Run against frozen and replacement planners: hashes exact chosen contacts,
 * timings, route keys, escaped/swipe endpoints and unavailable counts. */
public final class PlannerEquivalenceRegression {
 static Note n(int id,int type,double hit,double end,double x){return new Note(id,type,hit,end,x,1,0,true,0);}
 static final Viewport V=new Viewport(0,0,1000,600);static final TouchBounds B=new TouchBounds(0,0,999,599);
 static String fingerprint()throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
  for(int guard:new int[]{0,24})for(boolean random:new boolean[]{false,true})for(int fixture=0;fixture<7;fixture++){
   AppSettings.values.put("tap_noise_clock_guard_ms",Integer.toString(guard));AppSettings.values.put("tapmod_enabled",Boolean.toString(random));TapMod.reload();List<Note> notes=new ArrayList<>();
   if(fixture==0)notes.add(n(1,4,.22,.22,.5));
   if(fixture==1){notes.add(n(1,3,.1,.8,.5));notes.add(n(2,4,.3,.3,.5));}
   if(fixture==2){notes.add(n(1,1,.1,.1,.3));notes.add(n(2,4,.2,.2,.4));}
   if(fixture==3){notes.add(n(1,1,.1,.1,.1));notes.add(n(2,4,.15,.15,.9));}
   if(fixture==4){notes.add(n(1,4,.22,.22,.5));notes.add(n(2,4,.32,.32,.5));}
   if(fixture==5){for(int i=0;i<10;i++)notes.add(n(i+1,3,.1,.8,.04+.1*i));notes.add(n(20,4,.3,.3,.5));}
   if(fixture==6){notes.add(n(1,3,.1,.7,.25));notes.add(n(2,1,.15,.15,.65));notes.add(n(3,4,.35,.35,.65));notes.add(n(4,2,.5,.5,.65));}
   Chart chart=Chart.Companion.fromNative$app_debug(notes,0,(note,t,v)->new Point((float)(note.getPositionX()*v.getWidth()),(float)(v.getHeight()*(.5+.04*Math.sin(t)))),(note,t,v)->new Point(0,1));TapMod.preparationSalt(chart,1234567);
   if(fixture==4){JSONObject area=new JSONObject().put("bottomLeftPercentage",new JSONObject().put("x",.35).put("y",.3)).put("topRightPercentage",new JSONObject().put("x",.65).put("y",.7)).put("appearTime",.1).put("enableTime",.1).put("disableTime",.5).put("disappearTime",.5);BlockAreaSet.capture(new JSONObject().put("blockAreaList",new JSONArray().put(area)));BlockAreaSet.bind(chart);}
   GesturePlan plan=ContactPlanner.build(chart,V,B,new GestureProfile(true,70,.12f,true,true,true));out.writeInt(plan.unavailableNotes);out.writeInt(plan.events.size());TapMod.planning.set(true);
   try{for(TouchEvent event:plan.events){out.writeInt(event.getNote().getId());out.writeLong(event.getStartMs());out.writeLong(event.getEndMs());ContactPlanner.Route route=plan.routes.get(event);out.writeBoolean(route.flick);out.writeInt(route.uses);out.writeInt(route.keys.size());for(Map.Entry<Long,ContactPlanner.Anchor> entry:route.keys.entrySet()){out.writeLong(entry.getKey());Point p=entry.getValue().at(chart,entry.getKey(),V,B);out.writeFloat(p.getX());out.writeFloat(p.getY());}}}finally{TapMod.planning.remove();}
  }
  StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()))hash.append(String.format(Locale.ROOT,"%02x",b&255));return hash.toString();
 }
 public static void main(String[] args)throws Exception{AppSettings.values.clear();AppSettings.values.put("tap_noise_avoid","true");TapMod.init(new android.content.Context());String hash=fingerprint();if(args.length>0&&!args[0].equals(hash))throw new AssertionError("selected contact/trajectory changed: "+hash);System.out.println("PASS 28 exact planner fixtures fingerprint="+hash);}
}
