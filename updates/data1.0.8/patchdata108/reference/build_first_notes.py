"""Derive facts from a pinned public chart archive; no chart files are republished.

Usage: python3 patchdata108/reference/build_first_notes.py ARCHIVE.zip MANIFEST.json OUT_DIR
The optional expected/ files are private research fixtures, excluded from delivery.
"""
from pathlib import Path
from decimal import Decimal, localcontext, ROUND_HALF_UP
import hashlib,json,struct,sys,zipfile
from datetime import datetime,timezone

def micros(value):
    return int((value*1000000).to_integral_value(rounding=ROUND_HALF_UP))

def build(archive,manifest,destination):
    archive=Path(archive);manifest=json.loads(Path(manifest).read_text());out=Path(destination)
    out.mkdir(parents=True,exist_ok=True);expected=out/'expected';expected.mkdir(exist_ok=True)
    facts=[];total=0;mapping=[];began=datetime.now(timezone.utc).isoformat()
    with zipfile.ZipFile(archive) as z,localcontext() as decimal_context:
        decimal_context.prec=50
        names={name.split('/',1)[1]:name for name in z.namelist() if '/' in name and not name.endswith('/')}
        for index,row in enumerate(manifest['rows']):
            path=row['resourcePath'];data=z.read(names[path])
            assert len(data)==row['resourceSize']
            blob=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
            assert blob==row['resourceBlob'],('source snapshot differs',path,blob)
            chart=json.loads(data,parse_float=Decimal,parse_int=Decimal)
            assert int(chart['formatVersion']) in (1,3)
            offset=chart.get('offset',Decimal(0));notes=[];sequence=0
            for line_index,line in enumerate(chart['judgeLineList']):
                bpm=line['bpm'];assert bpm>0
                for side in ['notesAbove','notesBelow']:
                    for note in line[side]:
                        kind=int(note['type']);assert kind in (1,2,3,4)
                        head=note['time']*Decimal('1.875')/bpm
                        tail=head+(note.get('holdTime',Decimal(0))*Decimal('1.875')/bpm if kind==3 else 0)
                        notes.append((sequence,kind,float(head),float(tail),line_index,side=='notesAbove'))
                        sequence+=1
            assert notes,('empty chart',path)
            first=min(n[2] for n in notes);last=max(n[3] for n in notes)
            first_us=micros(Decimal(str(first)));offset_us=micros(offset)
            first_music_us=micros(Decimal(str(first))+offset)
            facts.append((row['id'],row['level'],first_us,offset_us,first_music_us,len(notes),micros(Decimal(str(last))+offset),blob))
            target=expected/f'{index:04}.bin'
            with target.open('wb') as stream:
                stream.write(b'CTE1'+struct.pack('>id',len(notes),float(offset)))
                for note in notes:stream.write(struct.pack('>iiddi?',*note))
            mapping.append({'id':row['id'],'difficulty':row['level'],'source':names[path],'expected':target.name})
            total+=len(notes)
            if (index+1)%100==0:print('verified_charts',index+1,'notes',total,flush=True)
    assert len({(r[0],r[1]) for r in facts})==len(facts)
    facts.sort(key=lambda r:(r[0],r[1]))
    header=['# phigros-first-notes-v1',
            '# source=https://github.com/7aGiven/Phigros_Resource/tree/'+manifest['resourceCommit'],
            '# game_version='+manifest['resourceVersion'],
            '# chart_count='+str(len(facts))+' note_count='+str(total),
            '# song_id\tdifficulty\tfirst_chart_us\toffset_us\tfirst_music_us\tnotes\tlast_music_end_us\tgit_blob_sha1']
    (out/'phigros-first-notes-v1.tsv').write_text('\n'.join(header+['\t'.join(map(str,row)) for row in facts])+'\n')
    (out/'corpus-index.json').write_text(json.dumps(mapping,ensure_ascii=False))
    report={'chart_count':len(facts),'note_count':total,'game_version':manifest['resourceVersion'],
            'source_commit':manifest['resourceCommit'],'archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),
            'fetched_at':began,'formula':'head_seconds = time * 1.875 / bpm; music_head = head_seconds + offset',
            'note':'Relative music timings; not a measured retry-to-audio or device delay.'}
    (out/'source-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report),flush=True)

if __name__=='__main__':build(*sys.argv[1:])
