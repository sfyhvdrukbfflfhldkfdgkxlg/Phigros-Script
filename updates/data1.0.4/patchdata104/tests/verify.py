"""DEX structural/linkage audit against the actual uploaded package and Android SDK."""
from pathlib import Path
import re,zipfile,hashlib,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
original_verifier=ROOT/'patch/tests/verify_apk.py'
ns={'__file__':str(original_verifier)}
exec(compile(original_verifier.read_text().split('with zipfile.ZipFile(apk) as z:')[0],str(original_verifier),'exec'),ns)
dex=ns['dex'];sdk_class=ns['sdk_class']
apk=ROOT/'deliverables/PhigrosScript_data1.0.4.apk'
with zipfile.ZipFile(apk) as z,zipfile.ZipFile(ROOT/'current103/PhigrosScript_data1.0.3.apk') as src:
    assert z.testzip() is None
    definitions={};helper_fields=[];helper_methods=[];hook_methods=[]
    for name in z.namelist():
        if re.fullmatch(r'classes\d*\.dex',name):
            found,fields,methods=dex(z.read(name));assert not set(found)&set(definitions),('duplicate class',name)
            definitions.update(found)
            if name=='classes8.dex':
                assert all(t.startswith('Lio/github/phiscript/') for t in found),'test/mock type packaged'
                helper_fields=fields;helper_methods=methods
            else:hook_methods.extend(m for m in methods if m[0].startswith(('Lio/github/phiscript/record/','Lio/github/phiscript/root/','Lio/github/phiscript/capture/AutoSessionRouter;','Lio/github/phiscript/capture/SessionFeedback;','Lio/github/phiscript/capture/OverlayHost;','Lio/github/phiscript/input/ForegroundEvents;','Lio/github/phiscript/input/GesturePlan;','Lio/github/phiscript/i18n/')))
    count=len(definitions)
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
    for kind,items in [('fields',helper_fields),('methods',helper_methods+hook_methods)]:
        for owner,name,signature in items:
            if owner.startswith('['):continue
            assert resolve(owner,kind,(name,signature)),('unresolved',owner,name,signature)
    changed=[];retained=0
    for name in src.namelist():
        if name.startswith('META-INF/') and name.upper().endswith(('.RSA','.DSA','.EC','.SF','MANIFEST.MF')):continue
        assert name in z.namelist(),('missing user entry',name)
        if src.read(name)!=z.read(name):changed.append(name)
        else:retained+=1
    assert set(changed)=={'AndroidManifest.xml','classes4.dex','classes5.dex','classes6.dex','classes7.dex','classes8.dex','classes9.dex'},changed
    assert ('choose','(Landroid/app/Activity;)V') in definitions['Lio/github/phiscript/root/RootPanel;']['methods']
    assert ('importPhira','(Landroid/content/Context;Ljava/util/List;)Lio/github/phiscript/assets/PhiraLibrary;') in definitions['Lio/github/phiscript/root/PlayEntry;']['methods']
    assert ('process','Ljava/lang/Process;') in definitions['Lio/github/phiscript/root/RootClient;']['fields'],'mock root client packaged'
    assert ('codec','Landroid/media/MediaCodec;') in definitions['Lio/github/phiscript/record/BitmapVideoEncoder;']['fields'],'mock encoder packaged'
    assert ('projection','Landroid/media/projection/MediaProjection;') in definitions['Lio/github/phiscript/record/ManualRecordingService;']['fields'],'real projection service missing'
    assert ('run','()V') in definitions['Lio/github/phiscript/capture/PhigrosAutoEngine;']['methods']
    assert ('client','Lio/github/phiscript/root/RootClient;') in definitions['Lio/github/phiscript/root/RootWarmup;']['fields'],'mock warm channel packaged'
    main_definition=definitions['Lio/github/phiscript/MainActivity;']
    for name in ('chooseApks','choosePhira','choosePhiraChart','calibration','editAlias','enableAccessibilityWithShizuku','accessibilityHelp','captureAccessibilityFailure','taskBusy'):
        signature='()Z' if name=='taskBusy' else '()V'
        assert (name,signature) in main_definition['methods'],('missing home callback',name)
    assert ('scan','(Lkotlin/jvm/functions/Function0;)V') in main_definition['methods']
    assert ('startSession','(Z)V') in main_definition['methods']
    for name in ('statusView','accessibilityView','logView'):
        assert (name,'Landroid/widget/TextView;') in main_definition['fields'],('missing live home field',name)
    assert ('attach','(Landroid/app/Activity;)V') in definitions['Lio/github/phiscript/root/HomeScreen;']['methods']
    assert ('start','(Landroid/app/Activity;Z)V') in definitions['Lio/github/phiscript/root/PlayEntry;']['methods']
    controls_definition=definitions['Lio/github/phiscript/capture/SharedPhiraControls;']
    for name,signature in [('menu','Lio/github/phiscript/vision/PhiraPauseDetector;'),('game','Lio/github/phiscript/vision/GameplayDetector;'),('hud','Lio/github/phiscript/capture/PhigrosHudProbe;')]:
        assert (name,signature) in controls_definition['fields'],('real shared detector missing',name)
    assert 'Lio/github/phiscript/capture/PhigrosRetryControls;' not in definitions,'independent old OCR retry controller still packaged'
    overlay_fields=definitions['Lio/github/phiscript/capture/RecognitionOverlay;']['fields']
    assert ('panel','Landroid/widget/LinearLayout;') in overlay_fields and ('label','Landroid/widget/TextView;') in overlay_fields
    assert ('overlay','Lio/github/phiscript/capture/RecognitionOverlay;') in definitions['Lio/github/phiscript/capture/AutoOverlayFeedback;']['fields']
    assert ('captureStopped','(Landroid/content/Context;Ljava/lang/String;)V') in definitions['Lio/github/phiscript/capture/SessionFeedback;']['methods']
    assert ('stop','Ljava/util/concurrent/atomic/AtomicBoolean;') in definitions['Lio/github/phiscript/capture/CaptureService;']['fields']
