# MMCL

插件化 Minecraft Java 启动器（Kotlin + Compose Multiplatform），支持 Windows / macOS / Linux。

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
