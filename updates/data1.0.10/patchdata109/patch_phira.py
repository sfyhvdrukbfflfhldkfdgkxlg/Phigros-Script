"""Enable the official Phira Pro Android package while retaining exact input focus checks."""
from pathlib import Path
import re


def patch(app):
    app = Path(app)
    engine = next(app.glob('smali*/io/github/phiscript/capture/PhiraSessionEngine.smali'))
    source = engine.read_text()
    method = '''.method private final isForeground()Z
    .locals 1

    invoke-static {}, Lio/github/phiscript/capture/PhiraForeground;->isForeground()Z

    move-result v0

    return v0
.end method'''
    source, count = re.subn(r'\.method private final isForeground\(\)Z\n.*?\.end method', lambda _: method, source, flags=re.S)
    assert count == 1, 'Missing Phira session foreground method'
    begin = '    invoke-static {}, Lio/github/phiscript/capture/PhiraForeground;->beginSession()V'
    if begin not in source:
        source, count = re.subn(r'(\.method public final run\(\)V\n    \.locals \d+\n)', lambda match: match.group()+'\n'+begin+'\n', source)
        assert count == 1, 'Missing Phira session lifecycle entry'
    engine.write_text(source)

    service = next(app.glob('smali*/io/github/phiscript/input/TouchService.smali'))
    source = service.read_text()
    match = re.search(r'\.method public final selectGame\(Ljava/lang/String;\)V\n.*?\.end method', source, re.S)
    assert match, 'Missing TouchService target selector'
    old = match.group()
    updated = old
    if '"org.flos.phirapro"' not in old:
        assert old.count('const/4 v0, 0x2') == 1
        updated = old.replace('const/4 v0, 0x2', 'const/4 v0, 0x3', 1)
        marker = '    invoke-static {v0}, Lkotlin/collections/SetsKt;->setOf([Ljava/lang/Object;)Ljava/util/Set;'
        assert updated.count(marker) == 1
        updated = updated.replace(marker, '''    const/4 v1, 0x2

    const-string v2, "org.flos.phirapro"

    aput-object v2, v0, v1

''' + marker, 1)
    source = source[:match.start()] + updated + source[match.end():]
    service.write_text(source)

    manifest = app / 'AndroidManifest.xml'
    source = manifest.read_text()
    if 'android:name="org.flos.phirapro"' not in source:
        marker = '<package android:name="org.flos.phira"/>'
        assert source.count(marker) == 1, 'Missing Phira package visibility entry'
        source = source.replace(marker, marker + '\n        <package android:name="org.flos.phirapro"/>', 1)
        manifest.write_text(source)


if __name__ == '__main__':
    import sys
    patch(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'analysis/data108')
