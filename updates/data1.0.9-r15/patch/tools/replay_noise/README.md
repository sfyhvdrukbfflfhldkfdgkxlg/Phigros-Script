# PPR 噪域回放对照工具

只读分析 Phira Pro v5 回放，不修改回放或应用。Python 3.10+，先安装 `python -m pip install zstandard`。读取、解压和事件上限复用父目录的 `analyze_ppr.py`；这里不包含另一份解析器，也不包含用户回放、谱面、JSON样本、类文件或成品JAR。

先用父目录解析器核对两份回放与原谱的内容指纹。必须使用录制时相同的原始谱面字节；必要时同时传入 `--extra extra.json --info-offset OFFSET`。不能仅按曲名或音符数量认定版本相同。

从版本根目录运行，输出目录选择源码目录以外的位置：

```powershell
python patch/tools/analyze_ppr.py OLD.ppr --chart AT.json --out C:/ReplayAnalysis/old
python patch/tools/analyze_ppr.py NEW.ppr --chart AT.json --out C:/ReplayAnalysis/new
python patch/tools/replay_noise/compare_noise.py OLD.ppr NEW.ppr --chart AT.json --out C:/ReplayAnalysis/comparison
python patch/tools/replay_noise/prepare_native_probe.py --chart AT.json --hits C:/ReplayAnalysis/comparison/new-noise-hits.json --out C:/ReplayAnalysis/comparison/probe-samples.json
```

`compare_noise.py` 对全曲的已记录游戏帧重建触点生命周期，生成 `old/new-noise.json`、`*-noise-hits.json`、`*-noise-states.json` 与 `*-contacts-full.json`。受污染接触的计数按每次DOWN→UP生命周期计算；ID只是触点槽位，不是玩家账号。输出只含触点和几何，不复制玩家元数据或本地游戏路径。

`noise_reference.py` 是独立几何参考，未调用生产 `BlockAreaSet`。它处理原生PGR的矩形、激活时间、事件缓动、锚点变换、反转奇偶及5%屏高inset；不适用于RPE投影相机或其他未解析的画布模型。`prepare_native_probe.py` 为同一触点生成时间偏移 `[-12,-5,0,+5,+12]` ms 的独立预期。新生成的hit文件自带aspect；旧hit文件可用 `--aspect` 指定比例（默认16/9）。

## 用最终APK交叉验证

使用项目已有dex2jar抽取指定APK，避免用当前源码编译出来的几何充当成品。`NativeNoiseProbe.java` 通过真实导入器加载原谱，再调用成品 `BlockAreaSet.covers`。`MODELS`应是按 `patch/tests/run_r16.py` / `run_r17.py` 编译的Android主机mock目录，不能包含替代导入器或几何实现。ECJ输出放源码以外。

```powershell
$probeWork = 'C:/ReplayAnalysis/comparison'
New-Item -ItemType Directory -Path "$probeWork/classes" -Force | Out-Null
java -Xmx1600m -cp 'tools/dex2jar/dex-tools-v2.4/lib/*' com.googlecode.dex2jar.tools.Dex2jarCmd -f -o "$probeWork/final.jar" FINAL.apk
$probeApi = "$probeWork/final.jar;tools/kotlin.jar;tools/json.jar;tools/platform/android-35/android.jar"
$probeClasspath = "$probeWork/classes;MODELS;$probeApi"
java -jar tools/ecj.jar -8 -nowarn -cp $probeClasspath -d "$probeWork/classes" patch/tools/replay_noise/NativeNoiseProbe.java
java -cp $probeClasspath NativeNoiseProbe AT.json "$probeWork/probe-samples.json" "$probeWork/probe-results.json"
```

探针输出逐条生产判断/参考判断及匹配统计。坐标使用PPR的Y-down定义：宽度W=1600，高度H=W/aspect，`screenX=(x+1)*W/2`，`screenY=H/2+y*W/2`。传给原生域判断的chart向量是 `(x,-y)`。生产完整矩形可能比带inset的原生触摸阻挡更保守；应分别检查漏检和额外阻挡，不能只统计总体一致率。

## 固定的上游依据与局限

参考版本为 Phira-Pro `3776f5f57e93632bda3c44788b93a8e7947e7da2`：

- [格式与记录器](https://github.com/Phira-Pro/Phira-Pro/blob/3776f5f57e93632bda3c44788b93a8e7947e7da2/phira/src/replay.rs)：v5头部/压缩，Stationary不记录。
- [触点变换与感染](https://github.com/Phira-Pro/Phira-Pro/blob/3776f5f57e93632bda3c44788b93a8e7947e7da2/prpr/src/judge.rs)：原始触点副本、Y方向及进入域后直到释放的状态。
- [几何和inset](https://github.com/Phira-Pro/Phira-Pro/blob/3776f5f57e93632bda3c44788b93a8e7947e7da2/prpr/src/core/block.rs)。
- [指纹算法](https://github.com/Phira-Pro/Phira-Pro/blob/3776f5f57e93632bda3c44788b93a8e7947e7da2/phira/src/replay/library.rs)。
- [PGR解析](https://github.com/Phira-Pro/Phira-Pro/blob/3776f5f57e93632bda3c44788b93a8e7947e7da2/prpr/src/parse/pgr.rs)与[音符缓存排序](https://github.com/Phira-Pro/Phira-Pro/blob/3776f5f57e93632bda3c44788b93a8e7947e7da2/prpr/src/core/line.rs)。

模型在没有新事件时保持最后位置，不在稀疏事件之间臆造硬件轨迹。同一帧内结束的短接触可能没有中间采样。污染是几何推算，不是文件记录的内部感染标记；游戏继续出帧但没有MOVE，不能单独证明脚本CPU或Android框架发生了何种延迟。v5没有硬件时间戳、设备分辨率、脚本版本或派发完成时刻。v6需要capture重建，现解析器会拒绝。该主机探针检查成品逻辑，不代表Android真机成绩。
