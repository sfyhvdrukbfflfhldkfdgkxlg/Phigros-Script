"""Minimal binary-XML edit: versionCode 38 and largeHeap for supplied large charts."""
import struct
U32=lambda b,o:struct.unpack_from('<I',b,o)[0]
def patch_manifest(source):
    chunks=[];o=8;strings=[];new_name=None;resource_ids=[]
    while o<len(source):
        kind,head,size=struct.unpack_from('<HHI',source,o);c=bytearray(source[o:o+size]);o+=size
        if kind==1:
            count,styles,flags,start,style_start=struct.unpack_from('<IIIII',c,8)
            assert styles==0 and flags&0x100==0,'expected unstyled UTF-16 pool'
            offsets=[U32(c,head+4*i) for i in range(count)]
            for v in offsets:
                p=start+v;n=struct.unpack_from('<H',c,p)[0];p+=2
                if n&0x8000:n=((n&0x7fff)<<16)|struct.unpack_from('<H',c,p)[0];p+=2
                strings.append(bytes(c[p:p+2*n]).decode('utf-16le'))
            assert 'largeHeap' not in strings
            new_name=count;name='largeHeap';raw=c[start:];offsets.append(len(raw));raw+=struct.pack('<H',len(name))+name.encode('utf-16le')+b'\0\0'
            pro='org.flos.phirapro';assert pro not in strings;offsets.append(len(raw));raw+=struct.pack('<H',len(pro))+pro.encode('utf-16le')+b'\0\0'
            release_name='data1.0.9-r14';offsets.append(len(raw));raw+=struct.pack('<H',len(release_name))+release_name.encode('utf-16le')+b'\0\0'
            raw+=b'\0'*((-len(raw))%4);new_start=start+12
            nc=bytearray(c[:head]);struct.pack_into('<I',nc,4,new_start+len(raw));struct.pack_into('<I',nc,8,count+3);struct.pack_into('<I',nc,20,new_start)
            nc+=struct.pack('<'+'I'*len(offsets),*offsets)+raw;c=nc;strings.extend([name,pro,release_name])
        elif kind==0x180:
            resource_ids=list(struct.unpack_from('<'+'I'*((size-head)//4),c,head));resource_ids +=[0]*(new_name-len(resource_ids));resource_ids.append(0x0101035a)
            c=bytearray(struct.pack('<HHI',kind,head,head+4*len(resource_ids))+struct.pack('<'+'I'*len(resource_ids),*resource_ids))
        chunks.append((kind,c))
    version=application=queries=0;out=[]
    for kind,c in chunks:
        if kind==0x102:
            start,asz,count=struct.unpack_from('<HHH',c,24);base=16+start;assert asz==20
            attrs=[bytes(c[base+i*asz:base+(i+1)*asz]) for i in range(count)]
            for i,a in enumerate(attrs):
                idx=U32(a,4)
                if idx<len(resource_ids) and resource_ids[idx]==0x0101021b:
                    a=bytearray(a);struct.pack_into('<I',a,16,38);attrs[i]=bytes(a);version+=1
                if idx<len(resource_ids) and resource_ids[idx]==0x0101021c:
                    a=bytearray(a);assert a[15]==3
                    struct.pack_into('<I',a,8,strings.index(release_name));struct.pack_into('<I',a,16,strings.index(release_name));attrs[i]=bytes(a)
            if strings[U32(c,20)]=='application':
                namespace=strings.index('http://schemas.android.com/apk/res/android');new_attr=struct.pack('<IIIHBBI',namespace,new_name,0xffffffff,8,0,0x12,0xffffffff)
                place=len(attrs)
                for i,a in enumerate(attrs):
                    idx=U32(a,4)
                    if idx<len(resource_ids) and resource_ids[idx]>0x0101035a:place=i;break
                attrs.insert(place,new_attr);struct.pack_into('<H',c,28,count+1)
                for index_offset in (30,32,34):
                    v=struct.unpack_from('<H',c,index_offset)[0]
                    if v>place:struct.pack_into('<H',c,index_offset,v+1)
                application+=1
            c=c[:base]+b''.join(attrs)+c[base+count*asz:];struct.pack_into('<I',c,4,len(c))
        if kind==0x103 and strings[U32(c,20)]=='queries':
            ns=strings.index('http://schemas.android.com/apk/res/android');pkg=strings.index('package');v=strings.index('org.flos.phirapro');line=U32(c,8)
            attr=struct.pack('<IIIHBBI',ns,strings.index('name'),v,8,0,3,v)
            out.extend([struct.pack('<HHIII',0x102,16,56,line,0xffffffff)+struct.pack('<IIHHHHHH',0xffffffff,pkg,20,20,1,0,0,0)+attr,struct.pack('<HHIIIII',0x103,16,24,line,0xffffffff,0xffffffff,pkg)]);queries+=1
        out.append(c)
    assert version==1 and application==1 and queries==1
    body=b''.join(out);return struct.pack('<HHI',3,8,len(body)+8)+body
