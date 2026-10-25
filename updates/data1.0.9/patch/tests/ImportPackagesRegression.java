package io.github.phiscript.input;

import io.github.phiscript.assets.*;
import io.github.phiscript.engine.Chart;
import java.io.*;
import java.util.zip.*;

/** Uses the three original nested ZIPs, including their real metadata. */
public final class ImportPackagesRegression {
    public static void main(String[] args) throws Exception {
        int count=0;
        try(ZipFile outer=new ZipFile("upload/Download.zip")) {
            java.util.Enumeration<? extends ZipEntry> entries=outer.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                if(!entry.getName().endsWith(".zip"))continue;
                try(InputStream input=outer.getInputStream(entry)) {
                    PhiraImportData data=PhiraImportDecoder.INSTANCE.decode(input,entry.getName());
                    Chart chart=Chart.Companion.parse(data.getNormalizedJson());
                    int expected=new int[]{3909,1930,1883}[count];
                    if(chart.getNotes().size()!=expected)throw new AssertionError("import lost notes: "+entry.getName());
                    System.out.println("IMPORTED "+entry.getName()+": title="+data.getTitle()+", level="+data.getLevel()
                        +", notes="+chart.getNotes().size()+", normalizedBytes="+data.getNormalizedJson().getBytes("UTF-8").length);
                    count++;
                }
            }
        }
        if(count!=3)throw new AssertionError("not all three packages imported");
        System.out.println("PASS all 3 original ZIP import pipelines");
    }
}
