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

注册能力（`Account` 自带启动凭据：`uuid`、`accessToken`、`userType`）：

```kotlin
override fun onLoad(context: PluginContext) {
    context.services.register(AuthProvider::class, MicrosoftAuthProvider())
}
```

> `plugins/hello` 是**开发示例**，不随应用内置；本地调试可手动装它的 JAR（见 §10）。
>
> 示例插件（`hello`）**不要注册真实能力**：能力会参与选择，示例插件抢到后会让真实插件失效。

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
| `AuthProvider` | 账户认证方式（`displayName` / `priority` / `isSignedIn` / `createSession`；内置 `auth-offline` 可删除或禁用）。`isSignedIn` 决定启动器会不会选你：装了插件但没登录时返回 `false`，别把离线账户挤掉 |
| `DownloadSource` | 内容下载源 |
| `LoaderInstaller` | 游戏加载器安装 |
| `UiPage` | 完整页面（导航栏追加一项） |
| `UiWidget` | 插槽小部件（顶栏 / 状态栏 / 首页卡片），见下 |
| `VersionManifestSource` | 版本清单来源（官方 / 镜像 / 本地），**应用内不内置任何实现** |
| `JavaRuntimeProvider` | Java 运行时下载来源（`runtime-adoptium` 示例，单独分发） |

接口定义示例：

```kotlin
interface AuthProvider {
    val id: String
    val displayName: String get() = id
    val priority: Int get() = 100        // 越小越优先；内置离线账户为 1000
    suspend fun login(): Account
    suspend fun createSession(username: String): Account = login()
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
    override val apiLevel = ApiLevel.CURRENT   // 按哪一级 API 编译，见 §10.4
    override fun onLoad(context: PluginContext) {
        context.services.register(VersionManifestSource::class, MojangVersionSource())
    }
}
```

- 源插件还可以提供资源对象地址：`override fun assetObjectUrl(hash: String): String?`，
  返回 `null` 表示不支持（游戏会因缺贴图崩溃）；官方源返回 `https://resources.download.minecraft.net/<前两位>/<hash>`
- 镜像类来源还可以改写普通文件地址：`override fun resolveUrl(original: String): String?`，
  core 会把它应用到版本详情、client jar、资源索引、库文件与日志配置；返回 `null` 表示按原地址下载。
  `source-bmclapi` 就是按主机前缀把 `piston-meta` / `piston-data` / `libraries.minecraft.net` 等映射到镜像
- 镜像在界面上必须标明出处：`override val attribution: String?`，设置页下载源与下载页都会显示它
  （BMCLAPI 使用条款要求）
- Java 运行时来源示例（`plugins/runtime-adoptium`，不内置）：

```kotlin
interface JavaRuntimeProvider {
    val id: String
    val displayName: String
    val priority: Int get() = 100     // 用户没显式选时按这个排序，越小越优先
    suspend fun resolve(majorVersion: Int, os: OperatingSystem, arch: Architecture): RuntimeDownload?
}
```

- 用户可以在「设置 → Java 运行时来源」里指定用哪个来源，选中值存进设置 `java.runtime.provider`
  （`auto` 表示按优先级自动选）；插件被卸载后这条设置自动失效，不会留下悬空选中
- `resolve` 收到的是**宿主**的 `os` / `arch`，插件只负责把它们映射成自己的地址参数；
  core 的安装器还会校验压缩包内 `release` 的 `OS_NAME` / `OS_ARCH`，给错平台的包会被拒绝并报明确原因
- 现成实现（都**不内置**）：
  - `plugins/runtime-adoptium`：Adoptium 官方接口，只提供 Temurin
  - `plugins/runtime-foojay`：foojay Disco API，一次提供 Zulu / Liberica / Microsoft / Corretto /
    Dragonwell / Oracle OpenJDK
  - `plugins/runtime-mirror`：国内镜像——清华 TUNA / 中科大 USTC 的 Temurin、华为云的 Oracle OpenJDK。
    镜像站没有"最新版"接口，插件读目录列表按版本号挑最高的一个
- 装了多个来源时，core 按顺序逐个调 `resolve`，用**第一个给出下载信息**的：
  某个来源不支持该平台或抛异常就换下一个，镜像站缺包不会让用户卡在"该来源不支持"

- 跟随启动器语言：
  - 非 UI 插件用 `PluginContext.locale`（`auto` 已解析成 `en` / `zh-CN` 等，切语言后立刻读到新值）
  - UI 插件在 `@Composable` 里读 `dev.mmcl.api.ui.LocalLauncherLocale`，切语言会触发重组；
    页面标题可以覆盖 `UiPage.LocalizedTitle()` 返回当前语言的标题（缺省回退 `title`）
  - 内置示例 `plugins/ui-about`（关于页 / 首页卡片 / 状态栏标记）就是按这个模式本地化的
- 插件元信息：`title`（展示名，缺省用 `id`）、`author`、`icon`（`PluginIcon` 枚举，由 `app` 映射成内置矢量图，
  插件不能自带图片）；`Plugin` 接口里都有默认实现，旧插件不受影响
