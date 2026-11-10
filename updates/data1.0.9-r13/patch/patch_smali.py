from pathlib import Path
import re,shutil
base=Path('analysis/decoded');out=Path('patch/smali');out.mkdir(exist_ok=True)
replaced=[]
for p in Path('patch/src').rglob('*.java'):
 replaced.append(str(p.relative_to('patch/src')).replace('.java',''))
for n in [4,5,6,7,8,11]:
 target=out/str(n)
 if target.exists():shutil.rmtree(target)
 shutil.copytree(base/('smali_classes'+str(n)),target)
 for name in replaced:
  p=target/(name+'.smali')
  if p.exists():p.unlink()
  for x in p.parent.glob(p.stem+'$*.smali'):x.unlink()

def update(n,name,fn):
 p=out/str(n)/(name+'.smali');s=p.read_text();s=fn(s);p.write_text(s)
def prelude(s,signature,code):
 a=s.index(signature);m=re.search(r'    \.locals \d+\n',s[a:]);assert m
 pos=a+m.end();return s[:pos]+code+s[pos:]

def chart(s):
 a=s.index('.method public final position(');b=s.index('    check-cast v7, Lio/github/phiscript/engine/Point;',a)
 pos=s.index('    return-object v7',b)
 hook='''    move-object/from16 v1, p0
    move-object/from16 v2, p1
    move-wide/from16 v3, p2
    move-object/from16 v5, p4
    move/from16 v6, p5
    invoke-static/range {v1 .. v7}, Lio/github/phiscript/input/TapMod;->adjustNote(Lio/github/phiscript/engine/Chart;Lio/github/phiscript/engine/Note;DLio/github/phiscript/engine/Viewport;ZLio/github/phiscript/engine/Point;)Lio/github/phiscript/engine/Point;
    move-result-object v7
'''
 return s[:pos]+hook+s[pos:]
update(7,'io/github/phiscript/engine/Chart',chart)
for cls,arg in [('Chart$Companion','Ljava/lang/String;'),('PhiraNativeChart$Companion','Lorg/json/JSONObject;')]:
 def wrap(s):
  sig='parse('+arg+')Lio/github/phiscript/engine/Chart;'
  s=s.replace('.method public final '+sig,'.method private final '+sig.replace('parse(','parseImpl('),1)
  s+='''\n.method public final '''+sig+'''
    .locals 1
    :try_start_patch
    invoke-direct {p0, p1}, Lio/github/phiscript/engine/'''+cls+''';->'''+sig.replace('parse(','parseImpl(')+'''
    move-result-object v0
    :try_end_patch
    invoke-static {}, Lio/github/phiscript/engine/BlockAreaSet;->clear()V
    return-object v0
    :catch_patch
    move-exception v0
    invoke-static {}, Lio/github/phiscript/engine/BlockAreaSet;->clear()V
    throw v0
    .catchall {:try_start_patch .. :try_end_patch} :catch_patch
.end method
''';return s
 update(7,'io/github/phiscript/engine/'+cls,wrap)
update(6,'io/github/phiscript/assets/ChartLibrary',lambda s:prelude(prelude(s,'.method public final declared-synchronized load(','''
    invoke-static/range {p0 .. p2}, Lio/github/phiscript/assets/ExternalCharts;->load(Lio/github/phiscript/assets/ChartLibrary;Ljava/lang/String;Ljava/lang/String;)Lio/github/phiscript/engine/Chart;
    move-result-object v0
    if-eqz v0, :original_load
    return-object v0
    :original_load
'''),'.method public final songs(','''
    invoke-static/range {p0 .. p0}, Lio/github/phiscript/assets/ExternalCharts;->songs(Lio/github/phiscript/assets/ChartLibrary;)Ljava/util/List;
    move-result-object v0
    if-eqz v0, :original_songs
    return-object v0
    :original_songs
'''))
def activity(s):
 assert '.method protected onActivityResult' not in s
 s+='''\n.method protected onActivityResult(IILandroid/content/Intent;)V
    .locals 1
    invoke-static {p0, p1, p2, p3}, Lio/github/phiscript/assets/ExternalCharts;->result(Landroid/app/Activity;IILandroid/content/Intent;)Z
    move-result v0
    if-eqz v0, :original_result
    return-void
    :original_result
    invoke-super {p0, p1, p2, p3}, Landroidx/activity/ComponentActivity;->onActivityResult(IILandroid/content/Intent;)V
    return-void
.end method
'''
 return prelude(s,'.method private final taskBusy()', '''
    invoke-static {}, Lio/github/phiscript/assets/ExternalCharts;->isBusy()Z
    move-result v0
    if-eqz v0, :original_busy
    return v0
    :original_busy
''')
update(4,'io/github/phiscript/MainActivity',activity)
print('patched smali in',out)

