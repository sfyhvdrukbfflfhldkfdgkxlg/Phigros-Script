data1.0.8 / versionCode 24，沿用 data 系列原签名，可覆盖安装 data1.0.7。

- 内置公开 Phigros 4.0.1.2 快照的 1057 个难度谱面首键基准，可在设置与工具查询。数据逐文件校验来源，包含每条线 BPM、offset、上下侧和同曲不同难度。
- Phigros 的 Root、APK 文件和已安装 APK 导入完成资源索引后，逐张解析并原子保存每个按键的头/尾时间、类型、ID、首键和校验值。可复用、校验、恢复，取消或后续失败保留已完成谱面；开打前补建缺失记录。
- 网络首键参考不覆盖本地数据；版本不同或未收录时按实际导入谱面计算。游戏包快照隔离时间表，避免沿用旧版本。
- 修复 Aleph-0 IN 等谱面反向判定线事件导致整谱拒绝，以及 Aphasia EZ 等官方 formatVersion 1 的打包坐标兼容。音符时间和数量不变。

基于固定公开快照的 1057 张谱面、677156 个音符，独立 Decimal 计算与打包后原生解析、首键基准和时间表恢复共 683499 项检查通过。时间表取消/失败/恢复、原多手势、多长按、Root/AOSP、实图、自动流程及打包 DEX 回归，签名/链接/对齐检查通过。

首键是相对音乐零点的判定时间，不能单独确定手机的实际开局、截图和输入延迟，因此保留原有开局/音符校准及微调。没有 Android 真机判定或同步验证。首次生成整库时间表需要额外读取和解析；无障碍兼容性受系统限制。

来源与复现：[7aGiven/Phigros_Resource 固定快照](https://github.com/7aGiven/Phigros_Resource/tree/5d40bfdf05bee53f5107a6ea5620ca0e953de815) · [Phigros 格式与 offset](https://docs.lchzh.top/learning/phigros/) · [读取规则](https://docs.lchzh.top/learning/phigros/metrics)。APK 只内置派生首键事实，不分发公开整库谱面、音频、图片；用户原包和照片也不分发。

English: Adds 1057 bundled first-note references and atomic, version-scoped per-note timing tables during Phigros imports, with resume, integrity checks and local-data precedence. Official v1 packed moves and backwards line events now load. A pinned 1057-chart/677156-note corpus passes independent timing and packaged native checks. Music-relative times do not establish the live start epoch, so existing calibration remains. Android device judgement/timing is unverified.
