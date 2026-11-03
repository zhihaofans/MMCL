# ARCHITECTURE.md — 跨平台 Minecraft 启动器架构说明

> 面向开发者与维护者。AI Agent 规则见 `AGENTS.md`，插件开发见 `PLUGIN_DEV.md`。
> 本项目主力开发环境为 **macOS 27（Apple Silicon）**，同时产出 Windows / macOS / Linux 三平台产物。

---

## 1. 项目概览

一个 Minecraft Java 版启动器，运行于 **Windows / macOS / Linux**，管理 Forge / Fabric / NeoForge / Quilt 等游戏模组的安装与启动。

支持范围（产品策略，见 `AGENTS.md` §0）：

| 平台 | 支持情况 |
|---|---|
| macOS | **仅 Apple Silicon（arm64）**；Intel Mac 不专门适配 |
| Windows | 默认目标 **Windows 11**；Win10 及更旧不专门兼容 |
| Linux | 主流发行版 |

| 游戏版本 | 支持情况 |
|---|---|
| 最新正式版及其同主版本系列（如 `26.x`） | ✅ 支持 |
| 快照 / old_beta / old_alpha | ❌ 仅展示，不下载/不启动 |
| 旧大版本（如 `1.21.x`） | ❌ 仅展示，不下载/不启动 |

设计目标：

1. **跨平台**：一套代码三平台原生运行
2. **最大化插件化**：外围功能以插件形式扩展
3. **稳定核心**：核心极简，保证“永远能启动”

版本策略：启动器遵循语义化版本，当前 **0.0.3**（能实际启动游戏之前固定不升版的闸门已过）；版本号唯一来源是 `gradle.properties` 的 `version`，`app` 通过 `BuildConfig`/资源读入，代码中不得重复定义。`0.x` 阶段允许破坏性 API 变更（提交标注 `API-BREAK`），`1.0.0` 起按 §6.1 执行。插件各自独立版本，**从 `0.0.1` 起**，与启动器版本解耦；插件包名遵循 `<pluginId>-<插件版本>.jar`。

> 游戏模组（放进 `mods/` 的 JAR）的通用性与本启动器语言无关；启动器只负责调用加载器安装器并管理文件。

---

## 2. 技术栈

| 领域 | 选型 |
|---|---|
| 语言 | Kotlin 2.4.x（编译用 JDK 27 toolchain，字节码目标 17） |
| UI | Compose Multiplatform 1.12.x |
| 构建 | Gradle 9.6.x（Kotlin DSL，只用 wrapper） |
| 插件发现 | `ServiceLoader` + 独立 `URLClassLoader` |
| 异步 | `kotlinx.coroutines` |
| 日志 | SLF4J + 平台实现（logback） |
| 测试 | JUnit 5 + kotlin.test |
| 开发主机 | macOS 27（Apple Silicon 优先） |
| JDK | Temurin 27（toolchain + jpackage），`java -version` 显示 `aarch64` |

---

## 3. 核心理念：瘦核心 + 富插件

```
┌─────────────────────────────────────────────┐
│  app  (入口 + Compose 外壳 + 内置插件打包)     │
├─────────────────────────────────────────────┤
│  core (插件运行时 + 启动引擎 + 事件总线)        │
├─────────────────────────────────────────────┤
│  api  api-ui  ui-kit  (契约 + UI 公共组件)      │
├─────────────────────────────────────────────┤
│  plugins/*  (认证 / 下载源 / 主题 / 面板 ...)   │
└─────────────────────────────────────────────┘
```

原则：

- **核心负责“如何可靠地启动”**，插件负责“启动什么”和“还能做什么”。
- 插件崩溃不能拖垮核心；核心崩溃是 P0 事故。
- API 是唯一契约，主程序与所有插件只依赖它。

---

## 4. 模块结构与依赖规则

```
/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties        # version=0.0.3（启动器版本唯一来源）
├── gradle/
│   ├── libs.versions.toml   # 依赖版本目录
│   └── wrapper/
├── api/                 # 纯契约：接口、数据类、事件、异常
├── api-ui/              # UI 扩展契约（依赖 Compose）
├── ui-kit/              # UI 公共组件：调色板、图标映射、PageScaffold（规划中，见 UI_DESIGN.md）
├── core/                # 插件运行时、启动引擎、事件总线、服务注册
├── app/                 # main 入口、Compose 窗口、组装
└── plugins/
    ├── hello/           # 示例插件（不内置，开发时手动安装）
    ├── ui-about/        # 已落地：UI 页面插件示例
    ├── theme-sakura/    # 已落地：主题插件示例
    ├── auth-microsoft/  # 规划中
    ├── auth-offline/
    ├── download-modrinth/
    ├── download-curseforge/
    ├── loader-forge/
    ├── loader-fabric/
    ├── ui-theme-dark/
    └── tool-log-analyzer/
```

