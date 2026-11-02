# 更新日志

本文件的格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 新增

- Windows 安装包配置：`windows { upgradeUuid（固定）, menuGroup, perUserInstall, dirChooser }`
- GitHub Actions 三平台打包（`.github/workflows/package.yml`）：手动触发或推送版本 tag 时产出
  dmg / msi / deb 并上传 artifact；macOS job 额外用 JDK 27 打包，三个平台版本号一致（`0.0.2`）
- 三平台安装包首次出包成功：`workflow_dispatch` 运行 `41431690295` 产出 macOS dmg / Windows msi /
  Linux deb 三个 artifact，均可下载

### 修复

- Linux「打开插件目录」：优先系统文件管理器（Thunar 等），找不到再退回 `xdg-open`，避免被 `$BROWSER` 当成网页打开
- 版本映射只在手动 `-PpackageJdk` 指定 JDK < 27 且在 macOS 打包时触发（实测 JDK 21 同样拒绝 0.x）；
  默认 JDK 27 下三平台都不映射
- Windows MSI 打包失败：安装包元信息里的中文超出 MSI 数据库代码页 1252，`light.exe` 报 LGHT0311；
  打包用的 `description` 改为 ASCII，界面内中文不受影响
- `DirectoryLauncherTest` 的期望值改用 `Path.toString()`，Windows 上不再因 `/` 与 `\` 差异失败
- 运行时来源解析不再静默吞异常：记录来源 id 与原因，且只捕获 `Exception`
- 安装器对插件提供的 `fileName` 只取 basename 并走路径校验，防止写出临时目录
- 平台校验把空的 `OS_NAME` / `OS_ARCH` 视同缺失，不再误杀合法运行时
- 运行时来源回退覆盖**安装阶段**失败：来源给错平台的包时自动尝试下一个来源

### 变更

- 构建工具链升级到 **JDK 27**（`jvmToolchain(27)`，字节码目标仍是 17）：编译与 jpackage 共用
  同一个 JDK，三平台安装包版本号统一为真实版本（`0.0.2`）；CI 也只装 JDK 27

- 设置页「Java 运行时来源」增加「自动（按优先级）」选项，选错可一键回到自动
- 「打开插件目录」移到 IO 线程执行，不再阻塞界面
- 运行时安装按魔数识别 zip / tar.gz：来源把格式标错也能正确解压
- 文档重整：`TODO.md` 补可玩闭环、启动参数／日志／修复、整合包与阻塞决策；去掉对其它启动器项目的对照表述（含 `UI_DESIGN.md`／`CHANGELOG` 历史措辞）

## [0.0.2] - 2026-11-01

### 新增

- 下载失败重试：单文件最多 3 次、指数退避；只重试网络错误 / 429 / 5xx，404/403 与 SHA-1 不符不重试
- 下载限速：设置页「限速（KB/s）」写入 `download.maxKbPerSecond`，所有并发下载共享一个令牌桶
- Java 运行时下载：`JavaRuntimeProvider` 扩展点 + core `JavaRuntimeInstaller`
  （下载 → 校验 SHA-1 → 解压 zip/tar.gz → 校验 `release` 版本 → 安装到 `runtime/<os>-<arch>/jdk-<major>/`）；
  缺少 Java 时游戏页可直接下载并自动重试启动
- `plugins/runtime-adoptium`：Adoptium Temurin 来源示例（不内置，需单独安装）
- `UiWidget` / `UiSlot` 插槽小部件：插件可往顶栏（`TOP_BAR`）、状态栏（`STATUS_BAR`）、
  首页卡片区（`HOME`）加内容；`UiRegistry` 统一收集、去重、排序，安全模式全部忽略
- `plugins/ui-about` 注册一张首页卡片与一个状态栏标记，作为 widget 示例
- 首页（默认页）：账户与下载源状态、最近实例（最多 3 个，可一键启动）、快捷入口
  （安装新版本 / 下载 / 插件 / 设置）；「启动」跳转游戏页执行，日志与停止只有一份实现
- `Instance` 增加 `lastLaunchedAtEpochMillis`（旧 `instances.json` 直接用默认值 0 兼容），
  `InstanceService` 增加 `markLaunched(id)` 与 `recent(limit)`，启动成功后自动记录
- `BUILD_WINDOWS.md`：Windows 上的编译、测试、运行与打包手册
- `BUILD_WINDOWS.md` 增补「编译并安装插件」：内置 / 非内置插件对照表、`gradlew :plugins:<name>:jar`、
  复制到 `%APPDATA%\MMCL\plugins` 后重启验证，以及 JAR 声明、多版本、Compose 冲突三个坑
- Java 运行时来源可选：「设置 → Java 运行时来源」列出已装插件注册的来源，点击即写入 `java.runtime.provider`；
  `JavaRuntimeProvider` 增加 `priority`（默认 100），用户没选时按优先级自动挑，插件卸载后设置自动失效
- `plugins/runtime-foojay`（不内置）：通过 foojay Disco API 一次提供 Zulu / Liberica / Microsoft / Corretto /
  Dragonwell / Oracle OpenJDK 多个发行版，已自动按宿主 `os`/`arch` 过滤，不再自己拼压缩包名
- 设置页新增「打开插件目录」按钮：按平台用 `open` / `explorer.exe` / `xdg-open` 打开插件目录，目录不存在时先创建；
  打不开就在按钮下方显示原因，不弹错误框也不中断界面
- `plugins/runtime-mirror`（不内置）：国内镜像来源——清华 TUNA / 中科大 USTC 的 Temurin、
  华为云的 Oracle OpenJDK。镜像站没有"最新版"接口，插件读目录列表按版本号挑最高的包
- 下载 Java 时按来源顺序逐个尝试：来源不支持该平台或请求失败就换下一个，用第一个能给出的，
  镜像站缺某个平台的包时不会直接报"该来源不支持"

### 修复

- `app/build.gradle.kts` 不再在配置阶段强查 JDK 27 工具链：`launcherFor{...}.getOrNull()` 在找不到匹配
  工具链时实际会抛 `ToolchainProvisioningException`，导致没有 JDK 27 的机器连 `help` 都跑不了；
  现改为捕获异常，并按**实际打包 JDK 的主版本**决定是否把 `0.x` 映射成 `1.x`
- `-PpackageJdk` 支持 JDK 根目录 / `bin` 目录 / `java` 路径三种写法；路径非法时打告警并继续，
  不再退化成 `Task 'Files\Eclipse' not found` 这种误导性报错
- 打包格式按宿主平台选择（Windows `Msi` / macOS `Dmg` / Linux `Deb`）：原脚本硬编码 `Dmg`，
  Windows 上 `packageMsi` 任务根本不存在
- 测试跨平台化，修复 Windows 上 4 个失败：`MinecraftLauncherTest` 不再依赖 `/bin/sh`
  （改用当前 JDK 的单文件源码模式造进程），`LaunchCommandBuilderTest` / `JavaRuntimeInstallerTest`
  的路径断言先统一分隔符再比较
- `BUILD_WINDOWS.md` 修正渲染测试的描述：`AppShellRenderTest` 实际是 11 个用例（不是 10 个），
  且用 `ImageComposeScene` 离屏渲染、不创建窗口，headless 环境同样能跑（附软件渲染的开启方式）
- Java 运行时安装前校验平台：读取压缩包内 `release` 的 `OS_NAME` / `OS_ARCH`，与宿主不符就拒绝安装并说明原因。
  此前只校验大版本，macOS arm64 上装了 x64 的包也能装进去，要到启动游戏才因架构不兼容而崩

### 移除

- `hello` 示例插件不再随应用内置（模块保留，开发时可手动安装）
- 删除 `GamePrepCard` 与渲染测试里的安装演示：下载页的版本行/任务行已接管，
  组件只剩截图用途；同时清理不再使用的测试桩

## [0.0.1] - 2026-11-01

第一个可玩的版本：能构建、能跑、能加载插件、界面可换肤、能下载并校验游戏文件，
并在 macOS Apple Silicon 上**真实启动 Minecraft `26.3`**（内置离线账户、多实例、游戏日志）。

### 新增

- 仓库文档：`AGENTS.md`、`ARCHITECTURE.md`、`PLUGIN_DEV.md`、`UI_DESIGN.md`、`TODO.md`、`CHANGELOG.md`
- Gradle 多模块：`api` / `api-ui` / `ui-kit` / `core` / `app` / `plugins:hello|ui-about|theme-sakura`；
  启动器版本单一来源 `gradle.properties`
- `api`：`Plugin`、`PluginContext`、`LauncherInfo`、`EventBus`、`ServiceRegistry`、`SettingsService`、
  能力接口（`AuthProvider` / `DownloadSource` / `LoaderInstaller`）、内置事件
- `core`：`ServiceLoader` 插件发现、依赖拓扑排序（含循环/缺失标记）、生命周期异常隔离、
  事件总线、服务注册表、跨平台数据目录、内置插件同步（带清单清理）、`FileSettingsService`
- `app`：外壳布局（左导航 / 顶栏 / 内容区 / 状态栏），内置 游戏 / 下载 / 插件 / 设置 四页
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

### 修复

- 无下载源时游戏页不再被空态盖住：实例列表照常显示，仍可启动本机已装好的实例
  （版本详情优先读本地缓存）；只在启动缺少详情时提示
- 无源提示统一写「下载源插件」，界面与日志不再点名具体插件（应用保持来源中立）

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
- 无头渲染出深色 / 樱花主题 / 设置页 / 插件页 / 版本清单 / 未安装源（含实例仍可见） / 安装卡片 / 安装进度 / 实例卡片 / 游戏日志 / 下载页 十一张截图
- 本机实跑截图确认：导航含插件页「关于」，设置页显示三个主题与下载源（mojang）
- Java 检测新增 10 个用例：来源优先级、版本不够跳过、缺 `release` 不误认、macOS 兜底、版本归一
- 真实冒烟：最新正式版 26.3 解析出 83 个待下载文件、约 127 MB、0 条告警
- 实跑：未装源插件时日志告警且不发起请求；把 `source-mojang` 放进 `plugins/` 后重启，
  成功拉取清单（最新正式版 26.3，921 个版本）并写入缓存
