from pathlib import Path
p=Path('analysis/data105');f=p/'apktool.yml';s=f.read_text()
assert "versionCode: 21" in s or "versionCode: '21'" in s
s=s.replace("versionCode: 21","versionCode: 22").replace("versionCode: '21'","versionCode: '22'").replace("data1.0.5","data1.0.6");f.write_text(s)
print('data1.0.6 version metadata applied; native hooks retained')
