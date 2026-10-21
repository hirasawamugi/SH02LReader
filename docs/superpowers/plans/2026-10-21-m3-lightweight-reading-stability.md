# M3 阅读优化与轻量稳定性补漏实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 按本轮成本约定在同一会话直接执行，不按任务派生实现/审查子代理，不分别出包。

**Goal:** 修正 TXT 提前短页，改善目录定位和 EPUB 图片页显示，补齐关键写入中断边界，以少量代表性检查交付一个维护版本。

**Architecture:** 沿用 M2.5 的文档、阅读界面与持久化边界。TXT 在引擎内实现有界连续分页及章节起点对齐；EPUB 优先调整首次样式应用和布局就绪时序。存储检查使用隔离数据库和磁盘中断现场，按结果定点修复，不扩展新子系统。

**Tech Stack:** Java、Android 原生 Views / StaticLayout、系统 WebView、SQLite；minSdk 27，现有 Gradle / JDK 工具链。

**Spec:** 本文“已确认的范围与设计”，合并保存用户于 2026-10-21 确认的范围、成本约束和执行任务，避免重复设计文档。

**状态：** 仅计划，尚未实现、运行 M3 测试或发布。基线为 M2.5 0.4.0-m2.5 / code 7，文档提交 21ba48e；保留现有本地分支和全部正式数据。

## 已确认的范围与设计

| 优先级 | 内容 | 本轮边界与完成标准 |
| --- | --- | --- |
| P1 | TXT 提前短页 | 先核对《大爱仙尊》第 23 节“而方源只能自己想办法”附近的原字节/换行、规范化偏移与读取块边界。现有约 8192 单元文本块独立分页是明确嫌疑，具体书中位置尚未复现。没有源换行时，读取块边界不应额外截断行或提前结束页面；全文末页允许不足一屏。 |
| P2 | TXT 目录标题置顶 | 目录已有章节偏移；跳到章节起点时，让标题从正文区域第一行开始。复用索引、不重新导入，不插入人为换行；兼顾上一/下一章入口。范围是跳转对齐，不追加“全书所有章节必须另起页”的规则。若需超出本轮有界分页改造才可实现，单列成本和延后原因，不扩大工程。 |
| P2 | EPUB 图片页显示 | 排查首次打开/跨资源加载与同资源翻页的区别；优先避免先显示出版物布局、再切成阅读器布局。图片尺寸、字体和布局达到可显示状态后再发布定位/ready；资源失败或等待超时有明确回退，保持按键可返回。保留引擎、资源隔离和既有支持范围。 |
| P1 | 写入中断补漏 | 复用写失败重试、新位置优先等已有证据；只补进度记录完整性、导入发布与目录提交之间、缓存替换与数据库提交之间的实际缺口。允许回到最后完整提交状态，禁止半条定位、损坏书库或提交前误删来源。 |
| 抽查 | 响应与内存 | 随上述功能检查收集少量数据，发现明显异常才继续定位；不做完整性能达标工程。 |

合盖、真实来电、一般后台/系统回收恢复及长期试读，由用户在后续日常使用中观察，不列为本轮 Codex 测试或交付门槛。文件/数据库重新打开只用于核对写入完整性；正常打开书籍用于核对分页定位，不扩展为生命周期回归。

## Global Constraints

- 正式应用 ID com.sh02l.reader、现有签名、书库、书签、设置及公共 Books 原件保留；测试使用独立 debug/test 包与自有数据，故障不注入正式书库。
- 不新增运行时依赖、格式、机型、常驻服务、网络资源或无关重构；按键规则、底栏与帮助保持现有语义。
- TXT 继续使用 UTF-16 内容定位；保持有界窗口和少量布局缓存，不整章/整书排版。新窗口上限须以常量明确并在夹具中检查，不沿用旧 8704 单元断言阻碍必要的跨块衔接。
- 不跑 100/1000 本书规模矩阵、完整 P95 统计、长时压力、两小时等待、主题/书架全排列或双设备重复整轮检查。
- 默认集中一次 verify、一次 release/Lint、一次所选原生回归及一次正式升级短检查；为确认新增缺陷只运行对应的最小用例，后续新改动/新失败只复核受影响部分。
- 新增默认围绕 7 个主要场景（TXT 2、EPUB 2、写入 3），复用少量相关用例，原生集中批次以约 10–15 个逻辑场景为目标；数量不是达标任务，也不以数量限制掩盖实际关键缺陷。
- 默认同一会话实施和自查；整合后一次针对本轮差异的集中审查，不设置逐任务独立审查、重复整轮审查或内部候选交付。
- 缺少设备/现用文本时，完成独立可执行的工作并标记待测，不用夹具结果冒充真实书籍复现或真机通过。

