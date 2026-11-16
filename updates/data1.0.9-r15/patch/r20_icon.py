"""Append a three-resource launcher icon without rebuilding the original table.

All existing resource IDs, string indices and typed resource chunks are retained.
Only a tiny independent aapt2 fragment is compiled; its own references are mapped
by actual resource names before appending its mipmap chunks to the input APK.
"""
from pathlib import Path
import struct
import subprocess
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
NO_INDEX = 0xffffffff
ICONS = {"ic_launcher": 0x7f0e0000, "ic_launcher_foreground": 0x7f0e0001,
         "ic_launcher_monochrome": 0x7f0e0002}


def u32(data, at=0):
    return struct.unpack_from("<I", data, at)[0]


def chunks(data, start=None):
    if start is None:
        start = struct.unpack_from("<H", data, 2)[0]
    pos = start
    while pos < len(data):
        kind, head, size = struct.unpack_from("<HHI", data, pos)
        assert head >= 8 and size >= head and pos + size <= len(data), (hex(kind), pos, head, size)
        yield pos, kind, bytes(data[pos:pos + size])
        pos += size
    assert pos == len(data)


def length8(data, at):
    value = data[at]
    return (((value & 0x7f) << 8) | data[at+1], at+2) if value & 0x80 else (value, at+1)


def length16(data, at):
    value = struct.unpack_from("<H", data, at)[0]
    return (((value & 0x7fff) << 16) | struct.unpack_from("<H", data, at+2)[0], at+4) if value & 0x8000 else (value, at+2)


def strings(pool):
    assert struct.unpack_from("<H", pool)[0] == 1
    head = struct.unpack_from("<H", pool, 2)[0]
    count, _, flags, begin = struct.unpack_from("<IIII", pool, 8)
    result = []
    for i in range(count):
        at = begin + u32(pool, head + i*4)
        if flags & 0x100:
            _, at = length8(pool, at)
            size, at = length8(pool, at)
            result.append(pool[at:at+size].decode("utf-8"))
        else:
            size, at = length16(pool, at)
            result.append(pool[at:at+size*2].decode("utf-16le"))
    return result


def append_strings(pool, values):
    """Preserve all old strings/style spans byte-for-byte, including indices."""
    old = strings(pool)
    index = {value: i for i, value in enumerate(old)}
    added = []
    for value in values:
        if value not in index:
            index[value] = len(old) + len(added)
            added.append(value)
    if not added:
        return pool, index
    head = struct.unpack_from("<H", pool, 2)[0]
    count, style_count, flags, begin, styles_begin = struct.unpack_from("<IIIII", pool, 8)
    offsets = pool[head:head+count*4]
    style_offsets = pool[head+count*4:head+(count+style_count)*4]
    gap = pool[head+(count+style_count)*4:begin]
    original_data = pool[begin:styles_begin or len(pool)]
    style_data = pool[styles_begin:] if styles_begin else b""
    payload = bytearray(original_data)
    new_offsets = bytearray()
    for value in added:
        new_offsets.extend(struct.pack("<I", len(payload)))
        units = len(value.encode("utf-16le")) // 2
        if flags & 0x100:
            encoded = value.encode("utf-8")
            def encode8(n):
                assert n < 0x8000
                return bytes((n,)) if n < 0x80 else bytes((0x80 | (n >> 8), n & 0xff))
            payload.extend(encode8(units) + encode8(len(encoded)) + encoded + b"\0")
        else:
            assert units < 0x8000
            payload.extend(struct.pack("<H", units) + value.encode("utf-16le") + b"\0\0")
    payload.extend(b"\0" * (-len(payload) % 4))
    new_begin = begin + len(new_offsets)
    header = bytearray(pool[:head])
    struct.pack_into("<I", header, 8, count + len(added))
    struct.pack_into("<I", header, 16, flags & ~1)  # appending need not retain lexicographic order
    struct.pack_into("<II", header, 20, new_begin, new_begin + len(payload) if styles_begin else 0)
    result = header + offsets + new_offsets + style_offsets + gap + payload + style_data
    struct.pack_into("<I", result, 4, len(result))
    assert strings(result) == old + added
    if styles_begin:
        assert bytes(result[u32(result, 24):]) == style_data
    return bytes(result), index