- 展示名不要写死：`title`、`AuthProvider.displayName`、`LauncherTheme.displayName` 这类会进界面的字符串，
  用 `get() = if (ctx.locale.startsWith("zh")) "中文" else "English"` 现取（`ctx.locale` 切换语言后立刻是新值）；
  写成 `val displayName = "中文"` 在英文界面下就是一条漏网的中文
- 插件设置页：注册 `SettingsSection`，`app` 在设置页统一渲染，插件不需要依赖 Compose：

```kotlin
class MySettings : SettingsSection {
    override val id = "dev.mmcl.example"
    override fun title(locale: String) = if (locale.startsWith("zh")) "示例插件" else "Example plugin"
    override fun items(locale: String) = listOf(
        SettingItem.Text(key = "example.name", title = "名字", defaultValue = "Player"),
        SettingItem.Toggle(key = "example.auto", title = "自动更新"),
        SettingItem.Choice(key = "example.mode", title = "模式", options = listOf(ChoiceOption("a", "A"))),
    )
}

context.services.register(SettingsSection::class, MySettings())
```

  - 值统一存在 `SettingsService` 里；`title(locale)` / `items(locale)` 会拿到当前界面语言
  - 内置示例：`plugins/auth-offline` 的「玩家名」（`player.name`）

- 语言包（`LocalePack`，不内置）：应用自带 `en` / `zh-CN`，插件可以补其它语言：

```kotlin
class JapanesePack : LocalePack {
    override val locale = "ja-JP"
    override val displayName = "日本語"
    override fun strings(): Map<String, String> = mapOf("settings.title" to "設定")
}

class JapanesePlugin : Plugin {
    override val id = "dev.mmcl.locale.ja"
    override val version = "0.0.1"
    override fun onLoad(context: PluginContext) {
        context.services.register(LocalePack::class, JapanesePack())
    }
}
```

- key 用层级命名（`page.settings.title`、`settings.section.appearance`），缺的 key 回退到内置英文；
- 现成示例：`plugins/locale-zh-tw`（繁體中文，**不内置**），覆盖当前全部 key
  插件语言包只写自己翻了的 key，不必抄全表
- 插件自己的页面文案自己负责：读设置 `ui.locale`（`auto` 表示跟随系统）再决定显示什么

- UI 插件还可以注册 `UiWidget` 往固定插槽加内容（`UiSlot.TOP_BAR` / `STATUS_BAR` / `HOME`）：

```kotlin
class StatusWidget : UiWidget {
    override val id = "dev.mmcl.example.status"
    override val slot = UiSlot.STATUS_BAR
    override val order = 100
    @Composable override fun Content(navigator: AppNavigator) { Text("同步完成") }
}

context.services.register(UiWidget::class, StatusWidget())
```

- 安全模式（`--safe-mode`）下所有插件页面与 widget 都不渲染
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
# 全部插件一次编译，收集到 build/plugins/
./gradlew packagePlugins

# 只编译一个
./gradlew :plugins:hello:jar
```

产物输出到 `plugins/hello/build/libs/dev.mmcl.plugin.hello-0.0.1.jar`
（文件名由 `tasks.jar { archiveBaseName.set(...) }` 固定为 `<pluginId>-<version>.jar`）；
`packagePlugins` 会把所有插件 JAR 复制到仓库根的 `build/plugins/`，方便一次性安装：

```bash
cp build/plugins/*.jar "$HOME/Library/Application Support/MMCL/plugins/"
```

内置的三个插件（`auth-offline` / `ui-about` / `theme-sakura`）不用手动装，`:app` 打包时自动同步。

### 10.2 内置插件

内置插件不进 Git 的 `src/main/resources`，而是在 `app` 构建期由 `prepareBuiltinPlugins` 任务收集：

1. `app/build.gradle.kts` 用自定义 configuration `builtinPlugins` 依赖插件模块；
2. 任务把插件 JAR 复制到构建目录的 `builtin-plugins/`，并生成 `index.txt`（一行一个 JAR 名）；
3. `processResources` 把这个目录打进 `app` 的 classpath 资源；
4. 运行期 `BuiltinPluginSync` 读取 `builtin-plugins/index.txt`，把 JAR 同步到
   `~/Library/Application Support/MMCL/plugins/`（Windows / Linux 见 `ARCHITECTURE.md` §12.3）。

新增内置插件只需要在 `settings.gradle.kts` 注册模块，并在 `app/build.gradle.kts` 里加一行
`builtinPlugins(project(":plugins:<name>"))`。

### 10.x API 级别

`Plugin.apiLevel` 声明插件按哪一级 API 编译（[`dev.mmcl.api.ApiLevel.CURRENT`](api/src/main/kotlin/dev/mmcl/api/ApiLevel.kt)）：

- 启动器拒绝加载 `apiLevel` **高于**当前版本的插件（状态 `INCOMPATIBLE_API`，日志写明需要的级别），
  所以插件不会在启动器太旧时跑到一半抛 `NoSuchMethodError`
- 没声明的旧插件（`apiLevel = 0`）按兼容处理，日志里会有一条告警
- 启动器只在**删改已有接口/签名**时才把 `ApiLevel.CURRENT` +1；新增带默认实现的方法不需要动

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