# SH02LReader

面向 SH-02L 的原生 Android 阅读器新工程。优先保证按键操作、稳定性、轻量与模块扩展能力；已确定先完成本地 TXT 阅读，随后加入 EPUB。

当前版本为 M1（`0.2.0-m1`）：真实 TXT 导入、书架、分页、位置保存/恢复，已接入目录和基础书签。33 项 JVM 测试通过，Android 8.1 模拟器与 SH-02L 各 12 项原生测试通过，含 1/10/50 MiB 文件。M0 实体按键已由用户手测确认；完整 TXT 稳定版仍按 M2/M3 推进。

| 路径 | 内容 |
| --- | --- |
| [docs/SH02L_READER_PLAN.md](docs/SH02L_READER_PLAN.md) | 技术方案、默认按键、阶段交付与性能目标 |
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

发布包输出到 `dist/v0.2.0-m1/`，附签名、校验值与来源记录。`smoke-release.ps1` 在已安装正式包的明确设备上生成两份验证文本，通过按键导入并比较前台强制停止前后的已提交位置；要求应用导入文件夹只含这两份夹具，避免选到其他文件。诊断记录可在应用“按键诊断 → 保存诊断记录”导出，文件位置在诊断页显示。

使用：书架 F3 导入，确认打开图书；正文 ↑/← 上一页，↓/→/确认下一页，F1/F3 菜单，F2 保持，F4 书架。2 目录，1/3 切章，4/6 字号，5 主题，7/9 应用亮度，8 信息栏，＊设置，＃书签，0 帮助。

设备资料、工具、缓存、文档和发布产物均在本目录内维护。旧工程保留于相邻的 `../AAReader/`；本工程不通过旧工程路径加载运行或构建依赖。
