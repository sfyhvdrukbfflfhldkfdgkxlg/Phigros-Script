from pathlib import Path
import json,re
base=Path(__file__).resolve().parent;path=base/'translations/en.json'
def unescape(s):return re.sub(r'\\([nrt"\\])',lambda m:{'n':'\n','r':'\r','t':'\t','"':'"','\\':'\\'}[m[1]],s)
data={}
for k,v in json.loads(path.read_text()).items():
 k=unescape(k);v=unescape(v)
 if k and not k[0].isspace():v=v.lstrip()
 data[k]=v
path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
lines=['package io.github.phiscript.i18n;','import java.util.LinkedHashMap;import java.util.Map;','public final class EnglishCatalog {private EnglishCatalog(){} public static Map<String,String> entries(){Map<String,String> m=new LinkedHashMap<>();']
for k,v in sorted(data.items()):lines.append('m.put('+json.dumps(k,ensure_ascii=True)+','+json.dumps(v,ensure_ascii=True)+');')
lines.append('return java.util.Collections.unmodifiableMap(m);}}');(base/'src/io/github/phiscript/i18n/EnglishCatalog.java').write_text('\n'.join(lines)+'\n')
print('Catalog entries',len(data))