## Review Focus

1. 读取边界落在句中、代理对/组合字符旁：不能额外断行、漏字或重复；由任务 1 的连续分页夹具覆盖。
2. 标题位于块边界、跳转后往返或改字号：标题置顶、页面邻接和内容锚点应同时成立；由任务 1 的章节夹具覆盖。
3. 图片缺尺寸、坏图或字体延迟：不能先宣布就绪后再跳页，也不能无限等待；由任务 2 的图文与失败夹具覆盖。
4. 进程消失跳过 finally，磁盘已有新文件而数据库仍为旧状态：不能把普通异常清理当作中断恢复证明；由任务 3 的持久现场覆盖。
5. 定位包含多个字段或缓存已提交但旧副本尚存：重新读取应得到完整一致的一代数据；由任务 3 覆盖，已测条件直接复用。

## 任务 1：TXT 连续分页与章节起点对齐

**Files:** 修改 engine-txt 的 TxtPaginator.java、TxtDocumentSession.java、TxtReadingSurface.java；按需调整 reader-core/text/Utf16File.java；扩展 app/src/androidTest/java/com/sh02l/reader/PaginationTest.java。

**Interfaces:** 保持通用 ReadingSurface / DocumentLocation 契约与 Page.start/end/text() 语义。分页器可新增 Page seekStart(long offset, Spec wanted)；TxtDocumentSession.seek(long offset, Spec spec) 在目标等于已有章节起点时使用它，其余定位维持“目标内容可见”。分页邻接和缓存状态由 TXT 引擎拥有，不回填到 Activity。

- [ ] 核对用户示例的现用源文件/规范化偏移，记录是否为源换行或块边界；不可取得时保留“待真实文本核对”，先用受控夹具验证独立分页造成的短页。
- [ ] 增补 testBlockBoundaryDoesNotCreateShortPage：约 2–3 个窗口的密排文本，把“办法”放在边界附近，并含跨界字符簇；用小夹具的连续 StaticLayout 作参考。断言自然行/页边界一致、逐页拼接等于原文、前后翻页一致，末页之外没有块尾短页；只运行此用例确认旧行为。
- [ ] 将“读取块”和“显示页”解耦，保留尾部未完成的行/页并接续有界内容；用轻量页首/邻接信息维持双向连续性。明确窗口/缓存上限和取消检查，禁止靠扩大一个固定块掩盖问题。
- [ ] 增补 testChapterSeekStartsAtHeadingAndTurnsRemainContinuous：章节位于读取边界附近，断言跳转页首为目标标题、相邻页范围连续；一次字号变化后原锚点仍可见，重新打开章节起点仍置顶。先运行对应旧行为，再实现 seekStart 路径；上一/下一章复用同一章节偏移。
- [ ] 定点运行新增两个场景和已存在的字符连续性用例；若标题置顶引入超出有界分页范围的重构，仅将置顶列为延后项并写明原因。通过后保存一组 TXT 改动，不单独出包或整轮验收。

## 任务 2：EPUB 图文页稳定显示

**Files:** 修改 engine-epub 的 EpubHtml.java、EpubReadingSurface.java、assets/reader.js；扩展 EpubHtmlTest.java、app/src/androidTest/java/com/sh02l/reader/EpubIntegrationTest.java 和 EpubFixtures.java。

