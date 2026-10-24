# Phigros Script data1.0.4

版本 data1.0.4，versionCode 20。基于 data1.0.3，同一更新签名。

## Changes / 本次修改

- 取消按估算独立触点数量拒绝启动的检查。读谱、手动选择和自动重试前的规划仍会准备手势，并尽量避免连接空隙造成额外重叠；超过估算数量也不会在这里拒绝谱面或删除音符。演奏通道仍遵循 Android 实际容量。
- Phigros 暂停检测使用自己的物理屏幕区域，不再套用 Phira 居中的 16:9 视口。三张新截图确认暂停图标后双击；菜单点击按住 55 ms，只在仍确认游戏暂停图标存在时有限次重试。手动暂停、菜单出现较慢时继续检测。
- 暂停菜单按组件、重试环形和继续三角形的排列识别，适应不同位置、间距、大小和明暗；稳定复核后点击图像中测得的重试中心。保留 Phira 原检测器，菜单不明确时不猜坐标。
- 设置与工具 → 语言 / Language：跟随系统、简体中文、English。翻译界面、对话框、通知和运行状态，保留原曲名、文件名及识别数据。
- 文件选择器和 Root Phira 导入统一支持取消。等待 su 时使用可中断管道轮询；原生解析器在已有读取/迭代调用前检查取消，异步清理进程，保留已完成批次。
- 黄键默认持续接住，连续黄键复用一条触控轨迹，同位置长键可接住黄键；双轨分别分配触点，蓝键头和红键保留独立手势。合适的黄键空隙最多连接 1200 ms，额外多押超限时退回不跨空隙规划。
- 红键默认向屏幕内侧滑动，靠近中心也保留有效长度；Root/无障碍、读谱/视觉路径均沿用系统边缘保护。

保留之前版本的 Root 导入、多长键、多指、前台保护、悬浮窗、服务重连和无声 MP4 录屏。

## Validation / 验证

主机回归与最终 APK 的 DEX 回归涵盖参考谱面、多长键、Root/无障碍续接、边缘保护、75 份原生 Phira 解码导入、取消/超时、持续黄键、红键方向、英文和原始曲名、暂停/重试及服务重连。另检查 ZIP CRC、DEX 校验和/跨 DEX 链接、签名和 16 KiB 对齐。报告见 tests/*.txt。

没有连接安卓真机。Phigros 当前版本暂停菜单未取得可核验的清晰实截图；本次菜单检测用合成像素测试，不能声称已验证所有游戏版本。实际截图、Root 注入、音乐时间轴和录屏编码器仍需设备验证。课题/剧情禁用重试、特殊皮肤及未适配 RPE 扩展不保证可自动演奏或全连。

资料：[Phira 判定实现](https://github.com/TeamFlos/phira/blob/main/prpr/src/judge.rs)、[已检查的触点视频](https://github.com/TeamFlos/phira/issues/547)、[Phigros 音符说明](https://wikiwiki.jp/phigros/howtoplay)、[双击暂停与三按钮说明](https://www.cr173.com/azyx/1646035.html)。

## Rebuild / 复现

这是 APK 补丁源码、脚本及测试，未包含签名私钥、工具二进制或原完整 Gradle 工程。GitHub 根目录旧 Gradle 工程不包含这里的新补丁。

需要 Python 3.9+、Java 17、ECJ、apktool 2.11.1、dex2jar 2.4、Android SDK 35 的 android.jar/D8/apksigner/zipalign、Kotlin stdlib 和 org.json，按脚本放在 tools/。

1. 基线置于 current103/PhigrosScript_data1.0.3.apk，SHA256：76c6cf44106eae9aafbb162bfa9090bb4118cf7ec1359a92e8e685ff19105ee0。
2. python3 patchdata104/prepare.py，然后对新解码目录执行一次 python3 patchdata104/apply.py。
3. 修改显示文本后可运行 localize_source.py / build_catalog.py。
4. 通过 PHI_KEYSTORE、PHI_KEY_ALIAS、PHI_PASSWORD_FILE 提供自己的签名材料，执行 bash patchdata104/build.sh。其他签名不能覆盖已安装的版本。
5. 执行 tests/run.sh、tests/verify.py、tests/packaged.sh，再运行 make_delivery.py。

仅替换 manifest 和 DEX 4/5/6/7/8/9，其余基线文件逐字节保留。生产包不含测试替身。

## English

Open Settings & tools → Language → English. Import the matching library, enable accessibility/screen capture and enter a song. Automatic Phigros mode pauses first, matches song/difficulty, locates Retry and starts input after the new gameplay screen appears. Stop is available in the overlay/notification. Yellow notes use sustained contacts; red notes flick inward. Cancel Phira imports with the main button. Completed batches remain available. Host tests passed; Android-device validation is still required.
