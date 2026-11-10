package io.github.phiscript.vision;

import java.util.*;

/** Exact-name precedence must not turn conflicting OCR into a guessed chart. */
public final class MatcherRulesRegression {
    static int checks;
    static SongCandidate song(String id,String title,String... aliases){return new SongCandidate(id,title,Arrays.asList(aliases));}
    static Identity match(List<SongCandidate> songs,List<String> titles,String... ranks){return PhigrosChartMatcher.match(titles,Arrays.asList(ranks),songs);}
    static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void id(Identity value,String id,String message){ok(value!=null&&value.getSongId().equals(id)&&value.getDifficulty().equals("IN"),message);}
    public static void main(String[] args){
        SongCandidate formal=song("formal","Signal"),other=song("other","Other Song","signal");List<SongCandidate> collision=Arrays.asList(formal,other);
        id(match(collision,Arrays.asList("Signal"),"IN"),"formal","exact formal name wins over foreign alias");
        id(match(Arrays.asList(song("sun","Sunlight","solar")),Arrays.asList("solar"),"IN"),"sun","unique alias retained");
        id(match(Arrays.asList(song("sun","Sunlight")),Arrays.asList("Sunligh"),"IN"),"sun","legacy one-edit tolerance retained");
        ok(match(Arrays.asList(song("a","Sunlight"),song("b","Sunlighs")),Arrays.asList("Sunligh"),"IN")==null,"ambiguous fuzzy result rejected");
        ok(match(Arrays.asList(song("a","Echo!"),song("b","ECHO")),Arrays.asList("echo"),"IN")==null,"duplicate normalized formal names rejected");
        ok(match(Arrays.asList(song("a","First Song","shared"),song("b","Second Song","shared")),Arrays.asList("shared"),"IN")==null,"shared alias rejected without formal evidence");
        ok(match(collision,Arrays.asList("Signal","Other Song"),"IN")==null,"multiple formal names rejected");
        for(List<String> title:Arrays.asList(Arrays.asList("IN Signal"),Arrays.asList("Signal IN"),Arrays.asList("LV: IN 12 Signal"),Arrays.asList("Signal IN Lv.12"),Arrays.asList("Ｓｉｇｎａｌ")))id(match(collision,title,"IN"),"formal","title normalization and edge ranks retained");
        ok(match(collision,Arrays.asList("Signal IN"),"HD")==null,"embedded and dedicated rank disagree");
        ok(match(collision,Arrays.asList("Signal IN","Signal HD"),"IN")==null,"two embedded ranks disagree");
        ok(match(collision,Arrays.asList("Signal"),"IN","HD")==null,"two dedicated ranks disagree");
        ok(match(collision,Arrays.asList("Signal IN"))==null,"title alone must not invent dedicated rank evidence");
        ok(match(collision,Arrays.asList("Signal"),"SP")==null,"unsupported SP remains manual");
        id(match(Arrays.asList(song("multi","Three Part Song")),Arrays.asList("Three","Part","Song"),"IN"),"multi","three-line title join retained");
        ok(match(collision,Arrays.asList("unrelated"),"IN")==null,"unknown title rejected");
        System.out.println("PASS R11 matcher rules "+checks+" assertions; exact names, aliases, fuzzy matches, Unicode, wrapped labels and conflicting ranks");
    }
}
