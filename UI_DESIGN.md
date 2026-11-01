# UI_DESIGN.md — 界面外壳与主题化方案

> 状态：**P1（外壳）与 P2（主题）已实现**（对应 0.0.2）；P3–P5 未开始。实现进度以 `TODO.md` 为准，规则见 `AGENTS.md`。
> 目标：做一套 HMCL 式的桌面界面，同时让**页面、部件、主题都能由插件替换**，三平台一致。

---

## 1. 目标与非目标

**目标**

1. HMCL 式布局：左导航栏 + 顶栏 + 内容区 + 底部状态栏，一套代码跑三平台
2. 界面由插件扩展：整页（Page）、区域内部件（Widget）、主题（Theme）三类扩展点
3. 内置页面保证**零插件也完整可用**（自举安全）
4. 主题可热切换、可持久化、坏了能回退
5. 插件做的页面与内置页面观感一致（统一组件库，而不是各画各的）

**非目标（本轮不做）**

- 自定义无边框标题栏、毛玻璃、转场动画
- 国际化（先用中文文案，`UiPage.title` 直接给字符串）
- 移动端 / Web 端

---

## 2. 布局蓝图

```
┌──────────────────────────────────────────────────────────────┐
│ 顶栏 TopBar                                    账户 · 通知 · ⋯ │ 56dp
├────────┬─────────────────────────────────────────────────────┤
│ 导航   │                                                     │
│ Rail   │   Page content                                      │
│ 72dp   │   （内置：首页 / 游戏 / 下载 / 设置 / 插件）                │
│        │   （插件可追加页面）                                 │
│  ───   │                                                     │
│  分组   │                                                     │
├────────┴─────────────────────────────────────────────────────┤
│ 状态栏 StatusBar    下载 2/3 · 任务进行中 · v0.0.1            │ 28dp
└──────────────────────────────────────────────────────────────┘
最小窗口 960×600，默认 1100×720；低于 960 宽时导航栏收成图标态
```

- **导航栏分组**：`MAIN`（首页/游戏/下载）· `TOOLS`（插件追加的工具页）· `SYSTEM`（设置），组间用分隔线，和 HMCL 的分区感一致
- **内容区**：每页自己滚动，外壳不接管；页面统一用 `PageScaffold`（标题 + 内容 + 可选右上角操作）
- **页面分工**：游戏页 = 实例/启动/日志；下载页 = 版本清单、共享文件安装与修复。
  下载任务归 core `DownloadsService`，切页不取消，状态栏显示进度
- **顶栏右侧**：账户入口（P3 接认证服务）、任务指示、设置快捷键；插件可通过 `UiSlot.TOP_BAR` 追加
- **状态栏**：任务进度、当前实例、启动器版本；插件可通过 `UiSlot.STATUS_BAR` 追加
- **首页卡片**：插件通过 `UiSlot.HOME` 往首页加快捷卡片（`UiWidget`）

---

## 3. 关键决策

| 编号 | 决策 | 理由 |
|---|---|---|
| D1 | 用**左侧 NavigationRail**，不用顶部 Tab | HMCL 同款；页面多时竖向更省空间；分组扩展性最好 |
| D2 | **废弃 `UiPanelRegistry`**，UI 贡献一律走 `ServiceRegistry` | 只保留一条注册路径，少一层 API；现有 `UiContribution` 也从这里出 |
| D3 | 主题 = **数据优先**（`ColorScheme`/`Typography`/`Shapes`）+ 可选 `Decorate` 包装 | app 自己套 `MaterialTheme`，插件主题改不坏布局骨架 |
| D4 | **默认主题与默认页面内置在 app**，插件只做增量 | 插件全崩也要能开机；符合"核心永远能启动"的哲学 |
| D5 | 契约放 `api-ui`，公共组件放新模块 **`ui-kit`**，`core` 永不依赖 Compose | 契约保持纯净；插件能复用同一套按钮/卡片/间距，观感统一 |

新增模块 `ui-kit` 的理由：如果只给 `api-ui`，插件要自己拼卡片、间距、图标映射，最后每个插件长得都不一样；但把这些实现塞进 `api-ui` 又违反"契约模块不含实现"。所以拆一层：

