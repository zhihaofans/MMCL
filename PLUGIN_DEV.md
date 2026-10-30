# PLUGIN_DEV.md — 插件开发指南

> 面向插件开发者。架构背景见 `ARCHITECTURE.md`，AI Agent 规则见 `AGENTS.md`。
> 本机开发环境为 **macOS 27（Apple Silicon）**，示例命令默认在 macOS 下执行。

---

## 1. 插件是什么

插件是一个独立 JAR，实现 `dev.mmcl.api.plugin.Plugin` 接口，通过 `ServiceLoader` 被 core 发现并加载。

- 插件不依赖 `core`，只依赖 `api`（UI 插件改依赖 `ui-kit`，它会传递 `api-ui`）
- 插件之间不直接引用，通过事件总线或服务注册通信
- 插件版本：**独立于启动器，从 `0.0.1` 起**；`build.gradle.kts` 的 `version` 与 `Plugin.version` 必须一致
- 插件 JAR 命名：`<pluginId>-<version>.jar`（用插件版本，不是启动器版本）

---

## 2. 最小插件示例

### 2.1 Gradle 配置

```kotlin
// plugins/hello/build.gradle.kts
plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(project(":api"))
    implementation("org.slf4j:slf4j-api:2.0.13")
}

kotlin { jvmToolchain(21) }
```

### 2.2 插件实现

```kotlin
package dev.mmcl.plugin.hello

import dev.mmcl.api.plugin.Plugin
import dev.mmcl.api.plugin.PluginContext

class HelloPlugin : Plugin {
    override val id = "dev.mmcl.plugin.hello"
    override val version = "1.0.0"

    private lateinit var ctx: PluginContext

    override fun onLoad(context: PluginContext) {
        ctx = context
        ctx.logger.info("Hello plugin loaded")
    }

    override fun onEnable() {
        ctx.logger.info("Hello plugin enabled on ${System.getProperty("os.name")}")
    }

    override fun onDisable() {
        ctx.logger.info("Hello plugin disabled")
    }
}
```

### 2.3 ServiceLoader 声明

文件路径：

```
plugins/hello/src/main/resources/META-INF/services/dev.mmcl.api.plugin.Plugin
```

文件内容（一行一个实现类全名）：

```
dev.mmcl.plugin.hello.HelloPlugin
```

---

## 3. 插件接口

```kotlin
package dev.mmcl.api.plugin

interface Plugin {
    val id: String                    // 反向域名，如 "dev.mmcl.auth.microsoft"
    val version: String               // 语义化版本
    val dependencies: List<String> get() = emptyList()
    val optionalDependencies: List<String> get() = emptyList()

    fun onLoad(context: PluginContext) {}
    fun onEnable() {}
    fun onDisable() {}
}
```

**生命周期约定：**

- `onLoad`：注入 `PluginContext`，注册服务，**不要做阻塞操作**
- `onEnable`：依赖已全部启用，可以开始工作
- `onDisable`：释放资源、取消订阅

---

## 4. 插件上下文

```kotlin
package dev.mmcl.api.plugin

interface PluginContext {
    val pluginId: String
    val dataDir: java.nio.file.Path      // 该插件专属数据目录
    val logger: org.slf4j.Logger
    val events: EventBus
    val services: ServiceRegistry
    val launcher: LauncherInfo           // 只读的启动器信息
}
```

- `dataDir` 由 core 创建：`<dataDir>/plugin-data/<pluginId>/`
- 禁止在 `dataDir` 之外写文件
- 在 macOS 27 下，实际路径类似
  `~/Library/Application Support/<AppName>/plugin-data/<pluginId>/`

---

## 5. 事件总线

```kotlin
interface EventBus {
    fun <T : Any> publish(event: T)
    fun <T : Any> subscribe(
        type: kotlin.reflect.KClass<T>,
        handler: (T) -> Unit
    ): Subscription
    fun interface Subscription { fun cancel() }
}
```

使用示例：

```kotlin
override fun onEnable() {
    ctx.events.subscribe(GameExited::class) { event ->
        ctx.logger.info("game exited: ${event.exitCode}")
    }
}

override fun onDisable() {
    // 若未手动 cancel，core 会在插件禁用时统一取消
}
```

**约定：**

- 事件处理器中不得抛异常，或自行 try-catch
- 长任务放到协程中，事件回调尽快返回

---

## 6. 服务注册

```kotlin
interface ServiceRegistry {
    fun <T : Any> register(type: kotlin.reflect.KClass<T>, instance: T)
    fun <T : Any> get(type: kotlin.reflect.KClass<T>): T?
    fun <T : Any> getAll(type: kotlin.reflect.KClass<T>): List<T>
}
```

注册能力：

```kotlin
override fun onLoad(context: PluginContext) {
    context.services.register(AuthProvider::class, MicrosoftAuthProvider())
}
```

消费能力：

```kotlin
val sources = ctx.services.getAll(DownloadSource::class)
sources.forEach { it.search("sodium") }
```

---

## 7. 能力扩展点

