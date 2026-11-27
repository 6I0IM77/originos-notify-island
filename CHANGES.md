# 0.1.33 改动说明

## 横屏跟随状态栏，不再常驻应用中央

原生两道显示门控：数据层 `AtomIslandsDataController.handleAtomIslandReceiversData`（bean 进入待显示列表需 `mStatusBarShowing || forceShowIsland() || ...`），窗口层 `IslandWindowControllerImpl.applyVisible`（窗口显示需 `(mForceIslandVisible || mStatusBarVisible || mPortrait) && 数据存在 && 非 AOD`）。本版把自有岛屿也按这两道门控处理：

- **数据层**：hook 过滤与 bean `forceShowIsland()` getter；横屏时只有原生状态栏已出现（优先读 `isStatusBarShowing()`，该接口缺失的配置回退通知面板 / QS 展开标志）才放行自有 bean；栏收起时不再强制把消息胶囊或中央卡片塞进待显示列表。
- **窗口层**：hook `applyVisible`；栏出现才在本次调用内临时抬高 `mForceIslandVisible`，调用结束恢复原值；栏收起时清掉残留 force，避免自有岛压在横屏应用上。原生的数据存在、AOD 判断、动画与窗口 / 输入清理仍然执行，force 标志不跨岛泄漏。
- 两层 hook 均包 try/catch：插件签名或字段不匹配时回退原生调用，模块安全停用而不是崩溃；OS5 缺失的 `mStatusBarVisible` 字段按可选反射处理。
- 消息内容、卡片样式、点击 / 长按 / 滑动手势、通话岛、计时与应用选择逻辑均未改动。

## 补齐仓库源码中缺失、0.1.32 已发布包内包含的改动

- **显示样式拆成左右两个独立选项**：左侧 = 联系人头像 / 应用图标，右侧 = 联系人名字 / 消息详细内容；右侧选“消息内容”时，联系人名字自动回到左侧，左侧同时显示头像或图标。设置改完随 RELOAD 广播原地重绘当前岛，无需重启。
- **通话通知识别补强**：QQ 等不带“通话”字样的呼叫文案（正在呼叫我 / 呼叫你 / 等待对方接听 / 正在等待对方等）按通话形态判定（ongoing 事件、全屏意图、或带接听 / 拒绝 / 挂断按钮）；读取 Android 12 通话通知 `callType` 区分来电与接通；通话通知只保留对方名字供点击路由，不计未读、不占主岛。
- 支持赞赏 / 咖啡两张二维码分别保存到相册。

版本 `versionCode = 37`、`versionName = "0.1.33"`，沿用原模块相同签名，可覆盖安装。

构建：`JAVA_HOME=<JDK21> ANDROID_HOME=<Android SDK> bash ./gradlew :app:assembleRelease`，再 `zipalign -p -f 4` 后用 V3 签名。横屏门控契约由 `tests/LandscapeContractCheck` 覆盖（OS5/6/7 栏显 / 栏隐放行、嵌套与异常清理、原生 force 保留）。

# 0.1.32 改动说明

