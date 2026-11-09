"""Retain chart-associated native noise sidecars in the existing bounded importer."""
from pathlib import Path
import sys

def apply(root: Path):
    path=root/'smali_classes6/io/github/phiscript/assets/PhiraImportDecoder.smali'
    text=path.read_text()
    marker='Lio/github/phiscript/assets/PhiraNoiseAssets;->withSidecar'
    if marker not in text:
        old='''    invoke-direct {v1, v2, v4, v0}, Lio/github/phiscript/assets/PhiraImportDecoder;->fromSource([BLjava/util/Map;Ljava/lang/String;)Lio/github/phiscript/assets/PhiraImportData;

    move-result-object v2

    return-object v2'''
        new='''    invoke-direct {v1, v2, v4, v0}, Lio/github/phiscript/assets/PhiraImportDecoder;->fromSource([BLjava/util/Map;Ljava/lang/String;)Lio/github/phiscript/assets/PhiraImportData;

    move-result-object v2

    invoke-static {v2, v12, v0}, Lio/github/phiscript/assets/PhiraNoiseAssets;->withSidecar(Lio/github/phiscript/assets/PhiraImportData;Ljava/util/Map;Ljava/lang/String;)Lio/github/phiscript/assets/PhiraImportData;

    move-result-object v2

    return-object v2'''
        assert text.count(old)==1,'Unexpected Phira archive decoder layout'
        text=text.replace(old,new)
    guard='Lio/github/phiscript/assets/PhiraNoiseAssets;->isSidecarName'
    if guard not in text:
        old='''    .param p2, "name"    # Ljava/lang/String;

    .line 84'''
        new='''    .param p2, "name"    # Ljava/lang/String;

    invoke-static/range {p2 .. p2}, Lio/github/phiscript/assets/PhiraNoiseAssets;->isSidecarName(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :noise_chart_candidate

    const/4 v0, 0x0

    return v0

    :noise_chart_candidate
    .line 84'''
        assert text.count(old)==1,'Unexpected Phira chart classifier layout'
        text=text.replace(old,new)
    path.write_text(text)
    print('Patched Phira archive sidecar association and chart classifier')


if __name__=='__main__':
    apply(Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[1]/'analysis/data108')
