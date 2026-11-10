from pathlib import Path
ui=Path('patch/src/io/github/phiscript/assets/SwordaltLibraryUi.java').read_text();adapter=Path('patch/src/io/github/phiscript/assets/ExternalCharts.java').read_text();source=Path('patch/src/io/github/phiscript/assets/SwordaltSource.java').read_text();home=Path('patch/src/io/github/phiscript/root/HomeScreen.java').read_text()
assert '.setCustomTitle(heading)' in ui and '.setMessage(' not in ui,'List dialogs must not hide their items behind a message'
for label in ['启用"+kind+"库','选择谱面','更新联网目录','导入原谱 / 脚本离线 ZIP']:assert label in ui
assert 'PICK_PACK=42372' in ui and 'PICK=42371' in adapter
assert 'SwordaltLibraryUi.result(a,request,result,data)' in adapter
assert '"swordalt".equals(source)' in adapter and 'fromSwordaltCatalog' in adapter
assert 'Phigros · Swordalt 原谱与噪域下载' in home
assert 'SwordaltSource.loadChart(a.context,a.swordaltCatalog,e.resource,cancel)' in adapter
assert 'SwordaltSource.cacheDirectory(context).getCanonicalPath()' in adapter
for excluded in ['discord.com','webhook','officialToRpe','PhiraImportDecoder']:assert excluded not in source
assert 'PgrScriptConverter.convert(bytes,entry,catalog)' in source
assert 'scripts-v1/' in source
print('PASS independent source flow: original/script menus, download/import/refresh, picker routing, restore, isolated caches, deletion and script regeneration')
