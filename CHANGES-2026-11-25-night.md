# 2026-11-25 夜间更新：通话岛点击直达通话页 + 微信只在接听后计时

本版仍是 `versionCode = 33`、`versionName = 0.1.32`，改动集中在第三方通话岛（微信 / QQ）的两处体验。

## 一、点通话岛直接回到软件内通话页

上一版把通话岛的 `buildIslandClick` 从 0 改成 1，指望原生 `AtomIslandBaseLinearLayout` 在点击时发送 `ClickIslandResp`。实测无效，原因是渲染层先把点击吃了：通话岛左右两半是模块自绘的 `View`，行内挂了 `setOnClickListener`，点击被它消费，原生点击回调收不到事件，实际发出的永远是“打开应用首页”。

本版把这条回调改走统一的 `IslandHost.openCall(pkg)`，按优先级落地：

1. 通话通知自带的 `contentIntent`（通知栏点进去怎么走就怎么走）；
2. `moveTaskToFront` 把承载通话的任务整窗置前——岛服务进程持有 `REAL_GET_TASKS`、`REORDER_TASKS`、`MANAGE_ACTIVITY_TASKS`，所以小窗、后台状态下也能把通话任务拉回前台；
3. `getLaunchIntentForPackage` 兜底打开应用。

`buildIslandClick = 1` 保留，作为自绘行之外的第二道保险。

## 二、微信只在对方接听后开始计时

微信有两个地方会把“通话页已打开”误当成“已接通”：

- **音频侧**：通知文案曾参与判定。微信通话通知在通话页一打开就带“通话中”，刚起岛就被判成接通。已在 `RAW_CLOCK` 集合（微信）中豁免。
- **通知侧**：`onCallNotice` 里文本命中“通话中 / 已接通 / 正在语音通话”即置 `callConnected = true` 并调 `markCallConnected()`。这条路径此前没有豁免微信，这才是“点进通话界面就开始走秒”的真实来源。本版 `noticeAnswered` 与 `markCallConnected` 均加 `RAW_CLOCK` 豁免。

现在微信计时的唯一入口是轮询到 `VOICE_COMMUNICATION` 录音；未接通期间岛上只显示“正在呼叫”，不出现秒数。QQ 维持原行为，仍按自身通知文案从“等待对方接听”翻成“通话中”的那一帧起表。

## 三、真机验证依据

用 `dumpsys audio` 每 1.5 秒采样一次通话模式、录音会话与播放通路，与用户操作时间对齐：

| 时间 | 事件 |
|-|-|
| 23:30:37.221 | `rec start riid:1011351 uid:10883 src:VOICE_COMMUNICATION pack:com.tencent.mm` |
| 23:30:50.422 | 同一条会话 `rec stop`，持续约 13 秒，对应用户 14 秒的说话时长 |
| 等待接听的 20 秒内 | 全机没有任何 `com.tencent.mm` 录音会话 |

结论：微信确实在接听后才有 `VOICE_COMMUNICATION` 录音，因此“只认语音采集”可以作为它的接通判据；提前计时的责任在通知文案那条路径，不在音频判据。

## 四、排查工具层面的修正

- `libxposed` 的 `log()` 写的是框架自身模块日志，不进 logcat；改为 `android.util.Log` 后依然看不到——OriginOS 对岛服务进程的日志做了整体过滤（`logcat --pid=<pid>` 零输出）。后续排查统一改用 `dumpsys audio` 采样，以及从设备拉回 APK 比对 dex 字符串来确认“装的是不是新包”。
- 设备侧可用 `adb`（`C:\ADB\adb.exe`，V2408A / Android 17 / OriginOS 7）核对 `lastUpdateTime`、SystemUI 与岛进程启动时间。

## 五、能否推广到其他应用

`VOICE_COMMUNICATION` 录音这个判据只对“接通才开麦”的应用可靠（微信已验证）。两类反例需要注意：

- 拨号即开麦的 VoIP 实现，会提前计时；
- 非通话应用也会占用该通道（采样中抖音就有 1 秒级的 `VOICE_COMMUNICATION` / `MIC` 会话）。

因此推广策略是按包做能力登记：验证过“接通才开麦”的包走计时，其余保持固定“通话中”不计时；未知包默认不计时。

## 六、文件级改动

| 文件 | 改动 |
|-|-|
| `NativeIsland.java` | 通话岛自绘行的点击回调改为 `host.openCall(pkg)` |
| `IslandHost.java` | 新增 `openCall()` 与 `bringCallTaskToFront()`（`getRunningTasks` 定位 + 反射 `moveTaskToFront`） |
| `CallWatcher.java` | `onCallNotice` 两处 `RAW_CLOCK` 豁免；新增音频状态采样 `playbackSummary()` |
| `ModuleMain.java` | `info` / `error` 改为 `android.util.Log`，定位日志被过滤的问题 |

## 七、已知限制

1. 点岛是否直达通话页取决于应用自身通知：通知被撤回时只能走任务置前，个别情况下会落在应用首页。
2. 任务置前受应用自身 `launchMode` 影响，小窗 / 分屏表现需按应用验证。
3. 岛服务进程日志被系统过滤，后续排查依赖音频状态采样。
4. 版本号未提升，覆盖安装不会提示升级，安装后需重启手机（或重启系统界面）才会加载新代码。
5. QQ 的点击路径与微信共用 `openCall`，但“等 20 秒再接”这类计时实测只对微信做过。

## 八、安装包

`OriginOS消息岛-0.1.32-微信接通才计时.apk`（2,479,056 B，已签名，可直接覆盖安装）。