**依赖方向（只允许自上而下）：**

```
app ──► core ──► api
 │                ▲
 ├──► ui-kit ──► api-ui ──► api
 └──► plugins/* ──► api (UI 插件：ui-kit)
```

**禁止的依赖：**

- `api` → 任何模块
- `core` → 任何具体插件
- 插件 A → 插件 B（通过事件总线或服务注册通信）
- 非 UI 插件 → `api-ui` / `ui-kit`（避免被 Compose 污染）
- `core` / `api` → Compose（UI 类型只允许出现在 `app`、`api-ui`、`ui-kit`）

---

## 5. 核心 vs 插件边界

**属于 core（不得插件化）：**

| 能力 | 理由 |
|---|---|
| 插件运行时本身 | 自举失败则全盘失效 |
| 启动执行链（JVM 参数、主类调用） | 启动器存在的意义 |
| Java 运行时检测与安装 | 启动前置条件；检测、下载、解压、版本与平台校验都在 core，**下载地址由 `JavaRuntimeProvider` 插件提供**，用户可在设置里选来源 |
| 版本清单解析 / 校验 / 缓存 | 启动前置条件，且不含任何具体来源 |
| 核心文件下载与校验 | 安全与完整性底线：文件规划、并发下载、SHA-1 校验、原子落盘都在 core；**下载地址来自插件** |
| 下载任务队列 | `DownloadsService`：串行、同版本去重、`StateFlow` 状态；任务属于应用作用域，切页不取消 |
| 实例与配置管理 | 所有插件的依赖基础 |
| 事件总线、服务注册 | 基础设施 |

**属于 plugins（必须插件化）：**

| 能力 | 示例插件 |
|---|---|
| 版本清单来源 + 资源对象地址 | `source-mojang`、`source-bmclapi`、`source-local`（**均不内置**，需另装） |
| 账户认证 | `auth-microsoft`、`auth-offline`、`auth-authlib` |
| 内容下载源 | `download-modrinth`、`download-curseforge`、`download-custom` |
| 游戏加载器适配 | `loader-forge`、`loader-fabric`、`loader-neoforge`、`loader-quilt` |
| UI 面板 | `ui-mod-browser`、`ui-skin-preview`、`ui-log-viewer` |
| 主题 | `ui-theme-dark`、`ui-theme-light` |
| 工具 | `tool-log-analyzer`、`tool-save-backup` |

**判断口诀**：去掉它，启动器还能不能“把游戏启动起来”？不能 → core；能 → plugin。

---

## 6. API 模块设计原则

`api` 只包含：

- 接口（`Plugin`、`PluginContext`、`EventBus`、`ServiceRegistry`、`LaunchService`、`DownloadSource`、`AuthProvider` 等）；UI 契约另见 `api-ui`（`UiPage`、`UiWidget`/`UiSlot`、`LauncherTheme`）
- 不可变数据类
- 密封类事件
- 异常类型
- **不含任何实现、不含平台特定代码、不含硬编码路径**

接口签名与示例代码见 `PLUGIN_DEV.md`。

### 6.1 API 兼容规则

- 新增方法必须提供默认实现
- 不删除、不改签名、不改语义
- 数据类新增字段给默认值
- API 变更需在 PR 描述中标注 `API-BREAK: none|minor|major`

---

## 7. 事件与服务注册

- **事件**：用于“通知已发生的事”
- **服务**：用于“注册可被消费的能力”
- 插件间通信**只能**走这两条路径，禁止直接 import 对方类
- 事件类型定义在 `api` 的 `events` 包下，使用 `data class` 或 `sealed class`

### 7.1 内置事件（示例）

| 事件 | 触发时机 |
|---|---|
| `GameLaunching` | 启动游戏前 |
| `GameExited` | 游戏进程退出后 |
| `InstanceCreated` | 新建实例后 |
| `InstanceDeleted` | 删除实例后 |
| `PluginEnabled` | 某插件启用后 |
| `PluginDisabled` | 某插件禁用后 |

### 7.2 内置服务类型（示例）

| 服务 | 作用 |
|---|---|
| `AuthProvider` | 账户认证方式（`priority` 选择，内置 `auth-offline`） |
| `DownloadSource` | 内容下载源 |
| `LoaderInstaller` | 游戏加载器安装 |
| `JavaRuntimeProvider` | Java 运行时下载来源（`priority` 选择，用户可在设置里指定） |
| `LocalePack` | 界面语言包（内置 `en` / `zh-CN`，插件补其它语言） |
| `UiContribution` | UI 面板/标签页（占位契约，P1 替换为 `UiPage`，见 `UI_DESIGN.md`） |

