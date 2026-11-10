import json,zipfile,time
from pathlib import Path
base=Path('analysis/swordalt');catalog=json.loads((base/'swordalt-catalog.json').read_text());dest=Path('deliverables/Phigros_Swordalt_OriginalCharts_and_Noise.zip')
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as z:
 z.write(base/'swordalt-catalog.json','swordalt-catalog.json')
 z.write(base/'noise-index.json','noise-index.json')
 z.write(base/'info.tsv','metadata/info.tsv');z.write(base/'difficulty.tsv','metadata/difficulty.tsv');z.write(base/'version.txt','metadata/version.txt')
 z.writestr('README.txt','原始谱面与噪域提取包\n来源：https://swordalt.github.io/phigros-chart-downloader/\n资源仓库：7aGiven/Phigros_Resource\n本包包含全部 1057 张原始 JSON（网站列出 1038 张，额外教学/变体 19 张）。25 张含噪域，共 17782 个 blockArea 条目。\ncharts/ 内文件与固定提交的 Git blob 完全一致；noise/ 为单独提取的噪域副本。\n可在 Phigros Script r4 的 Swordalt 独立入口导入本 ZIP；完整导入展开约 2.51 GiB，也可以只在应用中按歌曲下载。\n不含音乐、曲绘。本站与资源仓库非鸽游官方。资源归原权利人；工具代码许可不代表资源版权许可。\n')
 for i,e in enumerate(catalog['entries']):
  z.write(base/'raw-charts'/e['path'],'charts/'+e['path'])
  p=base/'noise'/e['path']
  if p.exists():z.write(p,'noise/'+e['path'])
  if (i+1)%200==0:print('packed',i+1,flush=True)
print('offline pack',dest.stat().st_size,flush=True)