**Interfaces:** 保持现有 EPUB 定位与通用阅读契约；EpubHtml.forDisplay 可增加携带初始阅读样式的重载，reader.js 的 report 结果补充图片/布局就绪信息，由 EpubReadingSurface 在同一 epoch 校验后发布 onLocation/onReady。

- [ ] 在同一个图文夹具上分别观察首次打开、跨资源进入和同资源前后翻页，区分首次样式替换、图片尺寸变化与重复加载；记录直接原因，不先实现缓存或更换引擎。
- [ ] 新增 testIllustratedSectionPublishesStableLocation 与 testUnavailableImageDoesNotBlockReading：图片未写宽高、含 SVG、延迟字体及坏资源组成两个小夹具；断言正式显示前已应用阅读样式、ready 后定位不因迟到资源再次跳动，失败仍可翻页/返回。针对问题先运行最小失败场景。
- [ ] 初始阅读样式尽量随 HTML 生效；必要时在样式/尺寸及最终绘制稳定前遮住过渡内容，再一次显示。沿用现有字体缩放和 4 秒等待量级，资源失败/超时明确回退，不无限轮询、不把所有图片统一延时固定秒数。
- [ ] 用既有代次取消保护处理快速切章/关闭时的迟到回调；保持外部资源、脚本、文件访问限制。复核两个新场景及一条既有内链/重排定位用例后保存改动。

## 任务 3：写入中断边界补漏

**Files:** 新增 app/src/androidTest/java/com/sh02l/reader/M3WriteBoundaryTest.java；复用 LibraryManagementTest / ImportCommitSafetyTest 的隔离工具。只有发现真实问题才修改 BookRepository.java、ProgressWriter.java、BookFileStore.java、DurableFiles.java 或相关导入提交入口。

**Interfaces:** 复用 saveProgress(String, ReadingPosition)、importBook(InputStream, String, Observer)、cleanupStaging()、rebuild(...)；测试重新创建独立 BookRepository 并检查磁盘/数据库结果。优先使用既有 Observer 和持久目录现场，不增加通用故障注入框架。

- [ ] 先核对已有写失败重试、新进度优先、删除后过期写入失效及失败重建回退的覆盖；已覆盖且实现未改的条件不重新写测试。
- [ ] testProgressWriteKeepsWholeLocator：核对一次数据库更新同时写入定位、版本与进度；针对尚未覆盖的 EPUB 定位写失败，断言失败后旧记录所有字段完整，恢复后新记录所有字段一致。复用现成重试检查；事务回滚模拟与真实进程中断明确区分。
- [ ] testUncommittedImportCanBeRetriedSafely：保留“文件已发布、书目尚未提交”的持久现场，重新开库后不显示半成品，同源重试可完成，提交前来源未删除、原件哈希不变；复用现有导入协调器对提交后删除的验证。
- [ ] testInterruptedRebuildChoosesCommittedGeneration：为缓存替换后数据库未提交、已提交但旧副本未清理各保留一个小现场。新建仓库后，缓存、编码/长度、进度和书签与已提交一代一致，原件有效；不能仅抛异常后依靠 finally 清理来证明通过。
- [ ] 快照可准确表达的窗口不再反复强杀；若某个关键窗口只能通过真实中断核对，最多对独立测试进程做一次定点中断并复开数据，不触及正式应用。仅在发现缺陷时补最小修复和对应复核；结果不宣称证明硬件断电安全。

## 任务 4：集中轻量检查与一次交付

**Files:** 按实现更新 docs/M3_VALIDATION.md、M3_RELEASE_NOTES.md、TXT_STORAGE.md、CODE_STRUCTURE.md 和交接；修改 app/build.gradle、scripts/publish-local.ps1 的 M3 版本/文档映射。测试仍复用 scripts/build.ps1、test-device.ps1。

**Interfaces:** 拟定正式版本 0.5.0-m3 / code 8，沿用应用 ID 与证书；publish-local.ps1 -Version 0.5.0-m3 必须读取 M3 文档和 images/m3，不能回退到 M1.5。历史发布目录不可覆盖。

