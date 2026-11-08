# 代码维护入口（M4.1）

依赖方向：`app → engine-txt / engine-epub → reader-android → reader-core`。引擎不依赖 app；core 不依赖 Android。

| 调整内容 | 入口与职责 |
| --- | --- |
| 新机型按键 | `reader-core/DeviceProfiles` 登记版本化配置，`DeviceKeyMap.Builder` 建立键码/扫描码对应、按键标签与可选帮助布局 ID；`device_profile` 偏好预留手动选择 ID。未知设备使用标准 Android 键码，不猜测扫描码。 |
| 页面按键 | `app/InputBindings` 从 `InputContext` 解析逻辑动作，并生成同一上下文的 F 键提示；`KeyRouter` 保留整次物理按键归属。 |
| 菜单内容与顺序 | `app/MenuCatalog` 固定动作 ID、标题、顺序和格式能力；`LibraryFeatures/ReaderFeatures` 提供执行回调。新增动作同时补规则与提示。 |
| 列表移动 | `reader-core/ListSelection` 负责循环/跳过说明项，`NavigationState.Screen` 保存选择及像素位置；`NavigationListView` 只负责视口。正文分页和输入不使用循环选择。 |
| 阅读任务与状态 | `app/ReadingController` 独立管理打开任务、会话代次、渲染器及跳转历史。`ReaderSession.position` 是实际上屏定位，菜单元数据任务不改变它的代次。 |
| 新文档格式 | `DocumentImportRegistry` 准备原件/元数据；`DocumentEngineRegistry` 组合 DocumentSession 与 ReadingSurface。通用目录/搜索/书签使用 ContentEntry 和 DocumentLocation。 |
| TXT 分页 | `engine-txt/TxtReadingSurface` 拥有排版队列与 Canvas；`TxtPaginator` 以连续页范围衔接有界读取窗口，最多 16384 单元/布局、3 个布局窗口、1024 个仅含偏移的页范围；`TxtDocumentSession` 复用章节起点做置顶，保留 UTF-16 定位。 |
| EPUB 排版 | `engine-epub/EpubArchive` 解析归档/目录并限定资源边界，`EpubDocumentSession` 实现定位/搜索，`EpubReadingSurface` 与 `reader.js` 控制系统 HTML/CSS 排版。 |
| 数据库 | `app/LibraryDatabase` 维护 schema 和迁移；`BookRepository` 是书库事务门面，负责原件发布、删除与 TXT 重建恢复。 |
| 文件与传输 | `BookFileStore` 限定私有书目录；`ImportExportController` 执行完整性校验、导入提交和导出 I/O。Activity 仅保留 Android 文件选择/授权生命周期。 |
| 进度持久化 | `ProgressWriter` 串行合并未开始的保存，连同格式定位保存派生进度；失败保留重试。删除通过 ID 代次使已出队写入失效。 |
| 外观与帮助 | `ReadingSettings` 保存布局选择，`ReadingStyle` 是引擎接收的不可变快照。`SoftKeyBar/KeyHelpView` 使用当前映射和规则；实际 SH-02L 示意布局由 `HelpLayout` 提供。 |

M3 的 EPUB 初始和重排样式由 EpubReadingSurface 统一生成，经 EpubHtml 放入文档，再由 reader.js 应用同一规则；图片/字体就绪和最终绘制回调确认后才发布 ready/定位。受信脚本的 reader-nav 链接路由仍经当前书籍随机 host、https 与归档路径校验，统一进入 ReadingController 的跳转历史。

`MainActivity` 负责窗口、Android 生命周期和组件装配，不再直接操纵 TXT 页对象。M0 样例导航模型仅保留在测试夹具中，产品只用 `LegacyAppearance` 读取历史外观。

新增文档定位必须使用独立的 `format/version/payload`；不能把 EPUB 百分比或章节号伪装为 TXT offset。数据库中的旧 offset 对 EPUB 仅供旧书架百分比显示，恢复、书签与搜索始终使用格式定位。版本变化要显式迁移或拒绝，不能默默把未知定位当作 0。

同一资源中多个目录片段通过 `DocumentSession.compare` 比较内容顺序，不能用显示百分比代替。EPUB 在资源线程准备有界的锚点索引，界面不为比较目录而读取文件；当前章节按实际上屏范围确定。直接点击 HTML 内链也通过 `ReadingSurface.Listener.onNavigate` 交回阅读控制器，由同一个跳转历史拥有返回行为。

验证按修改风险选择：公共键码/选择/定位用 JVM；数据库、Android 输入和实际排版用独立 debug 包。发布脚本同时检查所有编译模块的源码来源。正式应用数据和公共 Books 原件不得为测试清空。

Java 统一使用四空格排版。运行 `scripts/format-java.ps1` 格式化本次改动，或用 `-Paths 路径列表 -Check` 检查指定文件。工具固定为 [google-java-format 1.24.0](https://github.com/google/google-java-format/releases/tag/v1.24.0)，脚本校验 SHA-256；下载保存在忽略的 `tools/downloads`，不进入 APK，也不添加运行时依赖。

M4 外观边界：`ThemeSpec` 描述七个语义颜色、书架样式及静态背景参数；`BackgroundStore` 管理原子配置发布和未引用文件清理；`AppearanceFeatures` 管理独立草稿、预览、按键操作和单工作线程图片处理。MainActivity 只装配外观入口、窗口背景和结果回调。已显示的 bitmap 由视图生命周期释放，废弃且未显示的解码结果立即回收。

M4.3 保持七色存储格式，`ThemeSpec` 只对完整匹配旧默认值的两套预设更新颜色，不改用户自定义颜色或背景。`reader-core/ColorContrast` 负责不依赖 Android 的对比度计算，`Palette` 构造时派生焦点底色、文字和图标色；`UiStyle` 与 `BookRowView` 共用结果。封面图片不参与图标染色。没有逐帧颜色计算、额外主题图片或运行时依赖。

M4.1 将背景导入交给 `BackgroundDecoder` 分块处理，小封面仍用 `CoverDecoder`；二者共用 `ImageTransform` 的 EXIF/缩放/对齐规则。分块位图用后立即回收，只发布完整结果。已保存的屏幕尺寸图无需重复分块；原子保存与草稿生命周期继续由原模块负责。

`EpubSessionFactory` 只保留最近一本 EPUB 的不可变元信息，保守估算最多 2 MiB；不保留 ZIP/DOM/正文。应用 `DocumentEngineRegistry` 先通过仓库完整校验原件，再用内容摘要匹配复用对象。每次仍新建 ZIP 会话并检查归档条目，关闭时释放句柄。换书、销毁和 `onTrimMemory` 清除复用对象；短临界区避免 UI 等待解析，代次防止回收后旧任务重新填入缓存。该工厂不能接收未经校验的文件标识。

M4 导入先通过 `TextImporter.saveOriginal` 流式复制并同步计算 SHA-256，仓库确认已有原件有效后直接复用记录；仅新文件进入格式准备阶段。`StorageManager` 提供按书缓存清理及逐书结果，原件、定位数据库与来源文件不在缓存删除范围内。

`ReadingStyle.sameLayout` 区分颜色与几何变化。TXT 仅换色更新绘制，重排复用当前首字符；EPUB 仅换色更新样式，不重新收集正文，连续重排沿用同一逻辑锚点，翻页或主动跳转后重置。纯阅读改变正文周边控件可见性，保留同一个 Canvas / WebView；帮助与菜单覆盖其上，不改变正文视口。
