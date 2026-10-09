# 南京邮电大学校园导航

这是一个可直接导入 Android Studio 的校园导航演示项目，使用 Java、原生 Android View 和百度地图 Android SDK 编写。

## 已实现功能

- 获取并显示当前位置：首次运行请求定位权限，使用百度定位 SDK 更新当前位置。
- 校园地点搜索：支持搜索教学楼、图书馆、食堂、操场、宿舍等地点。
- 地点详情：显示开放时间、楼层和主要功能。
- 步行/骑行路线：选择目的地后切换路线模式，调用百度路线规划并显示真实路线轨迹、距离和预计时间。
- 热门地点推荐：首页显示热门地点，点击即可定位并查看详情。
- 语音提示：点击“开始导航”后使用系统 TTS 播报路线摘要。
- 临时活动点：点击“添加活动”录入活动名称和地点，保存在当前运行实例中并显示在当前定位点。

## 打开方式

1. 使用 Android Studio 选择 `NjuptCampusNavigator` 文件夹。
2. 等待 Gradle 同步完成。
3. 使用 Android 手机或模拟器运行 `app`。
4. Android 13 及以上设备首次运行时允许定位权限。
5. 如果地图显示鉴权失败，请确认 `gradle.properties` 中的 `BAIDU_AK` 已绑定包名 `com.example.njuptcampusnavigator`、调试 SHA1 和发布 SHA1。

项目不包含 `local.properties`，Android Studio 首次打开时会使用你本机配置的 Android SDK。若没有自动识别，请在 Android Studio 的 SDK Manager 中安装 Android SDK Platform 35，并在项目设置中选择正确的 SDK 路径。

## 国内网络环境

项目已将 Gradle 插件、Google Maven 和公共依赖仓库切换为阿里云镜像，并将 Gradle Wrapper 下载地址切换为阿里云地址，不主动使用 `google()`、`mavenCentral()` 或 `dl.google.com`。如果 Android Studio 仍提示下载 SDK Platform 或 Build Tools，请在 SDK Manager 中配置国内 SDK 镜像，或提前在能联网的环境安装对应 SDK。

## 说明

本项目已接入百度地图 SDK。`CampusData` 中的建筑经纬度是仙林校区附近的初始示例坐标，正式提交前建议用百度地图坐标拾取器或学校官方资料校准每个地点的精确坐标。