def package_parts(package):
    assert struct.unpack_from("<H", package)[0] == 0x200
    head = struct.unpack_from("<H", package, 2)[0]
    assert head >= 284 and u32(package, 8) == 0x7f
    if head >= 288:
        assert u32(package, 284) == 0, "nonzero type ID offset is unsupported"
    parts = list(chunks(package))
    by_offset = {at: part for at, _, part in parts}
    return head, parts, by_offset[u32(package, 268)], by_offset[u32(package, 276)]


def name_reserved_type(pool):
    old = strings(pool)
    if old[13] == "mipmap":
        return pool
    assert old[13] == "?14" and "mipmap" not in old
    expanded, _ = append_strings(pool, ["mipmap"])
    head, count = struct.unpack_from("<H", expanded, 2)[0], u32(expanded, 8)
    # Repoint only the unused slot to the appended bytes. Drop the extra offset
    # so there is still precisely one name per original type-ID position.
    offset = u32(expanded, head+(count-1)*4)
    result = bytearray(expanded[:head+(count-1)*4] + expanded[head+count*4:])
    struct.pack_into("<I", result, head+13*4, offset)
    struct.pack_into("<I", result, 8, count-1)
    struct.pack_into("<I", result, 20, u32(expanded, 20)-4)
    if u32(expanded, 24):
        struct.pack_into("<I", result, 24, u32(expanded, 24)-4)
    struct.pack_into("<I", result, 4, len(result))
    expected = old[:13]+["mipmap"]+old[14:]
    assert strings(result) == expected
    return bytes(result)


def table_parts(table):
    assert struct.unpack_from("<H", table)[0] == 2 and u32(table, 8) == 1
    parts = list(chunks(table))
    pools = [(at, data) for at, kind, data in parts if kind == 1]
    packages = [(at, data) for at, kind, data in parts if kind == 0x200]
    assert len(pools) == len(packages) == 1
    return parts, pools[0], packages[0]


def entries(chunk):
    """Yield actual entry IDs and offsets, including Android sparse/offset16."""
    assert struct.unpack_from("<H", chunk)[0] == 0x201
    head = struct.unpack_from("<H", chunk, 2)[0]
    flags, count, begin = chunk[9], u32(chunk, 12), u32(chunk, 16)
    if flags & 1:
        for i in range(count):
            entry, offset = struct.unpack_from("<HH", chunk, head+i*4)
            yield entry, begin + offset*4
    elif flags & 2:
        for entry in range(count):
            offset = struct.unpack_from("<H", chunk, head+entry*2)[0]
            if offset != 0xffff:
                yield entry, begin + offset*4
    else:
        for entry in range(count):
            offset = u32(chunk, head+entry*4)
            if offset != NO_INDEX:
                yield entry, begin + offset


def icon_resource_ids(table):
    _, _, (_, package) = table_parts(table)
    _, parts, types, keys = package_parts(package)
    type_names, key_names = strings(types), strings(keys)
    found = {}
    for _, kind, chunk in parts:
        if kind != 0x201 or type_names[chunk[8]-1] != "mipmap":
            continue
        for entry, at in entries(chunk):
            size, flags, key = struct.unpack_from("<HHI", chunk, at)
            assert not flags & (1 | 8) and size >= 8, "expected ordinary simple file resource"
            name = key_names[key]
            resource = 0x7f000000 | chunk[8] << 16 | entry
            assert name not in found or found[name] == resource
            found[name] = resource
    return found


def relocate_xml(xml, mapping):
    data = bytearray(xml)
    assert struct.unpack_from("<H", data)[0] == 3
    for pos, kind, chunk in chunks(data):
        if kind == 0x180:
            head = struct.unpack_from("<H", chunk, 2)[0]
            for offset in range(head, len(chunk), 4):
                old = u32(chunk, offset)
                if old in mapping:
                    struct.pack_into("<I", data, pos+offset, mapping[old])
        elif kind == 0x102:
            begin, width, count = struct.unpack_from("<HHH", chunk, 24)
            assert width >= 20
            for i in range(count):
                at = pos + 16 + begin + i*width + 12
                if data[at+3] in (1, 2, 7, 8) and u32(data, at+4) in mapping:
                    struct.pack_into("<I", data, at+4, mapping[u32(data, at+4)])
        elif kind == 0x104 and chunk[23] in (1, 2, 7, 8) and u32(chunk, 24) in mapping:
            struct.pack_into("<I", data, pos+24, mapping[u32(chunk, 24)])
    return bytes(data)


