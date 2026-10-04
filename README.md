# Phigros Script

安卓本机谱面读取、画面识别与无障碍自动演奏实验应用。无需 root；优先直接读取已安装的 Phigros APK，读取受限时可使用 Shizuku 的 ADB 权限。

## 下载 APK

打开本仓库 [Releases](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases)，在最新版本的 Assets 中直接下载 **Phigros-Script-v0.1.1.apk** 安装，无需解压。附带的 SHA256SUMS.txt 可校验下载完整性。

后续开发构建仍可在 **Actions → Build Android APK → 最近一次成功的运行 → Artifacts** 下载 **Phigros-Script-debug**，解压安装。Actions 产物保留 30 天。私有仓库的 Release 和 Actions 下载都需要登录有仓库权限的 GitHub 账号。

这是调试签名的实验版本。云端缓存尽量保留调试签名；缓存被清理后，新包可能需要卸载旧包才能安装。

## v0.1.1 的变化

- 自动演奏默认先检测左上角暂停图标，连续新截图确认后双击，再在暂停菜单识别曲名/难度并加载谱面。
- 唯一识别“继续 / Resume / Continue”按钮后恢复；丢弃暂停前截图和时钟，从移动音符重新对齐。不会用倒计时静止画面启动触控。
- 可在校准页面修改暂停图标搜索区域、双击间隔，或关闭先暂停功能。默认左上区域为 0,0,0.18,0.24，双击间隔 140ms。
- 改善曲名与难度在同一行、IN15 连写、较长曲名单个 OCR 字符错误等匹配；扩大对齐时间范围，不再只看前20个非长按音符。
- 区分“系统开关已开启但服务未连接”和“系统未开启”，保护无障碍初始窗口读取，增加“无障碍自查 / 复制诊断”、崩溃与连接事件记录。

“只识别，不点击”模式不会自动点击暂停或继续，可手动操作。识别失败会停止并保留游戏当前状态，不会反复双击暂停。暂停菜单需要可识别的继续按钮文字及另一项菜单文字；只有图标的布局暂不能自动恢复。

## 手机使用

1. 安装 Phigros 和本应用。
2. 在本应用点击“扫描本机谱库”。默认读取包名 `com.PigeonGames.Phigros` 的 base APK 和 split APK。
3. 如果出现读取权限错误，再使用“连接 Shizuku”。Android 11 以上一般可以在手机上通过无线调试启动 Shizuku；重启后通常需要重新启动。无障碍权限本身不提供 APK 文件访问权限。
4. 先运行“只识别，不点击”，同意系统录屏，进入歌曲测试曲名、难度和时间对齐。
5. 开启本应用的无障碍服务，返回应用确认显示“无障碍服务已连接”，再使用“开始自动演奏”。若 Android 提示“受限制的设置”，先在系统的本应用详情页允许受限制的设置，再开启无障碍。保持游戏横屏，关闭游戏镜像模式。
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
- 暂停图标使用局部双竖线图形检测，低对比背景或不同皮肤可能无法检测；无真实截图时不能保证所有布局适配。
- 视觉对齐依赖可见音符，长按/稀疏开头、特殊演出、金色边框或复杂背景可能无法锁定。错过的音符不会补点。
- 默认玩法视口是全屏。有黑边或特殊布局时，在“校准识别区域与延迟”修改视口。输入格式为归一化 `x,y,宽,高`。
- 选曲页同时显示多个难度时，不会任选一个。可限定当前难度的 OCR 区域，或等待入场画面显示唯一难度。
- 曲名初值从资源 ID 推导，未宣称完整解析游戏的 GameInformation 数据。个别名称不一致时用“修正曲名识别”添加别名。
- 暂停检测依赖画面和前台状态，不能保证即时识别所有菜单。需要立即停止时使用音量减或通知栏停止。
- 不读取其他应用私有存档。Shizuku/ADB 权限不等于 root；不支持的加密或新资源结构会报错。

## 无障碍退出设置后关闭或未连接

先看主页显示的真实系统开关与连接状态；“未连接”不一定表示权限被关闭。可在系统中关闭后重新开启本服务。仅当系统提示“受限制的设置”时，按系统要求在应用详情允许。

如果开关确实自动关闭，新版会保留应用崩溃和服务事件，可点“无障碍自查 / 复制诊断”提供手机型号、Android版本及日志。应用不会自动修改系统无障碍开关；系统后台限制、进程结束等情况仍需设备信息确认，不能仅凭现象断定原因。

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
