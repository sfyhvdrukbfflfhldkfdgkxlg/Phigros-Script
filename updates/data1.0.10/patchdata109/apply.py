from pathlib import Path
import hashlib,json
app=Path('analysis/data108')
before={str(f.relative_to(app)):hashlib.sha256(f.read_bytes()).hexdigest() for f in app.glob('smali*/**/*.smali')}
p=Path('analysis/data108/apktool.yml');s=p.read_text()
assert "versionCode: 24" in s or "versionCode: '24'" in s
s=s.replace("versionCode: 24","versionCode: 26").replace("versionCode: '24'","versionCode: '26'").replace("data1.0.8","data1.0.10");p.write_text(s)
print('data1.0.10 version metadata applied')

# Phira Pro compatibility
from patch_phira import patch as patch_phira
patch_phira(Path('analysis/data108'))

# Preserve noise metadata in actual APK parser entry points.
from patch_noise import apply as patch_noise
patch_noise(app)

# Recording surface fanout
from patch_recording import patch as patch_recording
patch_recording(Path('analysis/data108'))

from patch_noise_assets import apply as patch_noise_assets
patch_noise_assets(app)

after={str(f.relative_to(app)):hashlib.sha256(f.read_bytes()).hexdigest() for f in app.glob('smali*/**/*.smali')}
modified=sorted(name for name in set(before)|set(after) if before.get(name)!=after.get(name))
changed_dex=sorted({'classes'+(name.split('/')[0].removeprefix('smali_classes') if name.split('/')[0]!='smali' else '')+'.dex' for name in modified})
report={'version':'data1.0.10','versionCode':26,'baseline':'data1.0.8','previous_source':'data1.0.9','changed_dex':changed_dex,'replaced_helper_dex':'classes8.dex','modified_smali':modified,'bundled_first_note_rows':1057}
Path('patchdata109/hook-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('Modified native DEX:',changed_dex)
