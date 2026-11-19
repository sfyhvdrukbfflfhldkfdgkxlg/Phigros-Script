"""Localize R15 app-owned literals before composing messages with chart names or OCR output."""
import json
import re
from pathlib import Path

PACKAGE = Path("io/github/phiscript")
SOURCE = Path(__file__).parent / "src" / PACKAGE / "i18n/NativeMessages.java"
FAMILIES = set("""
AccessibilityActivation AccessibilityStatus AccessibilitySystemCollector ApkAccess ApkFiles ApkManifestInfo
ApkReadService Chart ChartKt ChartLibrary FirstNoteCatalog ImportedApkArchive ImportedApkArchiveKt
ImportedApks InstalledApks InstalledApksKt NativeAnim NativeTween PhiraChartParser PhiraExport
PhiraImportDecoder PhiraLibrary PhiraLibraryKt PhiraNativeChart PhiraNativeChartKt PhiraPbcDecoder
PhiraPecDecoder PhiraSelectionPolicy PhiraSessionEngine RpeNativeNormalizer SelfAccessibilityEnabler
SelfAccessibilityToggle SongIdentifier TouchEvent TouchService TouchTimelineKt UpdateCatalog
UpdateChecker Viewport VisualNoteTracker VisualTouchState
""".split())
# Kept in their original form for source fingerprints, chart IDs, and language changes.
STORED = {
    "ChartLibrary": {"已安装 APK", "导入 APK"},
    "PhiraLibrary": {"所选文档"},
    "PhiraImportDecoder": {"未命名谱面", "未知难度"},
}
STRING = r'"(?:[^"\\]|\\.)*"'
CONSTANT = re.compile(r'^    const-string(?:/jumbo)? ([vp]\d+), (' + STRING + r')$', re.MULTILINE)
TRANSLATE = "Lio/github/phiscript/i18n/NativeMessages;->text(Ljava/lang/String;)Ljava/lang/String;"


def apply(root):
    messages = {json.loads(literal) for literal in re.findall(
        r'^\s*entries\.put\((' + STRING + r'), ', SOURCE.read_text(encoding="utf-8"), re.MULTILINE)}
    covered = set()
    for package in sorted(Path(root).glob("smali_classes[3-8]/" + PACKAGE.as_posix())):
        for path in package.rglob("*.smali"):
            family = path.stem.split("$", 1)[0]
            if family not in FAMILIES:
                continue
            source = path.read_text(encoding="utf-8")

            def localize(match):
                register, literal = match.groups()
                value = json.loads(literal.replace(r"\'", "'"))
                if value not in messages:
                    return match[0]
                covered.add(value)
                if family in {"AccessibilityActivation", "ApkAccess"} or value in STORED.get(family, ()):
                    return match[0]
                return (match[0] + "\n    invoke-static/range {" + register + " .. " + register
                        + "}, " + TRANSLATE + "\n    move-result-object " + register)

            localized = CONSTANT.sub(localize, source)
            if localized != source:
                path.write_text(localized, encoding="utf-8")
    missing = messages - covered
    if missing:
        raise ValueError("Native messages missing from the R15 classes: " + repr(sorted(missing)))