# The largest supplied processed chart has 477755 native keyframes. Retain a bounded one-million-frame budget.
update(7,'io/github/phiscript/engine/NativeAnim$Companion',lambda s:s.replace('const v10, 0x61a80','const v10, 0xf4240'))

# Keep legacy mode writes from the original Phira UI consistent with all entry points.
def routing_preferences(text):
 for signature,method in [('.method public final save(Ljava/lang/String;Ljava/lang/String;)V','onSaved(Lio/github/phiscript/AppSettings;Ljava/lang/String;Ljava/lang/String;)V'),('.method public final saveFlag(Ljava/lang/String;Z)V','onFlagSaved(Lio/github/phiscript/AppSettings;Ljava/lang/String;Z)V')]:
  a=text.index(signature);b=text.index('.end method',a);fragment=text[a:b];assert fragment.count('return-void')==1
  fragment=fragment.replace('    return-void','    invoke-static/range {p0 .. p2}, Lio/github/phiscript/root/ModeRouting;->'+method+'\n    return-void')
  text=text[:a]+fragment+text[b:]
 return text
update(4,'io/github/phiscript/AppSettings',routing_preferences)

def apk_source_success(text):
 import re
 pieces=re.split(r'(?=^\.method )',text,flags=re.M);changed=0
 for i,part in enumerate(pieces):
  signature=part.split('\n',1)[0]
  if not ('importApks$lambda$42$lambda$41' in signature or 'scan$lambda$35$lambda$34' in signature):continue
  lines=part.splitlines(True);result=[]
  for line in lines:
   result.append(line)
   if 'RuntimeState;->setLibrary(' in line:result.append('    invoke-static/range {p1 .. p1}, Lio/github/phiscript/assets/ExternalCharts;->onApkLibrary(Landroid/app/Activity;)V\n');changed+=1
  pieces[i]=''.join(result)
 assert changed==3,changed
 return ''.join(pieces)
update(4,'io/github/phiscript/MainActivity',apk_source_success)

# Import writes and bounded reads already accept 256 MiB of normalized JSON.
# Kotlin inlined the older 8 MiB limit separately into playback and restore;
# both must use the same inclusive limit, while length and SHA-256 stay intact.
def imported_chart_limit(text):
 old='const v5, 0x800001'
 assert text.count(old)==1, 'expected exactly one stale import size check'
 return text.replace(old,'const v5, 0x10000001')
for cls in ['PhiraLibrary','PhiraLibrary$Companion']:
 update(6,'io/github/phiscript/assets/'+cls,imported_chart_limit)

def replace_method(text,signature,body):
 a=text.index(signature);b=text.index('.end method',a)+len('.end method')
 return text[:a]+signature+'\n'+body+'\n.end method'+text[b:]

def native_projection(text):
 for method in ['position','normal']:
  signature='.method public final declared-synchronized '+method+'(Lio/github/phiscript/engine/Note;DLio/github/phiscript/engine/Viewport;)Lio/github/phiscript/engine/Point;'
  text=replace_method(text,signature,'''    .locals 1
    invoke-static/range {p0 .. p4}, Lio/github/phiscript/engine/NativeChartProjection;->'''+method+'''(Ljava/lang/Object;Lio/github/phiscript/engine/Note;DLio/github/phiscript/engine/Viewport;)Lio/github/phiscript/engine/Point;
    move-result-object v0
    return-object v0''')
 return text
