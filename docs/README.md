# 文档索引

当前版本：**M4.3 / 0.6.3-m4.3 / versionCode 12**，已发布 [GitHub 试用版](https://github.com/hirasawamugi/SH02LReader/releases/tag/v0.6.3-m4.3)。M4 已按批准方案实施 01–04，修补版持续按反馈调整；05、06 后续推进，其余暂缓。

[M4.3 说明](M4_3_RELEASE_NOTES.md) · [M4.3 验证](M4_3_VALIDATION.md) · [项目约定](../AGENTS.md)。

## 使用与构建

| 文档 | 适合查阅的内容 |
| --- | --- |
| [使用指南](USAGE.md) | 安装升级、导入文件、实体按键、外观与数据管理 |
| [构建指南](BUILDING.md) | 从新克隆配置 JDK / SDK、编译与选择验证范围 |
| [发布指南](PUBLISHING.md) | 发布签名、产物来源、GitHub 仓库与 Release |
| [更新记录](../CHANGELOG.md) | 各版本已交付的变化 |
| [设备资料](../device/README.md) | SH-02L 硬件与按键记录 |

## 当前实现与交付证据

| 文档 | 内容 |
| --- | --- |
| [代码结构](CODE_STRUCTURE.md) | 模块、输入、阅读会话和存储的维护入口 |
| [TXT 与存储约定](TXT_STORAGE.md) | 缓存、定位、数据库与文件提交边界 |
| [M4 变更说明](M4_RELEASE_NOTES.md) | 本阶段书库清理、按键操作与外观功能 |
| [M4.3 验证记录](M4_3_VALIDATION.md) | 本轮配色、真机截图、数据保留与清理 |
| [维护交接](HANDOFF.md) | 已完成状态、现场记录与后续维护提醒 |
| [MATVT 兼容性记录](MATVT_DIAGNOSIS.md) | 已观察问题与复现资料 |

## 计划与历史

[历史文档索引](HISTORY.md) 汇集阶段说明、验证、设计和进度记录。原路径保留，便于旧提交、发布脚本与既有链接继续追溯。

[原始技术方案](SH02L_READER_PLAN.md) 和 [验收清单](SH02L_ACCEPTANCE.md) 包含分阶段目标与历史完整测试组合。阅读时以文中的日期、阶段和实际结果为准；尚未勾选的历史组合不自动成为当前版本的任务。

历史验证中的 `tools/logs/` 指维护者本机保存的原始日志，已被 Git 忽略；仓库提供结论、方法、来源提交和选定截图，不包含这些日志。`images/` 同时包含设计稿和实机截图，应按所在阶段和文档说明区分。
