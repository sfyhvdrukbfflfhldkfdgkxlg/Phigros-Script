"""Read DEX class definitions and member references without executing code."""
import struct,zipfile
class DexInfo:
    def __init__(self,b):
        self.b=b
        def table(off):return struct.unpack_from('<II',b,off)
        n,p=table(56);self.strings=[]
        for i in range(n):
            start=struct.unpack_from('<I',b,p+4*i)[0];_,start=self.uleb(start);end=b.index(0,start)
            self.strings.append(b[start:end].decode('utf-8',errors='replace'))
        n,p=table(64);self.types=[self.strings[struct.unpack_from('<I',b,p+4*i)[0]] for i in range(n)]
        n,p=table(72);protos=[]
        for i in range(n):
            _,ret,args=struct.unpack_from('<III',b,p+12*i);parameters=[]
            if args:
                size=struct.unpack_from('<I',b,args)[0];parameters=[self.types[struct.unpack_from('<H',b,args+4+2*j)[0]] for j in range(size)]
            protos.append('('+''.join(parameters)+')'+self.types[ret])
        n,p=table(88);self.methods=[]
        for i in range(n):
            owner,proto,name=struct.unpack_from('<HHI',b,p+8*i);self.methods.append(self.types[owner]+'->'+self.strings[name]+protos[proto])
        n,p=table(80);self.fields=[]
        for i in range(n):
            owner,kind,name=struct.unpack_from('<HHI',b,p+8*i);self.fields.append(self.types[owner]+'->'+self.strings[name]+':'+self.types[kind])
        self.classes=set();self.defined_methods=set();self.defined_fields=set()
        n,p=table(96)
        for i in range(n):
            entry=p+32*i;owner=struct.unpack_from('<I',b,entry)[0];data=struct.unpack_from('<I',b,entry+24)[0];self.classes.add(self.types[owner])
            if not data:continue
            counts=[]
            for k in range(4):v,data=self.uleb(data);counts.append(v)
            for count in counts[:2]:
                index=0
                for k in range(count):
                    delta,data=self.uleb(data);index+=delta;_,data=self.uleb(data);self.defined_fields.add(self.fields[index])
            for count in counts[2:]:
                index=0
                for k in range(count):
                    delta,data=self.uleb(data);index+=delta;_,data=self.uleb(data);_,data=self.uleb(data);self.defined_methods.add(self.methods[index])
    def uleb(self,p):
        value=shift=0
        while True:
            byte=self.b[p];p+=1;value|=(byte&127)<<shift
            if byte<128:return value,p
            shift+=7

def apk_dexes(path):
    with zipfile.ZipFile(path) as z:return {n:DexInfo(z.read(n)) for n in z.namelist() if n.endswith('.dex')}

def aggregate(dexes):
    result={k:set() for k in ['classes','types','methods','fields','defined_methods','defined_fields']}
    for d in dexes:
        for k in result:result[k].update(getattr(d,k))
    return result

def base_type(t):return t.lstrip('[')
