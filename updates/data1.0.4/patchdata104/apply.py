"""Apply once to a fresh data1.0.3 decode. Display localization never changes OCR/data tokens."""
from pathlib import Path
import re,json
ROOT=Path(__file__).resolve().parents[1]
app=ROOT/'analysis/data103'
yml=app/'apktool.yml';s=yml.read_text();assert 'versionCode: 19' in s and 'versionName: data1.0.3' in s
yml.write_text(s.replace('versionCode: 19','versionCode: 20').replace('versionName: data1.0.3','versionName: data1.0.4'))
changed=set();counts={'display_bridges':0,'parser_checks':0}
def mutate(p,s):
 if p.read_text()!=s:
  p.write_text(s);folder=p.relative_to(app).parts[0];changed.add('classes.dex' if folder=='smali' else folder.replace('smali_','')+'.dex')

touch=app/'smali_classes7/io/github/phiscript/input/TouchService.smali';s=touch.read_text()
start=s.index('.method public final tapNormalized(');end=s.index('.end method',start)
method=s[start:end];assert method.count('const-wide/16 v20, 0x23')==1
s=s[:start]+method.replace('const-wide/16 v20, 0x23','const-wide/16 v20, 0x37')+s[end:];mutate(touch,s)

main=app/'smali_classes4/io/github/phiscript/MainActivity.smali';s=main.read_text()
old='invoke-virtual {v2, p1, p2, v3}, Lio/github/phiscript/assets/PhiraLibrary$Companion;->importFiles(Landroid/content/Context;Ljava/util/List;Lkotlin/jvm/functions/Function1;)Lio/github/phiscript/assets/PhiraLibrary;'
assert s.count(old)==1
s=s.replace(old,'invoke-static {p1, p2}, Lio/github/phiscript/root/PlayEntry;->importPhira(Landroid/content/Context;Ljava/util/List;)Lio/github/phiscript/assets/PhiraLibrary;')
mutate(main,s)
application=app/'smali_classes4/io/github/phiscript/PhigrosApplication.smali';s=application.read_text()
needle='invoke-super {p0}, Landroid/app/Application;->onCreate()V';assert s.count(needle)==1
s=s.replace(needle,needle+'\n\n    invoke-static {p0}, Lio/github/phiscript/i18n/UiText;->attach(Landroid/content/Context;)V');mutate(application,s)

# Static bridges keep exactly the same register operands and return type.
D='Landroid/app/AlertDialog$Builder;';N='Landroid/app/Notification$Builder;'
bridges={
 ('setText','(Ljava/lang/CharSequence;)V'):('text','(Landroid/widget/TextView;Ljava/lang/CharSequence;)V'),
 ('setText','(I)V'):('textRes','(Landroid/widget/TextView;I)V'),
 ('setText','(Ljava/lang/CharSequence;Landroid/widget/TextView$BufferType;)V'):('textBuffer','(Landroid/widget/TextView;Ljava/lang/CharSequence;Landroid/widget/TextView$BufferType;)V'),
 ('setContentDescription','(Ljava/lang/CharSequence;)V'):('description','(Landroid/view/View;Ljava/lang/CharSequence;)V'),
 ('setTitle','(Ljava/lang/CharSequence;)'+D):('title','('+D+'Ljava/lang/CharSequence;)'+D),
 ('setMessage','(Ljava/lang/CharSequence;)'+D):('message','('+D+'Ljava/lang/CharSequence;)'+D),
 ('setPositiveButton','(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)'+D):('positive','('+D+'Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)'+D),
 ('setNegativeButton','(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)'+D):('negative','('+D+'Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)'+D),
 ('setNeutralButton','(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)'+D):('neutral','('+D+'Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)'+D),
 ('setItems','([Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)'+D):('items','('+D+'[Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)'+D),
 ('setSingleChoiceItems','([Ljava/lang/CharSequence;ILandroid/content/DialogInterface$OnClickListener;)'+D):('choices','('+D+'[Ljava/lang/CharSequence;ILandroid/content/DialogInterface$OnClickListener;)'+D),
 ('makeText','(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;'):('toast','(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;'),
 ('setContentTitle','(Ljava/lang/CharSequence;)'+N):('noticeTitle','('+N+'Ljava/lang/CharSequence;)'+N),
 ('setContentText','(Ljava/lang/CharSequence;)'+N):('noticeText','('+N+'Ljava/lang/CharSequence;)'+N),
}
call=re.compile(r'(?m)^(\s*)invoke-(virtual|static)(/range)? (\{[^}\n]+\}), (Landroid/(?:widget|view|app)/[^;]+;)->([^\s(]+)(\([^\n]+)$')
def display(m):
 indent,opcode,ranged,registers,owner,name,sig=m.groups();mapping=bridges.get((name,sig))
 if mapping is None:return m.group()
 if name=='setText' and not owner.startswith('Landroid/widget/'):return m.group()
 if name=='makeText' and owner!='Landroid/widget/Toast;':return m.group()
 counts['display_bridges']+=1
 return indent+'invoke-static'+(ranged or '')+' '+registers+', Lio/github/phiscript/i18n/NativeUi;->'+mapping[0]+mapping[1]
def checks(method):
 out=[]
 for line in method.splitlines():
  target=re.search(r'^\s*invoke-(?:virtual|interface)(?:/range)? .*?, (?:Ljava/io/[^;]+;->read|Ljava/util/Iterator;->(?:hasNext|next)|Lorg/json/(?:JSONArray|JSONObject);->(?:get|opt|length)|Ljava/lang/String;->(?:charAt|length))',line)
  if target:
   out.append('    invoke-static {}, Lio/github/phiscript/root/PhiraImportControl;->check()V');counts['parser_checks']+=1
  out.append(line)
 return '\n'.join(out)
for folder in sorted(app.glob('smali*')):
 if folder.name=='smali_classes8':continue # Replaced by newly compiled helpers.
 for p in (folder/'io/github/phiscript').rglob('*.smali'):
  s=p.read_text();s=call.sub(display,s)
  rel=str(p.relative_to(folder))
  if ('/assets/' in rel and any(x in p.name for x in ('Phira','RpeNative','ChartJson'))) or ('/engine/' in rel and 'PhiraNativeChart' in p.name):
   s=re.sub(r'(?ms)^\.method\b.*?^\.end method',lambda m:checks(m.group()),s)
  mutate(p,s)
report={'changed_dex':sorted(changed),'counts':counts}
(ROOT/'patchdata104/hook-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
