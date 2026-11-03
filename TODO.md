# TODO

> 未完成的**唯一**来源。完成一项 = 从本文件删除 + 在 `CHANGELOG.md` 记一条（用户视角）。
> 版本号、发版、tag 一律先问用户；功能拆分与边界判断见 `AGENTS.md` 第 2 节。
> MMCL 坚持**插件化、不内置下载源**；游戏版本仍按 AGENTS：**最新正式版 + 同主版本系列**。下列「想法」不默认承诺。

## 进行中

- [ ] 拿到 MSI 后在 **Windows 11** 实机验证安装 / 升级 / 卸载（产物来自 CI 的 `MMCL-windows-msi`）
  - 完成定义：干净机安装能开、覆盖安装不丢数据目录、卸载清掉开始菜单项

## 待办

### 0.1.x — 可玩闭环（能下、能开、能修）

> 目标：新用户装完启动器 + 必要插件后，能下到最新正式版并启动；失败可重试／修复。

- [ ] `source-bmclapi`（或同类国内清单／资源镜像）插件：国内拉取版本清单与资源更稳（做成插件，不内置）

**明确不做（本阶段）**

- 1.19 之前等旧大版本 natives／全历史版本适配 —— 与 AGENTS「只维护最新正式版同系列」冲突；若要做须先改产品边界，再单开里程碑

### 0.2.x — 界面与上手（主题／向导／账号入口）

> `UI_DESIGN`：P1–P4 已完成；本段是 P5 + 上手路径。

- [ ] 高 DPI 真机验证：Windows 125%／150%／175%、macOS Retina、Linux 100%／125%（渲染测试已加 1.5 倍用例）
- [ ] P5：i18n 剩余页面迁移（壳层与设置页已完成；游戏/下载/插件/首页文案待迁移）
- [ ] 首次启动向导：装下载源 →（可选）装 Java 运行时来源 → 装版本 → 建实例
- [ ] 账户区：离线账户管理完善 + `auth-microsoft` 入口预留（设备码／OAuth 本体见 0.3.x）
- [ ] 顶栏／首页展示当前账户与皮肤预览（有微软账户后）

### 0.3.x — 插件生态（加载器／内容平台）

- [ ] `auth-microsoft`（设备码 / OAuth；刷新令牌存本地，失败可重登）
- [ ] `loader-fabric`／`loader-forge`／`loader-neoforge`（**只针对最新正式版系列**；Quilt 是否单独立项另议）
- [ ] `download-modrinth` + `download-curseforge`：按加载器＋游戏版本搜 mod／整合包
- [ ] 资源包／光影包安装入口（至少 Modrinth；CurseForge 视 API）
- [ ] 插件设置页（通用机制：插件自报设置项；Java 运行时来源已先落在应用设置）
- [ ] 插件版本冲突、签名与来源校验

### 0.4.x — 整合包与内容互操作

- [ ] 导入：Modrinth／CurseForge／常见开源实例包格式（能建实例并拉依赖）
- [ ] 导出：至少一种可再导入的格式（优先 mrpack 或常见开源实例包格式；社区格式按需）
- [ ] 实例级「打开游戏目录／mods／resourcepacks／shaderpacks／saves」快捷入口

### 0.5.x — 打包发布

- [ ] 应用图标：设计 1024px 源图并导出 `.icns`／`.ico`／PNG，接入
      `nativeDistributions.macOS.iconFile`、Windows MSI、窗口／Dock 图标
      （目前用的是 Compose 默认图标）
- [ ] macOS 签名 + 公证：Developer ID Application 证书、`notarytool` 提交与 stapling（未签名版已可本地运行）
- [ ] 自动更新通道
- [ ] Linux 发行形态写清范围：当前目标 deb；若要 AppImage／rpm／Flatpak 需单独拍板后再开项
- [ ] CI 冒烟：package workflow 成功后校验 artifact 存在；可选再跑 `:app:test`

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

## 阻塞 / 待用户决定

- [ ] Windows 注册表／Linux 常见目录的 JDK 探测 —— 要不要做；**若两版内未定，默认不做**
- [ ] Apple Developer 账号与 Developer ID 证书 —— 等用户提供，用于签名与公证
- [ ] Quilt／OptiFine／Legacy Fabric 是否进入官方插件集（默认可只做「最新系列」主流三加载器）
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
