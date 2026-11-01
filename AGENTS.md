# AGENTS.md — AI 编码 Agent 规则

> 面向 AI 编码 Agent。动手前先读第 1、2、3、6、7、8 节。
> 架构细节见 `ARCHITECTURE.md`，插件开发见 `PLUGIN_DEV.md`。
> 三者冲突时，以 `ARCHITECTURE.md` 为准，并在同一次改动里直接修正文档（本仓库暂无远程 PR 流程，不需要“提 PR”）。

---

## 0. 项目信息

- 项目名：**MMCL**（插件化 Minecraft Java 启动器）
- 当前版本：**0.0.2**，唯一来源是 `gradle.properties` 的 `version`，代码里不得另写一份
- 包名 / groupId：**`dev.mmcl`**（插件 id 形如 `dev.mmcl.auth.microsoft`）
- 目标平台与支持范围：
  - **macOS：仅 Apple Silicon（arm64）**；Intel Mac 不专门适配，运行时只告警不拦
  - **Windows：默认目标 Windows 11**；Win10 及更旧系统"能跑就跑"，不专门兼容
  - Linux：主流发行版
- 游戏版本：只支持**最新正式版 + 同主版本系列**（例如最新是 `26.3` 时支持 `26.x`）；
  快照 / old_beta / old_alpha / 旧大版本不保证，清单里标注"不受支持"，不提供下载或启动
- mod 加载器插件（Fabric / Forge / NeoForge）只针对最新正式版所属系列维护
- 仓库形态：本地 git 仓库；**任何 git 操作都必须先经用户允许**，见第 7 节
- 发布形态：**应用不内置任何下载源/下载地址**。版本清单从哪拉、游戏文件从哪下，全部由用户安装的插件决定
  （`source-mojang` 等只以独立插件分发）。没有源插件时启动器只能启动本机已装好的实例
- 版本阶段约定：`0.x` 阶段允许 API 破坏性变更，但必须在提交说明里标注 `API-BREAK:`；`1.0.0` 起严格执行 6.1 节的兼容规则
- 版本规则：
  - **启动器**：`gradle.properties` 的 `version` 是唯一来源（当前 `0.0.2`）
  - **插件**：每个插件版本**独立，从 `0.0.1` 起**，不跟随启动器版本；插件 `build.gradle.kts` 的 `version` 与代码里 `Plugin.version` 必须一致，插件 JAR 名因此为 `<pluginId>-<插件版本>.jar`
  - **版本闸门**：能实际启动 Minecraft 之前固定 `0.0.1`（**已于 2026-10-31 达成**：真实启动 `26.3`），
    闸门已过，当前 `0.0.2`；后续升版（含 `0.1.0`）仍需用户批准
  - **升版需要请示**：即使过了闸门，启动器与插件的 `version` 变更前也要先问用户，同意后再改并同步 `CHANGELOG.md`
- 文档清单（都是**必须维护的活文档**，不是一次性产物）：
  - `AGENTS.md`：本文件，AI Agent 规则
  - `ARCHITECTURE.md`：架构与边界，冲突时以它为准
  - `UI_DESIGN.md`：界面外壳与主题化方案（含分期与安全阀）
  - `PLUGIN_DEV.md`：插件开发指南
  - `BUILD_WINDOWS.md`：Windows 上的编译、测试、运行与打包手册（含网络镜像、jpackage 版本限制等坑）
  - `TODO.md`：**唯一未完成清单**，分「进行中 / 待办 / 阻塞」；完成一项就**从 TODO 删除**并在 CHANGELOG 记一条，不留已完成的勾选
  - `CHANGELOG.md`：Keep a Changelog 格式，每次改动先写进 `[Unreleased]`，发版时归档

---

## 1. 速读

- 语言：Kotlin / JVM，**字节码目标 17**，编译用 **JDK 21 toolchain**（见 5.5）
- UI：Compose Multiplatform（仅 `app`、`api-ui`、`ui-kit` 可依赖；方案见 `UI_DESIGN.md`）
- 构建：Gradle Kotlin DSL，多模块，**只用仓库自带的 Gradle Wrapper**
- 插件发现：`java.util.ServiceLoader`
- 开发主机：**macOS 27（Apple Silicon）**，需同时产出 Windows / macOS / Linux 产物
- 铁律：
  - `api` 不含实现
  - `core` 不含具体功能
  - 插件之间不直接类引用
  - **任何 git 操作先问用户**
- 新增功能 → **默认先做插件**，除非它属于核心边界
- 改 `api` 前必须确认向后兼容（`0.x` 阶段允许破坏，但要标注）
- 改 `core` 后必须跑插件加载测试

---

## 2. 每次改动前的三问

