# SH-02L 设备资料

当前版本与使用方式见 [项目首页](../README.md) 和 [使用指南](../docs/USAGE.md)。下述采集与预检是对应阶段的历史记录，最新交付结果见 [M3 验证](../docs/M3_VALIDATION.md)。

`sh02l按键值.txt` 是从旧工程复制的独立资料副本，迁移时已校验与原记录的 SHA-256 一致。新工程按键配置以本目录的记录为依据。

原始文件主要是 ACTION_UP。M0 已补采实体 Down/Up/Repeat 和窗口焦点：留存 173 条已消费事件，用户完成指定方向、确认、F 键与长按序列并反馈正常。部分左键/F3 事件未留在快照中，结论分别标注用户观察和日志证据，详见 [M0 验证记录](../docs/M0_VALIDATION.md)。

实测设备为 SH-02L，Android 8.1.0 / API 27，540×960、240 dpi，ABI `armeabi-v7a,armeabi`。系统字号比例为 1.3；布局按实际密度、字号和可用宽高计算。M1 的读取和排版证据见 [M1 验证记录](../docs/M1_VALIDATION.md)，日志置于本工程被 Git 忽略的 `tools/logs/`。

2026-10-20 M2.5 USB 准备检查：当前系统 WebView 为 `com.android.webview 67.0.3396.127`，设备读写权限保留，正式应用为 `0.3.1-m2` / code 6；从设备拉取的 APK 与已发布 M2 哈希相同。证据见 `tools/logs/m25-device-preflight-20261020.txt`、`m25-installed-baseline.json`。此为设备与安装包基线，不代表 EPUB 兼容性已经通过。

资料溯源：按键文件最初来自相邻旧 AAReader 工程的同名记录，本仓库已经保留完整副本，不需要额外取得旧工程。
