# SH02LReader 交接：M2 日常阅读与存储管理

## 当前 M2（2026-10-19）

用户已授权 M2 并允许适当减少测试。版本 `0.3.1-m2` / code 6，沿用正式 ID 和签名；分支 `feature/m2-daily-use`，M1.5 未提交修订已保存为 `f6417b5` / tag `m1.5-v0.2.6`。不推送远端，不清空手机正式书库。

新增搜索/输入/百分比跳转、编码纠正、行距/边距/字体导入、图书和书签管理、封面移除、50 条一批的书架、存储占用及选择清理。删除图书联动私有原件/缓存/封面/进度/书签；import 来源单独选择，外部 Books/内存卡保留。编码纠正按阅读比例近似映射位置。数据库 v1→v2 增加索引，删除/重建有恢复目录，旧缓存渐进补充校验值；进度失败保留待重试并使用 256 KiB 实分配应急空间。

入口：`NavigationState` 保存页面/选择，`ReaderInputView` 负责原生输入，`ReadingSettings/FontStore` 管理排版和单份字体，`ReaderFeatures` 管理搜索/跳转/编码，`LibraryFeatures/StorageManager` 管理显式删除和空间；`DocumentSession/TxtDocumentSession` 提供 seek/move 边界。MainActivity 继续负责窗口和生命周期。删除还需失效该 id 的内存定位与异步进度代次，避免同 ID 重导入恢复被删除的进度。

执行清单见 [M2 计划](superpowers/plans/2026-10-19-m2-daily-reading.md)，候选与实际验证以 [M2 验证](M2_VALIDATION.md) 为准。中文系统输入法组合、真实来电/合盖及长期试读仍需用户反馈，M3 性能预算和 EPUB 未列为已完成。不重复 M1.5 主题问答或大规模视觉排列。

以下为 M1.5 历史交付记录。

最后更新：2026-10-19。M1.5 产品代码与正式包历史记录见 [M1.5 验证](M1_5_VALIDATION.md)。

2026-10-19 试读后追加共享存储权限修复：Android 8.1 进入手机存储时补申请缺少的读写权限，拒绝写入但已有读取权限时仍可复制。新增三项原生回归，旧实现均观察到对应失败；46 项 JVM、模拟器及 SH-02L 各 58 项完整原生回归、debug/release 构建与 Lint 通过。正式权限修复包已覆盖安装到手机，读写授权、Books 和应用导入目录的复制/移动选项、重启与当时原有 1 本书保留均验证通过；完整移动在独立测试包中验证。按用户明确确认，已卸载独立旧 AAReader 及应用数据，并清理 debug/test 包及该轮夹具，Books 两份原书保留。包位于 `dist/m1.5-storage-permission-20261019/`，版本号与证书沿用 M1.5，来源和验证见 [权限修复真机验证](M1_5_STORAGE_PERMISSION_VALIDATION.md)。

同日继续完成主题与列表反馈，版本升为 `0.2.6-m1.5` / code 4：白色采用纯白背景；米白使用暖纸色，调整配套文字和选中底色；统一列表在页面内移动选框，越过边界才滚动，帮助与父文件夹返回恢复条目和像素位置，也处理布局前连续按键。右侧预留 12 dp，滚动条改为 3 dp 圆角细条。46 项 JVM、模拟器与 SH-02L 各 65 项原生测试通过，正式包沿用证书覆盖安装；来源、真机状态和清理见 [主题与导航修订验证](M1_5_THEME_NAVIGATION_VALIDATION.md)。后续升级必须保留当前用户书库，不重复清空数据。

## 目录和版本

- 独立新工程：`F:\VscodeAnaconda\Codex\ProjectForCodex\AAReader\SH02LReader`。外层 `AAReader` 只是工作区容器，不是本工程 Git 根目录。
- 实现分支：`feature/m15-theme-ui`，工作树 `.worktrees/m15-theme-ui/`；已在本地快进合入 master，主工程根目录可直接构建；保留该工作树作为验证现场，没有推送远端。
- 旧 AAReader 在相邻 `..\AAReader`，没有修改；新工程只使用自己的工具目录。
- M1.5 当前：`0.2.6-m1.5` / versionCode 4；正式 ID `com.sh02l.reader`，测试 ID `com.sh02l.reader.debug`。
- 当前安装候选目录 `dist/v0.2.6-m1.5/`，APK、精确大小、哈希、签名、基线提交与未提交源码快照见 `build-info.json` / `SHA256SUMS.txt`。首版 `dist/v0.2.5-m1.5/` 保留。
- M1 原包保留于主 checkout 的 `dist/v0.2.0-m1/`，tag `m1-v0.2.0`；M0 同样保留。不要重写旧版本产物。
- 独立签名证书 SHA-256：`4d8b8a216c0f59faf78d271ca19ee6908e3c99729dcc5035aaa107feb1e17fbd`。

## 用户明确决定

采用第一版“柔和圆润”效果；移除左侧蓝条和列表横线。12 dp 圆角、14 dp 上下留白、6 dp 行间距；同级同样式行等高。长名称单行省略，仅选中且页面可见时滚动；覆盖、失焦和后台停止。不要再展示网页样稿或重复确认设计。

最初按键说明要求已实施：参考 SH-02L 实体分布绘制原生线稿，F1 左上、F3 左下、F2 右上、F4 右下；底部 I/II/III 保持系统键含义。方向盘与 2/5/8/0 同轴，数字和罗马键列边缘一致。0 反复开关，CENTER 不退出，F2/F4 只关闭帮助，↑↓ 滚动；恢复原菜单焦点及正文 View/锚点，后台导入继续。

