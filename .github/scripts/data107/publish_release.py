"""Publish only the three exact data1.0.7 assets; never overwrite another release."""
import json,os,subprocess,sys
from pathlib import Path

REPO='sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script';TAG='data1.0.7'
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
    if set(expected)!={'PhigrosScript_data1.0.7.apk','PhigrosScript_data1.0.7_Source.zip','SHA256SUMS.txt'}:raise RuntimeError('Unexpected release assets')
    import hashlib
    for name,item in expected.items():
        p=root/name
        if p.stat().st_size!=item['size'] or hashlib.sha256(p.read_bytes()).hexdigest()!=item['sha256']:raise RuntimeError('Local asset mismatch')
    release=find_release()
    if release is None:
        # Use the creation response directly: collection reads can lag a new draft.
        payload={'tag_name':TAG,'target_commitish':commit,'name':'Phigros Script '+TAG,
                 'body':Path(notes_path).read_text(),'draft':True,'prerelease':True}
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
    if release['draft']:gh('api','--method','PATCH','repos/'+REPO+'/releases/'+str(release['id']),'-F','draft=false')
    published=api('releases/tags/'+TAG);ref=api('git/ref/tags/'+TAG)
    if published['draft'] or ref['object']['sha']!=commit:raise RuntimeError('Published release/source verification failed')
    print(published['html_url'])
    print('Published verified APK, source ZIP and SHA256SUMS; remote asset digests match local bytes.')

if __name__=='__main__':publish(*sys.argv[1:])