---

## 8. 插件运行时（core）

`core` 是唯一的“引擎”，职责严格受限：

1. **扫描与加载**：从 `<dataDir>/plugins/*.jar` 加载
2. **依赖排序**：按 `id` 做拓扑排序，检测循环与缺失
3. **生命周期**：`onLoad → onEnable → … → onDisable`
4. **上下文注入**：为每个插件构造 `PluginContext`
5. **事件总线实现**
6. **服务注册表实现**
7. **启动引擎**：组装 JVM 参数、调用游戏主类、文件与 Java 运行时管理

### 8.1 错误隔离

- `onLoad / onEnable / onDisable` 全部包在 try-catch 中
- 任何插件异常不得向上传播到 core 主流程
- 事件订阅回调异常同样隔离，记录后继续分发
- 缺失依赖：该插件标记为 `MISSING_DEP` 并跳过
- 循环依赖：全部标记为 `CYCLE` 并跳过
- API 级别不匹配：插件声明的 `apiLevel` 高于当前 `ApiLevel.CURRENT` 时标记 `INCOMPATIBLE_API` 并跳过，依赖它的插件按 `MISSING_DEP` 处理

---

## 9. 插件打包与加载策略

**统一策略：所有插件都从插件目录加载。**

- 内置插件在构建时由 `:app` 的 `prepareBuiltinPlugins` 任务收集到构建目录 `builtin-plugins/`，并生成 `index.txt`
- 该目录作为资源打进 `app` 的 classpath；启动时 core 的 `BuiltinPluginSync` 按 `index.txt` 把内置插件复制到 `<dataDir>/plugins/`
- 用户手动放入的插件与内置插件走**同一条** `URLClassLoader` 加载路径
- 插件 JAR 命名：`<pluginId>-<version>.jar`，如 `dev.mmcl.plugin.hello-0.0.1.jar`

这样只有一套加载逻辑，内置/外置无差别，便于测试与升级。

> **下载源例外**：`source-*` 类插件**不进入** `builtin-plugins/`，也不随应用分发。
> 应用启动时若没有任何 `VersionManifestSource`，游戏页显示"未安装版本源插件"，
> 启动器本身不发起任何与游戏文件相关的网络请求。

---

## 10. 依赖与生命周期

- 依赖通过 `id` 声明，core 负责拓扑排序
- 加载顺序：依赖先于被依赖者 `onLoad`，再按同序 `onEnable`
- 关闭顺序：逆序 `onDisable`
- 缺失依赖 / 循环依赖：禁用相关插件，UI 显示“插件不可用”提示，**不影响其他插件**
- 插件版本冲突：目前以 `id` 唯一，同一 `id` 多版本时保留最高版本并告警

---

## 11. UI 扩展

完整方案见 **`UI_DESIGN.md`**（布局蓝图、契约、主题机制、分期与安全阀）。要点：

- 模块分层：`api-ui`（纯契约）→ `ui-kit`（公共组件，规划中）→ UI 插件 / `app`
- 页面、部件、主题统一通过 `ServiceRegistry` 注册，`app` 负责渲染与兜底
- 默认页面与默认主题内置在 `app`，插件只做增量；`--safe-mode` 可跳过全部插件 UI
- 非 UI 插件绝不依赖 `api-ui` / `ui-kit`；`core` 永不依赖 Compose
- **当前状态**：`UiContribution` 只是占位契约，`app` 尚未渲染，P1 会替换为 `UiPage`（见 `TODO.md`）

---

## 12. 跨平台、macOS 27 与安全

### 12.1 跨平台

- 所有路径用 `Path` / `Path.of`，禁止字符串拼接分隔符
- 需要区分平台时统一走 `core` 的 `Platforms.currentOs()` / `Platforms.currentArch()`；插件只能读 `PluginContext.launcher.os/arch`，不得自己判断 `os.name`
- 可执行文件、Java 运行时按平台分目录：`<dataDir>/runtime/<os>-<arch>/`
- 不依赖 shell 命令；必要时通过 `ProcessBuilder` 并做平台分支

### 12.2 macOS 27 要点

