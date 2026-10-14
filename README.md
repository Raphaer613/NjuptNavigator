# 南京邮电大学校园导航

这是一个可直接导入 Android Studio 的校园导航演示项目，使用 Java、原生 Android View 和百度地图 Android SDK 编写。

## 已实现功能

- 获取并显示当前位置：首次运行请求定位权限，使用百度定位 SDK 更新当前位置。
- 校园范围判断：实时定位在校园范围外时，自动将当前位置和路线起点设为南校门。无法定位时，地图默认显示校园中心。
- 校园地点搜索：支持搜索教学楼、图书馆、食堂、操场、宿舍等地点。
- 地点详情：显示开放时间、楼层和主要功能。
- 步行/骑行路线：选择目的地后切换路线模式，调用百度路线规划并显示路线轨迹、距离和预计时间。
- 手动输入起终点：路线规划区域支持输入校园地点名或普通地址；留空起点时使用当前位置，校外定位自动回退到南门。
- 实时步行/骑行导航：通过百度步骑行导航组件进入导航界面，使用设备定位进行导航和偏航纠正；导航语音提示由百度导航输出文本并交给 Android 系统 TTS 播报。
- 热门地点推荐：首页显示热门地点，点击即可定位并查看详情。
- 语音提示：导航过程中的转向提示由 Android 系统 TTS 播报。百度离线 TTS `authSn/audio_key` 与地图 AK 是不同凭据；当前项目采用外置 TTS，不需要把离线 TTS key 配置到地图 AK。
- 临时活动点：点击“添加活动”录入活动名称和地点，保存在当前运行实例中并显示在当前定位点。
- 应用图标：已配置“校园地图 + 定位箭头”主题的自适应图标，兼容 Android 8.0 及以上设备和旧版回退图标。

## 打开方式

1. 使用 Android Studio 选择 `NjuptCampusNavigator` 文件夹。
2. 等待 Gradle 同步完成。
3. 使用 Android 手机或模拟器运行 `app`。
4. Android 13 及以上设备首次运行时允许定位权限。
5. 如果地图显示鉴权失败，请确认 `gradle.properties` 中的 `BAIDU_AK` 已绑定包名 `com.example.njuptcampusnavigator`、调试 SHA1 和发布 SHA1。

## 修改应用图标

当前图标资源位于：

```text
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/values/icon_colors.xml
app/src/main/AndroidManifest.xml
```

如果需要更换图形，修改 `ic_launcher_foreground.xml`；如果需要更换底色，修改
`icon_colors.xml` 中的 `icon_background`。重新安装应用后，桌面图标才会更新；部分启动器会缓存旧图标，
此时先卸载旧应用再重新安装即可。

## BWNavi 依赖下载

项目当前声明了：

```gradle
implementation "com.baidu.lbsyun:BaiduMapSDK_Map-BWNavi:8.2.0"
```

联网同步时，Android Studio 会通过项目配置的国内 Maven 镜像下载它。如果 Gradle 提示找不到
`BaiduMapSDK_Map-BWNavi:8.2.0`，按下面方式使用百度官方离线 SDK：

1. 登录百度地图开放平台，进入控制台中与你的 Android 应用对应的 SDK 下载页面。
2. 在功能选择中勾选“步骑行导航（含基础地图）”，下载与项目版本匹配的开发包，版本必须和
   `8.2.0` 对齐。自 7.5.9 起，步骑行导航只提供 AAR 格式。
3. 解压官方压缩包，将官方提供的 AAR 文件放入：
   `app/libs`
4. 将离线包内的 `assets` 目录中的 PNG 等资源复制到：
   `app/src/main/assets`
5. 如果离线包已经包含 BWNavi 功能，打开 `app/build.gradle`，暂时注释掉 Maven 行：

```gradle
// implementation "com.baidu.lbsyun:BaiduMapSDK_Map-BWNavi:8.2.0"
```

项目已经配置了：

```gradle
implementation fileTree(dir: "libs", include: ["*.jar", "*.aar"])
```

所以放入文件后只需点击 `File > Sync Project with Gradle Files`。不要把不同版本的普通地图 AAR、
BWNavi AAR 和其它百度 SDK 混在一起；所有百度 SDK 版本应与离线包的说明保持一致。

项目不包含 `local.properties`，Android Studio 首次打开时会使用你本机配置的 Android SDK。若没有自动识别，请在 Android Studio 的 SDK Manager 中安装 Android SDK Platform 35，并在项目设置中选择正确的 SDK 路径。

## 国内网络环境

项目已将 Gradle 插件、Google Maven 和公共依赖仓库切换为阿里云镜像，并将 Gradle Wrapper 下载地址切换为阿里云地址，不主动使用 `google()`、`mavenCentral()` 或 `dl.google.com`。如果 Android Studio 仍提示下载 SDK Platform 或 Build Tools，请在 SDK Manager 中配置国内 SDK 镜像，或提前在能联网的环境安装对应 SDK。

## 说明

本项目已接入百度地图 SDK 和步行/骑行导航组件。开放“开始导航”前，需在百度地图开放平台为应用包名 `com.example.njuptcampusnavigator` 和对应签名申请导航 SDK 权限；导航和地图共用配置在 Manifest 中的地图 AK。当前语音由 Android 系统 TTS 播报，不需要百度内置离线 TTS 的 `audio_key`。`CampusData` 中的地点经纬度和校园电子围栏范围可根据学校官方资料或百度地图坐标拾取器继续校准。电子围栏使用 `CAMPUS_MIN_LATITUDE`、`CAMPUS_MAX_LATITUDE`、`CAMPUS_MIN_LONGITUDE`、`CAMPUS_MAX_LONGITUDE` 四个值；南校门回退位置由 `SOUTH_GATE` 定义。
