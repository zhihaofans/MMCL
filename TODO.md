# TODO

> 未完成的**唯一**来源。完成一项 = 从本文件删除 + 在 `CHANGELOG.md` 记一条（用户视角）。
> 版本号、发版、tag 一律先问用户；功能拆分与边界判断见 `AGENTS.md` 第 2 节。
> MMCL 坚持**插件化、不内置下载源**；游戏版本仍按 AGENTS：**最新正式版 + 同主版本系列**。下列「想法」不默认承诺。

## 进行中

- [ ] 拿到 MSI 后在 **Windows 11** 实机验证安装 / 升级 / 卸载（产物来自 GitHub Release 的 `MMCL-x.y.z.msi`）
  - 完成定义：干净机安装能开、覆盖安装不丢数据目录、卸载清掉开始菜单项

## 待办

### 0.2.x — 界面与上手（主题／向导／账号入口）

> `UI_DESIGN`：P1–P5 已完成；本段是上手路径（向导、账户区）。

- [ ] 高 DPI 真机验证：Windows 125%／150%／175%、macOS Retina、Linux 100%／125%（渲染测试已加 1.5 倍用例）

### 0.3.x — 插件生态（加载器／内容平台）

> 26.3 调研结论：Fabric 生态最大（Modrinth 10,799 项）且只需纯 JSON 合并；
> NeoForge/Forge 必须跑 installer 的 processor 生成 patched client jar。
> 接入顺序 **Fabric → NeoForge → Forge**；Quilt 不做（Loader 仍 beta、QSL 已停）。

**加载器前置（core，先做）**

- [ ] `inheritsFrom` 合并：libraries / arguments / mainClass / assetIndex / logging / client jar
- [ ] classpath 支持父版本 client jar（现在固定 `versions/<id>/<id>.jar`，加载器版本没有该文件）
- [ ] Maven 坐标库解析（Fabric profile 只有 `name`+`url`，没有 `downloads`）与空 URL 本地产物（Forge 的 patched client jar）
- [ ] Java 进程执行器：processor 的临时目录、日志、超时、退出码隔离
- [ ] `LoaderInstaller` API 重做：`listVersions` + `install` 返回 overlay/警告（0.x 允许，标 API-BREAK）
- [ ] Java 25 前置检查（26.x 的 `javaVersion.majorVersion=25`，安装与启动前都要查）

**加载器插件**

- [ ] `loader-fabric`（先做）：26.3 profile 纯 JSON（7 个库、无 natives、无 processor），
      补 loader 自身哈希；同时验证上面的 core 改造
- [ ] `loader-neoforge`：先 spike 验证 patched client jar 进 classpath；
      然后 installer processor、`-DlibraryDirectory`、`--fml.*` 参数
- [ ] `loader-forge`：复用 processor 引擎；promotions（files 主路径 + Maven 兜底）、
      83 MB 本地产物、`--launchTarget forge_client`
- [ ] `download-modrinth` + `download-curseforge`：按加载器＋游戏版本搜 mod／整合包
- [ ] 资源包／光影包安装入口（至少 Modrinth；CurseForge 视 API）

### 0.4.x — 整合包与内容互操作

- [ ] 导入：Modrinth／CurseForge／常见开源实例包格式（能建实例并拉依赖）
- [ ] 导出：至少一种可再导入的格式（优先 mrpack 或常见开源实例包格式；社区格式按需）
- [ ] 实例级「打开游戏目录／mods／resourcepacks／shaderpacks／saves」快捷入口

### 0.5.x — 打包发布

- [ ] 应用图标：设计 1024px 源图并导出 `.icns`／`.ico`／PNG，接入
      `nativeDistributions.macOS.iconFile`、Windows MSI、窗口／Dock 图标
      （目前用的是 Compose 默认图标）
- [ ] macOS 签名 + 公证：Developer ID Application 证书、`notarytool` 提交与 stapling（未签名版已可本地运行）
      - 现状：下载版被 Gatekeeper 拦（只有「移到废纸篓」）；临时绕过见 `AGENTS.md §9`
        （`xattr -dr com.apple.quarantine <app>`，只读文件报错可忽略）；本地构建产物不受影响
- [ ] 自动更新通道
- [ ] 可选：自定义 WiX `resource-dir`，安装时检测并卸载旧的 per-machine 版本（只影响 0.0.2 用户，先观察是否需要）
- [ ] Linux 发行形态写清范围：当前目标 deb；若要 AppImage／rpm／Flatpak 需单独拍板后再开项
- [ ] CI 冒烟：package workflow 成功后校验 Release 资产齐全（dmg/msi/deb 各一）
      （测试已在 CI 前置跑：JVM 测试在 Ubuntu，`:app:test` 在 macOS；见 `.github/workflows/package.yml`）

### 1.0.0 — 稳定版

- [ ] `api` 冻结并承诺向后兼容（见 `ARCHITECTURE.md` §6.1）
- [ ] 崩溃上报与插件故障自愈（先做本地分类＋复制报告）
- [ ] 三平台真实用户验证

### 质量与工程

- [ ] ktlint / detekt 接入构建前置检查
- [ ] 插件版本自动化：`build.gradle.kts` 的 `version` 生成成资源，消除与 `Plugin.version` 的重复
- [ ] 插件加载测试覆盖循环依赖／缺失依赖／异常隔离的完整矩阵
- [ ] 独立 `CFBundleVersion`：jpackage 不支持，需打包后改 plist + 重签（自动更新前必须做）
- [ ] Compose UI 点击／路由冒烟（`UI_DESIGN` 曾列，P4 后评估）
- [ ] 退出清理覆盖 macOS ⌘Q：⌘Q 走 Compose 的 `exitApplication()`，绕过 `onCloseRequest`，
      `manager.close()` 不执行（插件 `onDisable`、下载队列落盘都拿不到机会）；
      补 JVM shutdown hook 或菜单代理，保证退出路径都清理
      - 验收：⌘Q 与关窗两种退出，日志都有「插件 xxx 已禁用」且无未完成下载残留

## 阻塞 / 待用户决定

- [ ] Windows 注册表／Linux 常见目录的 JDK 探测 —— 要不要做；**若两版内未定，默认不做**
- [ ] Apple Developer 账号与 Developer ID 证书 —— 等用户提供，用于签名与公证
- [ ] OptiFine／Legacy Fabric 是否进入官方插件集（Quilt 已定不做）
- [ ] CurseForge「阻止第三方下载」条目的策略（跳过／官方页打开／自备 API 密钥）

## 想法（未承诺）

> 无用户点名且超过一个小版本未动 → 删除或移回待办并写清动机。

- 插件市场（远程索引 + 一键安装）
- 插件沙箱与热重载
- 存档备份、世界管理、服务器列表／快速加入
- 代理／镜像全局设置（HTTP／SOCKS）
- 启动器内置帮助文档
- 日志上传 mclo.gs（第三方粘贴站；需先定本地脱敏与确认流程，默认关闭）
- 更激进的中国网络优化（多 CDN 测速选路）
- 功能投票／讨论区驱动优先级
