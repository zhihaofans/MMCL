# TODO

> 未完成的**唯一**来源。完成一项 = 从本文件删除 + 在 `CHANGELOG.md` 记一条（用户视角）。
> 版本号、发版、tag 一律先问用户；功能拆分与边界判断见 `AGENTS.md` 第 2 节。

## 进行中

- [ ] 游戏日志面板：把游戏输出流到界面（目前只写日志与内部回调）
      验收：启动后界面能实时看到最后 N 行、退出码可见、不卡 UI

## 待办

### 0.1.x — 能启动游戏

- [ ] 离线账户插件（`auth-offline`，正式替代内置占位）
- [ ] 正版账户（`auth-microsoft`，见 0.3.x）
- [ ] 下载失败重试与限速
- [ ] 老版本 natives / classifiers 下载（1.19 之前）
- [ ] `source-bmclapi` 等镜像源插件
- [ ] Java 运行时下载（解压到 `<dataDir>/runtime/<os>-<arch>/`，地址由运行时来源插件提供）

### 0.2.x — 界面

- [ ] P3：首页 / 实例页接 core 数据服务（依赖 0.1.x 的实例与启动链）
- [ ] P4：`UiWidget` / `UiSlot`（顶栏、状态栏、首页卡片）

### 0.3.x — 插件生态

- [ ] `auth-microsoft`（设备码 / OAuth 流程）
- [ ] `download-modrinth` + `download-curseforge`
- [ ] `loader-fabric` / `loader-forge` / `loader-neoforge`（**只针对最新正式版系列维护**）
- [ ] 插件设置页
- [ ] 插件版本冲突、签名与来源校验

### 0.4.x — 打包发布

- [ ] Windows 安装包（MSI/EXE，目标 Windows 11）与 Linux 发行包（deb/AppImage）
- [ ] macOS `.app` / `.dmg`（仅 arm64），Developer ID 签名 + 公证
- [ ] 自动更新通道
- [ ] 首次启动向导

### 1.0.0 — 稳定版

- [ ] `api` 冻结并承诺向后兼容（见 `ARCHITECTURE.md` §6.1）
- [ ] 崩溃上报与插件故障自愈
- [ ] 三平台真实用户验证

### 质量与工程

- [ ] ktlint / detekt 接入构建前置检查
- [ ] 插件版本自动化：`build.gradle.kts` 的 `version` 生成成资源，消除与 `Plugin.version` 的重复
- [ ] 插件加载测试覆盖循环依赖 / 缺失依赖 / 异常隔离的完整矩阵
- [ ] 下载失败重试的用例：断网中途恢复、SHA-1 不符后重试成功

## 阻塞 / 待用户决定

- [ ] 资源对象 `assets/objects/<hash>` 下载 —— 需要来源插件提供资源基址（等接口设计）
- [ ] 运行时下载协议 —— 需要运行时来源插件（等协议设计，地址同样不能内置）
- [ ] Windows 注册表 / Linux 常见目录的 JDK 探测 —— 等用户决定要不要做

## 想法（未承诺）

- 插件市场（远程索引 + 一键安装）
- 插件沙箱与热重载
- 存档备份、日志分析等工具插件
