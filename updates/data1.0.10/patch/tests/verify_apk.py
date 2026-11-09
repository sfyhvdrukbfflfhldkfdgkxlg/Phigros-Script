"""Verify packaged DEX checksums, class uniqueness, helper linkage and resources."""
from pathlib import Path
import hashlib,struct,sys,zipfile,zlib

ROOT=Path(__file__).resolve().parents[2]
apk=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'deliverables/PhigrosScript_0.3.0_Root.apk'
def uleb(data,at):
    value=shift=0
    while True:
        b=data[at];at+=1;value|=(b&127)<<shift
        if b<128:return value,at
        shift+=7
def dex(data):
    assert data[:4]==b'dex\n'
    assert hashlib.sha1(data[32:]).digest()==data[12:32], 'DEX SHA-1'
    assert zlib.adler32(data[12:])&0xffffffff==struct.unpack_from('<I',data,8)[0], 'DEX Adler32'
    def table(at):return struct.unpack_from('<II',data,at)
    n,off=table(56);strings=[]
    for i in range(n):
        at=struct.unpack_from('<I',data,off+i*4)[0];_,at=uleb(data,at)
        strings.append(data[at:data.index(b'\0',at)].decode('utf8','replace'))
    n,off=table(64);types=[strings[struct.unpack_from('<I',data,off+i*4)[0]] for i in range(n)]
    def type_list(off):
        if not off:return []
        n=struct.unpack_from('<I',data,off)[0]
        return [types[struct.unpack_from('<H',data,off+4+i*2)[0]] for i in range(n)]
    n,off=table(72);protos=[]
    for i in range(n):
        _,ret,params=struct.unpack_from('<III',data,off+i*12)
        protos.append('('+''.join(type_list(params))+')'+types[ret])
    n,off=table(80);fields=[]
    for i in range(n):
        owner,typ,name=struct.unpack_from('<HHI',data,off+i*8)
        fields.append((types[owner],strings[name],types[typ]))
    n,off=table(88);methods=[]
    for i in range(n):
        owner,proto,name=struct.unpack_from('<HHI',data,off+i*8)
        methods.append((types[owner],strings[name],protos[proto]))
    n,off=table(96);definitions={}
    for i in range(n):
        owner,flags,parent,interfaces,_,_,class_data,_=struct.unpack_from('<8I',data,off+i*32)
        d={'parents':([] if parent==0xffffffff else [types[parent]])+type_list(interfaces),
           'fields':set(),'methods':set()}
        if class_data:
            at=class_data;counts=[]
            for _ in range(4):v,at=uleb(data,at);counts.append(v)
            for kind,count in enumerate(counts):
                index=0
                for _ in range(count):
                    delta,at=uleb(data,at);index+=delta;_,at=uleb(data,at)
                    if kind>=2:
                        _,at=uleb(data,at);item=methods[index];d['methods'].add(item[1:])
                    else:item=fields[index];d['fields'].add(item[1:])
        definitions[types[owner]]=d
    return definitions,fields,methods

sdk=zipfile.ZipFile(ROOT/'tools/android/android.jar')
def sdk_class(owner):
    name=owner[1:-1]+'.class'
    if name not in sdk.namelist():return None
    data=sdk.read(name);at=8
    def u2():
        nonlocal at
        value=struct.unpack_from('>H',data,at)[0];at+=2;return value
    def u4():
        nonlocal at
        value=struct.unpack_from('>I',data,at)[0];at+=4;return value
    count=u2();cp=[None]*count;i=1
    while i<count:
        tag=data[at];at+=1
        if tag==1:n=u2();cp[i]=data[at:at+n].decode('utf8','replace');at+=n
        elif tag in (7,8,16,19,20):cp[i]=u2()
        elif tag in (3,4,9,10,11,12,17,18):at+=4
        elif tag in (5,6):at+=8;i+=1
        elif tag==15:at+=3
        else:raise AssertionError(('class tag',tag))
        i+=1
    def class_name(index):return 'L'+cp[cp[index]]+';'
    u2();u2();parent=u2();parents=[] if not parent else [class_name(parent)]
    for _ in range(u2()):parents.append(class_name(u2()))
    d={'parents':parents,'fields':set(),'methods':set()}
    for kind in ('fields','methods'):
        for _ in range(u2()):
            u2();name=u2();descriptor=u2();d[kind].add((cp[name],cp[descriptor]))
            for _ in range(u2()):u2();length=u4();at+=length
    return d

with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    definitions={};extra_fields=extra_methods=None
    for name in z.namelist():
        if name.startswith('classes') and name.endswith('.dex'):
            found,fields,methods=dex(z.read(name))
            assert not definitions.keys()&found.keys(), 'duplicate class'
            definitions.update(found)
            if name=='classes8.dex':
                extra_fields,extra_methods=fields,methods
                assert all(t.startswith(('Lio/github/phiscript/input/','Lio/github/phiscript/assets/','Lio/github/phiscript/root/')) for t in found), 'test classes packaged'
                assert 'Lio/github/phiscript/input/GestureBatch;' in found
                assert 'Lio/github/phiscript/root/RootDaemon;' in found
                assert ('process','Ljava/lang/Process;') in found['Lio/github/phiscript/root/RootClient;']['fields'], 'root mock transport packaged'
    app_count=len(definitions)
    assert ('scanSource','(Landroid/content/Context;Lio/github/phiscript/assets/ChartSource;Ljava/lang/String;Lkotlin/jvm/functions/Function1;)Lio/github/phiscript/assets/ChartLibrary;') in definitions['Lio/github/phiscript/assets/ChartLibrary$Companion;']['methods'], 'root import reflection target missing'
    assert ('taskBusy','()Z') in definitions['Lio/github/phiscript/MainActivity;']['methods'], 'root UI reflection target missing'
    assert ('Companion','Lio/github/phiscript/assets/ChartLibrary$Companion;') in definitions['Lio/github/phiscript/assets/ChartLibrary;']['fields'], 'root import singleton missing'
    def resolve(owner,kind,member,seen=None):
        seen=set() if seen is None else seen
        if owner in seen:return False
        seen.add(owner)
        if owner not in definitions:
            d=sdk_class(owner)
            if d is None:return False
            definitions[owner]=d
        d=definitions[owner]
        return member in d[kind] or any(resolve(p,kind,member,seen) for p in d['parents'])
    for kind,items in [('fields',extra_fields),('methods',extra_methods)]:
        for owner,name,signature in items:
            if owner.startswith('['):continue
            assert resolve(owner,kind,(name,signature)), ('unresolved helper reference',owner,name,signature)
    original=zipfile.ZipFile(ROOT/'upload/base(3).apk')
    def signature(name):
        s=name.upper();return s.startswith('META-INF/') and (s.endswith(('.SF','.RSA','.DSA','.EC')) or s=='META-INF/MANIFEST.MF')
    changed=[];retained=0
    for name in original.namelist():
        if signature(name):continue
        assert name in z.namelist(),('missing entry',name)
        if original.read(name)==z.read(name):retained+=1
        else:changed.append(name)
    assert set(changed)=={'AndroidManifest.xml','classes4.dex','classes6.dex','classes7.dex'},changed
print('PASS APK CRC, all DEX SHA-1/Adler32 checksums and',app_count,'unique app classes')
print('PASS every helper method/field resolves against packaged app classes and Android SDK')
print('PASS no test/mock classes packaged;',retained,'original entries retained byte-for-byte')
print('Changed original entries:',changed)
print('APK SHA-256:',hashlib.sha256(apk.read_bytes()).hexdigest())
print('APK bytes:',apk.stat().st_size)
