package io.github.phiscript.assets;
import java.io.*;import java.nio.file.*;import io.github.phiscript.engine.*;import io.github.phiscript.input.*;
public final class UserChartProbe {
 public static void main(String[] args)throws Exception{
  android.content.Context.root=Files.createTempDirectory("user-chart-probe").toFile();
  PhiraImportData imported=PhiraImportDecoder.INSTANCE.decode(new FileInputStream(args[0]),"Desultory_Signals_AT.pez");
  System.out.println("Decoded: "+imported.getTitle()+" / "+imported.getLevel()+"; JSON chars "+imported.getNormalizedJson().length());
  Chart chart=Chart.Companion.parse(imported.getNormalizedJson());
  System.out.println("Parsed: "+chart.getNotes().size()+" notes; noise "+BlockAreaSet.count(chart));
  GesturePlan.preflight(chart);
  System.out.println("PASS preflight");
 }
}
