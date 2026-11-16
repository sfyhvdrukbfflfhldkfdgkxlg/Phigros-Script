import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import noise_reference as ref
from analyze_ppr import bounded_read
p=argparse.ArgumentParser(description='Prepare independent-reference expectations at t plus/minus 5/12 ms for NativeNoiseProbe.')
p.add_argument('--chart',type=Path,required=True);p.add_argument('--hits',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
p.add_argument('--aspect',type=float,default=16/9,help='Fallback for hit files made before aspect was recorded')
a=p.parse_args();raw=json.loads(bounded_read(a.chart));last_aspect=None
hits=json.loads(bounded_read(a.hits))
for e in hits:
 aspect=e.get('aspect',a.aspect)
 if aspect!=last_aspect:ref.prepare(raw,aspect);last_aspect=aspect
 e['aspect']=aspect
 e['reference']=[bool(ref.checker(e['t']+s,{e['id']:e})) for s in [-.012,-.005,0,.005,.012]]
a.out.write_text(json.dumps(hits,indent=2))