```
app ──► core ──► api
 │
 ├──► api-ui ──► api          （纯契约：接口 + 枚举 + 数据类）
 ├──► ui-kit ──► api-ui       （公共组件：调色板、图标映射、PageScaffold、间距）
 └──► plugins/* ──► api / api-ui / ui-kit（UI 插件）
```

UI 插件只依赖 `ui-kit`（它会传递依赖 `api-ui`）；非 UI 插件一个都不许依赖。

---

## 4. API 契约（`api-ui`）

```kotlin
// 包：dev.mmcl.api.ui

/** 导航栏图标。app 把它映射成内置矢量图，插件不能塞任意 Drawable。 */
enum class UiIcon { BACK, GAME, DOWNLOAD, SETTINGS, PLUGIN, ACCOUNT, HOME, WRENCH, INFO, EXTENSION }

/** 导航分组：MAIN 在顶部，TOOLS 居中，SYSTEM 沉底。 */
enum class UiGroup { MAIN, TOOLS, SYSTEM }

/** 一个完整页面（占满内容区）。 */
interface UiPage {
    val id: String
    val title: String
    val icon: UiIcon get() = UiIcon.EXTENSION
    val group: UiGroup get() = UiGroup.TOOLS
    /** 越小越靠前；内置页用 0..99，插件页请用 100+。 */
    val order: Int get() = 100

    @Composable
    fun Content(navigator: AppNavigator)
}

/** 页面跳转与读当前路由；实现由 app 提供，保证唯一。 */
interface AppNavigator {
    val currentPageId: String
    fun navigate(pageId: String)
    fun back()
}

/**
 * 主题：数据优先 + 可选装饰。
 *
 * app 负责 MaterialTheme(colorScheme, typography, shapes)，再调用 Decorate 包一层，
 * 因此主题插件改不动布局骨架（导航栏宽度、页面结构都不由主题决定）。
 */
interface LauncherTheme {
    val id: String
    val displayName: String
    val dark: Boolean

    val colorScheme: ColorScheme
    val typography: Typography get() = Typography()
    val shapes: Shapes get() = Shapes()

    /** 想加背景图/渐变/边框时覆盖；默认什么都不做。 */
    @Composable
    fun Decorate(content: @Composable () -> Unit) {
        content()
    }
}
```

`ui-kit` 提供插件侧的落地工具（避免每个插件重复造轮子）：

```kotlin
// 包：dev.mmcl.ui.kit

/** 主题值对象：插件只改配色就能做一个主题。 */
data class SimpleLauncherTheme(
    override val id: String,
    override val displayName: String,
    override val dark: Boolean,
    override val colorScheme: ColorScheme,
    override val typography: Typography = Typography(),
    override val shapes: Shapes = Shapes(),
) : LauncherTheme

object MmclPalettes { val Dark: ColorScheme; val Light: ColorScheme }
object MmclIcons { fun of(icon: UiIcon): ImageVector }
object MmclSpacing { val xs, s, m, l, xl }
@Composable fun PageScaffold(title: String, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit)
@Composable fun EmptyState(icon: UiIcon, text: String, hint: String? = null)
```

**为什么不用 sealed interface**：插件在别的模块里，Kotlin 禁止跨模块继承 sealed 类型（我们已经在 `LauncherEvent` 上踩过一次），扩展点一律用普通 interface。

---

## 5. 主题机制

**发现**：`services.getAll(LauncherTheme::class)`，加上 app 内置的两个（`builtin.dark` / `builtin.light`），按 id 去重，内置优先保留。

**选择**：设置键 `ui.theme`（P2 引入 `SettingsService`）。缺省 `builtin.dark`；找不到对应 id 时回退 `builtin.dark` 并告警。

**应用**（app 内）：

```kotlin
@Composable
fun AppRoot(controller: ThemeController, shell: @Composable () -> Unit) {
    val theme = controller.current               // MutableState<LauncherTheme>
    MaterialTheme(theme.colorScheme, theme.typography, theme.shapes) {
        theme.Decorate { shell() }
    }
}
```

**热切换**：`ThemeController.current` 是 Compose `MutableState`，设置页里点一下立刻换肤，不用重启；同时写回 `SettingsService`。

**安全阀**：

- 启动参数 `--safe-mode`（或环境变量 `MMCL_SAFE_MODE=1`）→ 忽略**所有插件主题与插件页面**，只用内置的，用于"装了坏插件进不去界面"的自救
- 主题 id 解析失败 → 回退内置深色，不抛异常
- 插件页面渲染异常目前只能靠安全阀（Compose 组合异常无法在运行时吞掉后继续），这是已知限制，写进文档