update(7,'io/github/phiscript/engine/PhiraNativeChart',native_projection)

def clamp_animation(text):
 start=text.index('.method public final value(D)D')
 anchor=text.index('    :cond_4\n',start)+len('    :cond_4\n')
 text=text[:anchor]+'''    invoke-virtual {v11}, Lio/github/phiscript/engine/NativeKeyframe;->getTime()D
    move-result-wide v13
    cmpg-double v8, p1, v13
    if-gez v8, :r6_native_track_started
    invoke-virtual {v11}, Lio/github/phiscript/engine/NativeKeyframe;->getValue()D
    move-result-wide v12
    goto :goto_3
    :r6_native_track_started
'''+text[anchor:]
 needle='    div-double/2addr v13, v15'
 assert text.count(needle)==1
 return text.replace(needle,needle+'\n    invoke-static/range {v13 .. v14}, Lio/github/phiscript/engine/NativeChartProjection;->unit(D)D\n    move-result-wide v13')
update(7,'io/github/phiscript/engine/NativeAnim',clamp_animation)

def additive_layers(text):
 # Phira Pro sums valid layers even when earlier or intermediate layers are empty.
 anchor='    :goto_1\n'
 assert text.count(anchor)==1
 text=text.replace(anchor,anchor+'    const/4 v2, 0x0\n')
 a=text.index('    if-nez v9, :cond_7')+len('    if-nez v9, :cond_7')
 b=text.index('    :cond_7',a)
 return text[:a]+'\n    goto/16 :goto_8\n\n'+text[b:]
update(7,'io/github/phiscript/engine/NativeAnim$Companion',additive_layers)

def phira_preparation(text):
 old='invoke-static/range {p3 .. p3}, Lio/github/phiscript/input/GesturePlan;->preflight(Lio/github/phiscript/engine/Chart;)V'
 assert text.count(old)==1
 text=text.replace(old,'invoke-static/range {p0 .. p3}, Lio/github/phiscript/input/SharedChartPlayback;->prepare(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lio/github/phiscript/engine/Chart;)V')
 text=replace_method(text,'.method private final control(FFILio/github/phiscript/capture/PhiraSessionEngine$Selection;)J','''    .locals 2
    invoke-static/range {p0 .. p4}, Lio/github/phiscript/input/SharedChartPlayback;->control(Ljava/lang/Object;FFILjava/lang/Object;)J
    move-result-wide v0
    return-wide v0''')
 a=text.index('.method public final run()V');b=text.index('.end method',a)
 fragment=text[a:b]
 import re
 fragment=re.sub(r'(?m)^(    )(return-void|throw v\d+)$',r'\1invoke-static/range {p0 .. p0}, Lio/github/phiscript/input/SharedChartPlayback;->close(Ljava/lang/Object;)V\n\1\2',fragment)
 return text[:a]+fragment+text[b:]
update(5,'io/github/phiscript/capture/PhiraSessionEngine',phira_preparation)

