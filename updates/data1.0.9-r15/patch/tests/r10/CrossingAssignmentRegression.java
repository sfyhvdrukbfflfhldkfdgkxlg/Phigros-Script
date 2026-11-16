package io.github.phiscript.input;

import android.content.Context;
import io.github.phiscript.AppSettings;
import io.github.phiscript.engine.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;

/** A crossing rejected before the last comparison must become a new contact. */
public final class CrossingAssignmentRegression {
    static int checks;
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Note note(int id,int line,double time){return new Note(id,2,time,time,0,1,0,true,line);}
    static Chart chart(int size,boolean crossing){
        List<Note> notes=new ArrayList<>();
        for(int i=0;i<size;i++)notes.add(note(i,i,1));
        for(int i=0;i<3;i++)notes.add(note(size+i,i,1.1));
        return Chart.Companion.fromNative$app_debug(notes,0,(note,t,v)->{
            int line=note.getLineIndex();boolean next=note.getId()>=size;
            float x=line==0?(next?600:500):line==2?(next&&crossing?500:600):800;
            return new Point(x,line<=2?450:50+line*3);
        },(note,t,v)->new Point(0,1));
    }
    static List<UnifiedGesturePlanner.Contact> live(Chart chart,int size,GestureProfile profile){
        List<UnifiedGesturePlanner.Contact> live=new ArrayList<>();
        for(int i=0;i<size;i++)live.add(new UnifiedGesturePlanner.Contact(new TouchEvent(chart.getNotes().get(i),950,1200),true,profile));
        return live;
    }
    static int[] assign(Chart chart,int size,GestureProfile profile,boolean bridge)throws Exception{
        List<TouchEvent> notes=new ArrayList<>();
        for(int i=0;i<3;i++)notes.add(new TouchEvent(chart.getNotes().get(size+i),1050,1150));
        Method assign=UnifiedGesturePlanner.class.getDeclaredMethod("assign",Chart.class,List.class,List.class,GestureProfile.class,boolean.class);
        assign.setAccessible(true);
        try{return (int[])assign.invoke(null,chart,live(chart,size,profile),notes,profile,bridge);}
        catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    static void coverage(Chart chart,GestureProfile profile)throws Exception{
        GesturePlan plan=UnifiedGesturePlanner.build(chart,profile,true,Collections.emptySet());
        Field field=GesturePlan.class.getDeclaredField("mixed");field.setAccessible(true);
        Map<TouchEvent,UnifiedGesturePlanner.Contact> paths=(Map<TouchEvent,UnifiedGesturePlanner.Contact>)field.get(plan);
        Set<Integer> ids=new HashSet<>();
        for(UnifiedGesturePlanner.Contact contact:paths.values())for(TouchEvent event:contact.nodes)
            ok(ids.add(event.getNote().getId()),"note must be assigned exactly once");
        ok(ids.size()==chart.getNotes().size(),"rejected crossing keeps every note");
        boolean separate=false;
        for(UnifiedGesturePlanner.Contact contact:paths.values())if(contact.nodes.size()==1&&contact.last().getNote().getId()==5)separate=true;
        ok(separate,"crossing note becomes its own contact");
    }
    public static void main(String[] args)throws Exception{
        Context.root=Files.createTempDirectory("crossing-assignment").toFile();TouchService service=new TouchService();
        new AppSettings(service).save("tapmod_enabled","false");TapMod.init(service);
        GestureProfile profile=new GestureProfile(true,70,.12f,true,true,true);
        for(int size:new int[]{3,26,64,128})for(boolean bridge:new boolean[]{true,false}){
            int[] rejected=assign(chart(size,true),size,profile,bridge);
            ok(Arrays.equals(rejected,new int[]{0,1,-1}),"only crossing link is rejected: "+size+" / "+bridge);
            int[] ordinary=assign(chart(size,false),size,profile,bridge);
            ok(Arrays.equals(ordinary,new int[]{0,1,2}),"noncrossing assignment preserved");
        }
        coverage(chart(3,true),profile);
        System.out.println("PASS crossing assignment "+checks+" assertions; live sizes 3/26/64/128; unmatched note preserved");
    }
}
