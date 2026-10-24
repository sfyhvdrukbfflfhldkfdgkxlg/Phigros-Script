"""Reconstruct exact preverified release bytes; never re-sign on the runner."""
from pathlib import Path
import hashlib,json,sys,zipfile

def digest(data):return hashlib.sha256(data).hexdigest()
def restore(base_path,parts_dir,out_dir):
    parts_dir=Path(parts_dir);out_dir=Path(out_dir);out_dir.mkdir(parents=True,exist_ok=True)
    manifest=json.loads((parts_dir/'manifest.json').read_text())
    base=Path(base_path).read_bytes()
    if digest(base)!=manifest['base_sha256']:raise ValueError('Base APK checksum mismatch')
    assembled={}
    for kind,group in manifest['parts'].items():
        data=bytearray()
        for part in group['parts']:
            name=part['name']
            if '/' in name or '\\' in name:raise ValueError('Invalid part name')
            block=(parts_dir/name).read_bytes()
            if len(block)!=part['size'] or digest(block)!=part['sha256']:raise ValueError('Part checksum mismatch: '+name)
            data.extend(block)
        if len(data)!=group['size'] or digest(data)!=group['sha256']:raise ValueError('Assembled checksum mismatch')
        assembled[kind]=bytes(data)
    import io
    with zipfile.ZipFile(io.BytesIO(assembled['apk-patch'])) as patch:
        if set(patch.namelist())!={'recipe.json','literal.bin'}:raise ValueError('Unexpected patch entries')
        recipe=json.loads(patch.read('recipe.json'));literal=patch.read('literal.bin')
    if recipe['base_sha256']!=digest(base):raise ValueError('Recipe baseline mismatch')
    target=bytearray()
    for kind,offset,length in recipe['ops']:
        if kind not in ('b','l') or not isinstance(offset,int) or not isinstance(length,int) or offset<0 or length<0:raise ValueError('Invalid operation')
        source=base if kind=='b' else literal
        if offset+length>len(source) or len(target)+length>recipe['target_size']:raise ValueError('Operation outside bounds')
        target.extend(source[offset:offset+length])
    if len(target)!=recipe['target_size'] or digest(target)!=recipe['target_sha256']:raise ValueError('Restored APK mismatch')
    apk='PhigrosScript_data1.0.8.apk';source='PhigrosScript_data1.0.8_Source.zip'
    for name,data in [(apk,target),(source,assembled['source'])]:
        expected=manifest['assets'][name]
        if len(data)!=expected['size'] or digest(data)!=expected['sha256']:raise ValueError('Release asset mismatch')
        (out_dir/name).write_bytes(data)
    sums=''.join(manifest['assets'][name]['sha256']+'  '+name+'\n' for name in (apk,source)).encode()
    expected=manifest['assets']['SHA256SUMS.txt']
    if digest(sums)!=expected['sha256'] or len(sums)!=expected['size']:raise ValueError('Checksum manifest mismatch')
    (out_dir/'SHA256SUMS.txt').write_bytes(sums)
    print('Restored exact signed data1.0.8 APK and source; all SHA256 and size checks passed.')

if __name__=='__main__':restore(*sys.argv[1:])
