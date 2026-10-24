from pathlib import Path
import re
p=Path('analysis/data107');f=p/'apktool.yml';s=f.read_text()
assert "versionCode: 23" in s or "versionCode: '23'" in s
s=s.replace("versionCode: 23","versionCode: 24").replace("versionCode: '23'","versionCode: '24'").replace("data1.0.7","data1.0.8");f.write_text(s)
print('data1.0.8 version metadata applied; native hooks retained')
chart=p/'smali_classes6/io/github/phiscript/assets/ChartLibrary$Companion.smali'
s=chart.read_text();method=re.search(r'(?ms)^\.method private final scanSource.*?^\.end method',s).group()
assert 'ChartTimingIndex;' not in method
assert re.findall(r'return-object (\w+)',method)==['v14']
replacement=method.replace('    return-object v14','    move-object/from16 v0, p1\n\n    invoke-static {v0, v14}, Lio/github/phiscript/assets/ChartTimingIndex;->importLibrary(Landroid/content/Context;Lio/github/phiscript/assets/ChartLibrary;)V\n\n    return-object v14')
chart.write_text(s.replace(method,replacement))
print('Shared native Phigros import now generates per-note timing records before returning the library.')
parser=p/'smali_classes7/io/github/phiscript/engine/ChartKt.smali'
s=parser.read_text();method=re.search(r'(?ms)^\.method private static final readEvents.*?^\.end method',s).group()
assert method.count('    if-ltz v13, :cond_2')==1 and ':phi_skip_backwards' not in method
replacement=method.replace('    if-ltz v13, :cond_2','    if-ltz v13, :phi_skip_backwards')
marker='    invoke-interface {v5, v13}, Ljava/util/Collection;->add(Ljava/lang/Object;)Z\n\n    const/4 v4, 0x0'
assert replacement.count(marker)==1
replacement=replacement.replace(marker,'    invoke-interface {v5, v13}, Ljava/util/Collection;->add(Ljava/lang/Object;)Z\n\n    :phi_skip_backwards\n    const/4 v4, 0x0')
parser.write_text(s.replace(method,replacement))
print('Native PGR parser ignores backwards judge-line events found in real official charts, retaining all note timings.')
companion=p/'smali_classes7/io/github/phiscript/engine/Chart$Companion.smali'
s=companion.read_text();marker='    invoke-direct {v1, v0}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V'
assert s.count(marker)==1 and 'OfficialChartCompatibility;' not in s
companion.write_text(s.replace(marker,marker+'\n\n    invoke-static {v1}, Lio/github/phiscript/assets/OfficialChartCompatibility;->upgrade(Lorg/json/JSONObject;)V'))
print('Native official PGR v1 move coordinates now normalize to v3 without altering note times.')