# Both games execute this exact state machine. Only chart metadata, identity,
# storage and target package are supplied by the source adapter.
def shared_phira_session(text):
 engine='Lio/github/phiscript/capture/PhiraSessionEngine;'
 source='Lio/github/phiscript/assets/SessionChartSource;'
 old_library='Lio/github/phiscript/assets/PhiraLibrary;'
 signature='.method public constructor <init>(Lio/github/phiscript/capture/FrameStore;'+old_library+'Lio/github/phiscript/SessionSettings;ZLjava/util/concurrent/atomic/AtomicBoolean;Lio/github/phiscript/capture/RecognitionOverlay;)V'
 assert text.count(signature)==1
 new_init=signature[len('.method public constructor '):].replace(old_library,source)
 text=text.replace(old_library,source)
 original_selection='invoke-virtual {v2}, Lio/github/phiscript/SessionSettings;->getPhiraChartId()Ljava/lang/String;'
 assert text.count(original_selection)==1
 text=text.replace(original_selection,'invoke-direct/range {p0 .. p0}, '+engine+'->sourceSelectionId()Ljava/lang/String;')
 text=replace_method(text,'.method private final isForeground()Z','''    .locals 1
    iget-object v0, p0, '''+engine+'''->library:'''+source+'''
    invoke-virtual {v0}, '''+source+'''->isForeground()Z
    move-result v0
    return v0''')
 text=replace_method(text,'.method private final match(Lio/github/phiscript/vision/OcrLabels;)Lio/github/phiscript/vision/PhiraCandidate;','''    .locals 2
    iget-object v0, p0, '''+engine+'''->library:'''+source+'''
    iget-object v1, p0, '''+engine+'''->candidates:Ljava/util/List;
    invoke-virtual {v0, p1, v1}, '''+source+'''->match(Lio/github/phiscript/vision/OcrLabels;Ljava/util/List;)Lio/github/phiscript/vision/PhiraCandidate;
    move-result-object v0
    return-object v0''')
 text+='\n'+signature+'''
    .locals 0
    invoke-static/range {p2 .. p2}, '''+source+'''->phira('''+old_library+''')'''+source+'''
    move-result-object p2
    invoke-direct/range {p0 .. p6}, '''+engine+'''->'''+new_init+'''
    return-void
.end method

.method private final sourceSelectionId()Ljava/lang/String;
    .locals 2
    iget-object v0, p0, '''+engine+'''->library:'''+source+'''
    iget-object v1, p0, '''+engine+'''->settings:Lio/github/phiscript/SessionSettings;
    invoke-virtual {v0, v1}, '''+source+'''->selectionId(Lio/github/phiscript/SessionSettings;)Ljava/lang/String;
    move-result-object v0
    return-object v0
.end method
'''
 # Shared user-facing messages must not name the other game's library.
 def neutral_message(match):
  import json
  try:message=json.loads('"'+match.group(2)+'"')
  except ValueError:return match.group(0)
  if 'Phira' not in message:return match.group(0)
  if message=='请先导入 Phira ZIP/PEZ 谱面包':message='请先选择已导入或联网谱库'
  else:message=message.replace('Phira 谱面模式','谱面模式').replace('Phira ','').replace('Phira','当前游戏')
  return match.group(1)+json.dumps(message,ensure_ascii=True)[1:-1]+match.group(3)
 return re.sub(r'(?m)^(    const-string(?:/jumbo)? [^,]+, ")((?:\\.|[^"\\\n])*)(")$',neutral_message,text)
update(5,'io/github/phiscript/capture/PhiraSessionEngine',shared_phira_session)

