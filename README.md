# SH02LReader

面向 SH-02L 的原生 Android 阅读器新工程。优先保证按键操作、稳定性、轻量与模块扩展能力；已确定先完成本地 TXT 阅读，随后加入 EPUB。

M0 原生按键与焦点原型已完成。已通过 24 项 JVM 测试、Android 8.1 模拟器及 SH-02L 各 6 项原生界面测试，正式签名包已在 SH-02L 完成 20 次冷启动；用户完成实体按键序列并反馈正常。接下来实现 M1 的真实 TXT 阅读模块。

| 路径 | 内容 |
| --- | --- |
| [docs/SH02L_READER_PLAN.md](docs/SH02L_READER_PLAN.md) | 技术方案、默认按键、阶段交付与性能目标 |
| [docs/SH02L_ACCEPTANCE.md](docs/SH02L_ACCEPTANCE.md) | 自动测试、模拟器与 SH-02L 真机验收清单 |
| [device/sh02l按键值.txt](device/sh02l按键值.txt) | 独立的 SH-02L 真机按键资料副本 |
| [tools/README.md](tools/README.md) | 新工程构建工具的目录约定 |
| [docs/M0_VALIDATION.md](docs/M0_VALIDATION.md) | M0 交付范围、测试证据、性能基线与待验证事项 |

本目录是 Android 源码工程根目录。目前包含 `app/`、`reader-core/` 和 Gradle Wrapper，TXT 引擎在 M1 加入。M0 提供阅读样例、菜单、设置、样例目录/书签、按键诊断与本地日志保存；真实图书导入和分页属于后续 TXT 阶段。

构建与测试（PowerShell，项目根目录）：

```powershell
.\scripts\build.ps1 -Task verify
.\scripts\build.ps1 -Task release
.\scripts\test-device.ps1 -Serial emulator-5554 -Label api27-emulator
.\scripts\publish-local.ps1
```

`verify` 执行 JVM 测试、Android Lint，并构建 debug 与原生测试 APK。`test-device.ps1` 接受明确的 ADB 设备编号，先检查设备已唤醒，再安装独立的 debug/test 包。正式应用 ID 为 `com.sh02l.reader`，debug 为 `com.sh02l.reader.debug`。

发布包输出到 `dist/v0.1.0-m0/`，附签名、校验值与来源记录。诊断记录可在应用“按键诊断 → 保存诊断记录”导出，文件位置在诊断页显示。

设备资料、工具、缓存、文档和发布产物均在本目录内维护。旧工程保留于相邻的 `../AAReader/`；本工程不通过旧工程路径加载运行或构建依赖。
