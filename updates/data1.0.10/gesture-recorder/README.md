# 独立手势录入器 1.0.0

这是单独的 Android 应用，包名 `io.github.phiscript.gesturerecorder`，不会出现在打谱主程序的页面里。最低 Android 8.0，target SDK 35，不申请联网、无障碍、悬浮窗、录屏或存储权限。多点能力由设备触摸屏决定。

安装 `deliverables/GestureRecorder_1.0.0.apk`。填写名称，点击“开始录入”，在画布中做单指或多指手势，点击“停止”。“回放预览”按原时序在画布中重放；“导出 JSON”通过系统文件选择器保存到指定位置。“重新录入”替换当前录入，“清空”清除当前录入和恢复草稿。

JSON 用于向开发者提供应用手势修改资料。主程序现有 `GestureProfile` 保存的是连接规则参数，不能直接导入这种原始触摸记录；本应用没有为主程序添加导入入口，也不会在其他应用中注入或回放手势。预览只在自己的画布中绘制轨迹。

## 导出约定

正式结构见 [gesture-recording.schema.json](gesture-recording.schema.json)，示例见 [examples/multi-touch.json](examples/multi-touch.json)。

- `schema` 和 `version: 1` 标识格式；`purpose` 固定为 `application-gesture-editing`。
- `tMs` 是相对按下“开始录入”时的 Android uptime 毫秒，来源为 `MotionEvent.getEventTime()`/`getHistoricalEventTime()`；它包含开始后等待第一根手指的时间，不使用系统日期。uptime 不包含设备深度睡眠。
- 每帧保存完整触点列表；`id` 是 pointer ID，不是数组下标。`actionPointerId` 标识新按下或抬起的指头，`MOVE`/`CANCEL` 时为 `-1`。`POINTER_UP`/`UP` 当帧仍包含刚抬起的触点，下一帧才移除。触点 ID 可在下一次接触中复用。
- Android 合并的历史运动样本逐帧保存为 `MOVE`，标记 `historical: true`。历史和当前帧按采样时间保存，同一毫秒的多个事件仍按接收顺序保存。
- `canvas.widthPx/heightPx` 是实际录入画布的像素尺寸，`x/y` 相对画布左上角，`nx=x/widthPx`、`ny=y/heightPx`。手指滑出边界时保留原坐标，归一化值可能小于 0 或大于 1。不能把画布坐标直接当作全屏绝对坐标。
- `rotation` 使用 Android `Surface.ROTATION_*` 值 0/1/2/3；`pressure` 保留 Android 原值，不保证所有硬件提供不同压力。
- 手动停止时，如果手指仍按在画布上，附加 `CANCEL` 关闭所有活动触点。切换应用、旋转、画布尺寸变化、无效触摸序列和达到录入上限也会停止，并设置 `interrupted: true`、`stopReason`。已有 `UP` 的触点不会再次取消。

每次最多 10 分钟、100000 个原始帧、1000000 个触点采样；停止用的最终 `CANCEL` 可额外占一帧。录入时保持屏幕点亮，切换应用停止。停止后的草稿会尝试原子保存到应用私有目录，下次打开恢复；旋转保留当前记录。系统在录入过程中直接终止进程、磁盘不足或卸载应用仍可能丢失尚未导出的数据，因此完成录入后请导出。

## 构建和验证

在项目根目录准备 `tools/ecj.jar`（ECJ 3.39）、`tools/android/{android.jar,aapt2,d8.jar,apksigner.jar,zipalign,libc++.so}`（Android SDK 35），Java 17+、Python 3、keytool；也可通过 `PHI_TOOLS_DIR` 指向相同布局。

```sh
bash gesture-recorder/tests/run.sh
bash gesture-recorder/build.sh
```

构建不依赖主程序 APK、主程序签名或 Gradle。第一次运行在 `gesture-recorder/build/signing/` 生成录入器自己的本地开发证书；整个 `build/` 被忽略，不属于公开源码。后续构建保留此目录可覆盖升级同一录入器；证书丢失后另签包不能覆盖已装录入器。不要为此应用使用主程序私钥。

测试覆盖双指按下/抬起、ID 复用、历史帧、同时间戳、开始前已有触点、停止 CANCEL、录入上限、无效浮点数、JSON 转义和可解析性。APK 构建执行签名验证与 zipalign 检查。尚未进行 Android 真机 UI、系统文件选择器和厂商触摸采样测试。
