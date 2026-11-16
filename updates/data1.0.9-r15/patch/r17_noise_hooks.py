"""Retain PEZ noise sidecars before normalization/persistence, using the decoded R15 ABI."""
from pathlib import Path
import re

def apply_noise(root):
    root=Path(root)
    path=root/'smali_classes6/io/github/phiscript/assets/PhiraImportDecoder.smali'
    text=path.read_text(encoding='utf-8')
    signature='.method private final fromSource([BLjava/util/Map;Ljava/lang/String;)Lio/github/phiscript/assets/PhiraImportData;'
    start=text.index(signature)
    match=re.search(r'    \.locals \d+\n',text[start:]);assert match
    at=start+match.end()
    text=text[:at]+'''    invoke-static/range {p1 .. p1}, Lio/github/phiscript/assets/NoiseImportBridge;->normalizeSource([B)[B
    move-result-object p1
'''+text[at:]
    original='    invoke-direct {v1, v2, v4, v0}, Lio/github/phiscript/assets/PhiraImportDecoder;->fromSource([BLjava/util/Map;Ljava/lang/String;)Lio/github/phiscript/assets/PhiraImportData;'
    assert text.count(original)==1
    text=text.replace(original,'''    invoke-static {v12, v0, v2}, Lio/github/phiscript/assets/NoiseImportBridge;->withPackageAreas(Ljava/util/Map;Ljava/lang/String;[B)[B
    move-result-object v2
'''+original)
    path.write_text(text,encoding='utf-8')
    return {'classes6.dex'}
