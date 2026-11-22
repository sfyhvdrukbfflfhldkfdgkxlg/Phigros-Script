"""Add playback-capture permission without requiring a physical microphone."""
import struct
import json
import re
from r20_icon import NO_INDEX, append_strings, chunks, u32


def add_audio_permissions(xml):
    parts = list(chunks(xml))
    pool = next(chunk for _, kind, chunk in parts if kind == 1)
    pool, index = append_strings(pool, ["android.permission.RECORD_AUDIO", "uses-feature",
                                        "android.hardware.microphone", "required"])
    namespace = index["http://schemas.android.com/apk/res/android"]
    def attribute(name, raw, kind, value):
        return struct.pack("<IIIHBBI", namespace, index[name], raw, 8, 0, kind, value)

    def element(name, attrs, line):
        tag = index[name]
        start = struct.pack("<HHIIIIIHHHHHH", 0x102, 16, 36 + 20*len(attrs), line,
                            NO_INDEX, NO_INDEX, tag, 20, 20, len(attrs), 0, 0, 0)
        return start + b"".join(attrs) + struct.pack("<HHIIIII", 0x103, 16, 24, line,
                                                     NO_INDEX, NO_INDEX, tag)

    result = bytearray(xml[:struct.unpack_from("<H", xml, 2)[0]])
    inserted = False
    for _, kind, chunk in parts:
        if kind == 1:
            chunk = pool
        elif kind == 0x180:
            resource_map = bytearray(chunk)
            head = struct.unpack_from("<H", chunk, 2)[0]
            offset = head + 4 * index["required"]
            resource_map.extend(bytes(max(0, offset + 4 - len(resource_map))))
            assert u32(resource_map, offset) in (0, 0x0101028e)
            struct.pack_into("<I", resource_map, offset, 0x0101028e)
            struct.pack_into("<I", resource_map, 4, len(resource_map))
            chunk = resource_map
        elif kind == 0x102 and u32(chunk, 20) == index["application"]:
            assert not inserted
            audio = index["android.permission.RECORD_AUDIO"]
            mic = index["android.hardware.microphone"]
            line = u32(chunk, 8)
            result.extend(element("uses-permission", [attribute("name", audio, 3, audio)], line))
            result.extend(element("uses-feature", [attribute("name", mic, 3, mic),
                                                    attribute("required", NO_INDEX, 0x12, 0)], line))
            inserted = True
        result.extend(chunk)
    assert inserted
    struct.pack_into("<I", result, 4, len(result))
    return bytes(result)


def apply_activity(decoded):
    path = decoded/"smali_classes4/io/github/phiscript/MainActivity.smali"
    text = path.read_text(encoding="utf-8")
    signature = "onRequestPermissionsResult(I[Ljava/lang/String;[I)V"
    assert signature not in text and ".super Landroidx/activity/ComponentActivity;" in text
    path.write_text(text + """
.method public onRequestPermissionsResult(I[Ljava/lang/String;[I)V
    .locals 1
    invoke-static {p0, p1}, Lio/github/phiscript/record/RecordingSettings;->onPermissionResult(Landroid/app/Activity;I)Z
    move-result v0
    if-nez v0, :audio_handled
    invoke-super {p0, p1, p2, p3}, Landroidx/activity/ComponentActivity;->onRequestPermissionsResult(I[Ljava/lang/String;[I)V
    :audio_handled
    return-void
.end method
""", encoding="utf-8")

    # The original projection confirmation and service status are native DEX
    # call sites. Keep their English dictionary keys in step with the new UI.
    replacements = {
        "打谱录屏（无声）": "打谱录屏",
        "Playback recording (silent)": "Playback recording",
        "可在首页关闭自动录屏。": "可在演奏页的录屏设置中选择画质和游戏声音。",
        "Automatic recording can be turned off on the home screen.":
            "Choose video quality and game audio in Recording settings on the Performance page.",
        "保存无声视频": "保存视频",
        "saves silent video": "saves video",
    }
    paths = ["smali_classes5/io/github/phiscript/capture/CaptureService.smali",
             "smali_classes8/io/github/phiscript/record/RecordingConsentActivity.smali",
             "smali_classes8/io/github/phiscript/i18n/EnglishCatalog.smali"]
    literal = re.compile(r'(^    const-string(?:/jumbo)? [vp]\d+, )("(?:[^"\\]|\\.)*")$', re.M)
    def replace_copy(match):
        value = json.loads(match[2].replace(r"\'", "'"))
        updated = value
        for old, new in replacements.items():
            updated = updated.replace(old, new)
        return match[0] if value == updated else match[1] + json.dumps(updated, ensure_ascii=True)
    for name in paths:
        file = decoded/name
        old = file.read_text(encoding="utf-8")
        new = literal.sub(replace_copy, old)
        assert new != old, f"Expected recording text in {name}"
        file.write_text(new, encoding="utf-8")