内置主题在 `app`（`BuiltinThemes`），插件主题示例见仓库里的 `plugins/theme-sakura`。

**示例主题插件**（约 20 行）：

```kotlin
class SakuraTheme : LauncherTheme by SimpleLauncherTheme(
    id = "dev.mmcl.theme.sakura",
    displayName = "樱花",
    dark = false,
    colorScheme = MmclPalettes.Light.copy(primary = Color(0xFFE91E63), secondary = Color(0xFFFF8A80)),
)

class SakuraThemePlugin : Plugin {
    override val id = "dev.mmcl.theme.sakura.plugin"
    override val version = "0.1.0"
    override fun onLoad(context: PluginContext) {
        context.services.register(LauncherTheme::class, SakuraTheme())
    }
}
```

---

## 6. app 内部结构（计划）

```
app/src/main/kotlin/dev/mmcl/app/
├── Main.kt              # 启动、装配、窗口
├── shell/
│   ├── AppRoot.kt       # 主题包装 + MaterialTheme
│   ├── AppShell.kt      # 顶栏 + 导航栏 + 内容区 + 状态栏
│   ├── NavRail.kt       # 分组导航，内置页 + 插件页
│   ├── TopBar.kt
│   ├── StatusBar.kt
│   └── NavigationState.kt  # AppNavigator 实现（Compose State）
├── pages/
│   ├── InstancesPage.kt # 游戏/实例（P3 接真实数据）
│   ├── DownloadsPage.kt
│   ├── SettingsPage.kt  # 数据目录、版本、主题选择
│   └── PluginsPage.kt   # 现有插件列表迁到这里
└── theme/
    ├── BuiltinThemes.kt # builtin.dark / builtin.light
    └── ThemeController.kt
```

状态流向：`core` 的服务 → app 的 `StateFlow`/Compose `State` → Composable；**Compose 类型不出现在 `api` / `core` 里**。

---

## 7. 与 core 的数据边界（重要）

当前 `core` 只有插件运行时，**没有**实例、账户、版本这些数据服务。所以顺序必须是：

1. P1/P2 只做外壳与主题，页面用空状态（`EmptyState`）
2. P3 之前由 core 提供最小读接口（`InstanceService`、`AccountService`、`VersionService`），放进 `api`
3. app 订阅这些接口 → 首页卡片、实例列表、启动按钮才有内容

绝不允许 `api-ui` / `ui-kit` 反向依赖 `core`。插件要拿实例数据，只能通过 `PluginContext` 暴露的服务接口（P3 再加字段，标 `API-BREAK`）。

---

## 8. 分期实施

| 阶段 | 范围 | 主要产出 | 验收 | 状态 |
|---|---|---|---|---|
| **P1 外壳** | 导航栏 + 4 个内置页 + 路由 + 空状态 | `api-ui` 的 `UiPage`/`AppNavigator`；`app/shell/*`；`plugins/ui-about` 示例页 | `:app:run` 能切页，插件页出现在 TOOLS 组 | ✅ 已完成 |
| **P2 主题** | `ui-kit` + `LauncherTheme` + `SettingsService` + 主题选择页 + 安全阀 | `ui-kit` 模块；`ThemeController`；`plugins/theme-sakura` 示例 | 切换即时生效、重启保持；`--safe-mode` 可用 | ✅ 已完成 |
| **P3 真实数据** | 实例页接 core 的实例与账户服务，启动按钮；下载页接版本清单与安装队列；首页总览 | core 的三个读接口 + app 卡片网格 + `DownloadsService` + `HomePage` | 能列出本机实例并点启动；下载页能安装/修复；首页可一键启动最近实例 | ✅ 已完成 |
| **P4 部件** | Widget Slot（顶栏/状态栏/首页卡片）+ 任务进度 | `UiWidget`/`UiSlot`；首页卡片示例 | 插件卡片出现在首页，状态栏显示下载进度 | ✅ 已完成 |
| **P5 深化** | 自定义标题栏、背景图主题、字体、i18n、窗口状态记忆 | 视需求 | 三平台观感一致 | ⏳ |

预估：P1 一轮、P2 一到两轮、P3 取决于启动链进度、P4 一轮、P5 按需。

