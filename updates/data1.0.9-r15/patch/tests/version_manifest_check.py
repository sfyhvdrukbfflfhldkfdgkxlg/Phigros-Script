"""Different-length AXML version metadata and old wrapper compatibility."""
from pathlib import Path
import re
import struct
import subprocess
import sys
import tempfile
from zipfile import ZipFile

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import build_r17
import build_110
import r20_icon as axml


def inspect(source):
    chunks=list(axml.chunks(source));pool=next(data for _,kind,data in chunks if kind==1);strings=axml.strings(pool)
    mapping=next(data for _,kind,data in chunks if kind==0x180);head=struct.unpack_from("<H",mapping,2)[0]
    resources=[axml.u32(mapping,at) for at in range(head,len(mapping),4)];nodes=[];versions={}
    for at,kind,chunk in chunks:
        if kind!=0x102:continue
        name=strings[axml.u32(chunk,20)];begin,width,count=struct.unpack_from("<HHH",chunk,24);attributes=[]
        for i in range(count):
            offset=16+begin+i*width;record=chunk[offset:offset+width];index=axml.u32(record,4);resource=resources[index] if index<len(resources) else 0
            if name=="manifest" and resource in (0x0101021b,0x0101021c):
                versions[resource]=(strings[axml.u32(record,16)] if record[15]==3 else axml.u32(record,16),record,at,offset)
            else:attributes.append(record)
        nodes.append((name,attributes))
    return strings,versions,nodes,chunks


def semantic_nodes(source):
    strings,_,nodes,_=inspect(source);result=[]
    for name,attributes in nodes:
        normalized=[]
        for attribute in attributes:
            namespace,key,raw=struct.unpack_from("<III",attribute);kind=attribute[15];value=axml.u32(attribute,16)
            normalized.append((None if namespace==axml.NO_INDEX else strings[namespace],strings[key],None if raw==axml.NO_INDEX else strings[raw],kind,strings[value] if kind==3 else value))
        result.append((name,normalized))
    return result


def check(base,final=None):
    base=Path(base);aapt=axml.ROOT/"tools/build-tools/android-15"/("aapt2.exe" if __import__("os").name=="nt" else "aapt2")
    with ZipFile(base) as apk:
        original=apk.read("AndroidManifest.xml");table=apk.read("resources.arsc")
    old_strings,old_versions,old_nodes,old_chunks=inspect(original);assert old_versions[0x0101021b][0]==39
    versions=[("v1",100), ("data1.1.0",11000), ("longer-version-name-than-the-original-1234567890",200), ("版本😀",300)]
    versions += [("data1.0.9-r"+str(revision),revision+24) for revision in (17,18,19,20)]
    with tempfile.TemporaryDirectory(prefix="manifest-versions-") as directory:
        for number,(name,code) in enumerate(versions):
            updated=build_r17.manifest_version(original,name,code);strings,current,nodes,chunks=inspect(updated)
            assert strings[:len(old_strings)]==old_strings,"existing AXML string indices shifted"
            assert current[0x0101021b][0]==code and current[0x0101021c][0]==name
            record=current[0x0101021c][1];assert record[15]==3 and strings[axml.u32(record,8)]==name,"typed/raw versionName disagreed"
            assert nodes==old_nodes,"non-version attributes changed"
            for (_,kind,before),(_,after_kind,after) in zip(old_chunks,chunks):
                assert kind==after_kind
                if kind not in (1,0x102):assert before==after,"unrelated XML chunk changed"
            assert len(updated)==axml.u32(updated,4)
            path=Path(directory)/(str(number)+".apk")
            with ZipFile(path,"w") as apk:apk.writestr("AndroidManifest.xml",updated);apk.writestr("resources.arsc",table)
            text=subprocess.run([str(aapt),"dump","badging",str(path)],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,encoding="utf-8").stdout
            assert f"versionCode='{code}'" in text and f"versionName='{name}'" in text,text
        # Version metadata may be set on either side of icon attribute addition.
        before=axml.manifest_icon(build_r17.manifest_version(original,"data1.1.0",11000))
        after=build_r17.manifest_version(axml.manifest_icon(original),"data1.1.0",11000)
        assert inspect(before)[1][0x0101021c][0]==inspect(after)[1][0x0101021c][0]=="data1.1.0"
        assert semantic_nodes(before)==semantic_nodes(after),"icon attrs changed with version patch order"
        # A string raw form of numeric versionCode must be updated consistently.
        raw=bytearray(original);_,_,code_node,code_offset=old_versions[0x0101021b]
        struct.pack_into("<I",raw,code_node+code_offset+8,axml.u32(old_versions[0x0101021c][1],8))
        raw_result=build_r17.manifest_version(raw,"data1.1.0",11000);strings,current,_,_=inspect(raw_result)
        assert strings[axml.u32(current[0x0101021b][1],8)]=="11000"
        rejected=False
        try:build_r17.manifest_version(original,"wrong-base",11000,expected_code=40)
        except AssertionError:rejected=True
        assert rejected,"unexpected baseline code accepted"
    assert build_110.VERSION=="data1.1.0" and build_110.VERSION_CODE==11000
    if final is not None:
        with ZipFile(final) as apk:_,current,_,_=inspect(apk.read("AndroidManifest.xml"))
        assert current[0x0101021b][0]==11000 and current[0x0101021c][0]=="data1.1.0"
    print("PASS version manifest: eight short/long/Unicode/legacy names, AAPT2 typed/raw verification, preserved old indices/attributes, icon ordering, numeric raw value and baseline rejection; data1.1.0 code11000.")


if __name__=="__main__":check(*sys.argv[1:])