本仓库为 [joelau0503/originos-notify-island](https://github.com/joelau0503/originos-notify-island) 的第三方修改分支，基于原作者 0.1.31 源码继续开发，版本号保持 `versionCode = 33`、`versionName = "0.1.32"`。

改动集中在五个方面：多岛共存与挤压、单岛宽度自适应、卡片环境色渐变、第三方通话岛、设置页重排与即时生效。每一节先给出行为变化，再给出实现位置，便于对照源码。

---

## 1. 多岛共存与挤压

**行为**：原作者的消息岛是独占型（`AtomIslandsType = 1`），上岛时会顶掉官方音乐岛、导航岛、通话岛。修改后消息岛可以与其他实时岛同屏：新消息占主位，旧的实时内容被压成左侧小球，两者互相挤压而非互踢。

**实现**：
- `NativeIsland.show()` 中 `buildAtomIslandsType(2)`，`buildScene("MMS")`，与官方分岛规则对齐。
- 依赖官方 `AtomIslandsInfo.updateInfoFromToShowList()`：只有当两个 bean 的 type 均不为 1 时才保留第二个岛。

**已废弃的方向**：反射改写主/侧岛槽位字段实现挤压，会导致通知无法销毁、官方音乐岛不关闭、微信通话手势失效，已彻底删除，请勿再试。

---

## 2. 单岛宽度自适应

**行为**：原作者在单岛状态（state 2）用 `cutout/2 + max(左宽, 右宽)` 决定边界，左右对称，内容少的一侧留大片死黑。修改后左右两侧按各自实测内容宽度收紧。

**实现**：
- `MessageViews.adaptiveHalfWidth()` 增加 `left` 参数，按侧计算所需宽度。
- `NativeIsland` 内 hook `ContainerAnimatorCommon.calculateBounds()`：state == 2 时把 `mMainBounds.left/right` 改为 `cutout/2 + 对应侧实测宽`，镜像官方 state 1/3 的非对称边界。
- 字段 `mMainBounds`、`mCenterCutout`、`mCapContentLeft/Right` 实际声明在父类 `AbsContainerAnimator`，因此用 `fieldOwner()` / `fieldIn()` 沿继承链查找，并加外层 try/catch：接口不匹配时模块安全停用而不是直接崩溃。

---

## 3. 卡片环境色渐变

**行为**：卡片背景不再是固定色，左半取发送人头像主色、右半取应用图标主色，两色沿弧线插值过渡，叠加多层柔光，并对边缘高光做逐像素压制。

**实现**：
- 新增 `IslandTint.java`（约 390 行）：`buildSurface()` 生成渐变位图，`GLARE_LIMIT = 0.185` 控制眩光压制强度。
- 头像取色失败或没有头像时，退回应用图标主色。

---

## 4. 播放类通知过滤

**行为**：音乐、视频、直播等“正在播放”的通知不再上岛，交给系统官方媒体岛；下载、安装、导出等带进度条的任务通知照常上岛。

**实现**（`SourceBridge.playback()`）：
- 判定为播放的条件：`MediaStyle` 模板、带媒体会话令牌（`EXTRA_MEDIA_SESSION` / `android.mediaSession`）、`CATEGORY_TRANSPORT`、视频类应用（抖音/极速版/直播/快手/快手极速/B站/YouTube）播放期间的常驻通知。
- 单独一个进度条**不构成**过滤条件；早期版本曾把“常驻 + 带进度”一并当作播放，导致下载被误伤，现已收窄。

---

## 5. 第三方通话岛

**行为**：
- 微信、QQ、TIM 及其他占用语音通话通道的应用通话时上岛；系统自带电话不接管，仍由官方通话岛显示。
- 左侧联系人头像与名字，右侧通话时长；点击胶囊打开对应应用，长按不触发小窗。
- 未接通时右侧显示“正在呼叫”；对方接听后从 0 开始计时。
- 计时起点有 0–800ms 的可配置延迟（默认 300ms），用于对齐应用自身的计时。
- 通话结束约 1 秒撤岛；左滑撤岛后本次通话不再出现。
- 消息与通话同时发生：消息占主岛，通话压成左侧小球。

**实现**：
- 新增 `CallWatcher.java`（约 340 行）：轮询 `AudioManager.getActivePlaybackConfigurations()` / `getActiveRecordingConfigurations()`，识别 `USAGE_VOICE_COMMUNICATION` 与 `AudioSource = VOICE_COMMUNICATION`；归属包名用反射调用隐藏 API `getClientUid()`，失败回退 `getClientPid()` 读 `/proc/<pid>/cmdline`。
- 接听判定：麦克风采集开始，或通话通知文案由“等待对方接听”变为“通话中 / 正在语音通话”；通知仍写“等待对方接受邀请”时强制停留在呼叫中，避免拨号即计时。
- 岛屿本体在 `NativeIsland`：id `Wire.PREFIX + "call"`、type 2、priority 3、scene `INCALL`、leftTemplate 1、rightTemplate 4、templateCard 6、permanent、showTime MAX；右侧时长由岛内 `TextView` 每 250ms 自刷，不重复推送，避免重播入场动画。
- 采集侧配套改动（`SourceBridge`）：
  - 通话通知（`CATEGORY_CALL` 或文案含“通话/邀请/来电”）一律放行，不受消息范围与播放过滤限制；
  - 写入时带 `call=1` 标记，`IslandHost` 只入库不上岛，避免出现第二个胶囊；
  - 新增 `alt` 字段汇总 `EXTRA_TITLE`、`EXTRA_SUB_TEXT`、`EXTRA_INFO_TEXT`、`EXTRA_TEXT_LINES`，用于取对方昵称；
  - 新增 `Wire.PEER` / `PEER_REPLY` 实时回查通道：SystemUI 侧请求后，采集侧用 `getActiveNotifications()` 直读原始通知回传名字与头像，模块侧原地替换胶囊内容。

---

## 6. 设置页重排与即时生效

**行为**：设置页由平铺开关改为四组；显示样式合并为四选一；新增“计时延迟”滑杆；任何设置改完立即生效，无需重启手机。

**分组**：
| 分组 | 内容 |
| --- | --- |
| 运行状态 | 模块 / 采集 / 显示状态灯与刷新 |
| 消息岛 · 内容 | 选择应用、显示样式、未读数量、扩展显示 |
| 通话岛 · 实时状态 | 通话上岛、显示联系人头像、所有应用通话上岛、计时延迟 |
| 停留与消失 | 消息常驻、停留时间 |

**实现**：
- `MessageDisplay` 枚举统一承载显示样式（`CONTENT` / `AVATAR` / `SENDER_ONLY` / `APP_ONLY`），一个偏好值即保证互斥；子开关按所选模式条件显示。
- 新增偏好 `call_settle_ms`（0–800，50 一档，默认 300），`NativeIsland.callSettle()` 与 `CallWatcher.begin()` 均从此读取。
- 新增广播 `Wire.RELOAD`：应用侧任何写入（勾选应用、所有开关、滑杆、样式选择器、总开关）后通知 SystemUI 重绘。
- `NativeIsland` 引入单调递增 node token，确保原生控制器把重绘当作新内容而非重复内容忽略。

偏好键清单（均有读取方）：`enabled`、`scope_packages`、`show_count`、`wide`、`message_display`、`sender_only_avatar`、`app_only_avatar`、`permanent`、`show_seconds`、`call_island`、`call_contact_avatar`、`call_island_all`、`call_settle_ms`、`bridge_token`。

---

## 7. 文件级清单

| 文件 | 状态 | 说明 |
| --- | --- | --- |
| `app/src/main/java/com/joelau/notifyisland/CallWatcher.java` | 新增 | 通话状态机与轮询 |
| `app/src/main/java/com/joelau/notifyisland/IslandTint.java` | 新增 | 环境色渐变 |
| `NativeIsland.java` | 修改 | 通话岛、宽度 hook、node token、重绘 |
| `SourceBridge.java` | 修改 | 播放过滤收窄、通话放行、`alt`、PEER 回查 |
| `MessageViews.java` | 修改 | 按侧宽度计算、单行滚动文本 |
| `IslandHost.java` | 修改 | 通话入口、RELOAD 监听、只入库不上岛 |
| `MainActivity.java` | 修改 | 设置页四组、样式四选一、计时延迟 |
| `Wire.java` | 修改 | `PEER` / `PEER_REPLY` / `RELOAD` |
| `MessageDisplay.java` | 修改 | 显示模式枚举与互斥 |
| `EntryStore.java` / `CardMotion.java` / `IslandIcons.java` | 修改 | 存储去重、卡片手势、图标取色 |
| `build.gradle.kts` / `proguard-rules.pro` / `scope.list` / `java_init.list` | 修改 | 编译、混淆与 LSPosed 作用域 |

---

## 8. 构建

```bash
export JAVA_HOME=<jdk21>
export ANDROID_HOME=<android-sdk>   # build-tools 37.0.0, platform android-37.0
./gradlew :app:assembleRelease
# zipalign -p -f 4 后续用 apksigner 签名
```

`minSdk 35`、`targetSdk 36`、`compileSdk 37`。

## 9. 已知限制

1. 通话联系人的名字与头像取自应用自身的通话通知；通知不带对方信息时退回显示应用名与应用图标，属于数据源限制。
2. 不走语音通话通道的应用（普通录音、K 歌、语音消息）不会被识别为通话，避免误判。
3. 宽度与边界 hook 依赖 SystemUI 插件内部字段，系统大版本更新后可能失效；已有“接口不匹配即安全停用”保护。
4. 版本号沿用原作者节奏，未提升 `versionCode`，覆盖安装时系统不会提示版本升级。
