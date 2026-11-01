# TODO

> 未完成的**唯一**来源。完成一项 = 从本文件删除 + 在 `CHANGELOG.md` 记一条（用户视角）。
> 版本号、发版、tag 一律先问用户；功能拆分与边界判断见 `AGENTS.md` 第 2 节。

## 进行中

- [ ] 首次跑通 GitHub Actions 三平台打包（`.github/workflows/package.yml`），确认 MSI / DMG / DEB 产物
- [ ] 拿到 MSI 后在 Windows 实机验证安装 / 升级 / 卸载（依赖上一条）

## 待办

### 0.1.x — 可玩性

- [ ] 老版本 natives / classifiers 下载（1.19 之前）
- [ ] `source-bmclapi` 等版本清单镜像源插件
- [ ] 断点续传：目前失败重试会整文件重下（大文件不友好）

### 0.2.x — 界面

- [ ] P5：窗口状态记忆、自定义标题栏、背景图主题、字体与 i18n
- [ ] 首次启动向导：装下载源 → 装版本 → 建实例

### 0.3.x — 插件生态

- [ ] `auth-microsoft`（设备码 / OAuth 流程）
- [ ] `download-modrinth` + `download-curseforge`
- [ ] `loader-fabric` / `loader-forge` / `loader-neoforge`（**只针对最新正式版系列维护**）
- [ ] 插件设置页（通用机制：插件自报设置项；Java 运行时来源选择已先落在应用设置页）
- [ ] 插件版本冲突、签名与来源校验

### 0.4.x — 打包发布

- [ ] 应用图标：设计 1024px 源图并导出 `.icns` / `.ico` / PNG，接入
      `nativeDistributions.macOS.iconFile`、Windows MSI、窗口/Dock 图标
      （目前用的是 Compose 默认图标）
- [ ] macOS 签名 + 公证：Developer ID Application 证书、`notarytool` 提交与 stapling（未签名版已可本地运行）
- [ ] 自动更新通道

### 1.0.0 — 稳定版

- [ ] `api` 冻结并承诺向后兼容（见 `ARCHITECTURE.md` §6.1）
- [ ] 崩溃上报与插件故障自愈
- [ ] 三平台真实用户验证

### 质量与工程

- [ ] ktlint / detekt 接入构建前置检查
- [ ] 插件版本自动化：`build.gradle.kts` 的 `version` 生成成资源，消除与 `Plugin.version` 的重复
- [ ] 插件加载测试覆盖循环依赖 / 缺失依赖 / 异常隔离的完整矩阵
- [ ] 独立 `CFBundleVersion`：jpackage 不支持，需打包后改 plist + 重签（自动更新前必须做）

## 阻塞 / 待用户决定

- [ ] Windows 注册表 / Linux 常见目录的 JDK 探测 —— 等用户决定要不要做
- [ ] Apple Developer 账号与 Developer ID 证书 —— 等用户提供，用于签名与公证

## 想法（未承诺）

- 插件市场（远程索引 + 一键安装）
- 插件沙箱与热重载
- 存档备份、日志分析等工具插件
