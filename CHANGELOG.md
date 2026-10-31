# 更新日志

本文件的格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

> **版本闸门**：能实际启动 Minecraft 之前固定 `0.0.1` —— **闸门已于 2026-10-31 达成**（真实启动 `26.3`）。
> 当前仍保持 `0.0.1`，这些条目未归档；改版本号、发版、打 tag 都要先经用户同意。

能构建、能跑、能加载插件、界面可换肤、能下载并校验游戏文件，但**还不能启动游戏**。

### 新增

- 仓库文档：`AGENTS.md`、`ARCHITECTURE.md`、`PLUGIN_DEV.md`、`UI_DESIGN.md`、`TODO.md`、`CHANGELOG.md`
- Gradle 多模块：`api` / `api-ui` / `ui-kit` / `core` / `app` / `plugins:hello|ui-about|theme-sakura`；
  启动器版本单一来源 `gradle.properties`
- `api`：`Plugin`、`PluginContext`、`LauncherInfo`、`EventBus`、`ServiceRegistry`、`SettingsService`、
  能力接口（`AuthProvider` / `DownloadSource` / `LoaderInstaller`）、内置事件
- `core`：`ServiceLoader` 插件发现、依赖拓扑排序（含循环/缺失标记）、生命周期异常隔离、
  事件总线、服务注册表、跨平台数据目录、内置插件同步（带清单清理）、`FileSettingsService`
- `app`：HMCL 式外壳（左导航 / 顶栏 / 内容区 / 状态栏），内置 游戏 / 下载 / 插件 / 设置 四页
- `api-ui` + `ui-kit`：`UiPage` / `AppNavigator` / `LauncherTheme` 契约，以及调色板、图标映射、
  `PageScaffold`、`EmptyState`、`SimpleLauncherTheme`
- 主题：`ThemeController` 热切换 + 持久化，内置深色/浅色；插件主题示例 `plugins/theme-sakura`
- UI 插件示例 `plugins/ui-about`（往导航栏加一页）
- 安全阀：`--safe-mode` / `MMCL_SAFE_MODE=1` 跳过全部插件主题与页面
- 构建校验 `:app:verifyPluginJars`：插件 JAR 禁止打包 `androidx/compose/**`
- 无头渲染测试 `AppShellRenderTest`，截图输出到 `app/build/screenshots/`
- 版本清单：`VersionManifest` 模型 + `VersionManifestService`（多源逐个尝试、30 分钟缓存、
  离线回退到过期缓存、临时文件 + 原子写盘；脏 JSON 不落盘）
- `api`：`VersionManifestSource` 能力接口，源完全由插件提供
- 插件 `plugins/source-mojang`（0.0.1）：Mojang 官方源，**不进入 `builtin-plugins/`，需另行安装**
- 游戏页：有源时可刷新版本清单并显示最新正式版/快照；无源时提示安装版本源插件
- 设置页：下载源列表与当前缓存来源，可切换优先源（`version.source`）
- 下载引擎：`HttpTransport` / `JdkHttpTransport`、`FileDownloader`（并发、SHA-1 校验、
  临时文件 + 原子落盘、跳过已校验文件、拒绝路径穿越）
- 版本详情：`VersionDetail` 模型 + `VersionDetailService`（按清单 URL 拉取、本地缓存、坏缓存自愈）
- 文件规划：`GameFilePlanner`（客户端 JAR、按平台 `rules` 过滤的库、资源索引、日志配置；
  natives/classifiers 只告警不下载）
- 编排：`GameInstaller`（`plan` 只规划，`install` 规划 + 下载）
- 游戏页 UI 接线：最新正式版卡片提供「检查文件」「下载游戏文件」，带确定性进度条、
  失败原因与跳过项提示；字节数按 MB/KB 格式化
- Java 运行时检测：`JavaRuntimeDetector` 按「设置指定 → 自带 runtime → `JAVA_HOME` →
  `PATH` → macOS `java_home`」顺序找够用的 JDK，只读 `release` 文件判版本（不拉起进程），
  `1.8.0_392` 归一为 8
