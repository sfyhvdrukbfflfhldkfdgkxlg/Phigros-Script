# Phigros Script data1.0.10

输出 versionCode 26。内部目录继续使用 patchdata109/；完整源码在 data1.0.9 发布包基础上修改，API 和原始 APK 构建基线仍为 data1.0.8。

## 本次修改

Phigros：首次大偏差校准尚未收敛时，不再让后续小漂移将剩余矫正速度从 25% 降为 3%。无活动触点且当前时刻与目标时刻都严格早于下一未发音符时，Root 和无障碍调度器可直接采用已确认的时钟起点。已有长按、已提交批次和已发按键不会重放。保留多帧证据门槛和截图延迟单次补偿，没有盲目硬减固定延迟。

Phira Pro：加入官方 org.flos.phirapro 包名、可见性声明、前台识别、触控目标和 Root 谱面目录。一次会话绑定首次识别的 Phira 客户端；同一次会话切换官方版与 Pro 会停止并提示重新开始，避免沿用旧谱面。原暂停/重试检测器继续使用。

噪域：读取 Next blockAreaList、原生秒制列表、关联外置列表和 Line2Area 标记，将 active 时段保存到规范谱面。窗口内禁用可选的黄键/红键连接、组合、借用长按接黄键与八指占位，并禁止跨窗口连接。原音符和原始长按保留，窗口外按既有开关规划。这是按 active 时间窗保守关闭连续连接，不计算动态噪域几何、绕行，也不保证所有噪域音符命中；只有旧 alpha 标记而无明确速度状态的 Line2Area 谱面会保守地整谱关闭连续连接。旧版已导入的规范 JSON 未保留噪域元数据，需要从原始谱包重新导入。

录屏：自动和手动录屏改为 Surface AVC 编码，横屏 1920×1080、竖屏 1080×1920、60fps、16Mbps。按原比例适配并加黑边。一次 MediaProjection 授权只创建一个 VirtualDisplay，GPU 分流录屏与识别截图，移除识别线程上的 Bitmap 缩放和 YUV 拷贝。视频采用独立 60Hz 时间戳，静止或低帧率的源画面会重复最近画面。能力不足时明确报告失败，不静默降为 30fps；实际流畅度需设备验证。录屏继续无声，没有新增麦克风权限。

独立手势录入器见 [../gesture-recorder/README.md](../gesture-recorder/README.md)。它导出版本化多指轨迹 JSON，供开发时修改应用手势。主程序没有新增录入页面或原始轨迹导入入口。

## 构建

Linux x86_64，Java 17+、Python 3.9+、curl、unzip、rg。准备固定版本工具及校验后的原始 APK：

```sh
bash patchdata109/bootstrap-tools.sh
python3 patchdata109/prepare.py
python3 patchdata109/apply.py
```

apply.py 只能对新解码的 versionCode 24 基线执行一次；要重来，删除生成的 analysis/data108/ 后重新 prepare/apply。源码不含构建工具、原版 APK、签名私钥或私有测试素材。

设置 PHI_KEYSTORE（keystore 绝对路径）、PHI_KEY_ALIAS、PHI_PASSWORD_FILE（密码文件绝对路径）后：

```sh
bash patchdata109/build.sh
bash patchdata109/tests/run.sh
python3 patchdata109/tests/verify.py
bash patchdata109/tests/packaged.sh
bash gesture-recorder/tests/run.sh
bash gesture-recorder/build.sh
python3 patchdata109/make_delivery.py
```

先构建主程序再运行完整测试；时间表与 packaged 回归读取最终 APK。私有 upload/Download.zip 和两张用户原图不在公开源码中，相关测试明确记录 SKIP。合成谱包、三个保留参考谱面、原生解析器、Root/AOSP 手势模型等继续执行，详见根目录 TESTING.md。

补丁脚本报告实际改动的 native DEX；打包只替换这些 DEX、helper DEX 与 Manifest，其他 APK 条目逐字节保留。完整源码 ZIP 同时含独立录入器源码、schema、示例与测试，排除 keystore、password 与 build 目录。

## 签名与验证范围

附带主程序 APK 使用新生成的开发测试证书，无法直接覆盖原签名安装包。原版证书 SHA256 为 80daf77af95563783906fa85c0ea679da03ed7724d2eec64ad2d32de0d383b76；只有持有对应原私钥并重签才能直接覆盖。此源码包不含该私钥。

完成 host 回归、实际 DEX 结构/链接、签名和 zipalign 检查。没有 Android 真机判定、触控注入、SAF UI、GPU 分流与持续 1080p60 性能验证；不能把模型测试当作真机效果证明。

## 规范依据

- [Phira Pro 噪域规范（固定提交）](https://github.com/Phira-Pro/Phira-Pro/blob/69f31f8e6c936614621fba67555d19b2ad74deb2/docs/development/rpe-next-noise.md)
- [Phira Pro Android 包名](https://github.com/Phira-Pro/Phira-Pro/blob/69f31f8e6c936614621fba67555d19b2ad74deb2/phira-android/app/build.gradle)
- [Android MediaProjection](https://developer.android.com/media/grow/media-projection)

原 data1.0.9 的 1057 个难度首键资料、逐谱时间表、官方 v1 坐标桥接、反向事件兼容、Root 增量导入、边缘避让、停止/重试与悬浮窗功能继续保留。资料来源与快照信息位于 reference/。
