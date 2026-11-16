# Safe Pulse launcher artwork

原创图标沿用应用的深色背景、薄荷色与浅红触点。`icon.svg` 是可编辑矢量源；Android 使用相同路径的前景、单色图层及自适应图标 XML。`res/mipmap/ic_launcher.png` 为 512px 传统图标。

重新导出 PNG：

```sh
python -m pip install pillow
python patch/tools/render_icon.py --export icon-1024.png
```

正式构建由 `r20_icon.py` 独立编译小型资源片段，再合入校验过的 R15 基线。它要求资源类型 `0x0e` 未被使用，仅将该占位名称改为 mipmap；保留所有原资源编号、类型配置块和文件。改变基线时会断言停止，不会尝试猜测空闲资源编号。

图标背景采用内联 shape；Android 8 的 [AdaptiveIconDrawable 实现](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java#L410)支持从 background / foreground 的子标签加载 Drawable。v26 使用背景与前景，v33 增加单色层。

`patch/tests/icon_resource_check.py BASE.apk FINAL.apk` 检查原有资源保持、图层引用、512px PNG 和清单 icon / roundIcon。图标导出图已人工检查；该资源检查不替代 Android 桌面的实际渲染。
