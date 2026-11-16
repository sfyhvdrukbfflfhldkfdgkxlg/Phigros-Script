"""Publish the signed data1.0.9-r15 APK and source; never overwrite another release."""
import json,os,subprocess,sys
from pathlib import Path

REPO='sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script';TAG='data1.0.9-r15'
def gh(*args,check=True,input=None):
    r=subprocess.run(['gh',*args],text=True,capture_output=True,input=input)
    if check and r.returncode:raise RuntimeError('GitHub operation failed: '+r.stderr[:700])
    return r
def api(path):return json.loads(gh('api','repos/'+REPO+'/'+path).stdout)
def find_release():
    # Drafts have no published tag ref and GET /releases/tags/<tag> returns 404.
    # Enumerate authenticated releases and then address the unique draft by ID.
    pages=json.loads(gh('api','--paginate','--slurp','repos/'+REPO+'/releases?per_page=100').stdout)
    matches=[r for page in pages for r in page if r['tag_name']==TAG]
    if len(matches)>1:raise RuntimeError('Multiple releases share the tag; refusing to guess')
    return matches[0] if matches else None
def publish(assets_dir,manifest_path,notes_path):
    if os.environ.get('GITHUB_REPOSITORY')!=REPO:raise RuntimeError('Wrong repository')
    manifest=json.loads(Path(manifest_path).read_text());commit=manifest.get('source_commit',os.environ['GITHUB_SHA']);root=Path(assets_dir);expected=manifest['assets']
    if set(expected)!={'PhigrosScript_data1.0.9-r15.apk','PhigrosScript_patch.zip','SHA256SUMS.txt'}:raise RuntimeError('Unexpected release assets')
    import hashlib
    for name,item in expected.items():
        p=root/name
        if p.stat().st_size!=item['size'] or hashlib.sha256(p.read_bytes()).hexdigest()!=item['sha256']:raise RuntimeError('Local asset mismatch')
    release=find_release()
    if release is None:
        # Use the creation response directly: collection reads can lag a new draft.
        payload={'tag_name':TAG,'target_commitish':commit,'name':'Phigros Script '+TAG,
                 'body':Path(notes_path).read_text(),'draft':True,'prerelease':True,'make_latest':'false'}
        release=json.loads(gh('api','--method','POST','repos/'+REPO+'/releases',
                              '--input','-',input=json.dumps(payload)).stdout)
    if release['tag_name']!=TAG or release['target_commitish']!=commit:raise RuntimeError('Existing release points to different source')
    present={a['name']:a for a in release['assets']}
    if set(present)-set(expected):raise RuntimeError('Unexpected pre-existing assets; refusing to alter release')
    for name,item in expected.items():
        if name in present:
            a=present[name]
            if a.get('state')!='uploaded' or a['size']!=item['size'] or a.get('digest')!='sha256:'+item['sha256']:raise RuntimeError('Existing asset differs; refusing overwrite')
        else:
            if not release['draft']:raise RuntimeError('Published release is incomplete; refusing mutation')
            gh('release','upload',TAG,str(root/name),'--repo',REPO)
    release=api('releases/'+str(release['id']))
    for name,item in expected.items():
        a=next((a for a in release['assets'] if a['name']==name),None)
        if a is None or a['state']!='uploaded' or a['size']!=item['size'] or a.get('digest')!='sha256:'+item['sha256']:raise RuntimeError('Uploaded asset verification failed')
    if release['draft']:gh('api','--method','PATCH','repos/'+REPO+'/releases/'+str(release['id']),'-F','draft=false','-f','make_latest=false')
    published=api('releases/tags/'+TAG);ref=api('git/ref/tags/'+TAG)
    if published['draft'] or ref['object']['sha']!=commit:raise RuntimeError('Published release/source verification failed')
    print(published['html_url'])
    print('Verified the published APK, source ZIP and checksums.')


def package_source(root, output):
    import zipfile
    patch = root / "patch"
    with zipfile.ZipFile(output, "w") as archive:
        for path in sorted(patch.rglob("*")):
            if path.is_file():
                info = zipfile.ZipInfo(path.relative_to(root).as_posix())
                info.external_attr = (path.stat().st_mode & 0xffff) << 16
                archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)


if __name__ == "__main__":
    import hashlib
    root = Path(__file__).resolve().parent
    release = Path("release")
    apk = "PhigrosScript_data1.0.9-r15.apk"
    source = "PhigrosScript_patch.zip"
    checksum = "SHA256SUMS.txt"
    package_source(root, release / source)
    assets = {}
    for name in (apk, source, checksum):
        if name == checksum:
            sums = "".join(spec["sha256"] + "  " + filename + "\n" for filename, spec in assets.items())
            (release / checksum).write_text(sums, encoding="ascii")
        data = (release / name).read_bytes()
        assets[name] = {"size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    expected = json.loads((root / "release-data" / "manifest.json").read_text())
    if assets[apk]["sha256"] != expected["apk_sha256"]:
        raise ValueError("The APK differs from the tested build")
    manifest = release / "publish-manifest.json"
    manifest.write_text(json.dumps({"assets": assets}))
    publish(release, manifest, root / "patch" / "PhigrosScript_changes.md")
