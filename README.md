# Phigros Script

安卓本机谱面读取、画面识别与无障碍自动演奏实验应用。无需 root；优先直接读取已安装的 Phigros APK，读取受限时可使用 Shizuku 的 ADB 权限。

## 下载 APK

打开本仓库 **Actions → Build Android APK → 最近一次成功的运行**，在 **Artifacts** 下载 **Phigros-Script-debug**，解压后安装其中的 APK。下载 Actions 附件通常需要登录 GitHub。产物保留 30 天，也可在 Actions 手动运行重新构建。

这是调试签名的实验版本。云端缓存尽量保留调试签名；缓存被清理后，新包可能需要卸载旧包才能安装。

## 手机使用

1. 安装 Phigros 和本应用。
2. 在本应用点击“扫描本机谱库”。默认读取包名 `com.PigeonGames.Phigros` 的 base APK 和 split APK。
3. 如果出现读取权限错误，再使用“连接 Shizuku”。Android 11 以上一般可以在手机上通过无线调试启动 Shizuku；重启后通常需要重新启动。无障碍权限本身不提供 APK 文件访问权限。
4. 先运行“只识别，不点击”，同意系统录屏，进入歌曲测试曲名、难度和时间对齐。
5. 开启本应用的无障碍服务，再使用“开始自动演奏”。保持游戏横屏，关闭游戏镜像模式。
6. 通知栏“停止”结束整个会话。启用无障碍后，音量减键也可停止。

应用使用打包在 APK 内的 OCR 模型，不从在线谱库下载歌曲谱面。首次匹配某个难度时才解包并缓存对应 JSON；游戏更新后需要重新扫描。

## 已实现的流程

- 读取当前安装包与分包的 Unity Addressables catalog。
- 将歌曲/难度资源键映射到本机 bundle，解析 UnityFS、SerializedFile 和 TextAsset。
- 支持未压缩、LZ4/LZ4HC、LZMA 的常见 UnityFS；未知格式明确报错。
- 用 OCR 识别歌曲和 EZ/HD/IN/AT，连续画面一致后才锁定；多候选不猜测。
- 对可见音符做几何拟合，多帧一致后计算谱面时间，不把入场动画或固定倒计时当音乐起点。
- 通过 Android 无障碍多指续接处理 tap、drag、hold、flick。
- 切换应用、检测到暂停/结算、触控失步或坐标越界时停止本次演奏。

## 需要知道的限制

这不是经过所有机型和官方全部谱面验证的成品，不能保证全连或 AP。

- 无障碍注入存在延迟。Android 10 及以前的中间手势采样更稀疏；建议 Android 11 以上。
- 视觉对齐依赖可见音符，长按/稀疏开头、特殊演出、金色边框或复杂背景可能无法锁定。错过的音符不会补点。
- 默认玩法视口是全屏。有黑边或特殊布局时，在“校准识别区域与延迟”修改视口。输入格式为归一化 `x,y,宽,高`。
- 选曲页同时显示多个难度时，不会任选一个。可限定当前难度的 OCR 区域，或等待入场画面显示唯一难度。
- 曲名初值从资源 ID 推导，未宣称完整解析游戏的 GameInformation 数据。个别名称不一致时用“修正曲名识别”添加别名。
- 暂停检测依赖画面和前台状态，不能保证即时识别所有菜单。需要立即停止时使用音量减或通知栏停止。
- 不读取其他应用私有存档。Shizuku/ADB 权限不等于 root；不支持的加密或新资源结构会报错。

## 构建

需要 JDK 17、Android SDK 35、Gradle 8.9：

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。工程不包含二进制 Gradle wrapper；Android Studio 导入时选择 Gradle 8.9，或使用已配置的 GitHub Actions。

## 代码结构

- `assets/`：已安装 APK、Shizuku 只读资源服务、Addressables 和 Unity 解析。
- `engine/`：v3 谱面时间与坐标。
- `vision/`：OCR 与多帧视觉对齐。
- `input/`：无障碍多指手势。
- `capture/`：MediaProjection、前台服务与会话状态机。

资源格式核对参考：[Phigros_Resource](https://github.com/7aGiven/Phigros_Resource)、[UnityPy](https://github.com/K0lb3/UnityPy)、[AssetStudio](https://github.com/Perfare/AssetStudio)。Shizuku 接口参考 [Shizuku-API](https://github.com/RikkaApps/Shizuku-API)。
