data1.0.6 / versionCode 22，沿用 data 系列原签名，可覆盖安装 data1.0.5。

- 修正 Phigros 宽屏坐标，默认居中 16:9，保留自定义视口；暂停按钮始终按物理屏幕检测。
- 默认按实际移动音符校准谱面时间，过滤静止背景，避免把 HUD 出现当成音乐起点。新微调与旧 HUD 偏移分别保存。
- 统一规划连续黄键、往返红键、蓝黄/蓝红、黄红切换及多轨混合多押。蓝键与长按头保留新 DOWN，同时音符不串行处理。
- 短按保持 48 ms；新谱面点击轮换空闲 ID，活跃长按 ID 不变。Root 不再给新 DOWN 紧接冗余 MOVE；菜单双击保持同一 ID。
- 无障碍支持运行时检测后的编号复用，取消固定四秒重启，并在当前批次内实际滑动。受系统限制时提示使用 Root。

Root/AOSP 状态机及打包 DEX 回归覆盖两个持续长按和 100 次蓝键、独立多押、原三谱音符保留、蓝键/长按头、混合轨迹、真实 PGR 投影时钟、原暂停实图、导入取消和英文等；签名、链接与对齐检查通过。

没有安卓真机验证。手机判定、同步延迟与录屏性能仍需实测。部分系统的无障碍编号复用受限，不能保证所有谱面或全连。附 APK、补丁源码与 SHA256 校验。

English: Repairs Phigros playfield coordinates and calibrates from moving notes instead of the HUD. Unified multi-touch paths preserve fresh blue/hold heads, independent chords, yellow/red transitions and alternating flicks. Fresh chart presses rotate free IDs while continuing contacts remain stable; menu double taps keep one ID. Accessibility ID reuse is system-dependent. Host, source-chart, screenshot and packaged-DEX regressions pass; device judgement and timing remain unverified.
