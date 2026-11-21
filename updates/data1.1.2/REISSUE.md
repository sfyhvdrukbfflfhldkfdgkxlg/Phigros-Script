# data1.1.2

## 中文

如果已安装原版 data1.1.2（版本号 11002），应用内检查更新会显示已是最新版。请从[本次发行页](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/tag/data1.1.2)下载 `PhigrosScript_data1.1.2.apk` 并覆盖安装。新版仍显示 data1.1.2，内部版本号为 11003。

底部的「演奏」页提供启动、触控权限和运行记录。「谱库」页用于管理 Phigros、Phira 和录制文件。应用会记住上次打开的页面。

打开「噪域与随机点击」可设置随机延迟和屏幕垂直漂移。延迟默认最多 **6 毫秒**，范围为 0–20 毫秒。漂移默认最多为玩法区域高度的 **3‰**，范围为 0–12‰，向上或向下。两项各自设为 0 即可关闭。变化会写入已经准备好的手势，同一份缓存再次播放时使用相同的时间和坐标。

连续大幅上下抖动时，长按手势保留按下位置。噪域安全检查仍然生效。预留距离随玩法区域高度缩放，1080 像素高的区域预留约 10 像素。升级后，本地谱面请在谱库内重新准备。云端歌曲在下次下载时适配当前设置。

## English

If you already have the original data1.1.2 (version code 11002), the in-app update check will report that you are up to date. Download `PhigrosScript_data1.1.2.apk` from the [release page](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/tag/data1.1.2) and install it over your existing copy. The new build still displays data1.1.2 and has version code 11003.

Use **Play** to start playback, check touch permissions and read the session log. **Library** contains the Phigros and Phira libraries and recordings. The app remembers the selected page.

The touch settings let you choose a maximum random delay (0–20 ms, default 6 ms) and vertical displacement (0–12‰ of play-area height, default 3‰ in either direction). Set either value to zero to turn it off. Timing and coordinates are saved with the gesture and reused on subsequent plays.

A sustained, large vertical oscillation can anchor a hold at its initial contact. Noise avoidance stays active with approximately 10 pixels of clearance in a 1080-pixel-high play area. Prepare local charts again after upgrading. Cloud tracks are adapted to the current settings when downloaded.

## Build

From `updates/data1.0.9-r15`, run the commands below with the R15 build tools and signing files. The published data1.1.2 cloud library can still be used with this build.

To reproduce the original cloud shards, pass the published data1.1.2 APK to the data1.1.2 library compiler. The reissue applies its current gesture settings and clearance checks when it installs a downloaded track into the local cache.

```sh
python patch/build_112.py upload/baseline-r15.apk --key testkey.pk8 --cert testkey.x509.pem
python patch/tests/run_112.py deliverables/PhigrosScript_data1.1.2.apk --cloud-smoke
```
