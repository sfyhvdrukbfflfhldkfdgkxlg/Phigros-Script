from pathlib import Path
root=Path('patch/src/io/github/phiscript')
entry=(root/'root/PlayEntry.java').read_text();ext=(root/'assets/ExternalCharts.java').read_text();router=(root/'capture/AutoSessionRouter.java').read_text();home=(root/'root/HomeScreen.java').read_text()
assert 'ModeRouting.kind(prefs)' in entry and 'ModeRouting.needsPhigros(prefs)' in entry
assert 'if(!ModeRouting.needsPhigros(new AppSettings(a)))return true;' in ext
assert 'if(result!=null)' in ext and 'commitPhigros(a,result,mode)' in ext
for name in ['useLocal','useCloud','useSwordalt']:
 start=ext.index('public static void '+name+'(');end=ext.index('\n',start)
 assert 'commitPhigros(' in ext[start:end],name
assert 'ModeRouting.kind(new AppSettings(context))' in (root/'capture/SessionFeedback.java').read_text()
assert 'settings.getMode()' in router and 'ModeRouting.autoEngine' in router
assert 'ModeRouting.migrateLegacy(prefs)' in (root/'root/RootPanel.java').read_text()
assert 'ExternalCharts.onApkLibrary(activity)' in (root/'root/RootPanel.java').read_text()
assert 'ExternalCharts.useApk(activity);' not in home,'APK source may only change after successful import'
assert '取消 Phira 导入' not in home,'shared import status must not identify the wrong game'
smali=Path('patch/smali/4/io/github/phiscript/AppSettings.smali').read_text();assert 'ModeRouting;->onSaved' in smali and 'ModeRouting;->onFlagSaved' in smali
activity=Path('patch/smali/4/io/github/phiscript/MainActivity.smali').read_text();assert activity.count('ExternalCharts;->onApkLibrary')==3
assert 'new File(super.getFilesDir(),"phigros-packages-v1")' in ext
assert 'setPhiraLibrary(' not in ext,'Phigros import must not publish into the Phira runtime library'
print('PASS mode wiring audit: local/cloud/Swordalt/APK success, isolated storage, restore, legacy migration, startup, service dispatch, original Phira writes, shared statuses')
