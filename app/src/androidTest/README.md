# Android TV 模拟数据

`assets/mock-library` 模拟应用当前使用的 HTTP 文件服务器。每个目录的 `files.json` 是数组，条目中 `type` 为 0、1、2 时分别表示目录、视频和图片；`cover` 和 `url` 相对于当前目录。

```text
/
├── files.json
├── 动画/files.json
│   └── 第一季/
│       ├── files.json
│       ├── 第01集.mp4
│       ├── 第01集.srt
│       └── 第02集.mp4
├── 自然/
│   ├── files.json
│   └── 森林.mp4
└── 相册/
    ├── files.json
    └── 蓝天.png
```

各目录还有 `cover.png`。MP4 为同一段约 18 秒、160×90 的合成测试画面和音频；图片也是合成的。它们仅随测试 APK 打包，不进入 release 应用。

`MockMediaServer` 在模拟器中从这些文件提供 HTTP 服务，支持 ExoPlayer 请求的字节范围；`MediaLibraryTest` 将服务器地址注入测试进程，并验证多级目录和实际视频播放。此地址不会改变正式应用的服务器配置。

SRT 测试字幕在测试代码中显式关联视频，用于检查播放器字幕解析和定位；应用目前尚未实现自动查找或加载外挂字幕。

如需重新生成视频和图片，可使用 FFmpeg：

```sh
ffmpeg -f lavfi -i 'testsrc2=size=160x90:rate=10:duration=18' \
  -f lavfi -i 'sine=frequency=440:sample_rate=44100:duration=18' \
  -c:v libx264 -preset veryfast -crf 35 -pix_fmt yuv420p -g 20 \
  -c:a aac -b:a 32k -movflags +faststart -shortest 第01集.mp4
ffmpeg -f lavfi -i 'color=c=skyblue:size=160x90' -frames:v 1 cover.png
```
