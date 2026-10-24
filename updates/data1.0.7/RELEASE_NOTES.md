data1.0.7 / versionCode 23，沿用 data 系列原签名，可覆盖安装 data1.0.6。

- Phira Root 导入每完成一张就立即保存谱面和原子索引，再读取下一张；取消、中断、无效文件与后续保存失败均保留此前已提交的谱面。
- Phigros 在确认重试开局后，先按原谱时间、谱面 offset 与原谱起点偏移开始打谱，实际音符校准在演奏中继续。
- 校准成功后逐步调整后续时间，不重放已发送的蓝键，不整体释放长按，同时多押仍为真实多触点。Root 与无障碍共用会话时钟，无障碍已提交的批次保持不变。
- 校准不可用或超时时继续原谱。设置中分别显示原谱起点偏移与校准后微调，英文同步更新。

新增原生 PhiraLibrary 磁盘恢复与 Root 消费者检查，覆盖逐张提交、提交失败及取消发生在提交前后的情况；新增两后端的动态时钟、多长按与新点击回归。原谱、实图、自动检测、输入和打包 DEX 回归及签名/对齐检查通过。

没有安卓真机验证。校准前的输入可能未对齐；校准无法补回此前漏判。为避免触点中断或短按被跳过，时钟以 0.75～1.25 倍逐步收敛，误差较大时过渡较久。手机判定、同步延迟与录屏性能仍需实测；无障碍兼容性受系统限制。附 APK、补丁源码与 SHA256 校验。

English: Phira Root now commits each completed chart before receiving the next. Phigros starts from original chart timing after a verified restart while calibration runs in parallel. Both input backends adjust future timing progressively, without rewinding, replaying heads or restarting active holds. Failed or unavailable calibration leaves original playback running. Offsets remain separate. Host/native-library, screenshot and packaged-DEX checks pass; Android device judgement and timing remain unverified.
