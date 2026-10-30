# TODO

> 里程碑与版本对应关系见 `CHANGELOG.md`；拆分功能的判断标准见 `AGENTS.md` 第 2 节。
> 勾选状态反映仓库真实进度，做完一项就改一项，不要提前打勾。

## 0.0.x — 骨架（当前）

- [x] 三份核心文档 + `TODO.md` / `CHANGELOG.md`
- [x] Gradle 多模块骨架（`api` / `api-ui` / `core` / `app` / `plugins/hello`）
- [x] 版本号 `0.0.1` 单一来源
- [x] 插件发现（`ServiceLoader`）+ 依赖拓扑排序 + 异常隔离
- [x] 事件总线与服务注册表实现
- [x] 跨平台数据目录（macOS / Windows / Linux）
- [x] 内置插件同步到 `<dataDir>/plugins/`
- [x] Compose 桌面外壳 + 插件列表
- [x] `./gradlew build` 全绿（含测试，core 34 个用例）
- [x] `./gradlew :app:run` 本机冒烟通过（窗口显示 0.0.1 + hello 插件 ENABLED）
- [x] 无头渲染测试：`AppShellRenderTest` 输出外壳截图到 `app/build/screenshots/`
- [ ] ktlint / detekt 接入 CI 前置检查
- [ ] 插件版本自动化：把 `build.gradle.kts` 的 `version` 生成为资源，消除与 `Plugin.version` 的重复

## 0.1.x — 能启动游戏

- [x] 版本清单（`version_manifest`）解析、多源尝试与缓存（30 分钟 + 离线回退）
- [x] `source-mojang` 版本源插件（0.0.1，**独立分发，不随应用打包**）
- [ ] `source-bmclapi` 等镜像源插件
- [x] 版本详情解析与本地缓存（`versions/<id>/<id>.json`）
- [x] 下载引擎：并发下载、SHA-1 校验、临时文件 + 原子落盘、跳过已校验文件、拒绝路径穿越
- [x] 文件规划：client JAR / libraries（按平台 rules 过滤）/ asset index / 日志配置
- [x] 真实数据冒烟：最新正式版规划出 83 个文件、约 127 MB
- [ ] 资源对象下载（`assets/objects/<hash>`）—— 地址基址需要由来源插件提供
- [ ] 老版本 natives / classifiers 下载（1.19 之前）
- [ ] 下载失败重试与限速
- [ ] 游戏文件下载与 SHA-1 校验
- [ ] Java 运行时检测（已有 JDK / 启动器自带 JRE）
- [ ] JVM 参数与主类组装，真实启动 Minecraft
- [ ] 游戏进程日志捕获与退出码上报（`GameExited`）
- [ ] 实例目录与实例配置管理
- [ ] 离线账户（`auth-offline` 插件）

## 0.2.x — 界面外壳与主题（对应 `UI_DESIGN.md` P1 / P2）

- [x] P1：`NavRail` + 顶栏 + 状态栏 + 内容区外壳
- [x] P1：`UiPage` / `AppNavigator` 契约与路由，4 个内置页（游戏 / 下载 / 设置 / 插件）
- [x] P1：示例 UI 插件 `plugins/ui-about`
- [x] P1：`UiContribution` → `UiPage`（API-BREAK: minor），删除 `UiPanelRegistry`
- [x] P2：新模块 `ui-kit`（调色板 / 图标映射 / `PageScaffold` / 间距）
- [x] P2：`LauncherTheme` + `ThemeController`，主题热切换
- [x] P2：`SettingsService`（core）+ `PluginContext.settings`（API-BREAK: minor）
- [x] P2：`--safe-mode`，跳过全部插件主题与页面
- [x] P2：示例主题插件 `plugins/theme-sakura`
- [x] 构建期校验：插件 JAR 禁止包含 `androidx/compose/**`
- [ ] P3：首页 / 实例页接 core 数据服务（依赖 0.1.x 的实例与启动链）
- [ ] P4：`UiWidget` / `UiSlot`（顶栏、状态栏、首页卡片）

## 0.3.x — 插件生态

- [ ] `auth-microsoft`（设备码 / OAuth 流程）
- [ ] `download-modrinth` + `download-curseforge`
- [ ] `loader-fabric` / `loader-forge` / `loader-neoforge`
- [ ] 插件设置页
- [ ] 插件版本冲突、签名与来源校验

## 0.4.x — 打包发布

- [ ] Windows 安装包（MSI/EXE）与 Linux 发行包（deb/AppImage）
- [ ] macOS `.app` / `.dmg`，Developer ID 签名 + 公证
- [ ] 自动更新通道
- [ ] 首次启动向导

## 1.0.0 — 稳定版

- [ ] `api` 冻结并承诺向后兼容（见 `ARCHITECTURE.md` §6.1）
- [ ] 崩溃上报与插件故障自愈
- [ ] 三平台真实用户验证

## 待定 / 想法

- 插件市场（远程索引 + 一键安装）
- 插件沙箱与热重载
- 存档备份、日志分析等工具插件