包名：`dev.mmcl.api.capability`。插件**注册**能力（`services.register`），其它插件**消费**能力（`services.getAll`）。

| 接口 | 作用 |
|---|---|
| `AuthProvider` | 账户认证方式 |
| `DownloadSource` | 内容下载源 |
| `LoaderInstaller` | 游戏加载器安装 |
| `UiContribution` | UI 面板/标签页（占位契约；P1 起为 `UiPage`，见 `UI_DESIGN.md`） |
| `VersionManifestSource` | 版本清单来源（官方 / 镜像 / 本地），**应用内不内置任何实现** |

接口定义示例：

```kotlin
interface AuthProvider {
    val id: String
    suspend fun login(): Account
}

interface DownloadSource {
    val id: String
    suspend fun search(query: String): List<ProjectHit>
}

interface LoaderInstaller {
    val loaderId: String
    suspend fun install(version: String, dir: java.nio.file.Path)
}
```

---

## 7.1 版本源插件（应用不内置下载源）

启动器**不含任何下载地址**：没有装 `VersionManifestSource` 插件时，游戏页只提示"未安装版本源插件"。
官方源 `source-mojang` 也是独立插件，不随应用打包。

```kotlin
class MojangVersionSource : VersionManifestSource {
    override val id = "mojang"
    override val displayName = "Mojang 官方"
    override val priority = 100            // 越小越先尝试

    // 只返回 JSON 原文：解析、校验、缓存、失败回退全部由 core 负责
    override suspend fun fetchManifestJson(): String = httpGet(DEFAULT_URL)
}

class MojangSourcePlugin : Plugin {
    override val id = "dev.mmcl.source.mojang.plugin"
    override val version = "0.0.1"
    override fun onLoad(context: PluginContext) {
        context.services.register(VersionManifestSource::class, MojangVersionSource())
    }
}
```

- 可以装多个源，core 按 `priority` 升序逐个尝试，全部失败才回退本地缓存
- 设置项 `version.source` 可指定优先使用哪个源
- 插件返回脏 JSON 只会让该源被跳过，不会写坏缓存
- 插件本身用 `compileOnly(project(":api"))`，HTTP 客户端自己选（示例用 JDK `HttpClient`）

## 8. UI 插件

> **状态**：契约已定义、渲染未实现。完整方案（布局、`UiPage`、主题、安全阀）见 **`UI_DESIGN.md`**。
> P1 会把下面的 `UiContribution` 替换成 `UiPage`，在那之前 UI 插件加了也不会显示。

**构建前提**：UI 插件必须启用 compose 编译器插件，并且**不要把 Compose 打进 JAR**（运行期由启动器提供）：

```kotlin
// plugins/my-ui/build.gradle.kts
plugins {
    kotlin("jvm")
    kotlin("plugin.compose")        // @Composable 必需
    id("org.jetbrains.compose")
}

dependencies {
    compileOnly(project(":ui-kit")) // 会传递 api-ui
    compileOnly(libs.slf4j.api)
}
```

当前占位契约（`api-ui`，P1 将被 `UiPage` 取代）：

```kotlin
interface UiContribution {
    val id: String
    val title: String
    val order: Int get() = 100
    @Composable fun Content()
}
```

目标形态（细节见 `UI_DESIGN.md` §4）：

```kotlin
class LogPage : UiPage {
    override val id = "dev.mmcl.ui.logviewer"
    override val title = "日志"
    override val icon = UiIcon.INFO
    override val group = UiGroup.TOOLS
    override val order = 120

    @Composable
    override fun Content(navigator: AppNavigator) {
        PageScaffold(title = "日志") { /* Compose UI */ }
    }
}

class LogViewerPlugin : Plugin {
    override val id = "dev.mmcl.ui.logviewer.plugin"
    override val version = "0.1.0"

    override fun onLoad(context: PluginContext) {
        context.services.register(UiPage::class, LogPage())
    }
}
```

**注意：**

- 非 UI 插件不要依赖 `api-ui` / `ui-kit`
- UI 插件不得在 `Content()` 中做阻塞操作，也不要从后台线程直接改 Compose 状态（走 `StateFlow`）
- 主题插件是另一类扩展点：注册 `LauncherTheme`，只能换配色/排版，**改不动布局骨架**
- 不要自己开窗口或改窗口装饰，外层窗口与平台差异由 `app` 统一处理

---

## 9. 依赖声明

```kotlin
class MyPlugin : Plugin {
    override val id = "dev.mmcl.my"
    override val version = "0.0.1"   // 插件自己的版本，从 0.0.1 起，与启动器无关
    override val dependencies = listOf("dev.mmcl.auth.microsoft")
    override val optionalDependencies = listOf("dev.mmcl.download.modrinth")
}
```

- 依赖通过 `id` 匹配，与版本号无关
- 缺失硬依赖 → 插件标记 `MISSING_DEP` 并跳过
- 循环依赖 → 相关插件全部标记 `CYCLE` 并跳过
- 可选依赖缺失不影响加载

