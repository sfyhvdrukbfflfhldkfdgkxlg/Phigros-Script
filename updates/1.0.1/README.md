# Phigros Script 1.0.1

## 中文

**预处理触点离线包。** 打开「谱库 → Phigros → 下载 → 小飞机网盘」，选择「预计算触点包 · 离线演奏（推荐）」并点「打开分享页」。下载 `Phigros_Touch_Plans_4.1.0_20261122.zip` 后返回，点「选择已下载 ZIP」。应用校验并启用 33 个触点分片，之后可离线读取全部 1,065 张谱面的时间轴、触点和噪域数据。下载和导入后的数据各约 413 MiB。也可继续使用 Google 云盘与 GitHub 按曲下载。[小飞机预处理触点包](https://share.feijipan.com/s/0hfX6hQd)

**小飞机离线原谱。** 如需 Phigros 4.1.0 的 1,065 张原谱，可下载约 348 MiB 的 ZIP，解压后约占 2.6 GiB。下载时可能需要小飞机官方客户端。回到应用选择已下载的 ZIP，应用会校验文件并导入。[小飞机原谱分享页](https://share.feijipan.com/s/sqfXebbe)

**升级。** 下载 `PhigrosScript_1.0.1.apk` 并覆盖安装。1.0.1 与 1.0.0 使用同一应用包名和签名，Android 版本号从 11008 升至 11009。旧版 `data1.x` 用户也可直接安装此 APK。[下载 1.0.1](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/1.0.1/PhigrosScript_1.0.1.apk) · [1.0.0 云端谱库](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/tag/1.0.0)

如果旧版应用内检查显示 `data1.1.4-to-1.0.0`，先按提示安装官方 1.0.0，再次检查更新即可获取 1.0.1。

## English

**Offline touch plans.** Open **Library → Phigros → Get charts → Feijipan**. Choose **Prepared touch pack · Offline playback (recommended)** and tap **Open share page**. Download `Phigros_Touch_Plans_4.1.0_20261122.zip`, return to the app and tap **Choose downloaded ZIP**. Once the app verifies and activates the 33 shards, it can read the timelines, touch coordinates and noise areas for all 1,065 charts offline. The download and installed data each take about 413 MiB. Google Drive and GitHub still offer per-chart downloads. [Prepared touch plans on Feijipan](https://share.feijipan.com/s/0hfX6hQd)

**Offline source charts on Feijipan.** A separate ZIP contains all 1,065 source charts for Phigros 4.1.0. The download is about 348 MiB and the unpacked charts use about 2.6 GiB. Feijipan may require its official client for the download. Return to the app and select the downloaded ZIP to verify and import it. [Open the source chart share page](https://share.feijipan.com/s/sqfXebbe)

**Updating.** Install `PhigrosScript_1.0.1.apk` over your current app. Versions 1.0.0 and 1.0.1 share the same Android package and signer, and the version code increases from 11008 to 11009. Users of the earlier `data1.x` releases can install this APK directly. [Download 1.0.1](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/1.0.1/PhigrosScript_1.0.1.apk) · [1.0.0 cloud library](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/tag/1.0.0)

If an older app offers an update named `data1.1.4-to-1.0.0`, install the official 1.0.0 APK it offers, then check for updates again to get 1.0.1.

## 构建与验证 / Build and verify

在 `updates/data1.0.9-r15` 目录使用 R15 基线 APK、构建工具及匹配的签名凭据执行。Run from `updates/data1.0.9-r15` with the R15 baseline APK, toolchain and matching signing credentials. An official 1.0.0 APK can be supplied with `--previous` to check the upgrade pair. [构建环境 / Build environment](../data1.0.9-r15/DATA1.1.0.md)

```sh
python patch/build_101.py upload/baseline-r15.apk --key testkey.pk8 --cert testkey.x509.pem --output deliverables/PhigrosScript_1.0.1.apk
python patch/tests/run_101.py deliverables/PhigrosScript_1.0.1.apk --previous deliverables/PhigrosScript_1.0.0.apk
```

本地有两份离线 ZIP 时，可将以下参数追加到验证命令。To exercise both offline imports, append these arguments to the verification command.

```text
--offline-plans Phigros_Touch_Plans_4.1.0_20261122.zip --offline-sources Phigros_Charts_4.1.0_20261122.zip
```
