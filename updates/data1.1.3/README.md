# Phigros Script data1.1.3

## 中文

**data1.1.3 的 Android 版本号为 11005。** 可从应用内检查更新，或下载 `PhigrosScript_data1.1.3.apk` 覆盖安装。此前本地提供的 11004 测试包也可以直接升级。[GitHub 发行页](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/tag/data1.1.3)

- **导入提速。** 导入时会跳过整段触点路径都不可能碰到的噪域，边缘附近继续做精确检查。准备完成后，演奏直接读取缓存中的时间轴和触点。
- **噪域避让。** 设置页、Root 和无障碍现在读取相同的避让参数。安全窄缝内可保持长按，移动噪域逼近时会抬指。无障碍检查覆盖同批触点调整后的结束时间、等待回调和抬指。
- **界面。** 「演奏」和「谱库」两页使用浅色玻璃卡片与悬浮导航，会为底部手势条、横屏侧边导航和键盘留出空间。支持系统背景模糊的 Android 12 及以上设备可在弹窗中显示模糊效果。

从公开发行的 data1.1.2（11002 或 11003）升级后，请在「谱库 → 预计算触点」重新准备本地谱面一次。从 11004 升级时，已有匹配当前屏幕和设置的缓存可继续使用。云端谱库首次选择歌曲时会下载并准备对应曲目，随后可离线演奏。

## English

**data1.1.3 has Android version code 11005.** Use the in-app update check or install `PhigrosScript_data1.1.3.apk` over your current copy. This also upgrades the local test build numbered 11004. [GitHub release](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/tag/data1.1.3)

- **Faster imports.** During preparation, the planner skips noise areas that the entire touch path will clear. It still checks the geometry near boundaries. Playback reads the saved timing and coordinates.
- **Noise avoidance.** The settings page, root backend and accessibility backend use the same avoidance values. A hold can stay in a narrow gap while it remains safe or lift before a moving area reaches it. Accessibility checks cover the final gesture batch timing, the wait for the callback and the release stroke.
- **Interface.** Play and Library use light glass cards and floating navigation. Controls stay clear of the gesture handle, landscape navigation bar and keyboard. Dialogs use background blur on Android 12 or later when the device supports it.

After upgrading from a public data1.1.2 release (11002 or 11003), open **Library → Prepare touch points** and prepare your local charts once. Charts prepared by test build 11004 can reuse their cache on the same screen with the same settings. Cloud tracks are downloaded and prepared on first selection, then can be played offline.

## 构建与验证 / Build and verify

从 `updates/data1.0.9-r15` 目录执行以下命令。将官方 R15 APK 放入 `upload/baseline-r15.apk`，并传入签名密钥和证书。[工具布局](../data1.0.9-r15/DATA1.1.0.md)

Run the following commands from `updates/data1.0.9-r15` with the official R15 APK at `upload/baseline-r15.apk` and the signing key and certificate in the indicated paths. [Tool layout](../data1.0.9-r15/DATA1.1.0.md)

```sh
python patch/build_113.py upload/baseline-r15.apk --key testkey.pk8 --cert testkey.x509.pem
python patch/tests/run_113.py deliverables/PhigrosScript_data1.1.3.apk --cloud-smoke
```

若有 Desultory Signals 的原始 AT 和 IN 谱面，可在测试命令后加上 `--replay desultory-AT.json desultory-IN.json` 验证无障碍轨迹与噪域留边。

With the original Desultory Signals AT and IN chart files, add `--replay desultory-AT.json desultory-IN.json` to check accessibility gestures and noise clearance.