- 游戏页准备卡片显示 Java 状态：`Java 21（系统 JDK）` 或 `缺少 Java ≥21` 提示
- 测试：用 JDK 内置 `HttpServer` 跑通"详情 → 规划 → 下载 → 校验"全链路；
  `MMCL_NET_TESTS=1` 可跑真实 Mojang 冒烟
- 版本支持策略：`VersionSupport` 按「最新正式版 + 同主版本系列」判定；版本清单里标注
  `支持 / 不受支持`，快照与旧大版本只展示、不可下载或启动
- 平台策略：macOS 仅 Apple Silicon（Intel Mac 启动时告警但不拦），Windows 默认目标 Win11
- 启动执行链：`LaunchRequest` / `LaunchCommandBuilder`（`arguments` 按 rules/features 求值、
  占位符替换、classpath 拼接、log4j 配置、离线 UUID）/ `MinecraftLauncher`（起进程、逐行日志、
  `GameLaunching` / `GameExited` 事件、停止游戏）
- natives 按库名架构标识过滤（`natives-macos` vs `natives-macos-arm64`），规划与 classpath 共用同一份过滤结果
- 游戏页：下载完成后出现「启动游戏 / 停止游戏」与运行状态
- 冒烟门槛改用 Gradle 属性：`-Pmmcl.netTests=1`（真实清单）、`-Pmmcl.realLaunch=1`（真实下载并启动）
- 离线账户改为内置插件 `plugins/auth-offline`：读设置 `player.name` 生成离线 UUID、token `0`、
  `userType=legacy`；随应用分发（`builtin-plugins/`），可删除或禁用
- `api`：`AuthProvider` 增加 `displayName` / `priority` / `createSession`，
  `Account` 增加 `accessToken` / `userType`（都有默认值）
- core 启动链不再内置离线占位：`LaunchRequest` 改收 `Account`，删除 `offlineUuid()`；
  没有认证插件时启动被拦下并提示
- `hello` 示例插件不再注册 `AuthProvider`，避免示例抢占真实能力
- 下载页接通：版本清单 + 安装/修复共享文件（自动建实例开关），版本列表可滚动、
  支持「正式版 / 快照 / 全部」筛选，无源时引导去设置装插件；
  游戏页只保留实例、启动与日志，缺文件时显示徽标/横幅并跳转下载页
- core 新增 `DownloadsService`：串行任务队列、同版本去重、`StateFlow` 暴露状态，
  任务属于应用级作用域，**切页不取消**；状态栏显示下载进度
- core 新增 `Installer` 接口（`GameInstaller` 实现），便于测试与后续加载器复用队列
- 页面间用 app 内部 `NavigationIntent` 传递“去下载某版本”，不改 `api-ui` 的 `AppNavigator`
- macOS 打包：`:app:packageDmg` / `:app:createDistributable`；`bundleID = dev.mmcl.launcher`、
  dock 名；打包优先使用 JDK 27（jpackage 24~26 拒绝 `0.x` 版本，JDK-8380634 在 27 修复），
  找不到 27 时才退回 `1.x` 映射；jlink 补齐
  `java.naming` / `java.sql` / `java.net.http` / `jdk.crypto.ec` / `jdk.unsupported`
- 资源对象下载：`AssetIndex` 模型 + `AssetPlanner`；来源插件新增 `assetObjectUrl(hash)` 能力
  （官方源返回资源 CDN 地址），补齐 `assets/objects` 后游戏不再因缺贴图崩溃（实测 5147 个对象 / 473MB）
- 游戏日志面板：实例卡片下方展开，500 行环形缓冲、自动滚到底、显示运行中/退出码、可清空；
  日志来自 `LaunchSession.output`（`SharedFlow<String>`），同时保留 `snapshot()` 供补齐历史