---

## 10. 打包与安装

### 10.1 打包

在 macOS 27 下：

```bash
./gradlew :plugins:hello:jar
```

产物输出到 `plugins/hello/build/libs/dev.mmcl.plugin.hello-0.0.1.jar`
（文件名由 `tasks.jar { archiveBaseName.set(...) }` 固定为 `<pluginId>-<version>.jar`）。

### 10.2 内置插件

内置插件不进 Git 的 `src/main/resources`，而是在 `app` 构建期由 `prepareBuiltinPlugins` 任务收集：

1. `app/build.gradle.kts` 用自定义 configuration `builtinPlugins` 依赖插件模块；
2. 任务把插件 JAR 复制到构建目录的 `builtin-plugins/`，并生成 `index.txt`（一行一个 JAR 名）；
3. `processResources` 把这个目录打进 `app` 的 classpath 资源；
4. 运行期 `BuiltinPluginSync` 读取 `builtin-plugins/index.txt`，把 JAR 同步到
   `~/Library/Application Support/MMCL/plugins/`（Windows / Linux 见 `ARCHITECTURE.md` §12.3）。

新增内置插件只需要在 `settings.gradle.kts` 注册模块，并在 `app/build.gradle.kts` 里加一行
`builtinPlugins(project(":plugins:<name>"))`。

### 10.3 手动安装（macOS 27）

```bash
cp dev.mmcl.plugin.hello-0.0.1.jar \
  "$HOME/Library/Application Support/MMCL/plugins/"
```

重启启动器即可。

---

## 11. 插件数据目录

```
<dataDir>/
├── plugins/                     # 插件 JAR
└── plugin-data/
    └── <pluginId>/              # 每个插件专属
        ├── config.json
        └── cache/
```

在 macOS 27 下展开为：

```
~/Library/Application Support/<AppName>/
├── plugins/
└── plugin-data/
    └── <pluginId>/
```

- 只能读写自己的 `plugin-data/<pluginId>/`
- 禁止访问其他插件的数据目录

---

## 12. 错误处理与日志

- 使用 `ctx.logger`，禁止 `println`
- 不得吞掉异常
- 不得在事件回调中抛出未捕获异常
- `onDisable` 必须幂等

```kotlin
override fun onEnable() {
    try {
        // work
    } catch (e: Exception) {
        ctx.logger.error("failed to enable", e)
    }
}
```

---

## 13. 测试

每个插件至少包含一个针对核心逻辑的单元测试：

```kotlin
class HelloPluginTest {
    @Test
    fun `publishes hello event on enable`() {
        // 使用 fake PluginContext 验证行为
    }
}
```

建议：

- 把业务逻辑抽到不依赖 `PluginContext` 的类中，便于测试
- 使用 `kotlin.test` + JUnit 5
- 不要依赖真实网络或真实文件系统
- 在 macOS 27 本机 `./gradlew :plugins:<name>:test` 必须先绿

---

## 14. 常见问题

**Q：插件能依赖其他第三方库吗？**
能，但请使用 `implementation`，并在打包时确保不与其他插件冲突（必要时用 `shadowJar` 重定位）。

**Q：插件能访问 core 内部类吗？**
不能。插件只能通过 `api` 暴露的接口访问。

**Q：插件能修改启动器 UI 布局吗？**
现在还不能 —— 渲染尚未实现（见 `UI_DESIGN.md`）。目标形态：用 `UiPage` 加整页、用 `UiWidget` 往固定区域塞部件、用 `LauncherTheme` 换主题；**布局骨架（导航栏/顶栏/状态栏）不允许插件改**。

**Q：插件能调用游戏加载器安装器吗？**
通过 `ServiceRegistry` 获取 `LoaderInstaller` 能力，不要自己实现安装逻辑。

**Q：插件崩溃会影响其他插件吗？**
不会。core 对每个插件的生命周期和事件回调做了异常隔离。

**Q：插件怎么区分 macOS / Windows / Linux？**
读 `ctx.launcher.os` / `ctx.launcher.arch`（core 探测后注入，枚举 `OperatingSystem` / `Architecture`）。
不要在插件里自己写 `os.name` 字符串判断，也不要写死 `arm64` / `x86_64`。

---

## 15. 插件检查清单

- [ ] 只依赖 `api`（UI 插件用 `ui-kit`，且不要把 Compose 打进 JAR）
- [ ] 已声明 `META-INF/services/dev.mmcl.api.plugin.Plugin`
- [ ] 插件 `id` 为反向域名
- [ ] 版本为语义化版本，且**从 `0.0.1` 起**（插件独立版本，与启动器解耦）
- [ ] `build.gradle.kts` 的 `version` 与 `Plugin.version` 一致
- [ ] 不在 `onLoad` / `onEnable` 中阻塞
- [ ] 使用 `ctx.logger`，不 `println`
- [ ] 异常已隔离
- [ ] 路径使用 `Path`
- [ ] `onDisable` 幂等
- [ ] 有单元测试，且在 macOS 27 本机跑绿