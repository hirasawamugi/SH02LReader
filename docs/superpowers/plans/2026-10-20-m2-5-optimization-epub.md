# M2.5 结构优先、交互优化与 EPUB 整合实施任务表

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan inline. Steps use checkbox (`- [ ]`) syntax for tracking. 用户要求精简计划与验证，按本会话连续实施安排，不设置逐任务用户确认或逐任务独立审查。

**Goal:** 优先降低代码维护成本，完成约定的菜单、循环列表、多机型按键边界、EPUB 和帮助排版，交付一个整合候选。

**Architecture:** 机型映射、页面动作与提示共用规则；应用协调、书库持久化、原件/缓存及格式会话分离。保留现有 TXT 的内容和定位语义，以统一格式定位及阅读界面适配器接入 EPUB。

依赖方向：`reader-core` 不依赖 Android；新增轻量 `reader-android` 只承载 Android 阅读界面契约与样式快照，并依赖 core；TXT/EPUB 引擎依赖上述公共层；app 负责组合。引擎不反向依赖 app。

**Tech Stack:** Java/Android 原生 UI，minSdk 27、compile/target 35，AGP 8.6.1、Gradle 8.7、JDK 17。EPUB 后端在工作项 2 的兼容性筛选后锁定，新的工具或依赖保存在项目内。

**Spec:** [已确定的设计与成本约定](../specs/2026-10-20-m2-5-optimization-epub-design.md)。本文是一次迭代的工作项，不是多个版本；应用行为以该设计第 4 节为准。

## Global Constraints

- 结构优化是首要工程目标；先整理公共职责、规则、依赖和状态归属，功能通过新边界实现。
- M2.5 只交付一个整合版本；拟用 `0.4.0-m2.5` / versionCode 7，沿用 `com.sh02l.reader` 和原签名。独立测试包继续为 `com.sh02l.reader.debug`。
- F1 书籍/阅读菜单、F2 导入、F3 通用菜单、F4 返回；输入 F3 确认/F4 取消，具体上下文与排序复用设计表。
- 每次改功能同步维护底栏、按钮、任务提示及帮助图文，候选交付前统一改善帮助页排版。
- SH-02L 的键值以 `device/sh02l按键值.txt` 为依据，当前屏幕 540×960、240 dpi、font_scale=1.3；不调整系统字号、亮度、输入法或 MATVT 来迁就应用。
- 不清空正式应用数据，不改写 Books/内存卡原件，不重写历史发布包，不推送远端。
- 开发只补/跑相关风险用例，整合后一轮构建/Lint/关键回归、一次整体审查和一次正式升级冒烟；新失败与后续修改只复核关联项目，必要时扩大。
- 不运行 M3 完整压力、规模矩阵或两小时等待；不在模拟器和手机重复完整组合。

## Review Focus

1. 旧 TXT 的 ID、进度、书签和设置升级后保留；由 `M25DataMigrationTest.testV2LibraryPreservesTxtLocations` 固定真实数据库迁移行为。
2. 换菜单、输入、布局或机型映射后，旧按键/异步结果不落到新上下文；由 `DeviceKeyMapTest`、`KeyRouterTest` 与 `M25InteractionTest.testMenuSwitchAndInputOwnership` 覆盖。
3. 删除后同 ID 重导入、已出队进度、缓存清理继续保持正确；复用 `LibraryManagementTest`、`StorageManagementTest` 对应既有用例并适配格式定位。
4. EPUB 的归档路径、内部链接、资源访问与定位隔离；由 `EpubIntegrationTest.testResourceBoundaryAndInternalLinks` 和 `testReflowAndReopenLocation` 覆盖，不仅验证能打开文件。
5. 系统中文输入候选、旧 WebView 排版、实际字号下四键提示和帮助可读性；由同批真机输入检查与两张代表性截图验证，故障时保留日志先定位。

## 准备记录（2026-10-20）

