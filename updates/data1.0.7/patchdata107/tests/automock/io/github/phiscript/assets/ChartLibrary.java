package io.github.phiscript.assets;
import java.util.*;import io.github.phiscript.vision.SongCandidate;import io.github.phiscript.capture.AutoEngineRegression;import io.github.phiscript.engine.Chart;
public final class ChartLibrary {
 public List<SongCandidate> songs(){List<SongCandidate> s=new ArrayList<>();s.add(new SongCandidate("unit.song","Unit Song",Collections.emptyList()));if(AutoEngineRegression.scenario.equals("ambiguous"))s.add(new SongCandidate("other.song","Unit Song",Collections.emptyList()));return s;}
 public boolean hasChart(String song,String difficulty){return true;}public Chart load(String s,String d){AutoEngineRegression.loads++;AutoEngineRegression.loadedDifficulty=d;return new Chart();}
}
