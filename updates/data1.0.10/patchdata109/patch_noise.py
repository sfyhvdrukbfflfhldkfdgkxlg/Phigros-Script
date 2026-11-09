"""Attach imported noise metadata at real native/PGR parser return points."""
from pathlib import Path


def apply(app: Path):
    base = app / 'smali_classes7/io/github/phiscript/engine'
    native = base / 'PhiraNativeChart$Companion.smali'
    text = native.read_text()
    marker = 'NoiseGate;->attach('
    if marker not in text:
        old = '    move-result-object v3\n\n    return-object v3'
        assert text.count(old) == 1, 'Native chart return signature changed'
        new = ('    move-result-object v3\n\n'
               '    move-object/from16 v0, p1\n\n'
               '    invoke-static {v3, v0}, Lio/github/phiscript/input/NoiseGate;->attach(Lio/github/phiscript/engine/Chart;Lorg/json/JSONObject;)Lio/github/phiscript/engine/Chart;\n\n'
               '    move-result-object v3\n\n    return-object v3')
        native.write_text(text.replace(old, new))
    official = base / 'Chart$Companion.smali'
    text = official.read_text()
    if marker not in text:
        constructor = '    invoke-direct/range {v9 .. v17}, Lio/github/phiscript/engine/Chart;-><init>(Ljava/util/List;DLjava/util/List;Lkotlin/jvm/functions/Function3;Lkotlin/jvm/functions/Function3;ILkotlin/jvm/internal/DefaultConstructorMarker;)V\n\n    return-object v0'
        assert text.count(constructor) == 1, 'PGR chart return signature changed'
        replacement = constructor.replace('    return-object v0',
            '    move-object/from16 v1, v41\n\n'
            '    invoke-static {v0, v1}, Lio/github/phiscript/input/NoiseGate;->attach(Lio/github/phiscript/engine/Chart;Lorg/json/JSONObject;)Lio/github/phiscript/engine/Chart;\n\n'
            '    move-result-object v0\n\n    return-object v0')
        official.write_text(text.replace(constructor, replacement))
    print('Noise metadata attached at native and PGR parser returns')
