# v0.119.70 展开动画补丁

这个补丁以原版 v0.119.70 发布的 APK 为输入。卡片和有尺寸变化的消息列表在 340 毫秒内展开，宽度先弹开，随后短暂收紧再回到目标尺寸。紧凑胶囊出现时使用 270 毫秒动画。向外撑开的幅度受窗口边界和旁侧胶囊留白约束。[原版 v0.119.70 Release](https://github.com/sisjxj/FlymeIsland/releases/tag/v0.119.70)

主音乐胶囊宽度上限为 112 dp，镜头两侧分别显示封面和波形。播放时默认旋转封面。胶囊首次出现或恢复显示时，用 285 毫秒弹开并轻微回弹。横屏时收起音乐胶囊，回到竖屏后按当前播放状态恢复。

二级胶囊、展开卡片和消息列表中的应用图标采用 Android 系统图标遮罩。顶栏小胶囊使用圆形遮罩。二级胶囊的图标和文字共用顶部避让偏移，并可分别微调位置。

媒体设置和展开卡片移除了歌词相关入口。「关于与更新」链接到 `MQMQgo/FlymeIsland`，并列出 sisjxj 和 MQMQgo。

充电胶囊默认显示约 5 秒，推荐宽度为 156 dp。左侧显示电量，右侧根据系统提供的瞬时电流和电池电压估算电池端功率。设备无法提供读数时显示横线。

`0.119.70-fork.209` 的 Android `versionCode` 为 `209`。启动器使用蓝紫胶囊和薄荷绿波形组成的自适应图标，支持启动器的圆形与方圆形遮罩。

在 Linux 上使用 JDK 17、Python 3、Apktool 3.0.3、Android SDK 36 和 Build-Tools 36.0.0。脚本核对原版 APK 的 SHA-256，并生成可供签名的 APK。

```sh
bash tools/v0.119.70-animation/build.sh \
  /path/to/original.apk /path/to/apktool_3.0.3.jar /path/to/android-sdk \
  /path/to/FlymeIsland_v0.119.70-animation-unsigned.apk
```

用 Android SDK 的 `apksigner` 和自己的密钥签名。覆盖安装要求签名与已安装版本一致。公开分发完整 APK 时须按 GPL-3.0-only 提供对应源码。
