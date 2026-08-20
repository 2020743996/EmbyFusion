# Emby Fusion

[![最新版本](https://img.shields.io/github/v/release/2020743996/EmbyFusion?display_name=tag&label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC)](https://github.com/2020743996/EmbyFusion/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/2020743996/EmbyFusion/releases/latest/download/app-release.apk)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Emby Fusion 是一个面向 Android 手机、平板与折叠屏的多 Emby 源聚合播放器。它把多个服务器的电影合并成一个片库，并在播放前比较同一影片的所有版本。

## 下载

- [下载最新 APK](https://github.com/2020743996/EmbyFusion/releases/latest/download/app-release.apk)
- [查看全部版本与发布说明](https://github.com/2020743996/EmbyFusion/releases)
- 支持 Android 8.0（API 26）及以上；新版本可覆盖安装旧版本

当前版本：**v0.1.1**。本次更新重点修复添加 Emby 源后，大型片库可能长时间卡住界面的问题。完整变更见 [Release.md](Release.md)。

## 已实现

- 多服务器用户名/密码登录；访问令牌使用 Android Keystore 加密后保存在本机
- 并发加载多个 Emby 服务器；单个源离线时其余片库仍可用
- 大型片库按 250 条分页读取，匹配和规格评分在后台线程完成，避免添加源后阻塞界面
- 优先用 TMDb / IMDb / TVDb ID 合片，缺失时用片名、年份和时长容差匹配
- 按分辨率、Dolby Vision/HDR、编码、总码率、音频编码和声道数综合排序
- 影片卡片展示最佳版本与可用源数量，详情页可手动选择任一源
- Media3 / ExoPlayer 直接串流，支持拖动、横竖屏和系统播放控件
- 按 Emby 规范回报播放开始、每 10 秒进度、暂停/恢复和停止状态
- 跨服务器映射续播位置；开场 30 秒内和播放超过 95% 时自动清除续播点
- 当前版本播放失败时保留时间点并自动尝试同片下一个播放源
- 所有原始版本均无法直串时，自动请求服务器进行 H.264/AAC HLS 兼容转码，并在退出时关闭编码任务
- 播放器提供音轨设置和字幕轨道按钮
- 手机使用单栏导航，宽度达到 840dp 后自动切换为平板双栏详情布局
- 中文深色 Material 3 界面，支持 HTTP 局域网 Emby 和 HTTPS 反向代理

## 匹配与评分逻辑

同片判定的优先级是 `TMDb > IMDb > TVDb > 规范化原片名/片名 + 年份±1 + 时长±3分钟`。评分中分辨率权重最高，接着是 HDR 格式、编码、码率和音轨。这样 4K Dolby Vision 通常高于超高码率 1080p，但详情页始终保留人工选择。

这里的“最高规格”是文件规格，不等于当前设备最顺畅的版本。下一阶段可通过 `MediaCodecList`、网络测速和服务器探测增加“设备兼容最佳”“当前网络最佳”两个排序模式。

## 运行

1. 用 Android Studio 打开仓库根目录，等待 Gradle Sync。
2. 使用 Android 8.0（API 26）及以上真机或模拟器运行 `app`。
3. 在“播放源”页添加服务器。地址可填写 `https://example.com/emby` 或局域网 `http://192.168.1.2:8096`。

命令行构建：

```bash
./gradlew testDebugUnitTest assembleDebug
```

## 功能推演 / 后续迭代

1. **M1 基础可用（已完成）**：多源登录、电影聚合、规格比较、直接播放、手机/平板适配。
2. **M2 播放闭环（已完成）**：播放状态回报、跨源续播、音轨/字幕入口、跨源故障切换和最终 HLS 兼容转码回退。
3. **M3 智能选源**：启动时测各源延迟和吞吐；读取设备硬解能力；综合“画质、兼容性、网络、服务器负载”动态排序，并支持失败无感切源。
4. **M4 全媒体库**：剧集按 provider ID + 季/集号聚合；统一收藏、继续观看、搜索与筛选；离线缓存元数据。
5. **M5 大屏体验**：Android TV / 遥控器焦点、画中画、后台音频、外接键鼠快捷键、Chromecast。

## 已知边界

- 当前只聚合 `Movie`，还没有聚合剧集。
- 播放优先使用 Emby 静态直串；全部直串版本失败后才会请求 H.264/AAC HLS 兼容转码，暂不支持播放前按设备能力主动选择转码参数。
- 海报 URL 按 Emby 约定携带 API key；不要在不受信任的日志或第三方图片代理中记录完整 URL。
- `usesCleartextTraffic=true` 是为局域网 HTTP 服务器保留。只连接 HTTPS 时可改为网络安全配置，进一步限制明文域名。

## 开源协议

本项目采用 **MIT License**。

你可以自由使用、复制、修改、合并、发布、分发、再许可和/或出售本软件的副本，唯一条件是保留原始版权与许可声明。软件按「现状」提供,不附带任何明示或暗示的担保。完整条款见 [LICENSE](LICENSE)。