def append_table(original, fragment):
    original_parts, (global_at, global_pool), (package_at, package) = table_parts(original)
    _, (_, fragment_pool), (_, fragment_package) = table_parts(fragment)
    head, parts, type_pool, key_pool = package_parts(package)
    _, fragment_parts, fragment_types, fragment_keys = package_parts(fragment_package)
    assert all(chunk[8] != 14 for _, kind, chunk in parts if kind in (0x201, 0x202)), "mipmap is already populated"
    merged_types = name_reserved_type(type_pool)
    own = icon_resource_ids(fragment)
    assert set(own) == set(ICONS), own
    mapping = {old: ICONS[name] for name, old in own.items()}
    # Entry IDs are the fragment's actual lexicographic aapt2 assignment. Type
    # relocation is independent of the mini APK's type ID.
    assert all((own[name] & 0xffff) == (final & 0xffff) for name, final in ICONS.items()), own
    merged_global, global_index = append_strings(global_pool, strings(fragment_pool))
    merged_keys, key_index = append_strings(key_pool, strings(fragment_keys))
    mini_keys, mini_global, mini_types = strings(fragment_keys), strings(fragment_pool), strings(fragment_types)
    additions = []
    for _, kind, chunk in fragment_parts:
        if kind not in (0x201, 0x202):
            continue
        assert mini_types[chunk[8]-1] == "mipmap"
        relocated = bytearray(chunk)
        relocated[8] = 14
        if kind == 0x202:
            assert u32(chunk, 12) == 3
        else:
            for _, at in entries(chunk):
                size, flags, key = struct.unpack_from("<HHI", chunk, at)
                assert size >= 8 and not flags & (1 | 8)
                struct.pack_into("<I", relocated, at+4, key_index[mini_keys[key]])
                value = at + size
                assert struct.unpack_from("<H", chunk, value)[0] == 8
                if chunk[value+3] == 3:
                    struct.pack_into("<I", relocated, value+4, global_index[mini_global[u32(chunk, value+4)]])
                elif u32(chunk, value+4) in mapping:
                    struct.pack_into("<I", relocated, value+4, mapping[u32(chunk, value+4)])
        additions.append(bytes(relocated))
    assert additions
    package_header = bytearray(package[:head])
    output = bytearray(package_header)
    old_type, old_keys = u32(package, 268), u32(package, 276)
    for at, _, chunk in parts:
        if at == old_type:
            struct.pack_into("<I", output, 268, len(output))
            output.extend(merged_types)
        elif at == old_keys:
            struct.pack_into("<I", output, 276, len(output))
            output.extend(merged_keys)
        else:
            output.extend(chunk)
    output.extend(b"".join(additions))
    struct.pack_into("<I", output, 4, len(output))
    result = bytearray(original[:struct.unpack_from("<H", original, 2)[0]])
    for at, _, chunk in original_parts:
        result.extend(merged_global if at == global_at else output if at == package_at else chunk)
    struct.pack_into("<I", result, 4, len(result))
    # Strong invariant: every pre-existing resource type/configuration chunk is
    # still exactly the original bytes; no old ID/value/reference was rewritten.
    _, updated_parts, _, updated_keys = package_parts(bytes(output))
    old_chunks = [chunk for at, _, chunk in parts if at not in (old_type, old_keys)]
    assert [chunk for at, _, chunk in updated_parts[:len(parts)] if at not in (u32(output, 268), u32(output, 276))] == old_chunks
    assert strings(merged_global)[:len(strings(global_pool))] == strings(global_pool)
    assert strings(updated_keys)[:len(strings(key_pool))] == strings(key_pool)
    assert icon_resource_ids(result) == ICONS
    return bytes(result), mapping


