"""Check additive icon resources and original resource-ID/content stability."""
from pathlib import Path
import re
import struct
import subprocess
import sys
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import r20_icon as icon


def make_pool(values, utf8=False, styled=False):
    data, offsets = bytearray(), []
    for value in values:
        offsets.append(len(data))
        encoded = value.encode("utf-8" if utf8 else "utf-16le")
        units = len(value.encode("utf-16le"))//2
        def size8(n):
            return bytes((n,)) if n < 128 else bytes((0x80 | n >> 8, n & 255))
        data.extend(size8(units)+size8(len(encoded))+encoded+b"\0" if utf8 else struct.pack("<H", units)+encoded+b"\0\0")
    data.extend(b"\0"*(-len(data)%4))
    styles = struct.pack("<IIIIII", 1, 0, 1, 0xffffffff, 0xffffffff, 0xffffffff) if styled else b""
    begin = 28+len(values)*4+(4 if styled else 0)
    header = struct.pack("<HHIIIIII", 1, 28, begin+len(data)+len(styles), len(values), int(styled), 0x101 if utf8 else 1, begin, begin+len(data) if styled else 0)
    return header+struct.pack("<"+"I"*len(offsets), *offsets)+(struct.pack("<I", 0) if styled else b"")+data+styles


def unit_checks():
    for utf8 in (False, True):
        for styled in (False, True):
            old = make_pool(["alpha", "é𝄞"], utf8, styled)
            values = ["新增😀", "x"*200, "alpha"]
            result, indices = icon.append_strings(old, values)
            assert icon.strings(result) == ["alpha", "é𝄞", "新增😀", "x"*200]
            assert indices["alpha"] == 0 and indices["新增😀"] == 2
            assert not icon.u32(result, 16)&1
            if styled:
                assert result[icon.u32(result, 24):] == old[icon.u32(old, 24):]
                assert result[28+4*len(icon.strings(result)):28+4*len(icon.strings(result))+4] == struct.pack("<I", 0)
            list(icon.chunks(struct.pack("<HHI", 3, 8, len(result)+8)+result))


def check(base, patched):
    unit_checks()
    base, patched = Path(base), Path(patched)
    aapt = icon.ROOT/"tools/build-tools/android-15"/("aapt2.exe" if __import__("os").name == "nt" else "aapt2")
    def dump(*args):
        return subprocess.run([str(aapt), "dump", *map(str,args)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding="utf-8").stdout
    with ZipFile(base) as before, ZipFile(patched) as after:
        old_names, new_names = set(before.namelist()), set(after.namelist())
        for name in old_names:
            if name.startswith("res/"):
                assert after.read(name) == before.read(name), "original resource file changed: "+name
        additions = {name for name in new_names-old_names if name.startswith("res/")}
        assert len(additions) == 5, additions
        assert icon.icon_resource_ids(after.read("resources.arsc")) == icon.ICONS
        _, (_, old_global), (_, old_package) = icon.table_parts(before.read("resources.arsc"))
        _, (_, new_global), (_, new_package) = icon.table_parts(after.read("resources.arsc"))
        _, old_parts, old_types, old_keys = icon.package_parts(old_package)
        _, new_parts, new_types, new_keys = icon.package_parts(new_package)
        expected_types = icon.strings(old_types);expected_types[13] = "mipmap"
        assert icon.strings(new_types) == expected_types
        assert icon.strings(new_global)[:len(icon.strings(old_global))] == icon.strings(old_global)
        assert icon.strings(new_keys)[:len(icon.strings(old_keys))] == icon.strings(old_keys)
        old_chunks = [data for _, kind, data in old_parts if kind != 1]
        preserved = [data for _, kind, data in new_parts if kind != 1 and not(kind in (0x201, 0x202) and data[8] == 14)]
        assert old_chunks == preserved, "existing resource type/configuration bytes changed"
        pngs = [name for name in additions if name.endswith(".png")]
        assert len(pngs) == 1
        png = after.read(pngs[0])
        assert png[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack_from(">II", png, 16) == (512, 512)
        adaptive = [name for name in additions if "v26" in name or "v33" in name]
        assert len(adaptive) == 2
        for name in adaptive:
            tree = dump("xmltree", patched, "--file", name)
            assert "adaptive-icon" in tree and "@0x7f0e0001" in tree, tree
            assert "E: shape" in tree and 'color(0x010101a5)=#ff0b111b' in tree, tree
            if "v33" in name:
                assert "monochrome" in tree and "@0x7f0e0002" in tree, tree
    pattern = r"^\s*resource (0x[0-9a-f]+) (\S+)"
    old_ids = dict(re.findall(pattern, dump("resources", base), re.MULTILINE))
    new_ids = dict(re.findall(pattern, dump("resources", patched), re.MULTILINE))
    assert all(new_ids.get(resource) == name for resource, name in old_ids.items()), "existing aapt2 resource name/ID changed"
    assert {resource:name for resource,name in new_ids.items() if resource not in old_ids} == {f"0x{resource:08x}":"mipmap/"+name for name,resource in icon.ICONS.items()}
    manifest = dump("xmltree", patched, "--file", "AndroidManifest.xml")
    assert ":icon(0x01010002)=@0x7f0e0000" in manifest
    assert ":roundIcon(0x0101052c)=@0x7f0e0000" in manifest
    badging = dump("badging", patched)
    assert "application: label='Phigros Script' icon='" in badging and "icon=''" not in next(line for line in badging.splitlines() if line.startswith("application:")), badging
    print(f"PASS additive launcher icon: {len(old_ids)} existing resource IDs and all original res/* bytes preserved; three new mipmap IDs; PNG512, v26/v33 adaptive/monochrome references, manifest icon/roundIcon and UTF8/UTF16 styled pools.")


if __name__ == "__main__":
    check(*sys.argv[1:])
