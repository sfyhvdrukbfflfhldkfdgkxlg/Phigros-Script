"""Bounded read-only replay comparison using the independent native reference.

The v5 sample model holds last observed positions; it does not interpolate across
Stationary omissions or invent hardware timestamps / internal blocked ID flags.
All outputs omit player identities and local game directories.
"""
import argparse,collections,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from analyze_ppr import load_ppr,bounded_read
import noise_reference as ref


def geometry(replay,raw):
 ref.prepare(raw,replay['aspect_ratio'])
 events=replay['touches'];ei=0;active={};serial=0;contact_ids={};contacts={};hits=[];infected={};states=[]
 def event(e):
  nonlocal serial
  ident=e['id']
  if e['phase']==0:
   serial+=1;contact_ids[ident]=serial
   contacts[serial]=dict(serial=serial,id=ident,start=e['t'],end=None,events=[],first_blocked=None,last_blocked=None,blocked_samples=0,blocked_segments=[])
   infected.pop(ident,None)
  if ident in contact_ids:contacts[contact_ids[ident]]['events'].append(e)
  if e['phase']==2:
   if ident in contact_ids:contacts[contact_ids[ident]]['end']=e['t']
   active.pop(ident,None);infected.pop(ident,None);contact_ids.pop(ident,None)
  else:active[ident]=e
 previous_blocked=set()
 for frame in replay['frames']:
  while ei<len(events) and events[ei]['t']<=frame+.0001:event(events[ei]);ei+=1
  blocked=ref.checker(frame,active)
  now_blocked=set()
  for ident,areas in blocked:
   cid=contact_ids[ident];c=contacts[cid];now_blocked.add(cid)
   if c['first_blocked'] is None:c['first_blocked']=frame;c['first_areas']=areas
   c['last_blocked']=frame;c['blocked_samples']+=1
   if cid not in previous_blocked:c['blocked_segments'].append(dict(from_time=frame,to_time=frame,areas=areas))
   else:c['blocked_segments'][-1]['to_time']=frame
   infected[ident]=cid;hits.append(dict(t=frame,serial=cid,id=ident,areas=areas,x=active[ident]['x'],y=active[ident]['y'],aspect=replay['aspect_ratio']))
  states.append(dict(t=frame,active=len(active),blocked=sorted(now_blocked),infected=sorted(infected.values())))
  previous_blocked=now_blocked
 while ei<len(events):event(events[ei]);ei+=1
 contaminated=[c for c in contacts.values() if c['first_blocked'] is not None]
 firstarea=collections.Counter(a for c in contaminated for a in c['first_areas'])
 return dict(contact_count=len(contacts),contaminated_count=len(contaminated),blocked_contact_frame_samples=len(hits),
   first_area_counts=dict(firstarea),contaminated=contaminated),hits,states,list(contacts.values())


def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('old',type=Path);p.add_argument('new',type=Path);p.add_argument('--chart',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
 raw=json.loads(bounded_read(a.chart));a.out.mkdir(parents=True,exist_ok=True)
 for label,path in [('old',a.old),('new',a.new)]:
  j,header=load_ppr(path);report,hits,states,contacts=geometry(j,raw)
  for name,obj in [(label+'-noise.json',report),(label+'-noise-hits.json',hits),(label+'-noise-states.json',states),(label+'-contacts-full.json',contacts)]:
   (a.out/name).write_text(json.dumps(obj,indent=2),encoding='utf-8')
  print(label,json.dumps({k:report[k] for k in ['contact_count','contaminated_count','blocked_contact_frame_samples','first_area_counts']}))

if __name__=='__main__':main()
