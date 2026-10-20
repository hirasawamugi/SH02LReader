# SH02LReader

面向 SH-02L 的原生 Android 阅读器新工程。优先保证按键操作、稳定性、轻量与模块扩展能力；已确定先完成本地 TXT 阅读，随后加入 EPUB。

当前候选为 M2（`0.3.1-m2`）：在 M1.5 基础上增加正文搜索、百分比跳转、编码纠正、行距/边距/字体、书籍与书签管理、封面移除、分页书架和存储管理。可确认删除私有图书、选择清理 import 和回收可重建缓存，保留外部来源文件；数据库覆盖升级保留用户书库。新增输入使用系统原生控件，F3 确认、F2/F4 取消。功能与限制见 [M2 变更说明](docs/M2_RELEASE_NOTES.md)，本轮实际验证见 [M2 验证](docs/M2_VALIDATION.md)。

M1.5 的原有主题、列表与封面功能继续保留；历史记录见 [主题与导航修订验证](docs/M1_5_THEME_NAVIGATION_VALIDATION.md)。2026-10-20 路线调整：新增 M2.5 做代码/功能优化并融合原 M4 的 EPUB 接入，再按情况进行 M3 综合验收。下一步审阅 [M2.5 设计草案与优化候选](docs/superpowers/specs/2026-10-20-m2-5-optimization-epub-design.md)，当前 APK 与既有验证结果保持为 M2；最新交接见 [HANDOFF](docs/HANDOFF.md)。

M2.5 按用户要求一次整合交付，不拆内部版本；开发使用针对性检查，整合后集中构建、关键回归与升级验证，减少重复计划、审阅和测试。

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

本目录是 Android 源码工程根目录，包含 `app/`、`reader-core/`、`engine-txt/` 和 Gradle Wrapper。运行时仅依赖 Android 系统框架，不引入网络书源或常驻服务。

构建与测试（PowerShell，项目根目录）：

```powershell
.\scripts\build.ps1 -Task verify
.\scripts\build.ps1 -Task release
.\scripts\test-device.ps1 -Serial emulator-5554 -Label api27-emulator
.\scripts\smoke-release.ps1 -Serial emulator-5554 -Label emulator
.\scripts\publish-local.ps1
```

`verify` 执行 JVM 测试、Android Lint，并构建 debug 与原生测试 APK。`test-device.ps1` 接受明确的 ADB 设备编号，先检查设备已唤醒，再安装独立的 debug/test 包。正式应用 ID 为 `com.sh02l.reader`，debug 为 `com.sh02l.reader.debug`。

当前安装候选位于 `dist/v0.3.1-m2/`，附签名、校验值、来源提交、变更说明与验证记录。`test-device.ps1` 可用 `-TestClass` 选择原生测试类或方法，减少重复全量回归；正式用户数据不得清空。诊断记录可在应用“按键诊断 → 保存诊断记录”导出，文件位置在诊断页显示。

使用：书架 F3 导入，确认打开图书；正文 ↑/← 上一页，↓/→/确认下一页，F1/F3 菜单，F2 保持，F4 书架。2 目录，1/3 切章，4/6 字号，5 主题，7/9 应用亮度，8 信息栏，＊设置，＃书签，0 开关帮助，帮助中 CENTER 不退出。

共享存储：SH-02L（Android 8.1）进入“手机存储 / 内存卡”时会补申请缺少的读写权限；已有读取权限但未获写入权限时仍可复制导入。移动还需来源文件及所在目录可写，提交和完整校验成功后才删除选中的原文件；内存卡的实际权限仍以系统为准。Android 10 及以上不申请旧式写权限，系统文档入口按文件授权判断能否移动。

2026-10-19 权限修复包已覆盖安装到 SH-02L，58 项真机原生回归通过，Books 移动与应用导入目录验证、旧 AAReader 和测试残留清理完成；原有书架和 Books 原书保留。详见 [权限修复真机验证](docs/M1_5_STORAGE_PERMISSION_VALIDATION.md)。

应用导入文件夹：正式版为 `/storage/emulated/0/Android/data/com.sh02l.reader/files/import/`（也可使用 `/sdcard/Android/data/com.sh02l.reader/files/import/`）。先打开一次 F3 导入页以创建目录，再通过支持指定目标路径的传输工具放入 TXT 或封面图片；放入后在“应用导入文件夹”中手动选择，不会自动加入书架。复制保留来源，移动成功后删除来源。此目录属于应用专属存储，卸载时移除；公共 `Books` 目录可继续用于传书和保留原文件。

设备资料、工具、缓存、文档和发布产物均在本目录内维护。旧工程保留于相邻的 `../AAReader/`；本工程不通过旧工程路径加载运行或构建依赖。
