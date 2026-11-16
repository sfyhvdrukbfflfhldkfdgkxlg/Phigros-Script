# Phigros 手势录入器 1.0

独立 Android 应用，包名为 `io.github.phiscript.gesturecapture`。最低 Android 8.0，无需额外权限。样本仅用于开发者修改应用手势。

填写名称，点「开始录入」，在画布上操作，再点「停止录入」。单次录入支持多指和多段抬起后再次按下的手势。点「导出 JSON」后在系统文件选择器中保存。录入上限为 10 分钟和 60000 个触点样本，最后一条已完成的录入保存在应用私有草稿中。

JSON 顶层字段包括 `format=phiscript.gesture-edit`、`version=1`、名称、画布像素尺寸、持续时间和事件。每一帧的 `tMs` 为从点「开始录入」起的单调时钟毫秒。`down` 和 `up` 的 `changedId` 给出发生变化的触点 ID，`move` 和 `cancel` 的值为 -1。`points` 列出该帧所有触点以及画布像素坐标和归一化坐标，原点在左上角。`historical` 表示系统合并的历史移动样本。录入停止时尚未抬起的手指会收到终止帧。手指滑出画布时保留实际坐标。

从补丁工作目录运行 `bash patch/gesture-recorder/build.sh`。APK 签名使用该工作目录配置的测试证书。样本格式测试位于 `tests/TraceTest.java`。
