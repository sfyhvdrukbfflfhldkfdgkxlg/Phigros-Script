# Phigros Script data1.1.3-r3

## 中文

**Android 版本号 11007。** 在 r2 中检查更新即可升级。更早的版本可下载 APK 覆盖安装。[下载 APK](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/data1.1.3-r3/PhigrosScript_data1.1.3-r3.apk)

- **游戏声音录屏。** 打开「演奏 → 画质与声音」，启用「录制游戏声音」并保存。首次使用时允许 Android 请求的录音权限。Android 10 及以上可收录当前游戏允许捕获的内部音频，麦克风保持关闭。手动与自动录屏共用设置，默认静音。音频不可用时仍保存画面。
- **演奏与谱库界面。** 录屏卡片显示分辨率、帧率及声音授权状态，并提供最近录屏入口。宽屏时演奏与录屏控件并排显示，窄屏和大字号下会自动改为上下排列。
- **Phigros 4.1.0 谱库。** 新增 8 张可按曲下载的预处理谱面，共 1,065 张。新增的难度为《Inverted World》AT、《OMG》EZ、HD、IN，以及《STARGAZERS》EZ、HD、IN、AT。已缓存的旧谱可继续使用。

主机测试使用合成画面和节拍声验证了最终录屏代码写出的 H.264 与双声道 AAC 音轨。Phigros 和 Phira 的实际内录效果仍待真机验证。

## English

**Android version code 11007.** Use the update check in r2, or install the APK over an earlier version. [Download the APK](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/data1.1.3-r3/PhigrosScript_data1.1.3-r3.apk)

- **Record game audio.** Open **Performance → Video & audio**, enable **Record game audio**, tap **Save**, and allow the Android audio permission. On Android 10 and later, both automatic and manual recordings can include internal audio that the active game allows to be captured. The microphone stays off. Recording starts muted by default, and the app retains the video when audio is unavailable.
- **Play and Library layout.** The recording card shows resolution, frame rate and audio permission status and provides a shortcut to the latest recording. Wide screens show playback and recording controls side by side. Narrow windows and larger font sizes stack the controls vertically.
- **Phigros 4.1.0 library.** Eight new precomputed charts bring the library to 1,065. Download *Inverted World* (AT), *OMG* (EZ, HD, IN), and *STARGAZERS* (EZ, HD, IN, AT) on song recognition. Previously cached charts remain available.

A host test with synthetic flashes and tones verified H.264 video and stereo AAC audio from the app's recording code. Capture in Phigros and Phira still needs testing on a physical device.

## 构建与验证 / Build and verify

从 `updates/data1.0.9-r15` 运行以下命令。Run from `updates/data1.0.9-r15` with the official R15 APK and signing credentials. [工具布局 / Tool layout](../data1.0.9-r15/DATA1.1.0.md)

```sh
python patch/build_113r3.py upload/baseline-r15.apk --key testkey.pk8 --cert testkey.x509.pem
python patch/tests/run_113r3.py deliverables/PhigrosScript_data1.1.3-r3.apk --cloud-smoke --replay desultory-AT.json desultory-IN.json
```
