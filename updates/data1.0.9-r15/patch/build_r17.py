"""Build R17 from verified R15: helpers plus explicitly patched native DEX."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from zipfile import ZipFile

import build_r16 as previous
import r17_hooks
from lib.dex_info import DexInfo

ROOT=previous.ROOT
APK_NAME="PhigrosScript_data1.0.9-r17.apk"


def build(base,key,cert,output,release="data1.0.9-r17",code=41):
    assert hashlib.sha256(base.read_bytes()).hexdigest()==previous.BASE_SHA256,"Expected exact official R15 APK"
    assert (release,code) in (("data1.0.9-r17",41),("data1.0.9-r18",42),("data1.0.9-r19",43),("data1.0.9-r20",44),("data1.1.0",11000),("data1.1.1",11001),("data1.1.2",11002),("data1.1.2",11003),("data1.1.2",11004),("data1.1.3",11005),("data1.1.3-r2",11006),("data1.1.3-r3",11007))
    sdk=ROOT/"tools/platform/android-35/android.jar"
    bt=ROOT/"tools/build-tools/android-15"
    ecj=ROOT/"tools/ecj.jar"
    apktool=ROOT/"tools/apktool.jar"
    kotlin=ROOT/"tools/kotlin.jar"
    run=previous.run
    sep=os.pathsep
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="r17-",dir=ROOT,ignore_cleanup_errors=True) as tmp:
        work=Path(tmp)
        api=work/"base.jar";decoded=work/"decoded"
        run("java","-Xmx1600m","-cp",str(ROOT/"tools/dex2jar/dex-tools-v2.4/lib/*"),
            "com.googlecode.dex2jar.tools.Dex2jarCmd","-f","-o",api,base)
        # Decode the verified input, never an unversioned previously edited tree.
        run("java","-Xmx1600m","-jar",apktool,"d","-r","-f",base,"-o",decoded)
        before={str(p.relative_to(decoded)):hashlib.sha256(p.read_bytes()).hexdigest() for p in decoded.glob("smali*/**/*.smali")}
        r17_hooks.apply(decoded)
        noise=ROOT/"patch/r17_noise_hooks.py"
        if noise.exists():
            import r17_noise_hooks
            r17_noise_hooks.apply_noise(decoded)
        if code>=44:
            import r20_hooks
            r20_hooks.apply(decoded)
        if code >= 11001:
            import r20_native_i18n
            r20_native_i18n.apply(decoded)
            import timing_111_hooks
            timing_111_hooks.apply(decoded)
            import update_111_hooks
            update_111_hooks.apply(decoded)
            if code >= 11006:
                update_111_hooks.recognize_revisions(decoded)
        if code >= 11002:
            import playback_112_hooks
            playback_112_hooks.apply(decoded)
        if code >= 11005:
            import glass_dialog_hooks
            glass_dialog_hooks.apply(decoded)
        if code >= 11006:
            import recording_audio_hooks
            import recording_permissions
            recording_audio_hooks.apply(decoded)
            recording_permissions.apply_activity(decoded)
        after={str(p.relative_to(decoded)):hashlib.sha256(p.read_bytes()).hexdigest() for p in decoded.glob("smali*/**/*.smali")}
        changed=sorted(p for p in before.keys()|after.keys() if before.get(p)!=after.get(p))
        indexes=sorted({int(Path(p).parts[0].removeprefix("smali_classes")) for p in changed})
        assert indexes and 12 not in indexes
        classes=work/"classes";classes.mkdir();dex=work/"dex";dex.mkdir();tools=work/"toolclasses";tools.mkdir()
        sources=work/"sources.txt"
        sources.write_text("\n".join(p.relative_to(ROOT).as_posix() for p in sorted((ROOT/"patch/src").rglob("*.java")))+"\n",encoding="utf-8")
        run("java","-jar",ecj,"-8","-nowarn","-encoding","UTF-8","-cp",sep.join(map(str,(api,sdk,kotlin))),"-d",classes,"@"+str(sources))
        jar=work/"classes.jar"
        run("java","--add-modules","jdk.jartool","sun.tools.jar.Main","cf",jar,"-C",classes,".")
        run("java","-cp",bt/"lib/d8.jar","com.android.tools.r8.D8","--min-api","26","--lib",sdk,"--classpath",api,"--classpath",kotlin,"--output",dex,jar)
        assert {p.name for p in dex.glob("*.dex")}=={"classes.dex"}
        replacements={"classes12.dex":(dex/"classes.dex").read_bytes()}
        run("java","-jar",ecj,"-8","-nowarn","-cp",apktool,"-d",tools,"patch/Assemble.java")
        for index in indexes:
            target=dex/("classes"+str(index)+".dex")
            run("java","-Xmx1600m","-cp",sep.join(map(str,(tools,apktool))),"Assemble",decoded/("smali_classes"+str(index)),target)
            replacements[target.name]=target.read_bytes()
        if code>=44:
            import r20_icon
            replacements.update(r20_icon.build(base,work/"icon"))
        if code >= 11002:
            catalog_name = "phigros-cloud-plans-v1.json"
            catalog = (ROOT / "patch/assets" / catalog_name).read_bytes()
            manifest = json.loads(catalog)
            source_name = "phigros-swordalt-catalog-v1.json"
            source = (ROOT / "patch/assets" / source_name).read_bytes()
            charts = json.loads(source)
            if (manifest.get("version") != 1 or manifest.get("chartCommit") != charts["chartCommit"]
                    or set(manifest["entries"]) != {row["sha1"] for row in charts["entries"]}
                    or not manifest.get("shards")):
                raise ValueError("The prepared tracks do not cover the bundled Phigros catalog")
            replacements["assets/" + source_name] = source
            replacements["assets/" + catalog_name] = catalog
        all_classes=set()
        with ZipFile(base) as original:
            for name in original.namelist():
                if name.endswith(".dex"):
                    defined=DexInfo(replacements.get(name,original.read(name))).classes
                    assert not all_classes&defined,all_classes&defined
                    all_classes.update(defined)
            unsigned=work/"unsigned.apk"
            with ZipFile(unsigned,"w") as dst:
                for info in original.infolist():
                    name=info.filename
                    if name.upper().startswith("META-INF/") and name.upper().endswith((".RSA",".DSA",".EC",".SF","/MANIFEST.MF")):continue
                    data=replacements.get(name,original.read(name))
                    if name=="AndroidManifest.xml":
                        data=manifest_version(data,release,code)
                        if code>=11006:
                            data=recording_permissions.add_audio_permissions(data)
                    dst.writestr(info,data)
                for name,data in replacements.items():
                    if name not in original.namelist():dst.writestr(name,data)
        zipalign=bt/("zipalign.exe" if os.name=="nt" else "zipalign")
        aligned=work/"aligned.apk";signed=work/"signed.apk";signer=bt/"lib/apksigner.jar"
        run(zipalign,"-P","16","-f","4",unsigned,aligned)
        run("java","-jar",signer,"sign","--key",key,"--cert",cert,"--out",signed,aligned)
        report=run("java","-jar",signer,"verify","--verbose","--print-certs",signed,stdout=subprocess.PIPE,text=True).stdout
        assert previous.R15_CERT in report,report
        print(report)
        run(zipalign,"-c","-P","16","4",signed)
        run(sys.executable,"patch/tests/verify_dependencies.py",base,signed)
        output.write_bytes(signed.read_bytes())
        output.with_suffix(".hooks.json").write_text(json.dumps({"version":release,"versionCode":code,"modified_smali":changed,"changed_dex":sorted(n for n in replacements if n.endswith(".dex")),"changed_resources":sorted(n for n in replacements if not n.endswith(".dex") and n!="AndroidManifest.xml")},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(hashlib.sha256(output.read_bytes()).hexdigest(),output.name)


def manifest_version(source,release,code,expected_code=39):
    """Set typed/raw version fields without shifting any existing string index."""
    import struct
    import r20_icon
    assert isinstance(release,str) and release and 0<code<=0x7fffffff
    assert struct.unpack_from("<H",source)[0]==3
    parts=list(r20_icon.chunks(source))
    pools=[(at,chunk) for at,kind,chunk in parts if kind==1]
    maps=[chunk for _,kind,chunk in parts if kind==0x180]
    assert len(pools)==len(maps)==1
    pool_at,pool=pools[0];names=r20_icon.strings(pool)
    resource_head=struct.unpack_from("<H",maps[0],2)[0]
    resources=[r20_icon.u32(maps[0],at) for at in range(resource_head,len(maps[0]),4)]
    attributes={}
    for at,kind,chunk in parts:
        if kind!=0x102 or names[r20_icon.u32(chunk,20)]!="manifest":continue
        begin,width,count=struct.unpack_from("<HHH",chunk,24);assert width==20
        for index in range(count):
            offset=16+begin+index*width;name=r20_icon.u32(chunk,offset+4)
            resource=resources[name] if name<len(resources) else 0
            if resource in (0x0101021b,0x0101021c):
                assert resource not in attributes
                attributes[resource]=(at,offset,chunk[offset:offset+width])
    assert set(attributes)=={0x0101021b,0x0101021c}
    old_code=attributes[0x0101021b][2];old_name=attributes[0x0101021c][2]
    assert old_code[15] in (0x10,0x11) and r20_icon.u32(old_code,16)==expected_code
    assert old_name[15]==3 and struct.unpack_from("<H",old_name,12)[0]==8
    code_raw=r20_icon.u32(old_code,8)!=r20_icon.NO_INDEX
    new_pool,indices=r20_icon.append_strings(pool,[release]+([str(code)] if code_raw else []))
    result=bytearray(source[:struct.unpack_from("<H",source,2)[0]])
    for at,_,chunk in parts:
        if at==pool_at:
            result.extend(new_pool);continue
        changed=bytearray(chunk)
        for resource,(node,offset,old) in attributes.items():
            if node!=at:continue
            if resource==0x0101021b:
                struct.pack_into("<I",changed,offset+16,code)
                if code_raw:struct.pack_into("<I",changed,offset+8,indices[str(code)])
            else:
                struct.pack_into("<I",changed,offset+16,indices[release])
                if r20_icon.u32(old,8)!=r20_icon.NO_INDEX:struct.pack_into("<I",changed,offset+8,indices[release])
        result.extend(changed)
    struct.pack_into("<I",result,4,len(result))
    return bytes(result)


def version_code(source,code=41):
    import struct
    data=bytearray(source);pos=8;resources=[];count=0
    while pos<len(data):
        kind,head,size=struct.unpack_from("<HHI",data,pos)
        if kind==0x180:resources=struct.unpack_from("<"+"I"*((size-head)//4),data,pos+head)
        elif kind==0x102:
            start,width,n=struct.unpack_from("<HHH",data,pos+24)
            for i in range(n):
                at=pos+16+start+i*width;name=struct.unpack_from("<I",data,at+4)[0]
                if name<len(resources) and resources[name]==0x0101021b:
                    assert struct.unpack_from("<I",data,at+16)[0]==40
                    struct.pack_into("<I",data,at+16,code);count+=1
        pos+=size
    assert count==1
    return bytes(data)


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base",type=Path);parser.add_argument("--key",type=Path,required=True);parser.add_argument("--cert",type=Path,required=True)
    parser.add_argument("--output",type=Path,default=ROOT/"deliverables"/APK_NAME)
    args=parser.parse_args()
    build(*(p.resolve() for p in (args.base,args.key,args.cert,args.output)))
