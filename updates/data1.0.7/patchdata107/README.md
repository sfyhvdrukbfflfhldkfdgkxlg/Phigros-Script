# Phigros Script data1.0.7

Version data1.0.7 / versionCode 23. Based on data1.0.6, with the same project signing certificate.

## 本次修改

- Phira Root 导入改为逐张提交：收到一份完整谱面后立即解析、保存谱面文件与原子索引，再更新当前谱库，然后才读取下一份。取消、中断、无效谱面或后续保存失败时，之前完成的谱面保留；缓存随每份处理结束清理。
- 取消恰好发生在索引提交之后时，刚保存的谱面仍计入当前谱库；下一份在开始解析前检查取消。文件选择器的原有导入流程保留。
- Phigros 确认中央重试和新的游戏 HUD 后，立即按原谱音符时间、谱面 offset 及原谱起点偏移演奏。实际音符校准同时进行，不再等待校准才开始触控。
- 校准成功后，Root 和无障碍使用同一个会话时钟逐步调整后续手势。时钟不回退、不重放已经发送的音符，不为切换时钟整体释放长按，也不把同时音符串行执行。
- 调整期间时钟速度限制在 0.75～1.25 倍，避免跳过短音符或把 48 ms 蓝键压缩到一个 30 Hz 帧以下。误差越大，过渡越久；初始未校准期间已经发出的输入无法撤回。
- 无障碍每批次使用不可变时间映射，已提交的动作不会被中途修改；新触点的续接间隔仍按真实毫秒计算。Root 在后续调度轮次使用校准结果。
- 校准不可靠、超时或不可用时继续原谱；暂停、退出、焦点丢失、截图中断和停止按钮仍会结束触控。
- 设置中分别显示原谱起点偏移（-3000～10000 ms）与校准后微调（±500 ms），继续保留原键值。英文界面同步更新。

保留 data1.0.6 的混合手势规划、连续黄键、往返红键、独立多押、多长按、边缘保护、暂停识别、更新检查、服务重连、导入取消、悬浮窗和录屏。

## 验证与边界

新增真实 Root 导入消费者与原生 PhiraLibrary 的主机磁盘检查：第一张在下一张之前可恢复、无效文件与保存失败不破坏已提交谱库、取消发生在提交前后时的持久性。Android 文件、URI 与原子文件 API 由主机传输模型替代。

新增会话时钟检查：校准前立即使用原谱、提交批次映射不变、单次校准、单调过渡、收敛与线程隔离。Root 生产状态机和 AOSP 输入模型均验证两个持续长按加 20 次蓝键在提前/延后校准时不重启、不重放、不丢头，并在收敛后使用正确的时刻。自动检测流程验证 Root/无障碍均先开始，再接受后台校准；无法校准时仍继续演奏。

保留三个参考谱面、原两张暂停实图、之前的输入/导入/控制/英文回归。最终 APK 另做打包后 DEX、链接、签名及 16 KiB 对齐检查。报告见 tests/*.txt。

没有连接 Android 真机。主机与框架模型测试不等于已验证手机判定、音乐同步、存储硬件或录屏性能。初始输入可能尚未对齐，校准无法补回此前漏判。无障碍编号复用受系统限制，密集长按建议 Root；仍受 Android 10 个同时触点上限约束，不能保证所有谱面或全连。

## Rebuild

这是可复现的 APK 补丁源码；GitHub 根目录保留的旧 Alpha Gradle 工程不包含 data 系列功能。公开源码不含签名私钥、上传凭据、工具二进制、用户原谱包或照片。

需要 Java 17、Python 3.9+、ECJ 3.39.0、apktool 2.11.1、dex2jar 2.4、Android SDK 35（含 zipalign 的 lib64 依赖）、Kotlin stdlib 2.0.21 和 org.json 20240303，按脚本放入 tools/。

1. 基线置于 current106/PhigrosScript_data1.0.6.apk，SHA256 0148b93c31bbafd191b9db44b1f8ba007c58b6903baf376290671e115a662cd6。
2. python3 patchdata107/prepare.py；对新解码目录执行一次 python3 patchdata107/apply.py。
3. 提供 PHI_KEYSTORE、PHI_KEY_ALIAS、PHI_PASSWORD_FILE 后运行 bash patchdata107/build.sh。同签名才能覆盖安装。
4. bash patchdata107/tests/run.sh；python3 patchdata107/tests/verify.py；bash patchdata107/tests/packaged.sh；python3 patchdata107/make_delivery.py。
5. 原谱包测试需要 upload/Download.zip；实图测试需要 upload/01-1000731778.jpg、upload/02-1000731452.jpg。源码 ZIP 包含三个参考谱面的 JSON，用户原包与照片不公开分发。

相对 data1.0.6 只替换 AndroidManifest.xml 与 classes8.dex，其余基线条目逐字节保留。证书 SHA256：80daf77af95563783906fa85c0ea679da03ed7724d2eec64ad2d32de0d383b76。

## English

Phira Root imports now commit one chart at a time before reading the next record. Completed charts survive cancellation and later failures. Phigros starts from the original chart timing after a verified restart while note calibration continues in the background. Calibration adjusts future timing gradually without rewinding the cursor, replaying notes or restarting active holds. Original and calibrated offsets remain separate. Both input backends use the same per-session clock. Host/native-library, screenshot and packaged-DEX checks are included; no Android device judgement or timing verification has been performed.
