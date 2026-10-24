# Phigros Script data1.0.6

Version data1.0.6 / versionCode 22. Based on data1.0.5 and signed with the same project certificate.

## 本次修复

- 宽屏坐标：Phigros 自动读谱默认使用居中的 16:9 玩法区域，不再等待白色判定线才修正。暂停键仍使用物理屏幕坐标。完整白、黄、蓝判定线可继续校正边界；自定义视口保留，设置中也可关闭 16:9 适配。
- 起点同步：默认根据多张新鲜截图中实际下落音符与谱面视觉投影匹配，区分重试 HUD 出现与真正谱面时间。静止、重复、过期和不明确画面不授权开始输入。图像无法对齐时停止并给出原因，不能保证所有隐藏判定线、特效或特殊开场都能自动同步。
- 新同步模式使用独立的输入微调（默认 0、±500 ms），旧 HUD 起点偏移单独保存，避免把旧的大偏移重复加到新时钟。手动/HUD 模式计入谱面 offset；实际音符拟合的时钟不重复添加它。
- 黄、红和蓝黄/蓝红改为共同规划，以判定时间分组，分配独立轨迹。支持蓝→黄→红→黄、连续黄键、连续红键往返、多轨并行及混合多押。后续蓝键与长按头保留新的 DOWN；正在持续的黄键优先保留原轨迹。
- 同时音符保持同时，距离或时间不允许安全复用时使用另一个触点，不把正常多押错开执行。连接按实际时间插值；不可安全移动或会相交的连接不强制合并。同位置黄键可以由已有长按或同区域 Flick 接住。
- 红键主要沿判定线法线快速滑动，连续红键明确往返；黄红切换会回到接住位置，保留边缘避让。过密而不能形成有效新 Flick 时分开触点。
- 蓝键按下保持时间增加到 48 ms，减少低帧率下短按被漏读。Root 只在位置改变时发 MOVE，并在新的 DOWN 之前更新原手指，不再对新按下立即发冗余 MOVE。新的谱面点击轮换空闲编号，已有触点编号不变；暂停菜单双击继续使用同一编号。
- 无障碍加入触点编号复用，新增触点不必把已有长按整体重启；取消固定四秒重启。该能力使用运行时检测，系统禁止相应访问时会提示受限并保留兼容路径，此时不能保证长链全程不抬手，建议使用 Root。
- 视觉路径同样支持角色切换和平滑移动。无障碍使用当前批次未来端点，避免红键先静止一个批次才滑动；两个黄键不会因旧别名错误覆盖成一个触点。

保留更新检测、英文、Phira Root/文件导入与取消、多长按、悬浮窗、服务重连、停止按钮和无声录屏。

## 验证与边界

主机测试包含新的混合路径、2/3/4 押、蓝/长按头不提前、不被吞掉、独立轨迹、Flick 反向动作、平滑移动及两个持续长按加 100 次蓝键。Root 使用真实生产状态机；无障碍使用 AOSP MotionEventInjector 算法模型，验证持续长按不因新增蓝键重新 DOWN。另验证蓝键持续时间覆盖一个 30 Hz 游戏帧。

实际 PGR 投影模型测试覆盖不同 HUD 到音乐延迟、左右留白缩放、offset 不重复应用、静止/重复/过期拒绝。三个参考谱面的新规划检查保留所有音符及蓝键/长按头。旧版导入、真实谱包、边缘保护、暂停/重试、英文、取消与录屏入口回归保留；两张用户实图继续测试。最终 APK 另做打包后 DEX 回归、链接、签名及对齐检查。报告见 tests/*.txt。

没有连接 Android 真机。上述属于主机/图像/框架模型验证，不等于已验证手机上的判定、音乐同步和编码性能；也不保证全部 RPE 扩展、所有系统的无障碍编号复用或全连。Android 同时触点限制仍为 10，估算触点数量不会拒绝启动；实际超出系统容量会停止。

## Rebuild

这是可复现的 APK 补丁源码，GitHub 根目录保留的旧 Alpha Gradle 工程不包含 data 系列功能。公开包不含签名私钥、密码、上传凭据或工具二进制。

需要 Java 17、Python 3.9+、ECJ 3.39.0、apktool 2.11.1、dex2jar 2.4、Android SDK 35（含 zipalign 的 lib64 依赖）、Kotlin stdlib 2.0.21 和 org.json 20240303，按脚本放入 tools/。

1. 基线置于 current105/PhigrosScript_data1.0.5.apk，SHA256 4116e1486ed523e9be74a14b02b2940e8ddb799f894b3fa31214d640c619631b。
2. python3 patchdata106/prepare.py；对新解码目录执行一次 python3 patchdata106/apply.py。
3. 提供 PHI_KEYSTORE、PHI_KEY_ALIAS、PHI_PASSWORD_FILE 后运行 bash patchdata106/build.sh。同签名才能覆盖安装。
4. bash patchdata106/tests/run.sh；python3 patchdata106/tests/verify.py；bash patchdata106/tests/packaged.sh；python3 patchdata106/make_delivery.py。
5. 原谱包测试需要 upload/Download.zip；实图测试需要 upload/01-1000731778.jpg、upload/02-1000731452.jpg。用户原包和照片不随公开源码分发；源码 ZIP 包含三个参考谱面的 JSON。

相对 data1.0.5 仅替换 AndroidManifest.xml 与 classes8.dex，其余基线条目逐字节保留。

Project certificate SHA256: 80daf77af95563783906fa85c0ea679da03ed7724d2eec64ad2d32de0d383b76.

## Primary references

[Android MotionEventInjector](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java) and [Phira judgement implementation](https://github.com/TeamFlos/phira/blob/main/prpr/src/judge.rs). Phira is a reference, not proof of Phigros device behaviour.

## English

Unified, time-aware multi-touch planning keeps independent lanes and fresh blue/hold heads, supports yellow/red role changes and alternating flicks, and forecasts motion within the current accessibility batch. Phigros defaults to the centred 16:9 field and aligns its start from approaching notes instead of the HUD. Use Settings → Phigros automatic sync for calibration and viewport options. Root gives direct stable pointer control; accessibility ID reuse is system-dependent. Host/frame models pass; device judgement and latency remain unverified.
