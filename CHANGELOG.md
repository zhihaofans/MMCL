# 更新日志

本文件的格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 计划中

- 插件加载测试覆盖循环依赖、缺失依赖、异常隔离的完整矩阵
- `core` 的启动执行链（JVM 参数组装、主类调用）
- Java 运行时检测与下载

## [0.0.1] - 2026-10-30

首个可用骨架版本：能构建、能跑起来、能加载插件，但还不能启动游戏。

### 新增

- 仓库文档：`AGENTS.md`（AI Agent 规则，含 git 授权规则）、`ARCHITECTURE.md`、`PLUGIN_DEV.md`、`TODO.md`、`CHANGELOG.md`
- Gradle 多模块骨架：`api` / `api-ui` / `core` / `app` / `plugins/hello`，版本号单一来源 `gradle.properties`（`0.0.1`）
- `api`：`Plugin`、`PluginContext`、`LauncherInfo`、`EventBus`、`ServiceRegistry`、能力接口（`AuthProvider` / `DownloadSource` / `LoaderInstaller`）、内置事件
- `api-ui`：`UiContribution` / `UiPanelRegistry` 扩展契约
- `core`：基于 `ServiceLoader` 的插件发现、依赖拓扑排序（含循环与缺失依赖标记）、生命周期与异常隔离、事件总线、服务注册表、跨平台数据目录、内置插件同步
- `app`：Compose Multiplatform 桌面外壳，展示启动器信息与已加载插件列表
- `plugins/hello`：内置示例插件，演示 `META-INF/services` 声明与服务注册
- 单元测试：依赖排序、事件总线异常隔离、服务注册、插件生命周期隔离

### 已知限制

- 尚未实现游戏启动（`LaunchService` 只有契约，没有执行链）
- 尚未做账户认证、下载源、加载器安装等实际插件
- 尚未产出 Windows / Linux 安装包
- 未签名 / 未公证 macOS 产物

### 验证

- 工具链：Gradle 9.6.1 wrapper + JDK 21 toolchain（Temurin 21.0.13 arm64），字节码目标 17
- `./gradlew build` 通过；`core` 34 个单元测试、`plugins:hello` 2 个单元测试全绿
- `./gradlew :app:run` 在 macOS 27.2（Apple Silicon）上显示窗口，内置插件
  `dev.mmcl.plugin.hello` 经 `ServiceLoader` 加载并进入 `ENABLED`
