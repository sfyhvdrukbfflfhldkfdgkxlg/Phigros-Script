from pathlib import Path
p=Path('analysis/data106');f=p/'apktool.yml';s=f.read_text()
assert "versionCode: 22" in s or "versionCode: '22'" in s
s=s.replace("versionCode: 22","versionCode: 23").replace("versionCode: '22'","versionCode: '23'").replace("data1.0.6","data1.0.7");f.write_text(s)
print('data1.0.7 version metadata applied; native hooks retained')