M1.5 首版安装时，用户明确要求“把 M1 包括用户资料都给清理掉”，该次卸载与清理已完成，未删除共享存储来源 TXT/图片、电脑源码或旧发布包。后续试读修复采用覆盖安装，保留用户后来导入的书库；仅移除 debug/test 包和每轮自有夹具。

## M1.5 已实现

- 独立界面/正文主题与一次性旧 theme 迁移，米白、白色、暖炭灰。正文 5 键只改阅读颜色；设置分层、即时保存、逐层返回恢复焦点和列表位置。
- 纯文字、小书标、单列封面三种书架模式，默认小书标；继续阅读去重，统一等高选框与底部软键。
- 一个静态 JPEG/PNG/WebP 对应一本书的封面；统一 EXIF、居中裁剪 2∶3、保留原图、原子版本替换。输入 32 MiB 上限，工作位图 4 MiB，缓存 2 MiB，后台只加载可见及少量相邻行；缺失/损坏回退默认封面。
- TXT/封面共用应用文件夹、手机/内存卡、系统文件选择入口。默认复制；可核验且有删除权限的来源提供移动，持久提交并复核后才删除；不支持或删除失败准确保留来源。
- 导出原 TXT / 封面原图，保持原字节；说明卸载与清除数据会移除私有书库。
- 结束 Activity、取消/切换流程后的过时选择、授权、导入和导出回调不干扰新流程。

首版验证为 46 项 JVM、API 27 模拟器与 SH-02L 各 55 项原生测试；权限及主题导航修订后原生回归各 65 项通过，含三主题×三书架样式、帮助截图和七项列表回归。SH-02L 的 540×960 / 240 dpi / font_scale=1.3 保持原设置；没有更改全局字号、亮度或 MATVT 配置。升级、正式包恢复、交付 APK 与审查最终结果以最新验证报告为准。

## 代码入口

- `AppearanceState/Palette/AppearanceStore`：主题作用域和迁移；错误外观值仅回退相关设置，不清库。
- `NavigationListView/UiStyle/BookRowView/LibraryAdapter/SoftKeyBar`：统一边界滚动、几何、真实选择与 marquee 生命周期；避免另增焦点状态源。Screen 保存选中行像素偏移，包括尚未布局的目标偏移。
- `HelpLayout/KeyHelpView`：统一网格和 FontMetrics 原生绘制，不使用网页或捆绑大型位图。
- `MainActivity`：单窗口导航、overlay 和不可变来源目标；同一 `KeyRouter` 拥有整次 Down/Repeat/Up 周期，失焦/后台重置。
- `TextImporter/BookRepository`：流式原件、TXT v1 UTF-16 偏移、数据库 v1 及暂存发布。`ProgressWriter` 串行保存；`LatestWorker` 拒绝过时结果。
- `ImportCoordinator/AndroidImportSource/SpaceGuard`：身份、返回授权、内容、空间预算与提交后删除；只读不推断为可删除。
- `CoverStore/CoverDecoder/CoverLoader`：原件版本、方向、预算、可见行 token 校验。`OriginalExporter` 只导出原始字节。
- `TxtPaginator/PageView`：有界排版和纯绘制，最多三个约 8,192 单元窗口；菜单/帮助不改变正文尺寸。

## 后续 M2 / M3

M2：正文搜索与输入、进度跳转、编码纠正、书籍/书签管理、行距/边距/字体、损坏缓存恢复、书架数据分页和完整存储管理。输入态数字及方向键进入输入语义，不触发阅读快捷键；返回/取消保留原焦点及位置。共享存储权限拒绝/撤销与中文输入法仍需真实系统 UI 验证；M1 已有用户确认内存卡与系统选择器正常的记录，M1.5 增补了受控提供程序回归。

低存储必做：独立存储模块维护空间预算和应急空间；导入/索引前估算、过程中检查、只清理可重建临时缓存；进度失败保持待保存并在空间恢复后重试。故障注入覆盖导入耗尽、数据库提交失败、进度写入失败及恢复；不填满日常手机，不把 M1.5 的导入检查等同这项完成。彻底不可写时不能承诺新进度必然落盘。

M3：100 本书启动、翻页/列表延迟、正式包 10/50 MiB 稳态、真实来电、合盖/回收及异常写入。长期阅读由用户日常亲测，两小时为可累计参考，不安排 Codex 连续等待两小时。自动化回归、内存与耗时测量、日志定位由 Codex 完成；未获反馈时明确“待用户试读”。TXT 稳定后再做 EPUB。

MATVT-001 继续跟踪，见 [诊断记录](MATVT_DIAGNOSIS.md)。复发时立即暂停自动输入，先保存 crash/main/system/events、DropBox、窗口/Surface、时间和最近动作，再关闭弹窗与重测；不先清日志、重启或调整 MATVT。没有部署后台监控。

## 构建

AGP 8.6.1 / Gradle 8.7 / JDK 17 / compile-target 35 / min 27；无 NDK、AndroidX 或网络运行时依赖。

```powershell
.\scripts\build.ps1 -Task verify
.\scripts\test-device.ps1 -Serial <明确设备编号> -Label <标签>
.\scripts\build.ps1 -Task release
.\scripts\smoke-release.ps1 -Serial <明确设备编号> -Label <标签>
.\scripts\publish-local.ps1 -Version 0.2.6-m1.5
```

构建只设置进程环境。Windows JDK 原生堆地址冲突时，可在当前构建进程临时设置 `JAVA_TOOL_OPTIONS=-XX:HeapBaseMinAddress=4294967296`，结束恢复原值，不改全局 Java。测试不得与用户手测或同设备自动输入并行。正式冒烟只选脚本生成的两份 TXT；`-LegacyM1` 专供隔离升级前的旧 M1，无复制模式选择。