# The shared session keeps its gates and clock; menu appearance is supplied by
# the target game. Preserve the exact original Phira constructor as a delegate.
def game_pause_menu(text):
 engine='Lio/github/phiscript/capture/PhiraSessionEngine;'
 original='Lio/github/phiscript/vision/PhiraPauseDetector;'
 adapter='Lio/github/phiscript/capture/SessionPauseMenu;'
 selection='Lio/github/phiscript/capture/PhiraSessionEngine$Selection;'
 signature='.method private final pauseMenuFor('+selection+')'+original
 a=text.index(signature);b=text.index('.end method',a)+len('.end method')
 delegate=text[a:b].replace(' pauseMenuFor(',' phiraPauseMenuFor(',1)
 text=text[:a]+text[b:]
 text=text.replace(original,adapter)
 text+='\n'+delegate+'\n\n.method private final pauseMenuFor('+selection+')'+adapter+'''
    .locals 1
    invoke-static/range {p0 .. p1}, '''+adapter+'''->forSession(Ljava/lang/Object;Ljava/lang/Object;)'''+adapter+'''
    move-result-object v0
    return-object v0
.end method

.method private final activeRetryPoint()Lio/github/phiscript/vision/NormalizedPoint;
    .locals 1
    iget-object v0, p0, '''+engine+'''->pauseMenu:'''+adapter+'''
    invoke-virtual {v0}, '''+adapter+'''->retryPoint()Lio/github/phiscript/vision/NormalizedPoint;
    move-result-object v0
    return-object v0
.end method
'''
 text=prelude(text,'.method private final pauseRoiFor(','''
    invoke-static/range {p0 .. p0}, '''+adapter+'''->phigrosPauseRegion(Ljava/lang/Object;)Landroid/graphics/RectF;
    move-result-object v0
    if-eqz v0, :r9_phira_pause_region
    return-object v0
    :r9_phira_pause_region
''')
 a=text.index('.method private final prepareRetry(');b=text.index('.end method',a)
 body=text[a:b]
 pattern=r'invoke-virtual \{[^}]+\}, Lio/github/phiscript/SessionSettings;->getPhiraRestartPoint\(\)Lio/github/phiscript/vision/NormalizedPoint;'
 body,count=re.subn(pattern,'invoke-direct/range {p0 .. p0}, '+engine+'->activeRetryPoint()Lio/github/phiscript/vision/NormalizedPoint;',body)
 assert count==2,count
 return text[:a]+body+text[b:]
update(5,'io/github/phiscript/capture/PhiraSessionEngine',game_pause_menu)

# A rejected crossing is an unmatched incoming note. Stop comparing that note
# immediately, before a later pair could dereference live.get(-1). The caller
# already turns an unmatched note into a separate contact.
def stop_rejected_crossing(text):
 signature='.method private static assign('
 a=text.index(signature);b=text.index('.end method',a)
 body=text[a:b]
 old='''    const/4 v0, -0x1

    aput v0, v5, v4

    goto :goto_6'''
 new=old.replace('goto :goto_6','goto :goto_7')
 assert body.count(old)==1, 'crossing rejection changed'
 assert body.count(':goto_7\n    add-int/lit8 v4, v4, 0x1')==1
 body=body.replace(old,new)
 return text[:a]+body+text[b:]
update(8,'io/github/phiscript/input/UnifiedGesturePlanner',stop_rejected_crossing)

# Remove the obsolete per-note timing side table from APK imports and delegate
# every route to the same device settings screen (without a second retry edit).
def no_timing_import(text):
 needle='    invoke-static {v0, v14}, Lio/github/phiscript/assets/ChartTimingIndex;->importLibrary(Landroid/content/Context;Lio/github/phiscript/assets/ChartLibrary;)V'
 assert text.count(needle)==1
 return text.replace(needle,'    # No legacy timing side table; playback reads the selected chart.')
update(6,'io/github/phiscript/assets/ChartLibrary$Companion',no_timing_import)
update(4,'io/github/phiscript/MainActivity',lambda text:replace_method(text,'.method private final calibration()V','''    .locals 0
    invoke-static {p0}, Lio/github/phiscript/root/RecognitionSettings;->open(Landroid/app/Activity;)V
    return-void'''))

# These classes have no remaining callers after the shared-session migration.
# Dependency restoration/build validation fails if a retained class needs one.
for obsolete in ['assets/ChartTimingIndex','root/FirstNoteBrowser','capture/PhigrosStartGate','capture/PhigrosControls','capture/PhigrosHudProbe','capture/SharedPhiraControls']:
 path=out/'8'/('io/github/phiscript/'+obsolete+'.smali')
 assert path.is_file(),obsolete
 path.unlink()
 for nested in path.parent.glob(path.stem+'$*.smali'):nested.unlink()

from r12_hooks import wire
wire(update,out,prelude)

update(8,'io/github/phiscript/root/PhiraExport',lambda s:replace_method(s,'.method public static roots(I)Ljava/util/List;','''    .locals 1
    invoke-static {p0}, Lio/github/phiscript/root/PhiraChartRoots;->roots(I)Ljava/util/List;
    move-result-object v0
    return-object v0'''))
