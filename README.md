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
