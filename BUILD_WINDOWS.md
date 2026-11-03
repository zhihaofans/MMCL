# BUILD_WINDOWS.md — Windows 平台编译与打包手册

> 适用：在 Windows 10/11 上编译、测试、运行并打包本启动器。
> 项目栈：Kotlin / JVM（**编译用 JDK 27 toolchain，产出 17 字节码**）、Compose Multiplatform、Gradle 9.6.1 Wrapper、多模块。
> 相关文档：架构见 `ARCHITECTURE.md`，插件开发见 `PLUGIN_DEV.md`，Agent 规则见 `AGENTS.md`。
>
> **本文档中的每条结论都已在本项目的构建脚本与源码中核对过**，引用了具体文件行号。若代码变更导致不符，请以代码为准并回头修文档。

---

## 0. 先读这一段：四个最容易踩的坑

| 坑 | 现象 | 真相 |
|---|---|---|
| **JDK 27 是必需的** | `Cannot find a Java installation ... languageVersion=27` | `jvmToolchain(27)`：编译、测试、jpackage 统一用 JDK 27（字节码仍 17）。装 27 即可，不再需要 21 |
| **`-PpackageJdk` 路径必须整体加引号** | `Task 'Files\Eclipse' not found` | 路径含空格未加引号，被 shell 切成多个参数，后半段被当成**任务名**。跟 JDK 版本毫无关系 |
| **Windows 上没有 `packageMsi`** | `Task 'packageMsi' not found in project ':app'` | 打包格式是**按宿主平台**选的。Windows 上是 `packageMsi`，但那需要先安装 WiX；macOS 上只有 `packageDmg` |
| **Maven Central 连不上** | `Could not GET 'https://repo.maven.apache.org/...'` 且无更具体原因 | 国内直连中央仓库经常在 TCP/TLS 阶段失败。用 `init.gradle` 把镜像插到**最前面**（追加到末尾无效，见 §3.3） |

---

## 1. 环境要求

| 项目 | 要求 | 说明 |
|---|---|---|
| 操作系统 | Windows 10 21H2+ / Windows 11（x64 或 ARM64） | |
| **JDK** | **Temurin 27**（完整 JDK，含 `jpackage.exe`） | 根 `build.gradle.kts` 里 `jvmToolchain(27)` + `jvmTarget = JVM_17`，Gradle 必须能找到 **JDK 27** 才能编译与打包 |
| 构建工具 | 项目自带 Gradle Wrapper（`gradlew.bat`，Gradle 9.6.1） | 无需全局安装 Gradle |
| 终端 | PowerShell 7+ 或 Windows Terminal | |
| 网络 | 首次构建需下载依赖，约 300+ MB | 国内建议按 §3.3 配镜像 |
| **WiX Toolset 3.x** | **仅 `packageMsi` 需要** | 不打安装包可以不装 |

> **不要使用 Android Studio 自带的 JBR**：它不包含 `jpackage`，`packageMsi` / `createDistributable` 会失败。
>
> **`JAVA_HOME` 与 Gradle 的 JDK 是两个概念**：Gradle 用 `JAVA_HOME` 启动自身，再按 `jvmToolchain(27)` 去本机找 JDK 27 编译/打包。**两者都指向 JDK 27 最省事**；若 `JAVA_HOME` 是 21/25，Gradle 也会自动发现已安装的 27，但打包仍用 27。

---

## 2. 安装 JDK 27（winget）

### 2.1 安装

```powershell
winget install --exact --id EclipseAdoptium.Temurin.27.JDK --source winget
# 若 winget 源里没有 27，用：winget search Temurin
```

备选发行版（任选其一，功能等价）：

```powershell
# 其他发行版：只要保证是完整 JDK 27（含 jpackage.exe）即可
winget search Temurin
winget search OpenJDK
```

### 2.2 验证

**重新打开一个新的终端窗口**，然后执行：

```powershell
java -version
```

期望输出包含 `openjdk version "27.` 字样。

查找 JDK 安装路径：

```powershell
Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Select-Object FullName
```

### 2.3 配置 `JAVA_HOME` 与 `Path`