1. **这个功能去掉后，启动器还能不能把（已经装好的）游戏启动起来？**
   - 不能 → 属于 `core`（插件运行时、启动执行链、文件校验、实例管理）
   - 能 → 做成插件（认证、内容源、加载器安装、UI、主题）
   - 例外：**获取游戏文件的一切来源都是插件**，应用内不内置，哪怕"官方源"也一样
2. **它需要扩展点还是只需要事件？**
   - 要让别人调用/替换 → 服务注册
   - 只是通知发生了某事 → 事件总线
3. **它是否会让 `api` 失去向后兼容？**
   - 会 → 停止，改用默认实现或新接口（若当前仍是 `0.0.x`，确认后标注 `API-BREAK: minor`）

---

## 3. 禁止事项（硬性）

- ❌ `core` 依赖任何具体插件
- ❌ `api` 依赖任何实现模块
- ❌ 插件之间直接 `import` 对方类
- ❌ 在 `core` / `app` 里写死任何下载源地址（官方、镜像、CDN 都不行），或把源插件打包进应用
- ❌ 硬编码 `/Users/xxx`、`C:\`、`/home/` 等路径
- ❌ 在插件中使用 `GlobalScope`
- ❌ 在 `onLoad` / `onEnable` 中执行长时间阻塞操作
- ❌ 吞掉异常不记录日志
- ❌ 让插件异常导致 core 主流程崩溃
- ❌ `core` / `api` 依赖 Compose（UI 只属于 `app`、`api-ui`、`ui-kit` 与 UI 插件）
- ❌ 非 UI 插件依赖 `api-ui` 或 `ui-kit`
- ❌ 插件把自己的 UI 或主题实现塞进 `core`
- ❌ 改 `api` 时删除或修改已有方法签名（`0.x` 例外，但必须标注 `API-BREAK`）
- ❌ 在源码中写死 `arm64` 或 `x86_64` 的架构判断（用 `OperatingSystem.current()` 封装）
- ❌ **未经用户明确许可执行任何 git 操作**（`init`/`add`/`commit`/`push`/`tag`/`branch`/`reset`/`clean`/`stash`/`checkout`/`rebase` 等，见第 7 节）
- ❌ 使用系统 `gradle` 或擅自升级 wrapper 版本
- ❌ 提交构建产物（`build/`、`.gradle/`）与本地路径、密钥、令牌
- ❌ 为旧平台（Intel Mac / Windows 10）或旧大版本专门加兼容分支、测试矩阵（除非用户明确要求）

---

## 4. 代码规范

- Kotlin 官方风格；`ktlint` / `detekt` 配置就位后必须通过
- 公共 API 必须有 KDoc
- 插件 `id`：小写反向域名，段间用 `.`，如 `dev.mmcl.auth.microsoft`
- 版本：语义化 `MAJOR.MINOR.PATCH`；启动器版本只在 `gradle.properties` 维护，插件版本在各自模块维护（均从 `0.0.1` 起）
- 路径一律用 `java.nio.file.Path`，禁止字符串拼接分隔符
- 协程：插件对外 `suspend`，core 提供作用域
- 日志：使用 `PluginContext.logger`，禁止 `println`
- 资源：`use {}` 或 `Closeable`
- 插件 `onDisable` 必须幂等
- macOS 开发时只需考虑 Apple Silicon（`arm64`）；不构建、不测试 `x86_64` 产物（支持范围见第 0 节）

---

## 5. 常见任务手册

### 5.1 新增插件

1. `plugins/<name>/` 新建 Gradle 子项目，在 `settings.gradle.kts` 注册
2. `build.gradle.kts` 依赖 `:api`；UI 插件改依赖 `:ui-kit`（传递 `api-ui`），并启用 compose 编译器插件（见 `UI_DESIGN.md`）
3. 实现 `Plugin` 接口及所需能力接口（如 `AuthProvider`）
4. 在 `src/main/resources/META-INF/services/dev.mmcl.api.plugin.Plugin` 写入实现类全名
5. 在 `onLoad` 中通过 `context.services.register(...)` 注册能力
6. 如为内置插件，由 `:app` 的 `syncBuiltinPlugins` 任务复制 JAR 到 `builtin-plugins/`
7. 补测试，`./gradlew :plugins:<name>:test` 先绿

### 5.2 扩展 API

1. 在 `api` 新增接口/方法，**必须提供默认实现**
2. 在 `core` 中实现或转发
3. 更新 KDoc
4. 在提交说明标注 `API-BREAK: none|minor|major`
5. 补测试

### 5.3 修改 core

1. 先跑 `./gradlew :core:test`
2. 确保插件隔离逻辑不退化
3. 变更后跑全量 `./gradlew build`
4. 在 macOS 上额外跑 `./gradlew :app:run`，验证 Compose 界面在 Apple Silicon 下的表现

### 5.4 新增事件

1. 在 `api` 的 `events` 包下定义 `data class` / `sealed class`
2. `core` 在对应时机 `events.publish(...)`
3. 更新 `ARCHITECTURE.md` 的事件列表

### 5.5 本机开发环境（已就绪，勿重复安装）

1. 操作系统：macOS 27.x，Apple Silicon（`arm64`）
2. JDK：**Temurin 21（arm64）** 作为 Gradle toolchain；另有 Zulu 25（启动 Gradle）与
   **Temurin 27（只用于 jpackage 打包，见第 9 节）**
3. 校验：`/usr/libexec/java_home -V` 应列出 8 / 21 / 25 / 27；`./gradlew -version` 应显示 wrapper 版本与 `Launcher JVM`
4. Gradle：**只用 `./gradlew`**（版本以 `gradle/wrapper/gradle-wrapper.properties` 为准），不要 `brew install gradle`
5. 首次构建会联网下载依赖；网络受限时用 `./gradlew build --offline`（依赖须已在 `~/.gradle/caches`）
6. 三平台验证：`./gradlew build`（字节码跨平台一致）+ `./gradlew :app:run`（本机冒烟），打包见 `ARCHITECTURE.md` §13

---

## 6. 完成定义（DoD）

一次改动视为完成，需满足：

- [ ] 新功能以插件形式实现（或明确说明为何属于 core）
- [ ] 不破坏 `api` 兼容性，或已标注 `API-BREAK`
- [ ] `./gradlew build` 通过
- [ ] `./gradlew test` 通过，核心加载逻辑测试覆盖
- [ ] 新增公共 API 有 KDoc
- [ ] 无第 3 节反模式
- [ ] 跨平台路径处理已审查（含 macOS 的 `~/Library/Application Support`）
- [ ] 插件异常隔离已确认（手动或测试验证）
- [ ] 在 macOS 27 本机运行 `./gradlew :app:run` 无异常
- [ ] 版本号已按第 4 节规则处理（改动即说明是否升版）
- [ ] `CHANGELOG.md` 的 `[Unreleased]` 已记录本次改动（用户可见的行为变化必须写）
- [ ] `TODO.md` 已完成项已删除、新任务已登记（未完成的只在 TODO，已完成的只在 CHANGELOG）
- [ ] 文档（本文件 / `ARCHITECTURE.md` / `PLUGIN_DEV.md`）已同步

### 6.1 API 兼容规则

- 新增方法必须提供默认实现
- 不删除、不改签名、不改语义（`0.x` 阶段例外，须标注 `API-BREAK`）
- 数据类新增字段必须给默认值
- `api` 的公开类型只增不改；失效接口用 `@Deprecated` 标记并保留至少一个 MINOR 周期

---

## 7. Git 规则（强制）

- **任何 git 操作执行前，必须先获得用户明确许可**，包括但不限于
  `init`、`add`、`commit`、`push`、`pull`、`fetch`、`tag`、`branch`、`switch`、`merge`、`rebase`、
  `reset`、`revert`、`stash`、`clean`、`checkout`、`worktree`、`submodule`、修改 remote、修改 git config
- 许可**一次有效**：上一次同意过 `commit`，不代表下一次可以自动提交
- 只读操作（`status`、`diff`、`log`、`show`、`blame`）不算写操作，可以直接执行
- 破坏性操作（`push --force`、`reset --hard`、`clean -fd`、`branch -D`、`tag -d`、`filter-branch`）
  **必须单独申请**，并先说明会丢什么
- 提交前向用户展示 `git status --short` 与 `git diff --stat` 摘要，等确认
- 一个提交只做一件事；标题用祈使句、不超过 50 字符；正文写清动机与影响（含 `API-BREAK` 标注）
- 不自动 `amend` / `rebase` / `squash` 用户已有的提交
- 不修改全局 git 配置（`~/.gitconfig`），不动用户的 `user.name` / `user.email`
- `.gitignore` 先于第一次 `git add` 就位
- **版本号变更同样需要许可**：启动器（`gradle.properties`）与插件模块的 `version` 都要先问

---

## 8. 提交前自检清单

- 依赖方向是否仍然自上而下？
- 是否新增了插件之间的直接引用？
- 是否把具体功能写进了 `core`？
- 是否硬编码了平台相关路径（尤其是 `/Users/` 与 `C:\`）？
- 是否在 `onLoad` 中做了阻塞操作？
- 是否遗漏了 `META-INF/services` 声明？
- 是否更新了对应文档？
- 是否符合支持范围（macOS 仅 arm64；只支持最新正式版系列）？
- 是否已就本次 git 操作征求用户同意？

---

## 9. 已知坑（都真踩过）

- **Compose 访问器已弃用**：`compose.runtime` / `compose.foundation` / `compose.material3` 在 1.12.2 会报 deprecated，
  但仍可用；`material3` 在 Maven Central 没有 1.12.2 稳定制品，别手写坐标，走插件访问器
- **图标集冻结**：`org.jetbrains.compose.material:material-icons-core` 停在 1.7.3，
  `Icons.Filled.Folder/Download` 等不在 core 集合里；加图标先在 `UiIcon` 枚举里定义，再确认 core 有对应图
- **插件禁止打包 Compose**：`:app:verifyPluginJars` 会扫 `androidx/compose/**` 并让构建失败；
  插件用 `compileOnly(project(":ui-kit"))`
- **内置插件必须用 Sync**：`prepareBuiltinPlugins` 若用 `Copy`，旧版本 JAR 会残留在 `builtin-plugins/`，
  出现同一插件多版本同时打包
- **测试缓存会跳过截图生成**：`clean build` 后 `app/build/screenshots/` 可能是空的（test 结果走了 build cache），
  用 `./gradlew :app:test --tests "*AppShellRenderTest*" --rerun-tasks` 重新出图
- **锁屏时无法实拍**：macOS 锁屏时 `screencapture` 只能拿到登录界面；用 `ImageComposeScene` 的无头渲染出图，
  或等解锁后再截
- **Gradle 要写 `~/.gradle`**：沙箱里构建需要放开到工作区外；离线时用 `--offline`（依赖须已在缓存里）
- **插件版本双写**：`build.gradle.kts` 的 `version` 与 `Plugin.version` 必须一致，否则 JAR 名和界面显示会对不上
- **`:app:run` 是长驻进程**：窗口关闭前任务不结束，本机验证时别用阻塞写法等它返回
- **jpackage 24~26 拒绝首段为 0 的版本**（JDK-8380634，**JDK 27 已修**）：macOS 上直接打 `0.0.1`
  会报 “app-version 中的第一个数字不能为零”。打包配置会用 Gradle toolchain 找 JDK 27
  （也可 `-PpackageJdk=<jdk27 home>` 指定）；找不到才退回 `1.x` 映射。
  打完后用 PlistBuddy 确认 `CFBundleShortVersionString` 是真实版本，别让 1.x 漏到用户手里
- **jpackage 的 CFBundleVersion 与 ShortVersion 相同**：想要独立单调 build 号（自动更新需要）
  只能打包后改 plist 再签名，顺序是「改 plist → codesign → notarize」
- **26.x 的游戏控制台是 log4j2 XML**：一个事件占 4~6 行，别直接展示；用 `GameLogFormatter`
  转成 `[时间] [线程/级别] [类] 消息`。`logs/latest.log` 保留原始 XML，便于上报排查
- **示例插件不要注册真实能力**：能力按 `priority` 选择，`hello` 曾注册 `AuthProvider` 抢在真实认证插件前面；
  示例只订阅事件、不打日志以外的副作用
- **下载任务状态不能放 Compose 页面里**：`remember` + 页面协程会在切页时取消下载；用 core 的
  `DownloadsService`（应用级作用域 + `StateFlow`），页面只订阅
- **运行时来源回退要覆盖安装阶段**：只在 `resolve` 返回 null 时换源不够——来源给错平台/损坏包时
  是解压或平台校验阶段才失败；用 `JavaRuntimeSources.installFirst`，别在页面里手写“解析 + 安装”
- **重试要排除确定性失败**：SHA-1 不符、404/403 重试只会浪费时间；`FileDownloader` 只对
  网络错误 / 429 / 5xx 重试，测试里的 500-then-200 场景覆盖了这条
- **运行时解压必须防穿越、处理符号链接**：tar.gz 用 commons-compress，条目路径要
  `normalize()` 后校验仍在目标目录内；macOS JDK 压缩包含符号链接，创建失败只告警不中断
- **游戏崩溃先看崩溃报告**：`<instance>/crash-reports/crash-*.txt`；常见根因是 `assets/objects` 没下全
  （`NoSuchFileException …/assets/objects/xx/<hash>`，`CubeMapTexture`），不是启动参数问题
- **jlink 会裁掉运行时要用的模块**：打包后必须显式 `modules("java.naming", "java.sql", "java.net.http",
  "jdk.crypto.ec", "jdk.unsupported")`，否则 logback（javax.naming）或 HttpClient 会在启动时报
  `NoClassDefFoundError` —— 这个坑只在打包产物里出现，`:app:run` 不会暴露
