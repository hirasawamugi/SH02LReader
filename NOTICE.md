# 开源参考与运行时依赖

M0/M1 的按键周期、界面、TXT 导入、索引与分页代码为本工程新增实现。参考了以下项目的架构与交互思路，调研提交见实施计划：

- CoolReader：阅读动作与按键映射的职责划分，主许可证 GPL-2.0-or-later。
- KOReader：设备键值配置与通用界面的分离，主许可证 AGPL-3.0。
- NovelReader：本地文档、分章和分页的职责划分，主许可证 MIT。
- Readium Kotlin Toolkit：EPUB 架构筛选参考，主许可证 BSD-3-Clause。

M0/M1 尚未移植上述项目的源文件或捆绑它们的引擎。实际引入上游代码时补齐文件级来源、提交、许可与声明。

正式 APK 的运行时仅使用 Android 系统框架。JUnit 4.13.2 用于 JVM 测试，Android 平台测试框架用于原生界面测试；测试代码与测试依赖不进入正式 APK。

M2.5 EPUB 使用本工程编写的有界 ZIP/XML 包模型和系统 WebView 的 HTML/CSS 排版，没有复制或捆绑 Readium/CoolReader 引擎源码。支持无 DRM 的可重排 EPUB 2/3，禁用书内脚本、外部资源和本地文件访问。系统 WebView 随设备提供，不作为 APK 依赖发布。格式参考 [W3C EPUB](https://www.w3.org/TR/epub-33/)，渲染接口参考 [Android WebView](https://developer.android.com/reference/android/webkit/WebView)。实际设备版本及兼容性结果见 M2_5_VALIDATION.md。

Gradle、Android Gradle Plugin 和 Android SDK 为构建工具，版本与校验值见 `tools/TOOLCHAIN.json` 及 Gradle 依赖校验文件；工具本身不作为正式 APK 的应用代码打包。
