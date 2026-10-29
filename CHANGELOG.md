# 更新日志

本文件的格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

> **版本闸门**：启动器真正能启动 Minecraft 之前，版本号固定 `0.0.1`，不升版。
> 这一阶段的改动全部累积在 `[0.0.1]` 之下，发版时再整理。

## [Unreleased]

### 计划中

- 插件加载测试覆盖循环依赖、缺失依赖、异常隔离的完整矩阵
- `core` 的启动执行链（JVM 参数组装、主类调用）
- Java 运行时检测与下载

## [0.0.1] - 进行中

能构建、能跑、能加载插件、界面可换肤，但**还不能启动游戏**。

### 骨架

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

### 变更

- `UiContribution` → `UiPage`；删除 `UiPanelRegistry`（**API-BREAK: minor**）
- `PluginContext` 新增 `settings` 字段（**API-BREAK: minor**）
- `prepareBuiltinPlugins` 由 `Copy` 改为 `Sync`，内置插件同步增加 `.builtin-plugins.manifest`
- 插件版本与启动器版本解耦：所有插件统一从 `0.0.1` 起

### 已知限制

- 尚未实现游戏启动（`LaunchService` 只有契约，没有执行链）
- 首页/实例页还是空状态，等 `core` 的实例与账户服务
- 尚未做账户认证、下载源、加载器安装等实际插件
- 尚未产出 Windows / Linux 安装包；macOS 产物未签名/未公证

### 验证

- 工具链：Gradle 9.6.1 wrapper + JDK 21 toolchain（Temurin 21.0.13 arm64），字节码目标 17
- `./gradlew clean build` 通过；63 个单元测试全绿（core 45 / app 13 / 插件 5）
- `./gradlew :app:run` 在 macOS 27.2（Apple Silicon）显示窗口，三个内置插件均 `ENABLED`
- `--safe-mode` 实跑：页面 4 个（插件页 0）、主题 2 个
- 无头渲染出深色 / 樱花主题 / 设置页 / 插件页截图