Windows 不会自动设置 `JAVA_HOME`，需手动配置。

**图形界面方式：**

1. 开始菜单搜索「环境变量」→「编辑系统环境变量」
2. 「环境变量」→「系统变量」→「新建」
   - 变量名：`JAVA_HOME`
   - 变量值：例如 `C:\Program Files\Eclipse Adoptium\jdk-27`
3. 编辑系统变量 `Path` →「新建」→ 填入 `%JAVA_HOME%\bin`
4. 一路「确定」，**重开终端**

**PowerShell 一次性方式（仅当前会话有效）：**

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-27"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

**永久写入（当前用户）：**

```powershell
[Environment]::SetEnvironmentVariable(
  "JAVA_HOME",
  "C:\Program Files\Eclipse Adoptium\jdk-27",
  "User"
)
```

### 2.4 验证 `JAVA_HOME`

```powershell
echo $env:JAVA_HOME
& "$env:JAVA_HOME\bin\jpackage.exe" --version
```

`jpackage` 能输出版本号，说明打包链路可用。

> **顺手检查一下主版本**，后面 §6.2 要用到这个数字：
>
> ```powershell
> & "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Select-Object -First 1
> ```

---

## 3. 获取代码、进入项目、解决网络

### 3.1 进入项目

```powershell
cd D:\code\mmcl
```

确认根目录包含：

```
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew.bat
gradle\wrapper\gradle-wrapper.jar
gradle\wrapper\gradle-wrapper.properties
```

> **路径建议**：项目路径使用纯英文、无空格（如 `D:\code\mmcl`）。含中文或空格的路径会让 Gradle、WiX、jpackage 在拼接参数时出错——`C:\Users\张三\Downloads\...` 是最常见的事故现场。

### 3.2 检查 Gradle 能否启动

```powershell
.\gradlew.bat --version
```

输出中应看到：

```
Gradle 9.6.1
JVM: 27.x.x (Eclipse Adoptium 27.x.x+xx)
```

**这一条只验证"Gradle 本身能跑"**，还不能说明项目配置正确。如果这里就卡在 `Downloading https://services.gradle.org/...`，先按 §3.3 处理网络。

如果使用 IntelliJ IDEA：`File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK` 选择 Temurin 27。

### 3.3 Maven Central 连不上怎么办

**典型报错：**

```
> Could not resolve org.slf4j:slf4j-api:2.0.17.
   > Could not GET 'https://repo.maven.apache.org/maven2/org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.pom'.
      > repo.maven.apache.org
```

最后一行**只有一个主机名、没有更具体的原因**，就说明不是 404，而是 TCP 连接或 TLS 握手阶段就失败了。

**为什么"在末尾追加镜像"无效**：Gradle 只有收到 **404** 才会回退到下一个仓库；连接超时/重置会直接让整个模块解析失败，根本走不到后面的仓库。**镜像必须插到列表最前面。**

**做法**：新建（或覆盖）`%USERPROFILE%\.gradle\init.gradle`，内容：

```groovy
// 仅作用于本机，不要提交到仓库
def mirrors = [
    'https://maven.aliyun.com/repository/public',
    'https://maven.aliyun.com/repository/google',
    'https://maven.aliyun.com/repository/gradle-plugin',
]

def hasRepo = { repos, String u ->
    repos.any { r -> r.hasProperty('url') && r.url.toString().replaceAll('/$', '') == u }
}

def prependMirrors = { repos, List<String> urls ->
    urls.reverseEach { u ->
        if (!hasRepo(repos, u)) {
            def repo = repos.maven { url = uri(u) }
            repos.remove(repo)
            repos.add(0, repo)
        }
    }
}

settingsEvaluated { settings ->
    prependMirrors(settings.pluginManagement.repositories, mirrors)
    prependMirrors(settings.dependencyResolutionManagement.repositories, mirrors)
}
```

然后：

```powershell
.\gradlew.bat --stop      # 让 Daemon 丢弃旧配置
.\gradlew.bat help
```