- [ ] 集中执行一次构建/JVM/Lint；所选原生批次包含任务 1–3 与少量受影响用例，优先用 SH-02L；模拟器只补不可获得的执行条件，不重复整轮。命令：

      .\scripts\build.ps1 -Task verify
      $taskM3Cases = @(
          'PaginationTest#testBlockBoundaryDoesNotCreateShortPage',
          'PaginationTest#testChapterSeekStartsAtHeadingAndTurnsRemainContinuous',
          'PaginationTest#testEveryLogicalCharacterAppearsOnceAndBackwardPagesMatch',
          'EpubIntegrationTest#testIllustratedSectionPublishesStableLocation',
          'EpubIntegrationTest#testUnavailableImageDoesNotBlockReading',
          'EpubIntegrationTest#testResourceBoundaryAndInternalLinks',
          'EpubIntegrationTest#testReflowAndReopenLocation',
          'EpubIntegrationTest#testCustomFontWaitsForFinalMetrics',
          'M3WriteBoundaryTest',
          'LibraryManagementTest#testFailedProgressRemainsPendingUntilExplicitRetry',
          'LibraryManagementTest#testEncodingChangeMapsAnchorsAndFailedCommitRestoresOldCache',
          'LibraryManagementTest#testDequeuedProgressCannotUpdateADeletedAndReimportedBook'
      ) | ForEach-Object { 'com.sh02l.reader.' + $_ }
      .\scripts\test-device.ps1 -Serial <当前确认的设备编号> -Label m3-focused -TestClass ($taskM3Cases -join ',')

  此为新增场景均需要时的默认选择（约 14 个测试方法），已覆盖且实现未改的分支可从选择中删去；不直接选择整个 PaginationTest 类，以免顺带运行历史 50 MiB 检查。逐字连续性的小夹具计算与重开定位检查服务于本轮分页/排版修改，不扩展为设备长时间按键或生命周期测试。

- [ ] 复用一份约 10 MiB TXT 与一个小图文 EPUB，最多每种 20 次翻页、合计两轮切书；启动最多 3 次、列表移动最多 20 次，作为同批粗略抽查。每种格式采样就绪后、操作后、前台静置约 10 秒三个内存点；记录应用及可识别 WebView 相关进程，不把 ADB/界面转储耗时当实际绘制耗时。
- [ ] 无崩溃/ANR、明显卡顿或持续异常内存增长时结束抽查；仅异常触发一次定点复核。50 MiB、更多书籍与更长循环只在现有小样本暴露明确问题时用于定位，不预先安排；TXT 旧 80 MiB / P95 等目标作参考，EPUB 不套用 TXT 内存预算。
- [ ] 对本轮差异集中自查一次，关闭实际发现的问题；随后一次 release/Lint，并核对签名、来源与哈希。仅新增失败或后续代码变化才触发关联构建/复核。

      .\scripts\build.ps1 -Task release

- [ ] 若进入正式交付，先记录现有书架、内容锚点和设置，再做一次保留数据的覆盖升级。只打开已有书核对相关显示和数据，不加入测试书、不演练合盖/来电/后台回收/强停恢复；现有 smoke-release.ps1 默认含 Home/force-stop，本轮不直接运行其整套流程，也不为此单独扩写冒烟框架。
- [ ] 发布一个 dist/v0.5.0-m3/ 产物目录，附来源、哈希、精简验证和限制；清理本轮独立测试包/夹具，保留证据与用户正式数据。保持本地分支，不擅自合并或推送。

## 停止条件与记录

- 每项记录“已修复/已验证/待实机或真实文本/延后及原因”，未执行的 M3 项目不勾为通过。
- TXT 短页原因有证据且定点回归通过；标题置顶完成或明确记录其成本受限的延后结论；EPUB 已解决所复现的显示时序问题并保留失败回退。
- 关键写入边界有完整一致的数据结果；轻量抽查无未解释的明显异常。完成这些就结束本轮，不以补齐历史全部清单为目标。
- 用户日常试读反馈后续按实际问题处理，不要求提供累计两小时证明，不阻塞本轮维护版交付。
