# Phigros Script data1.0.9

Version data1.0.9 / versionCode 25. Based on data1.0.8 with the same project certificate.

## 黄键与多触点

所有黄键走持续接触或滑动路径，不生成 Tap 类型的短点按。普通连续黄键按实际落点连接，静止位置可保持按住；关闭可选连接时，独立黄键仍保持接触跨过判定时刻。触点紧张时缩短额外提前占位，不把黄键改为蓝键点击。

密集段（同刻至少 8 黄，或 250 ms 内至少 24 黄）尝试分配 8 个持续触点。相邻黄键间隔超过 300 ms 时分段。规划器尝试横排、竖排和两种网格，对每个黄键判定时刻及前后 32 ms 检查判定线方向和覆盖范围；采用比公开实测更窄的 ±0.10 玩法高度投影范围。8 个触点同时保持，黄键数量增加不会导致反复 DOWN/UP。起始按下避开邻近蓝键、长按头的时间范围，并在此前的整个占位区间保证其他活动触点不超过 2 个。

无法证明覆盖、边缘音符超出视口、或已有长按/其他按键需要更多触点时，保留原来的逐轨滑动方案，不删除音符、不强制把不同位置音符合成一个动作。低密度双押、三押、四押仍保持独立触点。蓝键与长按头仍有新的 DOWN；已有红键往返、点划和多长按继续保留。Root 与无障碍提交同一规划的真实多触点事件。8 指占位适用于谱面模式，视觉模式继续按识别到的位置保持黄键触点。

## Phigros 多帧轨迹校准

确认重试后仍先按原谱时间演奏。校准器把最多 5 个新鲜运动帧对同一谱面起点的匹配误差联合比较，可丢弃一个异常帧，但最新帧也必须匹配。连续共识成立后才锁定；重复节奏存在相近候选时继续等待。静止画面、重复/过期帧、低置信度和无效玩法区域不建立时钟。

锁定后继续在附近时间范围匹配，不再一次校准后永久停止。每次已确认的小漂移最多推进 24 ms，间隔至少 1 秒，用 3% 的时钟变速逐渐收敛；超过 80 ms 的突变不作为漂移直接采用。第一次较大同步仍用有界平滑收敛。调度时间不倒退，不重播已经发送的按键，不中断长按；已提交的无障碍批次保留原来的不可变时间投影。证据不足保持当前时间；初始校准超时继续原谱。

设置入口：Phigros 自动同步 → 多帧轨迹校准与持续漂移复核。保留起点偏移与校准后微调。首键表是相对音乐零点的谱面资料，不能单独确定手机上重试、显示和输入的延迟。截图延迟仍由原设置补偿；不能把 Android Image 的未知来源时间戳直接当作 uptime。音频指纹是另一条思路，但 Android 内录需要 RECORD_AUDIO、MediaProjection 和被录应用允许音频捕获，本版没有新增这套权限或依赖，也没有声称已实现音频同步。

## 保留功能和数据

data1.0.8 内置的 1057 个难度谱面首键基准、677156 个音符所对应的公开快照版本信息、逐谱原子保存时间表、官方 v1 坐标转换、反向判定线事件兼容全部保留。首键记录取自 Phigros 4.0.1.2 公开快照，实际导入谱面始终优先；谱面 offset 不重复添加。完整逐音符核对由 data1.0.8 完成，本版保留同一解析器 DEX 和资料资产，并重新验证时间表回归。

Phira Root 每完成一张即保存、导入取消、原有混合手势、边缘避让、暂停/重试、悬浮窗、检查更新、英文与录屏继续保留。

## 验证与范围

- 新增 1600 黄键密集段的逐键覆盖检查、8 指持续接触 + 长按 + 蓝键的 Root/AOSP 十触点事件序列、容量不足回退、普通黄键和多押、持续时钟修正检查。
- 校准测试覆盖不同开局前摇、画面缩放、offset 不重复、重复/过期/静止图像、捕获送达抖动、锁定后小幅漂移及冻结画面。
- 保留原三个参考谱面、红键往返、蓝黄/蓝红组合、多长按、导入、暂停/重试实图和框架模型回归；最终安装包还执行 DEX 还原后的回归和结构/链接/签名检查。报告见 tests/*.txt。

没有 Android 真机验证。模拟覆盖检查不能代替实际 Phigros/Phira 判定、厂商无障碍行为、录屏性能和音画延迟测试。8 个固定触点不可能保证任意旋转/移动/屏外谱面都能接住；未证明覆盖时继续按轨迹执行。边缘避让可能移动触点，仍可能影响屏幕边缘音符。

## 资料

- [Lchzh 谱面格式](https://docs.lchzh.top/learning/phigros/)：黄键判定时保持接触，红键滑动；按判定线 BPM 计算音符时刻与 offset。
- [Terrysquid 的判定研究](https://www.bilibili.com/opus/1226031520301449218)：黄键采用沿判定线方向的范围，一根保持的手指可覆盖多个黄键；连续红键需要新的有效划动。它是作者的研究记录，不是游戏厂商的稳定 API；本版取更保守的空间范围。
- [Android 内录限制](https://developer.android.com/media/platform/av-capture)；[Image 时间戳](https://developer.android.com/reference/android/media/Image#getTimestamp())。
- 原首键数据来源：[7aGiven/Phigros_Resource 固定提交](https://github.com/7aGiven/Phigros_Resource/tree/5d40bfdf05bee53f5107a6ea5620ca0e953de815)，详情见 reference/。仅发布派生首键事实，不分发全库谱面、音频、图片或第三方解码器。

## Rebuild

这是 APK 补丁源码，根目录旧 Alpha Gradle 工程不包含 data 系列功能。需要 Java 17、Python 3.9+、ECJ 3.39.0、apktool 2.11.1、dex2jar 2.4、Android SDK 35、Kotlin stdlib 2.0.21 和 org.json 20240303，按脚本放入 tools/。

1. 基线 current108/PhigrosScript_data1.0.8.apk，SHA256 284bd64193d0c029937378cf68ecc68f6ab5c81a138d50ed5458d57caa82e346。
2. python3 patchdata109/prepare.py；对新解码目录执行一次 python3 patchdata109/apply.py。
3. 提供 PHI_KEYSTORE、PHI_KEY_ALIAS、PHI_PASSWORD_FILE，运行 bash patchdata109/build.sh。同签名才能覆盖安装。
4. bash patchdata109/tests/run.sh；python3 patchdata109/tests/verify.py；bash patchdata109/tests/packaged.sh。
5. 原谱包测试需要 upload/Download.zip，实图需要 upload/01-1000731778.jpg 和 upload/02-1000731452.jpg；它们不公开分发。三个参考 JSON 保留在完整源码 ZIP。最后运行 python3 patchdata109/make_delivery.py。

本次仅替换 AndroidManifest.xml 和 classes8.dex；首键资料、原生解析/导入/触控接口及其他原条目逐字节保留。源码不含签名私钥、上传凭据、构建工具或用户照片/原谱包。项目证书 SHA256：80daf77af95563783906fa85c0ea679da03ed7724d2eec64ad2d32de0d383b76。

## English

Yellow notes remain sustained contacts or slides. Dense chart sections can use eight held pointers after conservative spatial coverage and capacity checks, leaving two slots for other notes. Unsupported sections retain exact paths. Multi-frame visual phase matching starts alongside original chart playback, then keeps checking for bounded drift without replaying notes or interrupting active holds. The first-note catalogue and original calibration adjustments remain. Tests use host and Root/AOSP models; no Android device judgement or audio-sync claim is made.