- **架构**：只支持 `arm64`（Apple Silicon）；不构建、不测试 `x86_64` 产物，Intel Mac 上只告警提示不受支持
- **JDK**：使用 `arm64` 版 Temurin 27（toolchain + jpackage），确认 `java -version` 含 `aarch64`
- **数据目录**：macOS 为 `~/Library/Application Support/MMCL/`，与 Windows（`%APPDATA%\MMCL`）、Linux（`$XDG_DATA_HOME/mmcl`）统一由 `Platforms.dataDir()` 计算
- **窗口与菜单**：Compose Multiplatform 在 macOS 上有原生菜单栏差异，需在 `app` 中做平台分支
- **打包**：`app` 需产出 `.app`（可选 `.dmg`），并配置 `Info.plist`
- **签名与公证**：发布给外部用户的 macOS 构建必须做 Developer ID 签名 + notarization，否则 Gatekeeper 会拦截
- **沙盒**：暂不启用 App Sandbox（启动器需要写游戏目录），若上架 App Store 需重新评估

### 12.3 各平台数据目录参考

| 平台 | 默认数据目录 |
|---|---|
| macOS | `~/Library/Application Support/MMCL/` |
| Windows | `%APPDATA%\MMCL\` |
| Linux | `$XDG_DATA_HOME/mmcl/`（缺失时 `~/.local/share/mmcl/`） |

统一由 `core` 的 `Platforms.dataDir()` 计算，插件只通过 `PluginContext.dataDir` 获取自己的 `plugin-data/<pluginId>/`。

数据目录布局（实例共享文件、独立存档）：

```
<dataDir>/
├── libraries/ assets/ versions/   # 所有实例共享（下载一次；assets/objects 约 470MB）
├── instances.json                 # 实例清单（原子写）
├── instances/<id>/                # 实例 game_directory：saves / mods / config
├── logs/latest.log                # 最近一次游戏输出（每次启动覆盖）
├── plugins/ plugin-data/          # 插件
├── runtime/<os>-<arch>/           # 自带 Java 运行时
├── cache/                         # 版本清单缓存
└── settings.properties            # 键值设置
```

### 12.4 安全

- 插件运行在独立 `URLClassLoader`，与 core 类路径隔离
- 插件访问文件、网络**必须**经 `PluginContext` 提供的受控接口
- 下载的插件 JAR 与游戏模组必须做完整性校验（SHA-1/SHA-256）
- 不在日志中输出令牌、密码、账户敏感信息

---

## 13. 构建、运行、测试

```bash
# 构建全部
./gradlew build

# 运行启动器（macOS 本机）
./gradlew :app:run

# 仅构建某插件
./gradlew :plugins:auth-microsoft:jar

# 测试
./gradlew test
./gradlew :core:test --tests "*PluginLoaderTest*"

# macOS 打包
./gradlew :app:packageDmg          # → app/build/compose/binaries/main/dmg/MMCL-<bundleVersion>.dmg
./gradlew :app:createDistributable # → app/build/compose/binaries/main/app/MMCL.app（未签名，可直接跑）
```

macOS 打包要点：

- **用 JDK 27 的 jpackage 打包**：JDK 24~26 拒绝首段为 0 的版本（JDK-8380634，27 已修），
  所以 `0.0.1` 只有换 27 才能保持真实版本；构建会通过 Gradle toolchain 自动找 27，
  也可用 `-PpackageJdk=<home>` 指定。找不到 27 时才退回 `0.0.1 → 1.0.1` 映射（仅临时可用）
- `macOS { bundleID = "dev.mmcl.launcher"; dockName = "MMCL" }`
- **必须显式声明 jlink 模块**：`java.naming`（logback）、`java.net.http`（下载器）、`jdk.crypto.ec`（TLS）、
  `java.sql`、`jdk.unsupported`；否则打包后启动即 `NoClassDefFoundError`
- 打完后核对 `Info.plist` 的 `CFBundleShortVersionString` 与 DMG 文件名都是真实版本（`0.0.1`）；
  自带的 launcher 运行时来自打包 JDK（27），与应用字节码目标 17 无关
- 未签名产物换机器打开会被 Gatekeeper 拦：右键「打开」或在系统设置里放行；正式分发需要
  Developer ID 签名 + notarization，顺序是「改 plist → codesign → notarize → staple」

必须存在的测试：

- `core`：插件发现、依赖拓扑排序、循环依赖、缺失依赖、异常隔离
- 每个插件：至少一个针对核心逻辑的单元测试
- 在 macOS 27 上跑一遍完整的 `./gradlew build`，确认没有平台相关测试失败

---

## 14. 未来可扩展方向

- 插件沙箱（限制文件与网络访问范围）
- 插件签名与来源验证
- 插件市场 / 远程索引
- 插件热重载（开发模式）
- 插件崩溃自动隔离与上报
- 适配 macOS 27 的新特性（如原生 SwiftUI 桥接、菜单与快捷键自动化）