print('PASS ZIP CRC, all DEX checksums,',count,'unique app classes')
print('PASS all helper fields/methods and added cross-DEX hooks resolve against packaged classes and Android SDK')
print('PASS unified native home, all reflected Activity actions and retained live status/log fields resolve against actual APK')
print('PASS actual shared Phira three-button detector and GameplayDetector; original detector DEX retained byte-for-byte')
print('PASS actual native overlay reflection fields and persistent failure/notification helper links')
print('PASS',retained,'data1.0.3 entries retained byte-for-byte; only manifest and DEX 4/5/6/7/8/9 changed')
app=ROOT/'analysis/data103'
main=(app/'smali_classes4/io/github/phiscript/MainActivity.smali').read_text()
assert main.count('RootPanel;->attach(Landroid/app/Activity;)V')==1
imports=(app/'smali_classes9/io/github/phiscript/PhiraRootImport.smali').read_text()
for name in ['choosePlayMode','playPhigros','importAll']:
    method=re.search(r'(?m)^\.method [^\n]*'+name+r'\(.*?^\.end method',imports,re.S).group()
    assert 'PlayEntry;->' in method and 'getField' not in method and 'getMethod' not in method
manifest=ET.parse(app/'AndroidManifest.xml').getroot();A='{http://schemas.android.com/apk/res/android}'
for tag,name in [('activity','RecordingConsentActivity'),('service','ManualRecordingService'),('provider','RecordingProvider')]:
    component=next(e for e in manifest.find('application').findall(tag) if e.get(A+'name')=='io.github.phiscript.record.'+name)
    assert component.get(A+'exported')=='false'
assert not any(e.get(A+'name') in ('android.permission.RECORD_AUDIO','android.permission.WRITE_EXTERNAL_STORAGE','android.permission.MANAGE_EXTERNAL_STORAGE') for e in manifest.findall('uses-permission'))
capture=(app/'smali_classes5/io/github/phiscript/capture/CaptureService.smali').read_text()
assert capture.count('Lio/github/phiscript/capture/AutoSessionRouter;')==3
assert capture.count('SessionFeedback;->captureStopped(Landroid/content/Context;Ljava/lang/String;)V')==1
assert 'versionName: data1.0.4' in (app/'apktool.yml').read_text()
print('PASS actual capture-to-auto-engine route, manual fallback, original Phira/recording entry points and non-exported recording components')
print('PASS no microphone/broad storage permission added')
overlay=(app/'smali_classes5/io/github/phiscript/capture/RecognitionOverlay.smali').read_text()
for method in ('context','allowed','type','remove'):
    assert 'OverlayHost;->'+method+'(' in overlay,('missing overlay hook',method)
assert 'ForegroundEvents;->packageFor(' in (app/'smali_classes7/io/github/phiscript/input/TouchService.smali').read_text()
assert 'RootPhiraImport;->start(Landroid/app/Activity;)V' in re.search(r'(?ms)^\.method public static start\(.*?^\.end method',imports).group()
assert ('preflight','(Lio/github/phiscript/engine/Chart;)V') in definitions['Lio/github/phiscript/input/GesturePlan;']['methods']
assert ('rebind','(Lio/github/phiscript/capture/RecognitionOverlay;Landroid/content/Context;)V') in definitions['Lio/github/phiscript/capture/OverlayHost;']['methods']
assert ('context','Landroid/content/Context;') in overlay_fields and ('manager','Landroid/view/WindowManager;') in overlay_fields
print('PASS actual service-owned overlay and focus hooks, reconnect route, Root Phira import route and pre-clock gesture planning')
print('SHA256',hashlib.sha256(apk.read_bytes()).hexdigest())
print('APK bytes',apk.stat().st_size)

assert ("controls","Lio/github/phiscript/capture/PhigrosControls;") in definitions["Lio/github/phiscript/capture/PhigrosAutoEngine;"]["fields"]
assert "Lio/github/phiscript/i18n/EnglishCatalog;" in definitions
assert "Lio/github/phiscript/root/PhiraImportControl;" in definitions
assert "PlayEntry;->importPhira(Landroid/content/Context;Ljava/util/List;)" in main
print("PASS independent Phigros controls, native import cancellation bridge and English display bridges")
