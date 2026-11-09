from pathlib import Path
import json, copy

OUT = Path(__file__).parent / 'fixtures'
OUT.mkdir(exist_ok=True)

def save(name, obj):
    (OUT / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2))

def note(kind, time, x, hold=0):
    return dict(type=kind, time=time, positionX=x, holdTime=hold, speed=1, floorPosition=0)

def line(notes, y=.5):
    return dict(bpm=120, notesAbove=notes, notesBelow=[], speedEvents=[dict(startTime=0,endTime=100000,value=1,floorPosition=0)],
        judgeLineMoveEvents=[dict(startTime=-1000,endTime=100000,start=.5,end=.5,start2=y,end2=y)],
        judgeLineRotateEvents=[],judgeLineDisappearEvents=[])

pgr = dict(formatVersion=3, offset=0, judgeLineList=[
    line([note(3,32,-4,128),note(3,32,0,128),note(3,32,4,128),
          note(1,64,-6),note(4,96,6),note(2,128,-6),note(1,160,0)])])
save('pgr_v3_multihold.json', pgr)
v1=copy.deepcopy(pgr);v1['formatVersion']=1
for l in v1['judgeLineList']:
    for e in l['judgeLineMoveEvents']:
        e['start']=e['end']=440260
save('pgr_v1_multihold.json',v1)

def event(start, end, a, b, easing=1):
    return dict(startTime=[start,0,1],endTime=[end,0,1],start=a,end=b,
                easingType=easing,easingLeft=0,easingRight=1,bezier=0)

def rnote(kind, beat, end, x, fake=0):
    return dict(type=kind,startTime=[beat,0,1],endTime=[end,0,1],positionX=x,
                above=1,isFake=fake,speed=1,size=1,alpha=255,yOffset=0)

def rline(notes, layers, parent=-1):
    return dict(Name='Test',Texture='line.png',isCover=1,notes=notes,
                eventLayers=layers,father=parent,rotateWithFather=True)

def layer(x=None, y=None, rotation=None):
    return dict(moveXEvents=x or [],moveYEvents=y or [],rotateEvents=rotation or [],
                alphaEvents=[],speedEvents=[])

rpe=dict(META={'RPEVersion':140,'offset':0},BPMList=[
    dict(startTime=[0,0,1],bpm=120),dict(startTime=[4,0,1],bpm=180)],judgeLineList=[
    rline([rnote(2,1,7,-200),rnote(2,1,7,200),rnote(1,3,3,0),rnote(3,5,5,0),
           rnote(4,6,6,0),rnote(1,2,2,0,1)],
        [layer(),layer([event(0,8,120,240)]),layer(),layer([event(0,8,60,60)])]),
    rline([rnote(2,2,6,0)],[layer(y=[event(0,8,80,80)])],parent=0)])
save('rpe_empty_layers_bpm_parent.json',rpe)
(OUT/'pec_multihold.pec').write_text('''0
bp 0 120
cp 0 0 1024 700
cd 0 0 0
n2 0 2 8 600 1 0
n2 0 2 8 1400 1 0
n1 0 4 1024 1 0
n3 0 5 1024 1 0
n4 0 6 1024 1 0
''')
print('Created PGR 1/3, RPE and PEC fixtures.')
