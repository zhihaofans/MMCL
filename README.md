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

> 最后更新：2026-11-27。**没实机跑过就不写“已验证”**；CI 只能证明能构建/打包。

| 平台 | 构建 / 打包（CI） | 实机运行 | 已验证内容 |
|---|---|---|---|
| macOS 27（Apple Silicon / arm64） | ✅ dmg | ✅ | 26.3 原版与 Fabric 0.20.1 启动、进入世界；Modrinth 安装与依赖；存储页删除；插件安装/禁用；更新页（0.0.8 开发版） |
| Windows 11 | ✅ msi | ⚠️ 未验证 | 仅 CI 构建；MSI 安装/升级/卸载待实机验证（见 TODO） |
| Linux（主流发行版） | ✅ deb | ⚠️ 未验证 | 仅 CI 构建；运行、桌面环境、字体待实机验证 |

游戏内测试基线：Minecraft 26.3 + Fabric Loader 0.20.1 + Fabric API 0.167.0+26.3；
Java 25（系统 Zulu 25 / 内置 JDK 25 都启动过）。

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
