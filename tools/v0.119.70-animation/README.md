# v0.119.70 展开动画补丁

这个补丁以原版 v0.119.70 发布的 APK 为输入。卡片以 340 毫秒的非线性动画展开，轮廓轻微撑开后回到目标尺寸。胶囊摘要先淡出，卡片内容平滑淡入。回弹幅度受容器边界和旁侧胶囊留白约束。[原版 v0.119.70 Release](https://github.com/sisjxj/FlymeIsland/releases/tag/v0.119.70)

在 Linux 上使用 JDK 17、Python 3、Apktool 3.0.3、Android SDK 36 和 Build-Tools 36.0.0。脚本核对原版 APK 的 SHA-256，并生成可供签名的 APK。

```sh
bash tools/v0.119.70-animation/build.sh \
  /path/to/original.apk /path/to/apktool_3.0.3.jar /path/to/android-sdk \
  /path/to/FlymeIsland_v0.119.70-animation-unsigned.apk
```

用 Android SDK 的 `apksigner` 和自己的密钥签名。覆盖安装要求签名与已安装版本一致。公开分发完整 APK 时须按 GPL-3.0-only 提供对应源码。
