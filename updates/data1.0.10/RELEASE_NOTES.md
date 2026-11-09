# Phigros Script data1.0.10 测试版

- 修复 Phigros 首次校准被小漂移拖慢的问题，并在安全空闲间隙完成时钟对齐。
- 支持 Phira Pro 官方包名、前台识别和 Root 谱库；会话切换客户端时停止。
- 按噪域 active 时间窗关闭可选连续连接、组合和八指占位，禁止跨窗连接，保留原音符与长按；支持内嵌及关联的外置列表。
- 自动/手动录屏采用独立 Surface 编码，1080p、60fps、16Mbps，宽屏等比留边；设备能力不足会报错，不静默降至30fps。
- 新增独立手势录入器 GestureRecorder_1.0.0.apk：多指录入、轨迹预览、JSON 导出，未集成到主程序。

## 下载

- PhigrosScript_data1.0.10.apk：主程序测试包。
- GestureRecorder_1.0.0.apk：独立手势录入应用。
- PhigrosScript_data1.0.10_Source.zip：两应用的完整源码、schema、参考谱面与验证记录。
- SHA256SUMS.txt：上述三个文件的校验值。

**主程序使用新的开发测试证书，不能直接覆盖原证书安装。** 旧版导入的谱面须从原始谱包重新导入才能保留噪域数据。源码和附件不包含任何签名私钥或密码。

Host 回归、最终 APK DEX 回归、结构/接口审计、两个 APK 的 v2/v3 验签和 zipalign、源码归档检查均通过。未进行 Android 真机判定、触控、GPU/EGL、持续1080p60性能或录入器SAF UI验证。噪域采用保守时间窗策略，不做动态几何绕障；原始长按不强行切断。

Git 中可浏览源码和验证记录位于 updates/data1.0.10/。完整源码 ZIP 还包含三个较大的参考谱面；直接克隆仓库后需先从源码 ZIP 补齐 analysis/realcharts/ 再运行完整回归。根目录 app/ 仍为旧 Alpha 工程，不包含 data 系列补丁。