---

## 9. 对现有代码的改动（含 API-BREAK）

| 变更 | 类型 | 状态 |
|---|---|---|
| `UiContribution` → `UiPage`（原 `Content()` 增加 `navigator`） | `API-BREAK: minor` | ✅ 已删除旧契约，仓库内无使用方 |
| 删除 `UiPanelRegistry`，统一走 `ServiceRegistry` | `API-BREAK: minor` | ✅ 已删除 |
| 新增模块 `ui-kit` | 新增 | ✅ 已落地 |
| `PluginContext` 增加 `settings: SettingsService` | `API-BREAK: minor` | ✅ 已落地，`core` 提供 `FileSettingsService` |
| `PluginManager` 增加 `settings` 参数（默认内存实现） | 新增 | ✅ 已落地 |
| `app/Main.kt` 拆分到 `shell/`、`pages/`、`theme/` | 内部重构 | ✅ 已落地 |
| `prepareBuiltinPlugins` 由 Copy 改为 Sync | 构建 | ✅ 避免旧版本 JAR 残留 |
| 内置插件同步增加清单 `.builtin-plugins.manifest` | core | ✅ 只清理自己写过的旧 JAR |

---

## 10. 风险与规避

| 风险 | 规避 | 状态 |
|---|---|---|
| 插件把 Compose 打进 JAR → 类冲突、体积爆炸 | 插件用 `compileOnly`；`:app:verifyPluginJars` 扫描 JAR 内 `androidx/compose/**`，命中即失败 | ✅ 已实现 |
| 插件在后台线程改 UI 状态 | 约定：生命周期回调只注册；运行时更新走 `StateFlow` | 文档约定 |
| 坏主题/坏页面导致进不去界面 | `--safe-mode`（或 `MMCL_SAFE_MODE=1`）跳过全部插件主题与页面；主题 id 不存在自动回退内置深色 | ✅ 已实现 |
| 主题把布局改崩 | app 负责 `MaterialTheme`，主题只提供配色 + 可选 `Decorate` | ✅ 已实现 |
| 图标/字体资源不便打包 | 图标走 `UiIcon` 枚举 → `MmclIcons` 内置矢量图（`material-icons-core` 的 40 个图标，不引 extended）；字体留到 P5 | ✅ 已实现 |
| macOS 标题栏与 Windows 差异 | P1 用系统默认标题栏；P5 再评估无边框 + 拖拽区域 | 按计划 |
| 组合期异常无法隔离 | 文档明确限制 + 安全阀；不承诺"主题插件永不崩" | 已知限制 |

---

## 11. 测试策略

- 单测（纯逻辑，不碰显示器）：
  - `UiRegistry`：内置页优先、`group`/`order` 排序、id 去重、`--safe-mode` 过滤 ✅
  - `ThemeController`：默认值、已保存主题、未知 id 回退、切换后写回、内置 id 不被覆盖 ✅
  - `FileSettingsService`：读写、删除、父目录创建、按键排序 ✅
  - `BuiltinPluginSync`：复制、跳过、旧版本清理、不删用户插件、拒绝路径穿越 ✅
- **无头渲染测试**：`AppShellRenderTest` 用 `ImageComposeScene` 把外壳画成 PNG（`app/build/screenshots/`），
  不需要真实显示器，可在 CI 里跑 ✅
- 冒烟：`:app:run` 人工看一眼
- 后续：接 Compose UI Test 做点击与路由断言（P4 时评估）

---

## 12. 已定 / 待定

**已定（0.0.1 阶段）**

1. 首页默认只允许插件**加卡片**（P4 的 `UiWidget`），不开放"整页替换"；确有需求再加显式开关
2. 图标使用内置矢量图：`UiIcon` 枚举 + `MmclIcons` 映射，依赖 `material-icons-core`（约 40 个图标），不引 `material-icons-extended`
3. 主题只提供"数据 + 可选装饰"，布局骨架由 app 固定

**待定**

4. 页面之间要不要传参（如"从下载页跳到某版本详情"）？P3 会暴露 `navigate(pageId)`，届时再设计 query 模型
5. 主题是否需要背景图 / 圆角 / 密度这类"主题参数"？P5 再定
6. i18n：当前 `title` 直接是字符串，多语言需要新的 `UiTitle` 抽象
