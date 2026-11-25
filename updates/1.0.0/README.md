# Phigros Script 1.0.0

## 中文

从 `data1.1.x` 升级时，请下载本次发行中的 `PhigrosScript_1.0.0.apk` 并覆盖安装。旧版会按显示版本排序，应用内检查更新可能仍显示已是最新。1.0.0 使用 Android 版本号 **11008**，高于 `data1.1.3-r3` 的 11007。

- **液态玻璃。** 演奏台与谱库使用分层浅色玻璃，Phigros 和 Phira 谱库各有自己的底色。切页时，底部的选中高光线性滑向下一页。文字和控件延伸到手势条上方的安全区域，页面背景铺到屏幕底边。
- **Phigros 谱库。** 已为游戏 4.1.0 的全部 1,065 张谱面重新计算触点，分成 33 个文件供按曲下载。选中曲目和难度后，应用按需读取这一张谱的预处理时间轴、触点和噪域数据。首次下载完成后可以离线使用。[Google 云盘谱库](https://drive.google.com/drive/folders/1ujVB1nzEXH1E7StkED74DKB2H6gIgNah) · [离线原谱 ZIP（四卷）](https://drive.google.com/drive/folders/1dR1HE2c2ArkrPqDQjYu-Jad6t8xVTm4U)
- **Phira 触点。** 同拍音符分配独立落点，长按保留连续手势。升级后，打开「谱库 → 预计算触点 → 准备 Phira / Pro 谱面」选择要演奏的曲目。使用本版所含触点引擎的测试包，曾在 iQOO 15 的 Phira Pro《Desultory Signals》AT Lv.18 上取得 1,000,000 分和 2026 全连。

## English

To upgrade from `data1.1.x`, install `PhigrosScript_1.0.0.apk` from this release over your existing copy. Older builds compare display versions and may report that they are up to date. Android version code **11008** allows an in-place upgrade from `data1.1.3-r3` (11007).

- **Glass interface.** The Play and Library pages use layered light glass. The Phigros and Phira libraries have distinct tints. A highlight slides between the two tabs at a constant speed. The background reaches the bottom of the screen and the controls clear the system gesture area.
- **Phigros library.** Touch plans for all 1,065 charts in Phigros 4.1.0 have been regenerated and packed into 33 files for per-chart downloads. Selecting a song and difficulty downloads its prepared timeline, touch coordinates and noise areas. Cached charts can be played offline. [Chart library on Google Drive](https://drive.google.com/drive/folders/1ujVB1nzEXH1E7StkED74DKB2H6gIgNah) · [Offline source charts (four-part ZIP)](https://drive.google.com/drive/folders/1dR1HE2c2ArkrPqDQjYu-Jad6t8xVTm4U)
- **Phira input.** Simultaneous notes receive separate touch positions, and holds keep continuous contact. After upgrading, open **Library → Prepare touch points → Prepare a Phira / Pro chart** and select a chart. In testing on an iQOO 15, the touch engine included in this release achieved 1,000,000 points and a 2,026-note combo on *Desultory Signals* AT Lv.18 in Phira Pro.

## 构建与验证 / Build and verify

在 `updates/data1.0.9-r15` 目录中执行。Run these commands from `updates/data1.0.9-r15` with the R15 baseline APK, build tools and matching signing credentials. [构建环境 / Build environment](../data1.0.9-r15/DATA1.1.0.md)

```sh
python patch/build_100.py upload/baseline-r15.apk --key testkey.pk8 --cert testkey.x509.pem --output deliverables/PhigrosScript_1.0.0.apk
python patch/tests/run_100.py deliverables/PhigrosScript_1.0.0.apk --cloud-smoke
```
