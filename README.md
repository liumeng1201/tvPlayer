# 儿童视频（tvPlayer）

一个面向 Android TV 和电视盒子的个人视频播放器。应用通过局域网 HTTP 服务浏览多级媒体目录，支持视频播放、外挂字幕、图片查看、播放进度记录和观看时长限制；界面主要使用电视遥控器操作。

## 技术栈

| 项目 | 当前配置 |
| --- | --- |
| 语言与界面 | Java、单 Activity、多 Fragment、XML 布局；部分页面使用 ViewModel 和 LiveData |
| Android | 最低 API 26，`compileSdk` / `targetSdk` 35 |
| 构建 | JDK 17、Gradle 8.7、Android Gradle Plugin 8.6.1 |
| 视频 | AndroidX Media3 ExoPlayer 1.9.4 |
| 数据与网络 | Room 2.6.1、OkHttp 4.12.0、Gson 2.10.1 |
| UI 与图片 | AndroidX Leanback、AppCompat、Material Components、Glide 4.16.0 |
| 测试 | JUnit 4、AndroidX Test、MockWebServer、Android TV 模拟器 |

依赖与版本以 [`app/build.gradle`](app/build.gradle) 和根目录 [`build.gradle`](build.gradle) 为准。

## 项目结构

```text
.
├── app/
│   ├── build.gradle                    # Android 配置、依赖与 release 签名
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/lm/android/tv/player/
│       │   │   ├── MainActivity.java   # 页面容器及遥控器按键转发
│       │   │   ├── data/               # 网络、Repository、数据模型、Room
│       │   │   ├── player/             # ExoPlayerManager
│       │   │   ├── ui/                 # 媒体浏览、播放、图片、设置和控件
│       │   │   └── util/               # URL、观看限制、SharedPreferences
│       │   └── res/                    # XML 布局、焦点样式、图标等
│       ├── test/                       # JVM 单元测试
│       └── androidTest/                # 设备测试及模拟媒体库
├── .github/workflows/release-apk.yml   # release 构建和测试
├── gradle/wrapper/                     # Gradle 版本配置
├── build.gradle / settings.gradle      # 插件和依赖仓库配置
└── myreleasekey.keystore               # 当前 release 签名配置使用的密钥库
```

应用采用单 Activity、多 Fragment 结构。媒体列表由 `MainFragment` / `MainViewModel` 通过 `FileRepository`、`ApiService` 获取；选择视频后，`PlayerFragment` 调用 `ExoPlayerManager`。播放进度由 Room 的 `AppDatabase` 和 `VideoProgressDao` 管理；播放模式、观看时长及冷却状态保存在 SharedPreferences 中。

## 局域网媒体服务

服务器地址目前在 [`Urls.java`](app/src/main/java/com/lm/android/tv/player/util/Urls.java) 中配置，默认值为 `http://192.168.1.248`。使用前请根据实际局域网修改并重新构建，确保电视设备能访问该地址。当前应用允许使用 HTTP 明文连接。

应用请求每个目录下的 `files.json`：根目录使用 `/files.json`，例如进入 `动画/第一季` 后请求 `/动画/第一季/files.json`。每个文件是一个 JSON 数组，包含以下三类条目：

| `type` | 类型 | 说明 |
| --- | --- | --- |
| `0` | 目录 | `name` 既用于展示，也用于进入同名子目录 |
| `1` | 视频 | `url` 是视频相对当前目录的文件名；`subtitle` 是可选的字幕文件名 |
| `2` | 图片 | `url` 是图片相对当前目录的文件名 |

`cover` 为相对当前目录的封面路径，可省略。视频的字幕是该视频的属性，而不是独立列表条目。例如：

```json
[
  {"name": "第一季", "type": 0, "cover": "第一季.png"},
  {"name": "第01集", "type": 1, "url": "第01集.mp4", "cover": "封面.png", "subtitle": "第01集.srt"},
  {"name": "第02集", "type": 1, "url": "第02集.mp4", "cover": "封面.png"},
  {"name": "剧照", "type": 2, "url": "剧照.png", "cover": "剧照.png"}
]
```

播放器根据扩展名识别 SRT、WebVTT、SSA/ASS、TTML/XML 字幕；未配置字幕时直接播放视频。自动化播放测试使用 SRT，其他格式及具体设备效果需另行验证。

设置页还会请求服务器根目录的 `/update.json`，通过 `versionCode`、`versionName` 和 `downloadUrl` 检查更新：

```json
{"versionCode": 302, "versionName": "3.1", "downloadUrl": "http://192.168.1.248/app-update.apk"}
```

版本号高于当前应用时，用户可选择下载并安装；APK 地址应为设备可访问的完整 URL。

## 播放与观看限制

- 遥控器确认键控制播放 / 暂停，左右键快退 / 快进 5 秒；播放结束可按设置进行列表循环或单集循环。
- 播放进度保存在 Room 数据库 `player.db`；设置、上次目录位置及观看限制保存在名为 `player` 的 SharedPreferences。当前数据库使用 `fallbackToDestructiveMigration()`，变更表结构时需要注意迁移和旧进度保留。
- 一个观看周期累计 **20 分钟实际播放时间**。达到上限后可看完当前视频，结束后进入 **2 小时冷却**；如果达到上限后中途暂停或退出，则从中断时开始冷却。
- 未达到上限时，暂停或退出后短时间内恢复会继续累计；中断满 2 小时后再次播放会重置观看额度。具体实现和常量位于 `ParentalControlManager`、`SharedPrefsHelper` 和 `PlayerFragment`。

## 本地构建与测试

准备 JDK 17、Gradle 8.7、Android SDK Platform 35，并在 Android Studio 或本地环境中配置 Android SDK 路径。项目目前只有 `gradle/wrapper/gradle-wrapper.properties`，**没有提交 `gradlew`、`gradlew.bat` 和 Wrapper JAR**；下面的命令使用本机安装的 Gradle：

```sh
# 构建签名后的 release APK
gradle :app:assembleRelease

# JVM 单元测试
gradle :app:testReleaseUnitTest

# Android TV 模拟器或设备上的 release 设备测试
gradle :app:connectedReleaseAndroidTest
```

APK 默认输出到 `app/build/outputs/apk/release/`。设备测试需要已启动并连接的 Android TV 模拟器或兼容设备；测试会安装 release APK 和对应的测试 APK。

`app/src/androidTest/assets/mock-library/` 提供多级中文目录、合成 MP4、封面、图片和 SRT 字幕。设备测试中的 `MockMediaServer` 在模拟器内提供 HTTP 服务，无需访问家庭局域网。数据结构与生成方法见 [`app/src/androidTest/README.md`](app/src/androidTest/README.md)。

## GitHub Actions

推送到 `master` 或手动运行时，[`release-apk.yml`](.github/workflows/release-apk.yml) 会构建 release APK，运行 release 单元测试和 Android TV 模拟器设备测试。自动触发默认使用 API 30；手动运行可选择 API 26、30 或 34。

运行成功后，可在该次 Actions 运行页面的 **Artifacts** 中下载 `release-apk`；`test-reports` 保存测试报告。两者目前均保留 **1 天**。自动化测试不能完全替代实体电视上的遥控器、视频解码、真实局域网及 APK 安装验证。
