# SH02LReader

面向翻盖按键手机的原生 Android 阅读器，目前在 SH-02L / Android 8.1 上验证。运行时仅使用系统框架。

M2.5 `0.4.0-m2.5` 已将代码结构优化、菜单/列表改进和 EPUB 合并交付，并覆盖安装到 SH-02L。F1 只打开所选书籍或阅读菜单，F2 导入 TXT / EPUB，F3 通用菜单，F4 返回/关闭；输入 F3 确认、F4 取消。可选列表首尾循环，危险操作末置并确认；提示和帮助图同步调整。

书库 schema、文件事务、导入导出、阅读控制与格式引擎已有独立边界。未来新增机型从配置映射逻辑键，新增格式实现统一的文档/阅读界面契约。维护入口见 [代码结构](docs/CODE_STRUCTURE.md)。

EPUB 支持无 DRM 的可重排 EPUB 2/3，包括目录、分页、内部链接及返回、搜索、书签、字号/主题/字体与持久化位置；不转成 TXT。固定版式、书内脚本、多媒体和加密资源不在本轮范围。具体边界见 [M2.5 变更说明](docs/M2_5_RELEASE_NOTES.md)，集中验证及交付状态见 [M2.5 验证](docs/M2_5_VALIDATION.md)。

按用户约定只做一个整合版本，开发检查按风险选择，正式书库与 Books 原件保留。M3 综合性能/耐久验收根据试读情况安排；历史 [M2 验证](docs/M2_VALIDATION.md) 保留，最新交接见 [HANDOFF](docs/HANDOFF.md)。

| 路径 | 内容 |
| --- | --- |
| [docs/SH02L_READER_PLAN.md](docs/SH02L_READER_PLAN.md) | 技术方案、默认按键、阶段交付与性能目标 |
| [M1.5 实现计划](docs/superpowers/plans/2026-10-18-m1-5-theme-ui.md) | 8 组实现任务与验证证据 |
| [M1.5 变更说明](docs/M1_5_RELEASE_NOTES.md) | 本轮功能、升级与存储边界 |
| [M1.5 主题与 UI 设计基线](docs/superpowers/specs/2026-10-18-m1-5-theme-design.md) | 已确认的问答、设备约束、主题/书架/封面/帮助设计与首版样稿 |
| [docs/SH02L_ACCEPTANCE.md](docs/SH02L_ACCEPTANCE.md) | 自动测试、模拟器与 SH-02L 真机验收清单 |
| [device/sh02l按键值.txt](device/sh02l按键值.txt) | 独立的 SH-02L 真机按键资料副本 |
| [tools/README.md](tools/README.md) | 新工程构建工具的目录约定 |
| [docs/M0_VALIDATION.md](docs/M0_VALIDATION.md) | M0 交付范围、测试证据、性能基线与待验证事项 |
| [docs/M1_VALIDATION.md](docs/M1_VALIDATION.md) | M1 内容连续性、恢复、规模测试与已知限制 |
| [docs/TXT_STORAGE.md](docs/TXT_STORAGE.md) | TXT 数据格式、定位、后台任务与故障边界 |

本目录是 Android 源码工程根目录，包含 `app/`、`reader-core/`、`reader-android/`、`engine-txt/`、`engine-epub/` 和 Gradle Wrapper。运行时仅依赖 Android 系统框架，不引入网络书源或常驻服务。

构建与测试（PowerShell，项目根目录）：

```powershell
.\scripts\build.ps1 -Task verify
.\scripts\build.ps1 -Task release
.\scripts\test-device.ps1 -Serial emulator-5554 -Label api27-emulator
.\scripts\smoke-release.ps1 -Serial emulator-5554 -Label emulator
.\scripts\publish-local.ps1
```

`verify` 执行 JVM 测试、Android Lint，并构建 debug 与原生测试 APK。`test-device.ps1` 接受明确的 ADB 设备编号，先检查设备已唤醒，再安装独立的 debug/test 包。正式应用 ID 为 `com.sh02l.reader`，debug 为 `com.sh02l.reader.debug`。

当前安装候选位于 `dist/v0.4.0-m2.5/`，附签名、校验值、来源提交、变更说明与验证记录。`test-device.ps1` 可用 `-TestClass` 选择原生测试类或方法，减少重复全量回归；正式用户数据不得清空。诊断记录可在应用“按键诊断 → 保存诊断记录”导出，文件位置在诊断页显示。

使用：确认打开图书；正文 ↑/← 上一页，↓/→/确认下一页。F1 书籍/阅读菜单，F2 导入，F3 通用菜单，F4 返回。2 目录，1/3 切章，4/6 字号，5 配色，7/9 窗口亮度，8 信息栏，＊设置，＃书签，0 开关帮助。输入 F3 确认、F4 取消；帮助中 ↑↓ 滚动，0/F4 关闭，确认键不退出。根层 F4 无操作，退出位于 F3 菜单并需确认。

共享存储：SH-02L（Android 8.1）进入“手机存储 / 内存卡”时会补申请缺少的读写权限；已有读取权限但未获写入权限时仍可复制导入。移动还需来源文件及所在目录可写，提交和完整校验成功后才删除选中的原文件；内存卡的实际权限仍以系统为准。Android 10 及以上不申请旧式写权限，系统文档入口按文件授权判断能否移动。

2026-10-19 权限修复包已覆盖安装到 SH-02L，58 项真机原生回归通过，Books 移动与应用导入目录验证、旧 AAReader 和测试残留清理完成；原有书架和 Books 原书保留。详见 [权限修复真机验证](docs/M1_5_STORAGE_PERMISSION_VALIDATION.md)。

应用导入文件夹：正式版为 `/storage/emulated/0/Android/data/com.sh02l.reader/files/import/`（也可使用 `/sdcard/Android/data/com.sh02l.reader/files/import/`）。先打开一次 F2 导入页以创建目录，再通过支持指定目标路径的传输工具放入 TXT、EPUB 或封面图片；放入后在“应用导入文件夹”中手动选择，不会自动加入书架。复制保留来源，移动成功后删除来源。此目录属于应用专属存储，卸载时移除；公共 `Books` 目录可继续用于传书和保留原文件。

设备资料、工具、缓存、文档和发布产物均在本目录内维护。旧工程保留于相邻的 `../AAReader/`；本工程不通过旧工程路径加载运行或构建依赖。
