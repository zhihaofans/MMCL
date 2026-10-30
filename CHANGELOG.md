# 更新日志

本文件的格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

> **版本闸门**：启动器能实际启动 Minecraft 之前，版本号固定 `0.0.1`，不升版。
> 下面的条目在过闸门后整段归档为 `[0.0.1]`；改版本号、发版、打 tag 都要先经用户同意。

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

### 变更

- `UiContribution` → `UiPage`；删除 `UiPanelRegistry`（**API-BREAK: minor**）
- `PluginContext` 新增 `settings` 字段（**API-BREAK: minor**）
- `prepareBuiltinPlugins` 由 `Copy` 改为 `Sync`，内置插件同步增加 `.builtin-plugins.manifest`
- 插件版本与启动器版本解耦：所有插件统一从 `0.0.1` 起
- **应用内移除全部下载地址**：删除内置清单 URL 与 `JdkHttpFetcher`，HTTP 实现归插件所有
- 核心边界收窄为"启动已安装的游戏"：获取游戏文件的一切来源都是插件
- **支持范围收缩**：macOS 只做 arm64；Windows 默认 Win11；游戏与加载器只维护最新正式版系列

### 修复

- 并发下载时进度回调顺序可能倒退（出现过 `[2, 1]`）：完成计数与回调改为串行执行

### 已知限制

- 尚未实现游戏启动（`LaunchService` 只有契约，没有执行链）
- 首页/实例页还是空状态，等 `core` 的实例与账户服务
- 尚未做账户认证、下载源、加载器安装等实际插件
- 尚未产出 Windows / Linux 安装包；macOS 产物未签名/未公证

### 验证

- 工具链：Gradle 9.6.1 wrapper + JDK 21 toolchain（Temurin 21.0.13 arm64），字节码目标 17
- `./gradlew build` 通过；118 个单元测试全绿（core 95 / app 16 / 插件 7）
- `./gradlew :app:run` 在 macOS 27.2（Apple Silicon）显示窗口，插件均 `ENABLED`
- `--safe-mode` 实跑：页面 4 个（插件页 0）、主题 2 个
- 无头渲染出深色 / 樱花主题 / 设置页 / 插件页 / 版本清单 / 未安装源 / 安装卡片 / 安装进度 八张截图
- 本机实跑截图确认：导航含插件页「关于」，设置页显示三个主题与下载源（mojang）
- Java 检测新增 10 个用例：来源优先级、版本不够跳过、缺 `release` 不误认、macOS 兜底、版本归一
- 真实冒烟：最新正式版 26.3 解析出 83 个待下载文件、约 127 MB、0 条告警
- 实跑：未装源插件时日志告警且不发起请求；把 `source-mojang` 放进 `plugins/` 后重启，
  成功拉取清单（最新正式版 26.3，921 个版本）并写入缓存
