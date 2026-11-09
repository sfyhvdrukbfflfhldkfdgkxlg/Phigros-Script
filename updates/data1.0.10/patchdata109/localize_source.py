"""Idempotent display-boundary localization, leaving OCR/data tokens intact."""
from pathlib import Path
import re
root=Path(__file__).resolve().parent/'src'
methods='setText setTitle setMessage setPositiveButton setNegativeButton setNeutralButton setItems setSingleChoiceItems setContentTitle setContentText setContentDescription bigText'.split()
pattern=re.compile(r'\.(?:'+'|'.join(methods)+r')\s*\(|Toast\.makeText\s*\(|new NotificationChannel\s*\(')
total=0
for path in root.rglob('*.java'):
 if 'i18n' in path.parts:continue
 source=path.read_text();edits=[]
 for match in pattern.finditer(source):
  start=match.end();depth=0;quote=None;escape=False;args=[];arg=start
  for i in range(start,len(source)):
   c=source[i]
   if quote:
    if escape:escape=False
    elif c=='\\':escape=True
    elif c==quote:quote=None
    continue
   if c in ('"',"'"):quote=c
   elif c in '({[':depth+=1
   elif c in ')}]':
    if c==')' and depth==0:args.append((arg,i));break
    depth-=1
   elif c==',' and depth==0:args.append((arg,i));arg=i+1
  index=1 if match.group().startswith(('Toast.','new ')) else 0
  if len(args)<=index:continue
  a,b=args[index]
  if 'UiText.text(' in source[a:b]:continue
  edits.extend([(b,b,')'),(a,a,'io.github.phiscript.i18n.UiText.text(')])
 for a,b,text in sorted(edits,reverse=True):source=source[:a]+text+source[b:]
 if edits:path.write_text(source);total+=len(edits)//2
print('Localized helper display calls:',total)
