"""Create the importable native-script archive after full geometry conversion checks."""
from pathlib import Path
import hashlib,json,zipfile

base=Path('analysis/swordalt')
catalog=json.loads(Path('patch/assets/phigros-swordalt-catalog-v1.json').read_text())
initial=json.loads(Path('analysis/r5-script-export-results.json').read_text())
repair_path=Path('analysis/r5-script-repair-results.json')
repair=json.loads(repair_path.read_text()) if initial['failures'] else {'charts':0,'notes':0,'noiseCharts':0,'noiseAreas':0,'scriptBytes':0,'positionComparisons':0,'assertions':0,'maxVisiblePixelError':0,'maximumFrames':0,'maximumScriptBytes':0,'failures':[]}
assert not repair['failures'],repair['failures']
assert len(initial['failures'])==repair['charts']
assert initial['charts']+repair['charts']==len(catalog['entries'])==1057
validation={k:initial[k]+repair[k] for k in ['charts','notes','noiseCharts','noiseAreas','scriptBytes','positionComparisons','assertions']}
for k in ['maxVisiblePixelError','maximumFrames','maximumScriptBytes']:validation[k]=max(initial[k],repair[k])
validation['failures']=[]
validation['source']='7aGiven/Phigros_Resource'
validation['sourceChartCommit']=catalog['chartCommit']
noise_index=[];script_size=notes=areas=0
expected=set()
def compact(o):return json.dumps(o,ensure_ascii=False,separators=(',',':')).encode('utf-8')
for row in catalog['entries']:
    path=row['path'];expected.add(path);data=(base/'scripts'/path).read_bytes();obj=json.loads(data)
    assert obj['phiscriptFormat']==1 and obj['phiscriptExport']['sourceSha1']==row['sha1']
    assert obj['phiscriptExport']['chartCommit']==catalog['chartCommit']
    count=sum(len(l['notes']) for l in obj['lines']);frames=sum(sum(len(a) for a in l['object'][key]) for l in obj['lines'] for key in ['x','y','rotation'])+count
    assert frames<=1_000_000
    noise=obj.get('blockAreaList',[]);assert len(noise)==row['noiseCount']
    row.update(sourceSha1=row['sha1'],sourceBytes=row['bytes'],sha1=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest(),bytes=len(data),notes=count,frames=frames)
    notes+=count;areas+=len(noise);script_size+=len(data)
    if noise:noise_index.append({key:row[key] for key in ['path','title','difficulty','noiseCount']})
assert {str(p.relative_to(base/'scripts')) for p in (base/'scripts').rglob('*.json')}==expected
assert notes==validation['notes']==677156 and areas==validation['noiseAreas']==17782
assert script_size==validation['scriptBytes']
catalog.update(phiscriptSwordaltScripts=1,scriptFormat='phiscriptFormat 1',scriptConverter=1)
(base/'script-catalog.json').write_bytes(compact(catalog))
Path('analysis/r5-script-final-results.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2))
readme='''Phigros Script — Swordalt 全部谱面的原生脚本包（R5）

本包包含 1057 个 phiscriptFormat 1 JSON 脚本，677156 个音符。
网站目录列出 1038 张，另有 19 张教学/变体；25 张含噪域，共 17782 个区域。
保留曲名、难度、音符类型/起止时间/横坐标、判定线移动与旋转及全部噪域字段。
线事件已转换为脚本原生关键帧；包含演奏结束后 10 秒的动画范围。
转换时核对全部音符及长按采样的判定位置，脚本来源及校验值见目录。

整包导入（需要 R5 应用）：
导入 / 选择谱库 → Phigros · Swordalt 原谱与噪域下载 → 导入原谱 / 脚本离线 ZIP。
直接选择本 ZIP，无须解压。导入后会启用此脚本库，已导入谱面可离线读取。
已下载谱面可在 设置与工具 → 删除已下载谱面缓存 中单张或批量删除；
目录保留，后续选择该曲会重新获取固定版本原谱并转换为相同脚本。

单曲导入：
解压后选择 charts/<歌曲编号>/<难度>.json，通过所需游戏的本地谱面导入入口导入。
JSON 已包含曲名与难度。Phigros 本地库和 Phira 导入库各自独立。
这些文件供脚本应用读取；本包不含音乐、插图，不能作为 Phira 游戏本体的完整曲包。

内容：
swordalt-catalog.json：整包导入目录、原始/脚本校验值及数量。
charts/：全部 1057 个已转换脚本。
noise/：25 个非空噪域列表的独立副本。
noise-index.json：含噪域脚本清单。
conversion-validation.json：转换验证统计。
source-catalog.json：原始数据目录，固定于此前提取的版本 4.0.1.2。

来源：https://swordalt.github.io/phigros-chart-downloader/
资源：https://github.com/7aGiven/Phigros_Resource
谱面提交：5d40bfdf05bee53f5107a6ea5620ca0e953de815
本包已通过主机上的解析、转换和导入检查，未在真实 Android 设备运行游戏验证。
'''
out=Path('deliverables/Phigros_Swordalt_All_Scripts.zip')
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    z.writestr('swordalt-catalog.json',compact(catalog))
    for row in catalog['entries']:
        path=row['path'];z.write(base/'scripts'/path,'charts/'+path)
    for row in noise_index:
        obj=json.loads((base/'scripts'/row['path']).read_text())
        z.writestr('noise/'+row['path'],compact(obj['blockAreaList']))
    z.writestr('noise-index.json',compact(noise_index))
    z.writestr('source-catalog.json',Path('patch/assets/phigros-swordalt-catalog-v1.json').read_bytes())
    z.writestr('conversion-validation.json',compact(validation))
    z.writestr('README.txt',readme.encode('utf-8'))
validation.update(zipBytes=out.stat().st_size,zipSha256=hashlib.sha256(out.read_bytes()).hexdigest())
Path('analysis/r5-script-final-results.json').write_text(json.dumps(validation,ensure_ascii=False,indent=2))
print(json.dumps(validation,ensure_ascii=False))