- 本地实施分支：`feature/m25-optimization-epub`，起点 `4ab0c7a`；复用当前 checkout 和工具缓存，不新建 SDK/Gradle 副本。
- 已通过 USB 识别 SH-02L，ADB serial `357776091146625`；Android 8.1.0，ABI `armeabi-v7a,armeabi`，当前 Awake。
- 设备 WebView `com.android.webview 67.0.3396.127`；这是兼容性筛选输入，尚无本轮 EPUB 运行通过结论。
- 已安装正式 `0.3.1-m2` / code 6，读写权限 granted；拉取安装 APK 的 SHA-256 与既有发布包一致：`dd065bfd81d99d8074537b6f6597f4353c79b26866d7c468485367a3bebaaf3e`，86,692 字节。
- 证据：`tools/logs/m25-device-preflight-20261020.txt`、`m25-installed-baseline.json`、`m25-books-before.txt`。未安装新测试包，未启动正式应用或修改其数据。
- 官方候选资料：[Readium 工具包及版本要求](https://github.com/readium/kotlin-toolkit#minimum-requirements)、[CoolReader 引擎与依赖](https://github.com/buggins/coolreader#external-dependencies)。官方要求只用于静态筛选，不能代替此设备的运行证据。

## 工作项 1：公共按键、动作与选择规则

**Files:** 修改 `reader-core/src/main/java/com/sh02l/reader/core/{Key,KeyRouter,Action,ReaderModel}.java`；新增同包 `DeviceKeyMap.java`、`DeviceProfiles.java`、`ListSelection.java`、`UiAction.java`；新增对应 `DeviceKeyMapTest.java`、`ListSelectionTest.java`，调整既有 `KeyRouterTest.java`。

**Interfaces:** `DeviceKeyMap.normalize(int keyCode, int scanCode): Key`、`label(Key): String`、`id(): String`；`DeviceProfiles.select(String manufacturer, String model, String overrideId): DeviceKeyMap`；`KeyRouter(Target, DeviceKeyMap)` 保留整次物理按键归属；`ListSelection.move(int selected, int delta, int count, IntPredicate selectable): int` 无可选项时返回 -1。`UiAction` 的稳定 ID、标题、重复策略供后续菜单、动作解析和提示共用。

- [x] 先补针对性失败用例：替代键码归一化到同一逻辑键、UNKNOWN 的配置内 scanCode 回退、不同物理键不共用周期；列表 `0↑→末项`、`末项↓→0`、跳过不可选项及空/单项行为。只运行这些 JVM 用例确认缺口。
- [x] 将 SH-02L 硬件常量移入配置，Key 保留逻辑身份；输入分派与 KeyRouter 使用同一映射实例。为未来选择/覆盖设置提供版本化存取边界，迁移生产共用的列表/动作规则，清理原型重复实现并保留仍需的历史设置读取。
- [x] 运行关联 JVM 用例确认预期，记录结果；不在此工作项另做完整手机回归或候选发布。

## 工作项 2：书库、阅读会话与协调职责

**Files:** 修改 `reader-core/.../DocumentSession.java`；新增同包 `DocumentLocation.java`、`DocumentCapability.java`、`ContentEntry.java`、`SearchBatch.java`。新增 `reader-android/build.gradle`、`reader-android/src/main/AndroidManifest.xml` 与 `reader-android/src/main/java/com/sh02l/reader/android/{ReadingSurface,ReadingStyle}.java`，调整 settings 与模块依赖。整理 `app/.../{BookRepository,ProgressWriter,ReaderSession,FeatureHost,MainActivity,ReaderFeatures}.java`，新增 `LibraryDatabase.java`、`BookFileStore.java`、`DocumentImportRegistry.java`、`DocumentEngineRegistry.java`、`ReadingController.java`、`ImportExportController.java`；适配 `engine-txt/.../TxtDocumentSession.java`，将 `app/.../PageView.java` 的 TXT 显示职责移入 TXT 阅读界面适配器。`...` 表示相应模块当前 `src/main/java/com/sh02l/reader/` 包路径。

**Interfaces:** `DocumentLocation(String format, int version, String payload)` 为不可变格式定位，TXT payload 保留原 UTF-16 offset；`DocumentSession` 提供 `format(): String`、`capabilities(): Set<DocumentCapability>`、`contents(): List<ContentEntry>`、`search(String, DocumentLocation, int, BooleanSupplier): SearchBatch`、`locationAt(double): DocumentLocation`、`progressionAt(DocumentLocation): double` 与 `close(): void`。`ContentEntry` 含标题及格式定位，`SearchBatch` 含命中定位/摘要与续查位置。公共 Android 层的 `ReadingStyle` 为排版/配色/字体参数的不可变快照，由 app 的 ReadingSettings 转换；`ReadingSurface` 提供 `view(): View`、`open(DocumentSession, DocumentLocation, ReadingStyle, Listener): void`、`move(int): void`、`seek(DocumentLocation): void`、`apply(ReadingStyle): void`、`close(): void`。其嵌套 Listener 用 `onReady()`、`onLocation(DocumentLocation, double)`、`onError(Exception)` 上报就绪/实际上屏定位/错误，控制器按会话代次校验。

- [x] 先固定旧 v2 数据库与失败恢复的代表性用例；明确原 ID、原件哈希、书签标签及 offset 不变，删除后的旧写入继续被拒绝。将格式迁移检查归入 `M25DataMigrationTest`，复用既有删除/重试夹具。
- [x] 拆清数据库、文件事务、格式导入和阅读协调；MainActivity 负责 Android 生命周期、窗口与组件装配，既有 TXT 也迁入新结构。SQLite v2→v3 显式增加格式与定位存储，保留旧 TXT 值，失败不清库。
- [x] EPUB 只做一次必要的候选筛选与小样：按 API 27、32 位 ARM、WebView 67、当前工具链、定位/搜索与许可筛选；选出一个后端并固定正式版本/提交/依赖校验。只验证显示、按键移动、重排与恢复所需能力，结果记入最终验证文档的选型段，不另出版本。选型只校准引擎专有接口，不阻塞公共职责整理；失败有明确证据再换候选。
- [x] 用既有 TXT 测试与新增迁移用例验证受影响行为；本项不独立打正式包或整轮验收。

## 工作项 3：菜单、循环列表、提示与帮助

**Files:** 新增 `app/.../MenuCatalog.java`、`InputBindings.java`、`InputContext.java`；修改 `MainActivity.java`、`NavigationState.java`、`NavigationListView.java`、`LibraryFeatures.java`、`ReaderFeatures.java`、`ReaderInputView.java`、`SoftKeyBar.java`、`HelpLayout.java`、`KeyHelpView.java`；新增 `M25InteractionTest.java`，调整既有导航/帮助测试中的旧键位预期。

**Interfaces:** `InputContext` 携带当前页面/菜单类别、所选书籍 ID、是否阅读及格式能力；`MenuCatalog.items(InputContext): List<MenuCatalog.Item>` 的嵌套 Item 引用工作项 1 的 UiAction，固定动作 ID、顺序与可用性。`InputBindings.resolve(InputContext, Key): Action` 供 KeyRouter 使用，`hints(InputContext, DeviceKeyMap): Map<Key, String>` 从同一动作定义生成提示；菜单执行交给工作项 2 的协调入口，列表移动调用工作项 1 的 ListSelection。

- [x] 先补代表性失败检查：书籍/通用菜单互不混入、无选书行为、首项关闭和默认常用项、F3/F4 输入归属、首尾循环后确认对象正确；保留打开/关闭所用同次按键剩余事件的检查。
- [x] 按设计第 4 节实现 F1–F4 和菜单顺序、替换同层菜单、当前批次循环与返回位置恢复；异步封面项保持稳定动作 ID，危险操作保留确认。
- [x] 同步更新全部页面、按钮、任务提示及帮助。两行四键底栏与帮助排版复用机型标签；记录阅读区域变化时锚点保持。重点检查实际字体比例下的键位对齐、层级、留白与换行，整合时统一截图验证。

## 工作项 4：EPUB 完整接入

**Files:** 新增依赖 core/reader-android 的 `engine-epub/` 模块及 `EpubDocumentSession`、`EpubReadingSurface`，内部使用工作项 2 已验证的后端；修改 `settings.gradle`、`app/build.gradle`、`app/src/main/AndroidManifest.xml`、`DocumentImportRegistry`、`DocumentEngineRegistry`、书库/存储/导出适配和 `NOTICE.md`；新增 `EpubIntegrationTest.java` 与受控 EPUB 夹具。

**Interfaces:** EPUB 实现工作项 2 的 DocumentSession/ReadingSurface，通用页面只消费格式能力与 DocumentLocation；资源 ID/内容锚点由适配器编码为带版本 payload。导入和导出保持原始 EPUB 字节；清理仅操作经该格式确认可重建的缓存。

- [x] 用受控 EPUB 固定目录/章节、图文、内链返回、搜索结果跳转、重排和重开的定位行为；另外检查非法归档路径、外部资源访问和过时会话回调，不只断言文件能打开。
- [x] 完成无 DRM 可重排 EPUB 2/3 的约定阅读流程，接入同一按键、主题、排版、书签、进度及存储入口；EPUB 不显示 TXT 编码纠正。引入上游代码或库时补齐来源与许可记录。
- [x] 在已连接 SH-02L 上把 EPUB 与工作项 3 的交互检查合并到最终代表性回归；记录包体和内存样本，不增加本轮 M3 压力项目。

## 工作项 5：集中验证、整体审查与一次交付

**Files:** 修改 `scripts/{build,test-device,smoke-release,publish-local}.ps1`；发布脚本源码一致性检查加入 `reader-android`/`engine-epub`，明确识别 `-m2.5` 对应 `M2_5_*` 文档，避免误取 M2 记录。新增 `docs/M2_5_VALIDATION.md`、`M2_5_RELEASE_NOTES.md`，更新 README/HANDOFF 与存储约定。

- [ ] 集中执行 `.\scripts\build.ps1 -Task verify` 和 `.\scripts\build.ps1 -Task release`，读取真实结果；在新模块中存在的 JVM 检查纳入同一 verify。编译版本为 `0.4.0-m2.5` / code 7。
- [ ] 在 `357776091146625` 用独立 debug/test 包，合并执行迁移、交互、EPUB 及受影响的既有 TXT/存储用例；只保留代表性内容连续性和大书检查，不重复完整视觉/双设备组合。完成一次整体代码审查，问题用关联用例修复和复核。
- [ ] 更新 smoke-release 的 F2/F3/F4 和格式定位读取，正式升级前记录现有书架/进度/设置；正式短冒烟不向用户书库导入测试图书或清空资料。新功能夹具已在独立包完成，正式包只检查既有书、菜单、恢复与升级。
- [ ] 验证签名/zipalign、来源与哈希后一次覆盖安装；拉取已安装 APK 核对，复查用户书库/进度/设置与 Books 基线。同批目视检查底栏及帮助页，新增失败只重测关联部分。
- [ ] 交付一个 `dist/v0.4.0-m2.5/` 正式候选及集中记录，保留历史发布物；清理本轮独立 debug/test 包和自有夹具，保留电脑证据。M3 仍按实际情况安排。

## 状态与执行说明

- [x] 设计范围已通过本轮用户反馈确定，结构优先、一次交付、精简验证的约定已纳入。
- [x] USB、设备环境、已安装 APK 身份与外部 Books 来源基线已记录。
- [x] 实施任务表已整理并自行检查接口依赖及发布脚本风险。
- [x] 任务表自检后已在当前会话连续实施，未新增逐任务确认或候选版本。
- 实施进度：工作项 1–4 完成，65 项 JVM、集中 29 项真机回归及修复关联复核通过，一次整体审查的八项发现关闭。正式签名升级与产物状态见 M2_5_VALIDATION。
