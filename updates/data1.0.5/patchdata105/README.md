# Phigros Script data1.0.5

Version data1.0.5 / versionCode 21. Based on the exact data1.0.4 APK and signed with the same project certificate.

## 本次修改

- 设置与工具 → 检查更新：读取本项目 GitHub Releases 的公开版本列表，包括正式发布的预览版本。按数字比较版本；只有带 APK 的较新版本才提示下载。可查看版本说明，安装由 Android 确认。网络失败时可直接打开 Releases。
- 连续黄键使用持续触点滑动接住；同落点长键可以复用。连续红键在同判定线、同侧、时间与距离接近时，使用同一触点来回滑动。保留系统边缘避让。
- 邻近蓝键后接红键或黄键时，保留蓝键的首次 DOWN，再移动到后续音符。后续新的蓝键和长键头仍产生独立按下。Root 与无障碍共享谱面规划；纯视觉模式也支持这些组合。
- 按用户提供的两张 Phigros 实图修复三键菜单。原缩小采样会切断细线重试环；现保留细线，并核对返回折线、重试圆环和继续三角形的位置与形状。重试坐标来自当前截图。
- 在清晰的游戏 HUD 上用多张新鲜截图确认曲名和难度，暂停后复用，避免模糊背景导致读谱卡住。旧版未模糊菜单和手动暂停仍可进行菜单内识别。
- 宽屏游戏画面存在完整的 16:9 判定线时，从判定线测出左右留白。缩小截图坐标映射回物理屏幕；自定义玩法视口不被自动覆盖。
- 保留取消独立触点数量启动检查、多长键、Root Phira 一键导入及取消、悬浮窗、服务重连和无声录屏。

## 验证范围

主机回归覆盖三个原始谱包/参考谱面、多长键、Root/无障碍、导入取消、大谱面、边缘保护、自动暂停/重试、服务重连和英文。新增回归验证版本比较与下载来源、红键方向交替、蓝红/蓝黄组合和两个后端的按下次数。两张原始 JPEG 做了不同截图比例、物理坐标映射、缺失按钮及游戏/暂停区分测试。最终 APK 另跑打包后 DEX 回归、跨 DEX 链接审计、ZIP CRC、签名和 16 KiB 对齐检查。结果在 tests/*.txt。

未连接 Android 真机；截图识别通过不代表已验证实际输入、音乐同步、录屏编码或所有 Phigros/RPE 谱面。

## Rebuild

这是 APK 补丁源码与测试，GitHub 根目录早期 Gradle 工程没有这里的新功能。私钥、密码及工具二进制不包含在公开源码里。

需要 Python 3.9+、Java 17、ECJ 3.39.0、apktool 2.11.1、dex2jar 2.4、Android SDK 35 的 android.jar/D8/apksigner/zipalign（含 lib64 依赖）、Kotlin stdlib 2.0.21、org.json 20240303，按脚本放入 tools/。

1. 基线：current104/PhigrosScript_data1.0.4.apk，SHA256 78bd45e8c514727d8b2d7716457f1085ad433538de01b86e7793ec18b33cc71c。
2. python3 patchdata105/prepare.py；对新解码目录执行一次 python3 patchdata105/apply.py。
3. bash patchdata105/build.sh。用 PHI_KEYSTORE、PHI_KEY_ALIAS、PHI_PASSWORD_FILE 指定签名；只有同一私钥签名才能覆盖已有安装。
4. bash patchdata105/tests/run.sh；python3 patchdata105/tests/verify.py；bash patchdata105/tests/packaged.sh；python3 patchdata105/make_delivery.py。
5. 原三个谱包测试需要 upload/Download.zip；实图测试需要 upload/01-1000731778.jpg 与 upload/02-1000731452.jpg。它们是用户提供的测试材料，不随公开源码分发。

相对 data1.0.4 只替换 AndroidManifest.xml 与 classes8.dex，其余基线条目逐字节保留。生产包不含测试替身。

Project signing certificate SHA256: 80daf77af95563783906fa85c0ea679da03ed7724d2eec64ad2d32de0d383b76.

## English

Use Settings & tools → Check for updates. Nearby blue-to-red/yellow patterns keep the blue press and then slide; consecutive red notes alternate directions without lifting. Yellow runs keep a contact. Automatic Phigros mode identifies the clear gameplay title/difficulty before pausing, recognizes the measured back/retry/play row, and starts only after a verified restart. The supplied screenshots and host simulations pass; Android device input and timing still need validation.
