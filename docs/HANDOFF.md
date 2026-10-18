# SH02LReader 交接：M1 已收尾，下一对话开始 M2

最后更新：2026-10-18。用户明确要求本轮暂时收尾，下一对话再开始 M2；本轮没有启动 M2 实现。

## 目标和目录

由 Codex 实现面向 SH-02L 的阅读器，优先实体按键、稳定、轻量和可扩展。先把本地 TXT 做稳，EPUB 留到 TXT 稳定后。不要要求用户自行整合开源工程或调试。

- 新源码根目录：`F:\VscodeAnaconda\Codex\ProjectForCodex\AAReader\SH02LReader`，独立 Git 仓库。
- 旧 AAReader 已隔离到相邻 `..\AAReader`，不要改旧项目，不从旧工具目录加载新工程依赖。
- 工作区默认 cwd 可能仍是外层 `AAReader`；构建时明确使用新工程根目录。
- 完整方案：`docs/SH02L_READER_PLAN.md`；验收清单：`docs/SH02L_ACCEPTANCE.md`；源码参考及许可：`NOTICE.md`。

## 当前可交付版本

| 项目 | 值 |
| --- | --- |
| 版本 | `0.2.0-m1`，versionCode 2 |
| 应用 ID | 正式 `com.sh02l.reader`；测试 `com.sh02l.reader.debug` |
| APK | `dist/v0.2.0-m1/SH02LReader-v0.2.0-m1.apk` |
| 大小 | 40,724 字节 |
| SHA-256 | `d7874e8c45a4625414392111c2dc07bbcf47fb48d63e4ef9a85ad116adee1361` |
| 对应代码提交 | `0aac0b1440729f0ac9c1736fdd4c03625ab5d90f`，tag `m1-v0.2.0` |
| 旧基线 | tag `m0-v0.1.0`，提交 `edaea58`，APK 保留在 `dist/v0.1.0-m0/` |
| 签名证书 SHA-256 | `4d8b8a216c0f59faf78d271ca19ee6908e3c99729dcc5035aaa107feb1e17fbd` |

正式包已覆盖安装到 SH-02L，设备上 APK 的 SHA-256 与交付包一致。书架中有两份原创验证读本；用户可以继续试读。收尾文档提交在代码提交之后，APK 来源仍以 `build-info.json` 和内嵌 VCS 信息的 `0aac0b1` 为准。

已完成：真实 TXT 导入、原始副本/去重、编码识别、虚拟化书架和文件列表、无动画分页、正文位置保存/恢复、自动目录与章节跳转、基础书签、字号/主题/窗口亮度/信息栏、帮助和诊断。M0 真实显示设置可迁移，样例页号不作为真实位置。

## 验证和设备状态

- 33 项 JVM 测试，API 27 模拟器与 SH-02L 各 12 项原生测试全部通过。
- 三种字号逐页拼接 25,271 个 UTF-16 单元，每个环境合计 760 页，逻辑连续无重复，反向页范围一致。
- 1/10 MiB GB18030 和 50 MiB UTF-8 无换行文件完成导入与排版，无 OOM；50 MiB 一次测试进程快照 PSS 28,100 KiB。不是峰值或正式稳态性能认证。
- 正式包 UTF-8/GB18030 导入和“等待提交 → 前台强制停止 → 重开”在两环境通过，前后 ID/偏移一致。
- 用户已完成 M0 和 M1 手测。M1 回复：“翻页、菜单和恢复都正常”。M1 留存 46 条实体事件，全部消费，F1—F4 Down/Up 均观察到；本轮没有 repeat>0，长时间 Repeat 仍留待 M3 专项测试。
- 真机：SH-02L / Android 8.1.0 / API 27，540×960，240 dpi，系统 font_scale=1.3，ABI `armeabi-v7a,armeabi`。手机仍可日常使用，没有修改其全局亮度/字号。
- 本任务创建的 `sh02l-api27` 模拟器已关闭，AVD 保留在项目 `tools/avd/`；ADB 服务保留。下次重新读取 `adb devices`，不要把设备序列号写入源码文档。
- 手机上的 debug/test 包已卸载，其压力测试数据随之移除。收尾补查发现根目录截图及应用目录中的截图/XML/导入副本共 8 个、654,162 字节，已校验备份后删除；备份和清单位于 `tools/logs/device-cleanup-20261018/`。正式书架的两份验证读本、阅读位置和设置保留，`diagnostics/latest.txt` 是应用正常诊断文件，仍会在正常使用时更新。
- 电脑端 SDK、Gradle 缓存、AVD、构建输出与测试记录保留在工程内，供下次 M2 使用；它们没有被作为手机测试残留清除。
- 一次正式包测试被 MATVT 鼠标工具崩溃弹窗挡住，按 Android Back 关闭后重测通过；没有禁用或卸载该工具。
- SH-02L 的无障碍界面树可能把底部报告为裁切；实际截图和用户观察均完整，不能据此硬编码 96 px 屏幕补丁。