> 本项目 `settings.gradle.kts` 设置了 `FAIL_ON_PROJECT_REPOS`，所以**不能**在各模块的 `build.gradle.kts` 里加仓库，`init.gradle` 或 `settings.gradle.kts` 是唯一入口。

**如果加了镜像仍失败**，八成是本机代理软件拦了 Gradle 但没走系统代理。Gradle **不读系统代理**，需要显式配：

```properties
# 追加到项目根 gradle.properties，或 %USERPROFILE%\.gradle\gradle.properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

---

## 4. 编译流程（按顺序执行）

### 4.1 配置检查（最快暴露脚本错误）

```powershell
.\gradlew.bat help
```

- 通过：输出 `BUILD SUCCESSFUL`
- 失败常见两类：

| 报错 | 原因 | 处理 |
|---|---|---|
| `e: build.gradle.kts:xx:xx: unexpected symbol` | 存在全角字符（`｝`、`，`、`（`） | 按行号改半角 |
| `Cannot find a Java installation ... matching: {languageVersion=27 ...}` | 本机没装 JDK 27 | 按 §2 安装 JDK 27 |
| `Task 'Files\Eclipse' not found in root project 'MMCL'` | `-PpackageJdk` 路径未加引号被空格切开 | 见 §4.1.2 |

#### 4.1.1 JDK 27：编译与打包统一

项目用 `jvmToolchain(27)`：编译、测试、jpackage 都用 **JDK 27**，字节码目标仍是 17。
三个平台的安装包因此都输出真实版本（例如 `0.0.3`）。

历史原因：macOS 的 jpackage 在 JDK < 27 上会拒绝首段为 0 的 app-version（JDK-8380634，JDK 27 已修）。
早期用 21 编译 + 27 打包，导致 macOS 的 dmg 被映射成 `1.0.x`，与应用内版本不一致。

`-PpackageJdk` 仍保留（调试/特殊环境），逻辑在 `app/build.gradle.kts`：

- 优先 `-PpackageJdk`；否则找 JDK 27 工具链；再否则用当前运行 Gradle 的 JDK
- 只有「macOS 宿主 + 打包 JDK < 27」时才把 `0.0.3` 映射成 `1.0.3`
- 找不到匹配工具链不再抛配置异常（已用 `runCatching` + `getOrNull` 包住）

| 你的 JDK | 能编译/测试/打包 | Windows/Linux 安装包版本 | macOS 安装包版本 |
|---|---|---|---|
| **27（推荐）** | ✅ | `0.0.3` | `0.0.3` |
| 21 / 24 / 25 / 26 | ✅（toolchain 仍要 27；`-PpackageJdk` 只影响打包 JDK） | `0.0.3` | `1.0.3`（映射） |

**结论：装 JDK 27 一个就够，编译和打包都不再需要 21。**

#### 4.1.2 `-PpackageJdk` 的正确写法

路径含空格时，**引号要包住整个 `-P` 参数**，不是只包路径：

```powershell
# PowerShell / cmd 通用
.\gradlew.bat build "-PpackageJdk=C:\Program Files\Eclipse Adoptium\jdk-27"
```

```powershell
# PowerShell 也可以先用变量，避免转义困扰
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-27"
.\gradlew.bat build "-PpackageJdk=$jdk"
```

**写错会怎样**：`-PpackageJdk=C:\Program Files\Eclipse Adoptium\jdk-27` 被切成三段，后两段被当作任务名，报 `Task 'Files\Eclipse' not found`。

修复后的脚本容错更多：传 JDK 根目录、`bin` 目录、`java.exe` 路径都可以；路径非法时会打一条明确告警并继续构建，而不是抛这个误导性错误。

### 4.2 编译全部模块

```powershell
.\gradlew.bat assemble
```

产物：

```
api\build\libs\
api-ui\build\libs\
ui-kit\build\libs\
core\build\libs\
app\build\libs\
plugins\<name>\build\libs\
```

跳过测试（`assemble` 本身不含测试，此参数仅在你自定义了依赖时有用）：

```powershell
.\gradlew.bat assemble -x test
```

### 4.3 运行测试

```powershell
.\gradlew.bat test
```

仅跑核心模块：

```powershell
.\gradlew.bat :core:test
```

只跑某个测试类（**必须带 `--rerun-tasks`，否则命中缓存不会真正执行**）：

```powershell
.\gradlew.bat :core:test --tests "*PluginLoaderTest*" --rerun-tasks
```

查看测试报告：

```
core\build\reports\tests\test\index.html
```

> **注意**：`app` 模块共 19 个测试，其中 **11 个**是基于 Compose 渲染的截图测试（`AppShellRenderTest`），产出 11 张 PNG 到 `app\build\screenshots\`。
>
> 这些测试用 `ImageComposeScene` 做**离屏渲染**，不创建窗口，所以在无桌面会话的 SSH / 服务 / CI 环境里也能跑——前提是 skiko 能加载原生库（Linux 需要 libGL、libEGL、libX11、fontconfig 与字体；Windows 一般开箱即用，至少要有字体）。
>
> 如果在无 GPU 的环境（容器、CI、虚拟机）里 skiko 初始化失败或渲染异常缓慢，可强制软件渲染。注意 `JAVA_TOOL_OPTIONS` 要靠新 daemon 才能继承，所以先 `--stop`：
>
> ```powershell
> .\gradlew.bat --stop
> $env:JAVA_TOOL_OPTIONS = "-Dskiko.renderApi=SOFTWARE"
> .\gradlew.bat :app:test --tests "*AppShellRenderTest*" --rerun-tasks
> ```
>
> 本仓库在无 X、无 GPU 的 Linux 容器上，就是靠这一步跑通全部 11 个用例的。

### 4.4 运行启动器（验证 UI 与插件加载）

```powershell
.\gradlew.bat :app:run
```

- 能弹出 Compose 窗口即视为通过
- 首次启动会把内置插件同步到 `%APPDATA%\MMCL\plugins\`

单文件源码跑不通、窗口一闪而过时，加日志看：

```powershell
.\gradlew.bat :app:run --info
```

### 4.5 编译并安装插件（内置之外的插件）

应用**只内置 3 个插件**，其余插件都要自己编译后手动装进插件目录。尤其是**所有下载源**：
应用本身不含任何下载地址，`source-mojang` 这类源插件不随安装包分发（`ARCHITECTURE.md` §9）。
没装源插件时，启动器能启动已装好的实例，但游戏页拿不到版本清单。

#### 4.5.1 哪些内置、哪些要自己装

| 模块 | 插件 id | 内置 | 作用 |
|---|---|---|---|
| `plugins/auth-offline` | `dev.mmcl.auth.offline` | ✅ | 离线账户（`player.name`，缺省 `Player`） |
| `plugins/ui-about` | `dev.mmcl.ui.about` | ✅ | 往首页加卡片、状态栏加标记的示例 |
| `plugins/theme-sakura` | `dev.mmcl.theme.sakura` | ✅ | 樱花主题示例 |
| `plugins/source-mojang` | `dev.mmcl.source.mojang` | ❌ | Mojang 官方版本清单源（**没它就没有任何可下载版本**） |
| `plugins/runtime-adoptium` | `dev.mmcl.runtime.adoptium` | ❌ | Temurin Java 运行时下载来源（缺 Java 时可一键下载） |
| `plugins/runtime-foojay` | `dev.mmcl.runtime.foojay` | ❌ | 一次提供多个发行版（Zulu / Liberica / Microsoft / Corretto / Dragonwell / Oracle OpenJDK），在「设置 → Java 运行时来源」里挑 |
| `plugins/runtime-mirror` | `dev.mmcl.runtime.mirror` | ❌ | 国内镜像：清华 TUNA / 中科大 USTC 的 Temurin、华为云的 Oracle OpenJDK；国内下载快很多 |
| `plugins/hello` | `dev.mmcl.plugin.hello` | ❌ | 纯示例：只订阅事件、不注册任何能力 |

前三个在首次启动时由 `BuiltinPluginSync` 从应用资源复制到插件目录；后三个在仓库里有源码，
但**不会**被打进安装包，必须自己构建。

#### 4.5.2 编译

```powershell
.\gradlew.bat :plugins:source-mojang:jar
.\gradlew.bat :plugins:runtime-adoptium:jar
.\gradlew.bat :plugins:runtime-foojay:jar
.\gradlew.bat :plugins:runtime-mirror:jar
```

也可以一次编全部：`.\gradlew.bat assemble`。产物在各自模块下：

```
plugins\source-mojang\build\libs\dev.mmcl.source.mojang-0.0.1.jar
plugins\runtime-adoptium\build\libs\dev.mmcl.runtime.adoptium-0.0.1.jar
```

文件名固定为 `<插件 id>-<插件版本>.jar`，版本来自插件模块自己的 `build.gradle.kts`
（**与启动器版本无关**，目前都是 `0.0.1`）。

#### 4.5.3 安装

把 JAR 复制进插件目录即可，**不要解压**（加载器只扫描目录下的 `*.jar` 文件，子目录一律忽略）：

```powershell
$plugins = "$env:APPDATA\MMCL\plugins"
New-Item -ItemType Directory -Force -Path $plugins | Out-Null
Copy-Item plugins\source-mojang\build\libs\*.jar      $plugins -Force
Copy-Item plugins\runtime-adoptium\build\libs\*.jar   $plugins -Force
dir $plugins
```

目录即 `C:\Users\<你>\AppData\Roaming\MMCL\plugins\`，装好后应该能看到 3 个内置插件 + 你复制进去的 JAR。

#### 4.5.4 验证

1. **重启启动器**（插件只在启动时扫描一次，热装不生效）
2. 左侧「插件」页出现新条目，状态为**已启用**
3. 「设置」页的下载源列表出现「Mojang 官方」——有源之后游戏页才会去拉版本清单
4. 日志里能看到 `发现插件 ... 已启用`；装坏了只会让该插件不可用，不影响启动器

#### 4.5.5 三个坑

- **JAR 必须声明 `META-INF/services/dev.mmcl.api.plugin.Plugin`**：缺失时该 JAR 直接被跳过，
  日志里是「插件 JAR 未声明 META-INF/services/...」
- **插件 id / 版本来自代码，不来自文件名**：改名不影响加载；同一 id 装了多份时，
  按 `Plugin.version` 保留最高版本并告警，旧的可以删掉
- **插件不能自带 Compose**：内置插件有 `:app:verifyPluginJars` 把关，手动装的外部插件没人拦，
  带了 `androidx/compose/**` 会在运行期类冲突

### 4.6 清理

```powershell
.\gradlew.bat clean
```

---

## 5. 打包 Windows 安装包

### 5.1 前置条件（逐条确认）

打包格式由宿主平台自动选择（`app/build.gradle.kts`）：

| 宿主 | targetFormats | 可用打包任务 |
|---|---|---|
| Windows | `Msi` | `packageMsi` |
| macOS | `Dmg` | `packageDmg` |
| Linux | `Deb` | `packageDeb` |

Windows 上打 Msi 必须装 **WiX Toolset 3.x**（jpackage 硬要求）：

```powershell
winget install --exact --id WiXToolset.WiXToolset
```

装完**重开终端**，验证：

```powershell
candle.exe -?
light.exe -?
```

两个都能输出版本信息才算就绪。

### 5.2 生成 `.msi`

```powershell
.\gradlew.bat :app:packageMsi
```

产物：

```
app\build\compose\binaries\main\msi\
```

### 5.3 生成免安装目录（推荐先跑这个）

```powershell
.\gradlew.bat :app:createDistributable
```

产物：

```
app\build\compose\binaries\main\app\MMCL\
```

目录内含 `MMCL.exe` 与内置 JRE，可直接双击运行，**不需要 WiX**。验证"能不能用"优先用它，比打 Msi 快得多。

### 5.4 用 GitHub Actions 出包（推荐）

仓库自带 `.github/workflows/package.yml`，在 GitHub 上手动触发（Actions → package → Run workflow），
或推送 `0.*` / `v*` tag 时自动跑三平台矩阵：

| 平台 | 任务 | 产物 |
|---|---|---|
| `macos-14` | `:app:packageDmg` | `MMCL-macos-dmg` artifact |
| `windows-latest` | `:app:packageMsi`（自动装 WiX 3） | `MMCL-windows-msi` artifact |
| `ubuntu-latest` | `:app:packageDeb` | `MMCL-linux-deb` artifact |

版本号说明：三个平台都输出 `gradle.properties` 里的真实版本（例如 `0.0.3`）。CI 统一安装 JDK 27，
编译与 jpackage 同一个 JDK，不做版本映射。

`windows { }` 里的 `upgradeUuid` 已经固定，**发布后不要改**，否则 MSI 会把新版本当成另一个产品，
无法升级/卸载旧版。

### 5.5 关于 `.exe` 安装包

jpackage **不支持**直接产出 exe 安装器（`.exe` 只能作为**应用启动器**，见 5.3 产物）。需要 exe 安装包请用 Inno Setup / NSIS 对 5.3 的目录再打一层——本项目当前没有这条链路。

### 5.6 打包失败排查

| 报错 | 原因 | 处理 |
|---|---|---|
| `Can not find WiX` / `wix.exe` 找不到 | 未装 WiX 或不在 `PATH` | `winget install WiXToolset.WiXToolset`，重开终端 |
| `candle.exe` 退出代码 14 | 非 `en-US` 区域设置导致 WiX 异常 | `Set-Culture en-US`，**重开终端**后重试；或临时改系统区域为英语（美国） |
| `app-version 第一个数字不能为零` | 手动 `-PpackageJdk` 指向 JDK < 27 且在 macOS 打包 | 用 JDK 27 打包（§4.1.1） |
| `jpackage.exe is missing` | 用了 JBR 或 JRE | 换完整 JDK（Temurin 27） |
| 文档早先提到的"自动下载 WiX" | **不存在** | jpackage 从不自动下载 WiX，必须手动安装 |

---

## 6. 常用命令速查

```powershell
# 配置检查
.\gradlew.bat help

# 编译
.\gradlew.bat assemble

# 测试
.\gradlew.bat test
.\gradlew.bat :core:test --tests "*PluginLoaderTest*" --rerun-tasks

# 运行
.\gradlew.bat :app:run

# 编译单个插件 → plugins\<name>\build\libs\
.\gradlew.bat :plugins:source-mojang:jar
.\gradlew.bat :plugins:runtime-adoptium:jar

# 安装插件（复制 JAR 到插件目录，重启生效）
Copy-Item plugins\source-mojang\build\libs\*.jar "$env:APPDATA\MMCL\plugins" -Force

# 免安装目录（不需要 WiX）
.\gradlew.bat :app:createDistributable

# 安装包（需要 WiX）
.\gradlew.bat :app:packageMsi

# 清理
.\gradlew.bat clean

# 停止 Daemon（改完 init.gradle / gradle.properties 后用）
.\gradlew.bat --stop

# 查看依赖树（排查冲突）
.\gradlew.bat :app:dependencies

# 详细日志
.\gradlew.bat assemble --info
.\gradlew.bat assemble --stacktrace
```

---

## 7. 常见问题排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `unexpected symbol` / `Expecting '}'` | `build.gradle.kts` 里有全角字符（`｝`、`，`、`（`） | 改半角 |
| `Cannot find a Java installation ... languageVersion=27` | 本机没装 JDK 27 | 按 §2 安装 |
| `Task 'Files\Eclipse' not found` | `-PpackageJdk` 路径含空格未加引号 | 引号包住整个 `-P` 参数（§4.1.2） |
| `Could not GET 'https://repo.maven.apache.org/...'` + 只有主机名 | 直连 Maven Central 失败 | 配 `init.gradle` 镜像（§3.3） |
| `Unsupported class file major version` | Gradle 启动用的 JDK 过低（低于 17） | 确认 `JAVA_HOME` 指向 JDK 27 |
| `No matching toolchains found ... languageVersion=27` | 本机没有 JDK 27 | 按 §2 安装 |
| `Task 'packageMsi' not found` | 在 macOS/Linux 上执行，或脚本旧版只写了 `Dmg` | 用当前平台对应的任务名（§5.1） |
| `jpackage.exe is missing` | 使用了 JBR 或 JRE | 换完整 JDK（Temurin 27） |
| `Can not find WiX` | 未装 WiX | `winget install WiXToolset.WiXToolset` |
| `candle.exe` 退出代码 14 | 非 `en-US` 区域设置 | `Set-Culture en-US`，重开终端 |
| `app-version 第一个数字不能为零` | 手动指定了 JDK < 27 在 macOS 打包 | 用 JDK 27 打包 |
| 测试里 `ProcessBuilder` 相关失败 | 测试硬编码了 `/bin/sh` 等 POSIX 路径 | 已修复为跨平台版本，确认拉取到最新代码 |
| 测试里路径断言失败（`\` vs `/`） | 测试硬编码了正斜杠 | 同上，已修复 |
| 打包后应用启动静默崩溃 | 同目录残留旧 `jre`/`runtime` 文件夹 | 移走该文件夹，让应用使用内置 JRE |
| `OutOfMemoryError` | 堆不足 | `gradle.properties` 已有 `-Xmx3g`，可调大到 `-Xmx4g` |
| 中文路径下构建失败 | 路径含中文/空格 | 项目放到纯英文路径 |

---

## 8. 与 macOS 的差异提示

- 在 Windows 上**只能**产出 Windows 安装包；`.dmg` 必须在 macOS 上构建。因为 `targetFormats` 按宿主平台选择，跨平台打包会报任务不存在
- 图标格式：Windows 用 `.ico`，macOS 用 `.icns`（当前项目**未配置任何自定义图标**，用的是 Compose 默认图标）
- **数据目录**（见 `core/platform/Platforms.kt`）：
  - Windows：`%APPDATA%\MMCL\`（即 `C:\Users\<你>\AppData\Roaming\MMCL`）
  - macOS：`~/Library/Application Support/MMCL/`
  - Linux：`$XDG_DATA_HOME/mmcl` 或 `~/.local/share/mmcl`
- 一次性产出三平台安装包需要 GitHub Actions 三平台矩阵（每个平台在自己的 runner 上构建）
- **Linux 分数缩放（125% / 150%）**：JDK 对 GTK 分数缩放经常只报整数倍，界面会偏小或发糊。
  JDK 没有可靠的自动修法，用环境变量显式覆盖即可（必须在启动前设置，重启终端生效）：

  ```bash
  MMCL_UI_SCALE=1.25 ./MMCL          # 或者 java -Dmmcl.uiScale=1.25 -jar ...
  ```

  接受 0.5–4.0 之间的值，`1.25` 与 `1,25` 都认；非法值会被忽略并打一条日志。
  Windows / macOS 一般由系统 DPI 自动处理，不需要设置

---

## 9. 最小验证清单

在 Windows 上完成一次"能编译、能跑、能打包"的验证，按顺序勾选：

- [ ] `java -version` 显示 27
- [ ] `echo $env:JAVA_HOME` 指向 Temurin 27
- [ ] `& "$env:JAVA_HOME\bin\jpackage.exe" --version` 成功
- [ ] `.\gradlew.bat --version` 显示 Gradle 9.6.1 + JVM 27
- [ ] `.\gradlew.bat help` 通过
- [ ] `.\gradlew.bat assemble` 通过
- [ ] `.\gradlew.bat test` 通过（**任意环境都能跑**，headless 见 §4.3 的说明）
- [ ] `.\gradlew.bat :app:run` 能弹出窗口
- [ ] `.\gradlew.bat :app:createDistributable` 产出可运行的 `MMCL.exe`
- [ ] （可选，需 WiX）`.\gradlew.bat :app:packageMsi` 产出 `.msi`
- [ ] 双击 `MMCL.exe` 能启动，且无需额外安装 Java
- [ ] `%APPDATA%\MMCL\plugins\` 下出现 3 个内置插件 JAR
- [ ] （需要能拉版本清单）编译并装入 `source-mojang`，重启后「设置」页能看到「Mojang 官方」（§4.5）
