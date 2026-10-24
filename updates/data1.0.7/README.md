# Phigros Script data1.0.7

[安装包、完整补丁源码与校验值](https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/tag/data1.0.7) · [修复说明](RELEASE_NOTES.md) · [构建与测试](patchdata107/README.md)

本目录为基于 data1.0.6 的 APK 补丁源码，根目录旧 Alpha Gradle 工程不包含这些功能。完整源码 ZIP 另含三个参考谱面的 JSON；用户原谱包与照片不公开分发。构建工具、基线 APK 和签名私钥不随源码提供。

release-data 保存校验后的字节差异与源码分片。工作流从公开的 data1.0.6 APK 恢复最终签名文件，核对本地和远端 SHA256 后公开 Release，不重新签名。没有 Android 真机验证，详见版本说明。
