# MMCL

**MMCL** 的正式全称是 **Mosaic Minecraft Client Launcher**；它同样可以理解为
**Minecraft Mod Client Launcher**。这是一个插件化 Minecraft Java 启动器
（Kotlin + Compose Multiplatform），支持 Windows / macOS / Linux。

## 下载

- 最新版本：[GitHub Releases](https://github.com/zhihaofans/MMCL/releases/latest)
- macOS：仅 Apple Silicon（arm64），`.dmg`
- Windows：Windows 11（`.msi`）
- Linux：`.deb`

应用**不内置任何游戏下载源**：版本清单与游戏文件都来自你安装的插件（例如 `source-mojang`）。
插件 JAR 随 Release 分发，可在启动器「插件」页直接安装。

## 支持范围

- 游戏版本：最新正式版 + 同一主版本系列（例如 26.x）；快照/旧大版本不保证
- macOS：仅 Apple Silicon；Windows：默认 Windows 11
- 加载器：Fabric 已支持；NeoForge / Forge 计划中

## 系统要求

### 启动器本身

| 项目 | 要求 |
|---|---|
| 系统 | Windows 11 64 位 / macOS 12+（仅 Apple Silicon arm64）/ Linux 64 位 |
| Java | 无需自装，安装包自带裁剪后的 JRE 27 |
| 内存 | 约 2 GB 可打开界面（实际游戏另算） |
| 显卡 | 不强制独显；界面支持软件渲染回退 |

### Minecraft 26.x（官方最低，2026-07-21 上调）

来源：[Mojang 公告](https://www.minecraft.net/en-us/article/minecraft-java-edition-system-requirements)、[官方 Java System Requirements 表](https://www.minecraft.net/en-us/store/minecraft-java-bedrock-edition-pc)。

| 项目 | 最低 | 推荐 |
|---|---|---|
| 目标 | 1080p / 30 FPS（Fast） | 1080p / 60 FPS（Fancy） |
| OS | Windows 10 64 位 / macOS 12+ / **Linux 64 位** | Windows 11 64 位 / macOS 14+ / **Linux 64 位** |
| 架构 | x64、ARM64 | x64、ARM64 |
| 内存 | 8 GB（独显）/ 12 GB（核显） | 16 GB |
| CPU | i3-10100 / Ryzen 3 3100 / Snapdragon X / Apple M1（≥4 核） | i5-12400 / Ryzen 5 5600 / Apple M2 Pro |
| GPU | **Vulkan 1.3，≥2 GB VRAM**（GTX 950 / RX 460 / Arc A310 起） | RTX 2060 / RX 5600 XT / Arc A580，6 GB VRAM |

- 官方 Linux 只要求 **64 位**，没有指定发行版；真正门槛是 Vulkan 1.3 驱动
- 低于最低配置**可能仍能启动**，但官方不保证性能/画质，并会逐步从 OpenGL 转向 Vulkan
- 云机/软件渲染（llvmpipe、lavapipe）低于官方配置，只用于验证启动链路，不代表可玩体验

内置「关于」插件页会显示本机系统、CPU 核数与内存，并给出是否达到 26.x 最低配置的提示（只提示，不阻止启动）。

## 测试状态

> 最后更新：2026-11-28。**没实机跑过就不写“已验证”**；CI 只能证明能构建/打包。

| 平台 | 构建 / 打包（CI） | 实机运行 | 已验证内容 |
|---|---|---|---|
| macOS 27（Apple Silicon / arm64） | ✅ dmg | ✅ | 26.3 原版与 Fabric 0.20.1 启动、进入世界；Modrinth 安装与依赖；存储页删除；插件安装/禁用；更新页（0.0.8 开发版） |
| Windows 11 | ✅ msi | ⚠️ 未验证 | 仅 CI 构建；MSI 安装/升级/卸载待实机验证（见 TODO） |
| Linux（Debian 13 x86_64） | ✅ deb | ✅（到标题画面） | 安装 deb、Release 插件、首次向导、26.3 资源、离线账号启动到标题画面；未进世界。无独显环境需 `mesa-vulkan-drivers`（Vulkan/llvmpipe），Xvfb 下 OpenGL 路径仍失败 |

游戏内测试基线：Minecraft 26.3 + Fabric Loader 0.20.1 + Fabric API 0.167.0+26.3；
Java 25（系统 Zulu 25 / 内置 JDK 25 都启动过）。

Linux 备注：Debian 13 + Xvfb + Mesa 25 的测试机没有 `/dev/dri`，装 `mesa-vulkan-drivers`
后游戏用 Vulkan（llvmpipe）进到标题画面；OpenGL 路径在该环境仍报 GLX visual 错误。
BMCLAPI 在该网络下下载 26.3 资源后期会超时，换 Mojang 官方源可完成（已知问题，见 TODO）。

## 构建

需要 JDK 27 与 Gradle Wrapper：

```bash
./gradlew build          # 全量构建与测试
./gradlew :app:run       # 本机运行
./gradlew packagePlugins # 收集插件 JAR
```

详见 `ARCHITECTURE.md`、`PLUGIN_DEV.md`、`BUILD_WINDOWS.md`。

## 许可证

[MIT](LICENSE) © 2026 zhihaofans
