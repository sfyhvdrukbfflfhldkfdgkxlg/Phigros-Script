from pathlib import Path
p=Path('analysis/data108/apktool.yml');s=p.read_text()
assert "versionCode: 24" in s or "versionCode: '24'" in s
s=s.replace("versionCode: 24","versionCode: 25").replace("versionCode: '24'","versionCode: '25'").replace("data1.0.8","data1.0.9");p.write_text(s)
print('data1.0.9 version metadata applied; native import/parser/input hooks retained')
