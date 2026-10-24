# Phigros Script data1.0.8

Version data1.0.8 / versionCode 24. Based on data1.0.7 and signed with the same project certificate.

## 首键核对与逐音符时间表

- APK 内置公开 Phigros 4.0.1.2 谱库快照的 1057 个难度谱面首键记录。逐文件核对 Git blob SHA-1，按每条判定线自己的 BPM 计算，上下侧全部参与最小值，包含谱面 offset。快照共 677156 个音符。
- 基准资产 assets/phigros-first-notes-v1.tsv 包含歌曲 ID、难度、谱面首键、offset、音乐首键、音符数、末尾时间与原文件 Git hash。设置与工具 → 首键时间 / 导入时间表 可查询。
- 公式：音符谱面秒数 = time × 1.875 / bpm；音乐秒数 = 谱面秒数 + offset。长按尾使用 time + holdTime。这里的首键指判定时刻，不是音符刚进入屏幕的时刻。
- Root、文件选择和已安装 APK 扫描共用原生导入入口。资源索引生成后，逐谱读取实际数据，保存每个按键的 ID、类型、头和尾时间（微秒）以及 offset、首键和 SHA256 校验。每张使用 AtomicFile 单独提交，不等待全库。
- 时间表按游戏包快照隔离，保存在应用私有 files/chart-timing-v1；重新导入可验证并复用已完成文件。损坏记录会重新生成，取消和失败保留已完成记录，汇总显示失败与未处理数量。开始演奏时也会核对当前谱面，补建缺失记录。
- 网络基准按歌曲 ID/难度、首键、offset（误差不超过 1 ms）和音符数核对。只规范去掉官方的 .0 后缀，不把 .1 等其他版本混为同谱。新版或自定义谱没有参考时仍按本地实际谱面计算，不套用旧表。
- 此核对只说明首键资料一致，不能单独证明整个谱面或手机时钟一致。完整 677156 个音符时间另用独立 Decimal 计算与应用原生解析器交叉检查。

## 关于校准

保留现有开局同步、实际音符校准及两个微调字段。谱面首键是相对音乐零点的时间，不能提供重试后手机上的实际音乐起点、截图延迟或输入延迟；这份表单独使用不足以替代它们。因此不删除仍有作用的校准。

Phigros 仍在确认重试开局后先按原谱执行，同时做音符校准；成功后平滑调整后续手势。时间表只记录和核对原谱时间，offset 不会再次加到已有时钟。保留 data1.0.7 的逐张 Phira Root 入库、混合手势、多押、多长按、边缘保护、英文、暂停/重试、悬浮窗和录屏。

## 兼容性修复

核对公开谱库时发现 Aleph-0 IN 等谱面的判定线事件存在 endTime < startTime。原解析器会整谱报错；现在忽略这些反向动画事件，音符时间和数量保持不变。另补齐官方 formatVersion 1 的打包移动坐标到 v3 的转换；原来 Aphasia EZ 等旧版谱面会直接被拒绝。转换只涉及判定线坐标，不改音符时间。其余原有解析和输入逻辑保留。

## 验证

新增二进制时间表、确定性恢复、版本隔离、损坏重建、失败/取消的原子性、部分导入恢复、网络基准校验与反向事件回归。公开参考谱库还做实际原生解析器的完整逐音符核对，原多手势、原三谱、两张实图、Root/AOSP 模型和打包 DEX 回归保留。报告见 tests/*.txt。

没有 Android 真机验证。首键表不会补回校准前的漏判，也不能证明手机判定、音乐同步或存储性能；首次生成整库时间表需要额外读取和解析时间。无障碍兼容性和十触点限制仍取决于系统。

## 数据来源

- [7aGiven/Phigros_Resource](https://github.com/7aGiven/Phigros_Resource/tree/5d40bfdf05bee53f5107a6ea5620ca0e953de815)，公开 chart 快照；版本与获取记录见 reference/source-report.json。仅发布派生首键事实，不分发其整库谱面、音频、图片或解码器。
- [Lchzh 谱面格式](https://docs.lchzh.top/learning/phigros/) 与 [实测读取规则](https://docs.lchzh.top/learning/phigros/metrics)：时间单位、offset 和无效事件处理参考。

## Rebuild

这是 APK 补丁源码；根目录旧 Alpha Gradle 工程不包含 data 系列功能。需要 Java 17、Python 3.9+、ECJ 3.39.0、apktool 2.11.1、dex2jar 2.4、Android SDK 35、Kotlin stdlib 2.0.21 和 org.json 20240303，按脚本放入 tools/。

1. 基线 current107/PhigrosScript_data1.0.7.apk，SHA256 5bb2237308ce02ba3077781caa8990f9e74edabece1fd459f081345e5df3bedf。
2. python3 patchdata108/prepare.py；对新解码目录执行一次 python3 patchdata108/apply.py。
3. 提供 PHI_KEYSTORE、PHI_KEY_ALIAS、PHI_PASSWORD_FILE，运行 bash patchdata108/build.sh。同签名才能覆盖安装。
4. bash patchdata108/tests/run.sh；python3 patchdata108/tests/verify.py；bash patchdata108/tests/packaged.sh。
5. 完整网络谱库检查需要自行下载上面固定提交的 chart 分支 ZIP，保存为 analysis/timing108/reference-charts.zip，运行 python3 patchdata108/reference/build_first_notes.py analysis/timing108/reference-charts.zip patchdata108/reference/source-manifest.json analysis/timing108/derived，再运行 bash patchdata108/tests/corpus.sh。该 ZIP、expected/ 全库时间样本不随公开源码提供。
6. 原谱包测试需要 upload/Download.zip，实图需要 upload/01-1000731778.jpg 和 upload/02-1000731452.jpg；它们不公开分发。原三个参考 JSON 保留在完整源码 ZIP。最后运行 python3 patchdata108/make_delivery.py。

本次替换 AndroidManifest.xml、classes6.dex（共享导入）、classes7.dex（PGR 反向事件处理）、classes8.dex，并新增首键事实资产，其余原条目逐字节保留。源码不含签名私钥、上传凭据、工具二进制或用户照片/原谱包。

项目证书 SHA256：80daf77af95563783906fa85c0ea679da03ed7724d2eec64ad2d32de0d383b76。

## English

Bundled first-note facts cover 1057 difficulty charts in a pinned public 4.0.1.2 snapshot. Imports atomically save each chart's complete head/tail timeline, validate checksums and references, resume completed work, and use actual imported data when versions differ. Music-relative note times do not establish the live gameplay/audio epoch, so existing synchronization and adjustments remain. Backwards judge-line events no longer reject otherwise readable charts. Host/corpus/framework checks do not constitute Android device judgement or latency validation.
