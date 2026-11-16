package io.github.phiscript.vision;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Formal chart names take priority over another song's shorthand alias. */
public final class PhigrosChartMatcher {
    private PhigrosChartMatcher(){}
    private static final String RANK="(?:(?:LV|LEVEL|DIFFICULTY|难度)[\\s:.：]*)?(EZ|HD|IN|AT)(?:\\s*(?:LV[\\s.]*)?\\d{1,2}(?:\\.\\d+)?)?";
    private static final Pattern PREFIX=Pattern.compile("^"+RANK+"\\s+",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern SUFFIX=Pattern.compile("\\s+"+RANK+"$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static void add(Set<String> keys,Map<String,Set<String>> ranks,String title,String rank){
        String key=SongMatcher.INSTANCE.normalize(title);
        if(key.isEmpty()||key.length()>200)return;
        keys.add(key);
        if(rank!=null)ranks.computeIfAbsent(key,k->new LinkedHashSet<>()).add(rank.toUpperCase(Locale.ROOT));
    }
    public static Identity match(List<String> titleLines,List<String> difficultyLines,List<SongCandidate> songs){
        Set<String> keys=new LinkedHashSet<>();Map<String,Set<String>> titleRanks=new HashMap<>();
        for(int start=0;start<titleLines.size();start++){
            StringBuilder joined=new StringBuilder();
            for(int count=0;count<3&&start+count<titleLines.size();count++){
                if(count>0)joined.append(' ');joined.append(titleLines.get(start+count));
                String raw=Normalizer.normalize(joined.toString().trim(),Normalizer.Form.NFKC);
                add(keys,titleRanks,raw,null);
                Matcher prefix=PREFIX.matcher(raw),suffix=SUFFIX.matcher(raw);
                if(prefix.find())add(keys,titleRanks,raw.substring(prefix.end()).trim(),prefix.group(1));
                if(suffix.find())add(keys,titleRanks,raw.substring(0,suffix.start()).trim(),suffix.group(1));
            }
        }
        Map<String,SongCandidate> exact=new LinkedHashMap<>();
        for(SongCandidate song:songs)if(keys.contains(SongMatcher.INSTANCE.normalize(song.getTitle())))exact.put(song.getId(),song);
        if(exact.isEmpty())return SongMatcher.INSTANCE.match(titleLines,difficultyLines,songs);
        if(exact.size()!=1)return null;
        SongCandidate chosen=exact.values().iterator().next();
        Identity identity=SongMatcher.INSTANCE.match(titleLines,difficultyLines,Collections.singletonList(chosen));
        if(identity==null)return null;
        Set<String> embedded=titleRanks.get(SongMatcher.INSTANCE.normalize(chosen.getTitle()));
        if(embedded!=null&&(embedded.size()!=1||!embedded.contains(identity.getDifficulty())))return null;
        return identity;
    }
}