- 完整游戏日志落盘 `<dataDir>/logs/latest.log`（每次启动覆盖，退出前 flush，保留原始 log4j2 XML）
- 日志格式化：`GameLogFormatter` 把 log4j2 XML 事件合并成
  `[时间] [线程/级别] [类名] 消息`，异常堆栈缩进保留；解析失败退回原始内容，界面与缓冲用可读行
- 启动前检查共享文件：客户端 JAR 缺失时直接提示「先检查/下载文件」，不再让游戏进程报错
- 实例管理：`Instance` / `InstanceStore`（JSON 原子读写、坏文件自愈）/ `InstanceService`
  （创建、删除、自动重名后缀；实例目录含 saves/mods/config）
- 游戏页改为实例列表：新建（用最新正式版）、启动、删除（二次确认，说明会删存档）；
  libraries / assets / versions 在所有实例间共享，只有存档与配置在实例目录里
- 启动参数拆分 `sharedRoot`（共享文件）与 `instanceDir`（`game_directory`），
  同一份文件可以被多个实例复用

### 变更

- `UiContribution` → `UiPage`；删除 `UiPanelRegistry`（**API-BREAK: minor**）
- `PluginContext` 新增 `settings` 字段（**API-BREAK: minor**）
- `prepareBuiltinPlugins` 由 `Copy` 改为 `Sync`，内置插件同步增加 `.builtin-plugins.manifest`
- 插件版本与启动器版本解耦：所有插件统一从 `0.0.1` 起
- **应用内移除全部下载地址**：删除内置清单 URL 与 `JdkHttpFetcher`，HTTP 实现归插件所有
- 核心边界收窄为"启动已安装的游戏"：获取游戏文件的一切来源都是插件
- **支持范围收缩**：macOS 只做 arm64；Windows 默认 Win11；游戏与加载器只维护最新正式版系列

### 变更

- `LaunchRequest` 的 `gameDir` 拆成 `sharedRoot` + `instanceDir`（core 内部类型，不影响插件 API）

### 修复

- 并发下载时进度回调顺序可能倒退（出现过 `[2, 1]`）：完成计数与回调改为串行执行

### 已知限制

- 默认账户来自内置 `auth-offline`：仍是离线账户，不能登录正版服务器（`auth-microsoft` 未实现）
- 游戏启动后没有把输出流到界面（只写日志与内部回调）
- 首页/实例页还是空状态，等 `core` 的实例与账户服务
- 尚未做账户认证、下载源、加载器安装等实际插件
- 尚未产出 Windows / Linux 安装包；macOS 产物未签名/未公证

### 验证

- 工具链：Gradle 9.6.1 wrapper + JDK 21 toolchain（Temurin 21.0.13 arm64），字节码目标 17
- `./gradlew build` 通过；161 个单元测试全绿（core 131 / app 19 / 插件 11）
- **真实启动成功**（过版本闸门）：`26.3` 下载 70 个文件 / 124 MB、0 失败，用 Zulu 25 拉起，
  日志出现 `Setting user: SmokeTest` 与 `Backend library: LWJGL version 3.4.3+4`，发出 `GameLaunching`
- `./gradlew :app:run` 在 macOS 27.2（Apple Silicon）显示窗口，插件均 `ENABLED`
- `--safe-mode` 实跑：页面 4 个（插件页 0）、主题 2 个
- 无头渲染出深色 / 樱花主题 / 设置页 / 插件页 / 版本清单 / 未安装源 / 安装卡片 / 安装进度 / 实例卡片 / 游戏日志 / 下载页 十一张截图
- 本机实跑截图确认：导航含插件页「关于」，设置页显示三个主题与下载源（mojang）
- Java 检测新增 10 个用例：来源优先级、版本不够跳过、缺 `release` 不误认、macOS 兜底、版本归一
- 真实冒烟：最新正式版 26.3 解析出 83 个待下载文件、约 127 MB、0 条告警
- 实跑：未装源插件时日志告警且不发起请求；把 `source-mojang` 放进 `plugins/` 后重启，
  成功拉取清单（最新正式版 26.3，921 个版本）并写入缓存
