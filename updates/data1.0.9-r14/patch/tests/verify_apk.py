import zipfile,struct,json,hashlib
from pathlib import Path
from collections import Counter
apk=Path('deliverables/PhigrosScript_data1.0.9-r14.apk');z=zipfile.ZipFile(apk);o=zipfile.ZipFile('upload/attachment_1413700149765945124.apk');names=[];versions=[]
def uleb(b,p):
 v=s=0
 while True:
  x=b[p];p+=1;v|=(x&127)<<s
  if x<128:return v,p
  s+=7
for n in z.namelist():
 if n.endswith('.dex'):
  b=z.read(n);assert b[:8] in [b'dex\n035\x00',b'dex\n038\x00'];versions.append(b[:8].decode());sc,so=struct.unpack_from('<II',b,56);tc,to=struct.unpack_from('<II',b,64);cc,co=struct.unpack_from('<II',b,96)
  for i in range(cc):
   ti=struct.unpack_from('<I',b,co+32*i)[0];si=struct.unpack_from('<I',b,to+ti*4)[0];sp=struct.unpack_from('<I',b,so+si*4)[0];_,p=uleb(b,sp);end=b.index(0,p);names.append(b[p:end].decode())
dup=[k for k,v in Counter(names).items() if v>1];assert not dup,dup
for obsolete in ['assets/ChartTimingIndex','root/FirstNoteBrowser','capture/PhigrosStartGate','capture/PhigrosControls','capture/PhigrosHudProbe','capture/SharedPhiraControls']:
 stem='Lio/github/phiscript/'+obsolete
 assert not any(name==stem+';' or name.startswith(stem+'$') for name in names),'obsolete class packaged: '+obsolete
unchanged=[];changed=[]
for n in o.namelist():
 if n in z.namelist() and not n.startswith('META-INF/'):
  (unchanged if o.read(n)==z.read(n) else changed).append(n)
assert set(changed)=={'AndroidManifest.xml','classes4.dex','classes5.dex','classes6.dex','classes7.dex','classes8.dex','classes11.dex'},changed
catalog=json.loads(z.read('assets/phigros-cloud-catalog-v1.json'));assert len(catalog['entries'])==1022
new_catalog=json.loads(z.read('assets/phigros-cloud-feyajI8g-v1.json'));assert new_catalog['share']=='feyajI8g' and len(new_catalog['entries'])==1052 and new_catalog['expectedCharts']==1057 and len(new_catalog['missing'])==5
manifest=z.read('AndroidManifest.xml');offset=8;resources=[];version_code=None;large_heap=None
while offset<len(manifest):
 kind,head,size=struct.unpack_from('<HHI',manifest,offset)
 if kind==0x180:resources=list(struct.unpack_from('<'+'I'*((size-head)//4),manifest,offset+head))
 if kind==0x102:
  start,attribute_size,count=struct.unpack_from('<HHH',manifest,offset+24)
  for index in range(count):
   attribute=offset+16+start+attribute_size*index;name=struct.unpack_from('<I',manifest,attribute+4)[0];value=struct.unpack_from('<I',manifest,attribute+16)[0]
   if name<len(resources) and resources[name]==0x0101021b:version_code=value
   if name<len(resources) and resources[name]==0x0101035a:large_heap=value!=0
 offset+=size
assert version_code==38 and large_heap is True
result={'version_code':version_code,'large_heap':large_heap,'new_share_entries':len(new_catalog['entries']),'new_share_expected':new_catalog['expectedCharts'],'new_share_missing':len(new_catalog['missing']),'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'dex_classes':len(names),'dex_versions':sorted(set(versions)),'duplicate_classes':len(dup),'changed_original_entries':changed,'unchanged_original_entries':len(unchanged),'catalog_entries':1022}
Path('analysis/r14-structural-test.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False))