完整记录见 `docs/M0_VALIDATION.md`、`docs/M1_VALIDATION.md`。原始记录在忽略目录 `tools/logs/`，正式包附 `VALIDATION.md`、`TEST_RESULTS.json`、签名、校验与原生测试摘要。

## 代码入口与约束

- `reader-core/.../KeyRouter.java`：完整按键周期、上下文归属、重复节流；失焦/后台重置。不要拆成互相冲突的多个全局按键监听器。
- `app/.../MainActivity.java`：单窗口导航、ListView 选择、菜单、异步会话。列表控制器的选择必须与可见高亮和真正确认对象一致，避免异步焦点回调改回旧选择。
- `reader-core/.../text/TextImporter.java`：流式复制、编码、归一化和索引；可复用其 override 参数实现后续编码纠正。
- `engine-txt/.../TxtPaginator.java`：有界 StaticLayout 排版；`app/.../PageView.java` 只绘制。最多三个约 8,192 单元窗口，末尾可能有不足一屏的页。
- `BookRepository`：SQLite v1 书架/书签、临时目录发布；`ProgressWriter`：串行合并位置；`LatestWorker`：一个运行、一个候选请求，代号拒绝过时结果。
- TXT 永久位置为归一化 UTF-16 偏移 v1，不是页号。见 `docs/TXT_STORAGE.md`。EPUB 将使用独立定位并做显式数据库迁移。
- 编码纠正需处理缓存原子替换和已有位置/书签，不能直接覆盖正文后复用全部旧偏移。删除书籍只操作私有副本，不能删除用户来源文件。
- 当前索引损坏会提示返回，尚未提供自动修复；完整孤立目录恢复、搜索、进度输入、编码纠正、书籍/书签管理和更多排版属于后续。

## 下次 M2 的范围

按计划补齐正文搜索与输入、进度跳转、编码纠正、书籍/书签管理、行距/边距/字体、损坏缓存恢复，并实测共享存储权限、内存卡、系统文件选择和中文输入法。输入时数字与方向键必须进入输入语义，不能同时触发阅读快捷键；所有返回和取消需恢复原焦点与位置。

新增功能同时回归取消、快速切书/重排、目录与书签定位。正式稳定版 M3 的两小时阅读、100 本书启动、翻页/列表延迟、真实来电与异常写入仍未完成，不能直接把 M1 称为完整 TXT 稳定版。

## 构建和发布

工具链为 AGP 8.6.1 / Gradle 8.7 / JDK 17 / compile-target 35 / min 27，无 NDK、AndroidX 或网络运行时依赖。项目工具与 SDK 位于 `tools/`，复用本机已安装 JDK 17，构建脚本只设置进程环境。不要改全局 Java。

```powershell
.\scripts\build.ps1 -Task verify
.\scripts\build.ps1 -Task release
.\scripts\test-device.ps1 -Serial <明确设备编号> -Label <标签>
.\scripts\smoke-release.ps1 -Serial <明确设备编号> -Label <标签>
.\scripts\publish-local.ps1
```

`test-device` 安装并操作独立 debug/test 包；运行时会唤醒设备。不要与用户正在进行的手测或同一设备上的其他自动输入并行。`smoke-release` 只适用于应用导入目录含两份生成夹具的情况，避免选到用户的其他文件。

发布脚本要求干净的已提交源码，验证签名/版本/对齐，并保护已有版本不可被不同 APK 覆盖。下一阶段改代码时递增版本，不重写 `v0.2.0-m1`。签名凭据在被忽略的 `tools/signing/`，需保留，不输出密码、不提交到 Git。缓存和 SDK、日志、发布包均被忽略，源码和文档已提交。
