# SDD ledger — plan: docs/superpowers/plans/2026-10-21-m3-lightweight-reading-stability.md

- 2026-10-21：用户连接 USB 并授权开始。设备 357776091146625 / SH-02L，正式 0.4.0-m2.5 / code 7；不清空正式数据。
- Pre-flight：任务 1 的 TXT Page 仍由引擎拥有，任务 2 继续通用位置契约；任务 3 不解释分页对象。任务 4 发布映射需补 M3，默认旧冒烟含生命周期检查，按计划跳过。
- Ruling: 沿用当前 feature/m25-optimization-epub 功能分支原位实施 — 已审阅计划要求保留本地分支且工具缓存就绪，不在主分支工作 — 错误代价为本地改动集中，保留阶段提交可恢复。
- Ruling: 采用本文件保存持久执行账本，不另调用依赖 Bash/英文任务标题的临时脚本 — Windows 与当前中文轻量任务表直接兼容，减少重复记录 — 错误代价为需要自行核对任务与提交。
- Ruling: 按用户已确认的成本约定自行做一次集中差异审查，不调用独立子代理 — 计划明确不派生实现/审查子代理 — 错误代价为缺少独立审查视角，以定点回归和明确限制弥补。
- Task 1: in progress；正在保存正式 UI/设置基线并构造 TXT 块边界与章节标题失败用例。
- 正式 UI 基线为 3 本：蛊真人 0.5%、大爱仙尊 0.9%、遮天 0.0%；公共 Books 普通文件为空，导入目录有用户封面，均保留。release 不可 run-as；系统 ACTION_CREATE_DOCUMENT 落到无创建按钮的文件选择页，本轮取消导出，不扩大为系统选择器修复。
- Task 1 RED: 真机 testBlockBoundaryDoesNotCreateShortPage 失败 expected 8259 / actual 8192，证明读取边界导致短页；首次标题夹具恰落在 8192，已移到块内部以覆盖未置顶行为。
- Task 1 RED: 标题移动到块内后 expected 8401 / actual 8360，确认标题未置顶。
- Ruling: build.ps1 新增 focused（assembleDebug + assembleDebugAndroidTest）用于关联检查 — 发现 test-apk 只打测试包，安装的主 debug APK 仍旧，首次 GREEN 实际重复了旧 APK 的两个失败；保留该证据 — 错误代价为新增一条构建选项，现有 verify/release 不变，避免后续误装旧实现或重复完整 Lint。
- Task 1: complete；连续读取保留未完成页，单次布局最多 16384 UTF-16 单元、3 个布局窗口、1024 个仅含偏移的页范围；章节起点复用既有索引。原真机两个 RED 均转 GREEN，连同既有多字号逐字/反向检查共 3 项通过（3.822s），证据 instrumentation-m3-txt-green-corrected.txt。现用第 23 节原文仍待逐字核对，不影响独立机制复现。
- Task 2: in progress；将用延迟图像资源检查过渡页面是否提前暴露，并保留坏图返回路径。
