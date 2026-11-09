# data1.0.10 验证记录

验证时间：2026-11-09。Java 21、ECJ 3.39.0、Android SDK/build tools 35，Linux x86_64。主程序版本 data1.0.10 / versionCode 26，最低 Android 8.0，target SDK 35。

| 验证 | 结果与范围 |
|---|---|
| 主程序 Android API 编译、D8、APK 构建 | 通过 |
| 完整 host 回归 run.sh | 通过；原私有 ZIP 与两张用户原图缺失，明确跳过对应素材测试 |
| 最终 APK packaged.sh | 通过；实际 DEX 还原后再执行自动流程、解析、时钟、Root/AOSP 手势与新回归 |
| Phigros 延迟 | 36 项针对首次校准/小漂移交互、正负 2 秒偏差、无触点空档、下一未发音符、真实两种调度模型的断言通过 |
| Phira/Pro | 51 项包名、首次客户端绑定、跨客户端停止、用户目录检查通过；native hook 接入检查通过 |
| 噪域规划 | 35 项 Next/native/Line2Area 时间窗、BPM、生命周期、连接断开和恢复、长按保留、密集段与缓存断言通过 |
| 噪域数据导入 | 28 项 host 与最终 APK native hook 测试通过：PGR/RPE Next/native 内嵌列表、裸 JSON、ZIP、Root 目录、外置列表关联、空列表优先、路径和难度隔离 |
| 录屏 | 固定 1080p 方向与比例、60Hz 一小时时间轴、一次授权、失败回退、ImageReader 生命周期及真实线程锁回归通过；最终 APK helper 同样通过模型检查 |
| DEX/ZIP 审计 | 9506 个不重复 app 类；helper 与新增跨 DEX 接口均可解析；485 个原 APK 条目逐字节保留，仅 Manifest 与 classes5/6/7/8.dex 改变 |
| 签名/对齐 | 两个 APK 的 v2/v3 验签和 zipalign 通过 |
| 独立录入器 | 多指/历史采样/时间/中断 CANCEL/JSON 转义/帧限制回归通过；示例及真实测试导出通过 Draft 2020-12 JSON Schema；独立包名、零权限检查通过 |

主程序使用开发测试证书，SHA256：8ad4d9ab7430ed97ae1c2672049f9746d60ef22d644129f3c5ec38bb805bd956。它与原版证书不同，因此不能直接覆盖旧签名安装。手势录入器使用自己的独立开发证书。

所有实际执行的主程序测试结果位于 patchdata109/tests/*.txt。缺失的私有素材不会用模拟数据伪装为原素材测试。保留的三个参考谱面、生成的谱包、原生解析器和其他回归已运行。

没有 Android 真机或模拟器验证：实际游戏判定、厂商触控注入、GPU/EGL 分流、编码器输出视频和持续 1080p60 性能、手势录入器 UI/SAF 等仍需设备验证。录屏时间轴模型检查不等于录制了一小时实机视频；噪域时间窗策略不等于动态几何绕障。
