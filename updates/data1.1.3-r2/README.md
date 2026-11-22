# Phigros Script data1.1.3-r2

## 中文

**Android 版本号为 11006。** 使用公开版 data1.1.3 或同名本地测试包的用户，请下载 APK 覆盖安装一次。旧版更新器只比较前三段版本号。从 r2 开始，应用也会按数字比较后续的稳定修订版，如 r3 和 r10。[下载 APK](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/data1.1.3-r2/PhigrosScript_data1.1.3-r2.apk)

- **液态玻璃界面。** 演奏和谱库页面可滚动到系统手势条下方，控件为手势条、横屏导航和键盘留出操作空间。Android 12 及以上支持浮动导航的局部模糊与折射。支持并启用跨窗口模糊的设备会在主页和弹窗显示全窗口模糊。
- **噪域落点。** 大面积遮挡时，预计算优先选择音符判定带内可到达的安全空隙中心。遇到单个噪域时，优先留出约可玩区高度 3.3% 的间距。窄缝仍会检查触点、时间保护窗口和整段手势。旧云端轨迹只有坐标，使用局部留边修正。导入包含判定线与噪域的原谱可启用完整选点。
- **无障碍手势。** 导入时会检查独立蓝键能否在同一位置保持到抬指。在 Desultory Signals AT 回放的主机复测中，原回放漏掉的 15 个蓝键均在各自判定带内收到按下事件，84.208 秒双长按的最长断触由原回放的 359 毫秒降至 24 毫秒。真机表现取决于设备及输入调度。

从公开版 data1.1.2 或 data1.1.3 升级后，请在「谱库 → 预计算触点」重新准备一次本地谱面。若已安装本地玻璃包并准备过触点，屏幕和设置匹配时可沿用该缓存。准备完成后，演奏直接读取已保存的时间轴和触点。

## English

**Android version code 11006.** If you use the public data1.1.3 APK or a local test APK with the same version name, install this APK over your current copy. The old updater compares only the first three version numbers. Starting with r2, update checks also recognize later stable revisions such as r3 and r10 in numeric order. [Download the APK](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/data1.1.3-r2/PhigrosScript_data1.1.3-r2.apk)

- **Liquid glass.** Play and Library scroll under the gesture handle, with room for controls around the navigation bar and keyboard. On Android 12 and later, the floating navigation refracts and blurs the content behind it. Home and dialogs also use full-window blur when cross-window blurring is available and enabled.
- **Noise avoidance.** When noise covers much of the play area, preparation prefers the center of a reachable safe gap within the note’s judgment band. For an isolated noise area it prefers a margin of about 3.3% of the playfield height. Narrow gaps still undergo contact, timing and path checks. Older cloud tracks retain coordinates only and receive local clearance adjustments. Import an original chart with judge lines and noise geometry for full gap selection.
- **Accessibility input.** Preparation checks whether an independent tap can stay at one position until release. In a host replay of Desultory Signals AT, all 15 previously missed taps received a touch-down inside their judgment bands. The longest interruption of the paired holds at 84.208 seconds fell from 359 ms in the original replay to 24 ms in the model. Results on a phone depend on device input scheduling.

After upgrading from public data1.1.2 or data1.1.3, open **Library → Prepare touch points** and prepare your local charts once. If you already prepared them with the local glass APK, its cache can be reused on the same screen with matching settings. Playback reads the saved timeline and coordinates.

## 构建与验证 / Build and verify

在 `updates/data1.0.9-r15` 下运行以下命令。Run from `updates/data1.0.9-r15` with the official R15 APK and the signing credentials. [工具布局 / Tool layout](../data1.0.9-r15/DATA1.1.0.md)

```sh
python patch/build_113r2.py upload/baseline-r15.apk --key testkey.pk8 --cert testkey.x509.pem
python patch/tests/run_113r2.py deliverables/PhigrosScript_data1.1.3-r2.apk --cloud-smoke --replay desultory-AT.json desultory-IN.json
```