def manifest_icon(manifest, icon=ICONS["ic_launcher"]):
    parts = list(chunks(manifest))
    pools = [(at, chunk) for at, kind, chunk in parts if kind == 1]
    resource_maps = [(at, chunk) for at, kind, chunk in parts if kind == 0x180]
    assert len(pools) == len(resource_maps) == 1
    pool_at, pool = pools[0]
    resource_at, resource_map = resource_maps[0]
    merged_pool, indices = append_strings(pool, ["icon", "roundIcon"])
    android = indices["http://schemas.android.com/apk/res/android"]
    resource_head = struct.unpack_from("<H", resource_map, 2)[0]
    resources = [u32(resource_map, at) for at in range(resource_head, len(resource_map), 4)]
    resources.extend([0] * (len(strings(merged_pool)) - len(resources)))
    for name, resource in (("icon", 0x01010002), ("roundIcon", 0x0101052c)):
        assert resources[indices[name]] in (0, resource)
        resources[indices[name]] = resource
    merged_map = bytearray(resource_map[:resource_head])
    merged_map.extend(struct.pack("<"+"I"*len(resources), *resources))
    struct.pack_into("<I", merged_map, 4, len(merged_map))
    updated = 0
    result = bytearray(manifest[:struct.unpack_from("<H", manifest, 2)[0]])
    names = strings(merged_pool)
    for at, kind, chunk in parts:
        if at == pool_at:
            result.extend(merged_pool)
        elif at == resource_at:
            result.extend(merged_map)
        elif kind == 0x102 and names[u32(chunk, 20)] == "application":
            begin, width, count, id_index, class_index, style_index = struct.unpack_from("<HHHHHH", chunk, 24)
            assert width == 20
            offset = 16 + begin
            attributes = [(i+1, chunk[offset+i*width:offset+(i+1)*width]) for i in range(count)]
            assert all(u32(attr, 4) not in (indices["icon"], indices["roundIcon"]) for _, attr in attributes), "application icon is already set"
            for name in ("icon", "roundIcon"):
                attributes.append((0, struct.pack("<IIIHBBI", android, indices[name], NO_INDEX, 8, 0, 1, icon)))
            attributes.sort(key=lambda item: resources[u32(item[1], 4)])
            new_indices = {old: i+1 for i, (old, _) in enumerate(attributes) if old}
            patched = bytearray(chunk[:offset])
            struct.pack_into("<HHHH", patched, 28, count+2, new_indices.get(id_index, 0), new_indices.get(class_index, 0), new_indices.get(style_index, 0))
            patched.extend(b"".join(attr for _, attr in attributes))
            patched.extend(chunk[offset+count*width:])
            struct.pack_into("<I", patched, 4, len(patched))
            result.extend(patched)
            updated += 1
        else:
            result.extend(chunk)
    assert updated == 1
    struct.pack_into("<I", result, 4, len(result))
    return bytes(result)


def build(base_apk, work, res_dir=None):
    """Return replacements plus new res/* entries for the root APK packager."""
    base_apk, work = Path(base_apk), Path(work)
    res_dir = Path(res_dir) if res_dir is not None else ROOT/"patch/icon/res"
    work.mkdir(parents=True, exist_ok=True)
    aapt = ROOT/"tools/build-tools/android-15"/("aapt2.exe" if __import__("os").name == "nt" else "aapt2")
    manifest = work/"AndroidManifest.xml"
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="io.github.phiscript.icon.fragment"><application android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher" /></manifest>', encoding="utf-8")
    compiled, mini = work/"compiled.zip", work/"icon-fragment.apk"
    subprocess.run([str(aapt), "compile", "--dir", str(res_dir), "-o", str(compiled)], check=True, cwd=ROOT)
    subprocess.run([str(aapt), "link", "-I", str(ROOT/"tools/platform/android-35/android.jar"), "--manifest", str(manifest), "--min-sdk-version", "26", "--target-sdk-version", "35", "--no-resource-deduping", "-o", str(mini), str(compiled)], check=True, cwd=ROOT)
    with ZipFile(base_apk) as original, ZipFile(mini) as fragment:
        table, mapping = append_table(original.read("resources.arsc"), fragment.read("resources.arsc"))
        replacements = {"resources.arsc": table, "AndroidManifest.xml": manifest_icon(original.read("AndroidManifest.xml"))}
        for name in fragment.namelist():
            if not name.startswith("res/"):
                continue
            assert name not in original.namelist(), "icon resource path would overwrite an original resource: "+name
            data = fragment.read(name)
            replacements[name] = relocate_xml(data, mapping) if name.endswith(".xml") else data
        paths = set(strings(next(chunk for _, kind, chunk in chunks(table) if kind == 1)))
        assert all(name in paths for name in replacements if name.startswith("res/"))
        assert sum(name.startswith("res/") for name in replacements) == 5, sorted(replacements)
        return replacements
