from pathlib import Path
p=Path('analysis/data104')
f=p/'apktool.yml';s=f.read_text();assert "versionCode: 20" in s or "versionCode: '20'" in s
s=s.replace("versionCode: 20","versionCode: 21").replace("versionCode: '20'","versionCode: '21'").replace("data1.0.4","data1.0.5");f.write_text(s)
f=p/'AndroidManifest.xml';s=f.read_text()
if 'android.permission.INTERNET' not in s:s=s.replace('<application ', '<uses-permission android:name="android.permission.INTERNET"/><application ',1)
f.write_text(s)
print('data1.0.5 version metadata applied; existing native hooks retained')
