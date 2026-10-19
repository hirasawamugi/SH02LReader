# M1.5 共享存储权限修复：真机验证

日期：2026-10-19（Asia/Shanghai）。SH-02L / Android 8.1（API 27），保持 540×960、240 dpi、font_scale=1.3。

本补丁声明 Android 9 及以下的共享存储写权限，进入手机存储时申请尚缺少的读写权限；权限回调按当前实际读取能力继续浏览，拒绝写入不阻止已有的复制能力。来源删除仍由既有完整校验、持久提交与删除能力检查控制。

## 验证结果

| 项目 | 结果 |
| --- | --- |
| 构建、Lint、签名 | debug/release 构建及 Lint 通过；沿用 M1.5 正式证书 |
| JVM 回归 | 46 项通过 |
| API 27 模拟器回归 | 58 项通过 |
| SH-02L 原生完整回归 | 58 项通过，152.187 秒 |
| SH-02L 已有读取权限的升级路径 | 单独验证缺少的写权限会被请求，通过 |
| SH-02L 拒绝写权限后继续复制 | 原生回归通过 |
| SH-02L Books 移动完整流程 | 独立 debug 包中，生成来源文件，经生产浏览与移动入口导入，正文一致，校验提交后来源删除，通过 |
| 正式包覆盖安装 | 安装包哈希与已验证候选一致，原有书架 1 本保留 |
| 正式包手机存储入口 | 经入口申请后，系统显示 READ/WRITE_EXTERNAL_STORAGE 均 granted=true；Books 测试文件提供复制与移动选项 |
| 正式包应用导入文件夹 | ADB 可直接传入文件，应用中可选择并提供复制与移动选项 |
| 正式包重启 | 读写权限保持，书架仍为原有 1 本 |

正式包的两次文件选择均在复制/移动选择页取消，没有给正式书库添加测试书籍；完整移动与来源删除由独立测试包验证。测试包及其书库在结束后卸载。

## 安装包与来源

正式 ID `com.sh02l.reader`，仍为 `0.2.5-m1.5` / versionCode 3。安装包位于 `dist/m1.5-storage-permission-20261019/SH02LReader-M1.5-storage-permission.apk`，63,644 字节，SHA-256：

```text
659ee0768ec4583a9b30d7744cfb5d948248c50f34b3788e59f450591fd6c6bb
```

从手机拉取已安装 APK 再次核对一致。候选目录 `build-info.json` 记录基线提交 `ded205d2a465d8b3cbdcb58ca8c2a579bce7b099`、未提交源码文件的 SHA-256 与本次真机验证状态；没有将基线提交冒称为包含补丁的已提交版本。

## 清理与最终状态

用户明确确认卸载旧 AAReader（`com.aareader`，`100.0-sh02l.8.1`）及其应用数据，已完成。`com.sh02l.reader.debug`、`com.sh02l.reader.debug.test` 及其应用数据均已卸载；其外部专属目录不存在。新版覆盖安装前的旧代码目录也已由系统移除。

本轮生成的 Books 测试子目录、import 测试文件、临时截图和 UI XML 已清理。`Books/aaRead` 原来的两份 TXT 文件及大小、修改时间保持，正式书架原有 1 本保留；应用导入文件夹为空，手机前台停在正式版书架。

crash 缓冲区测试前后 SHA-256 均为 `c8f9d6286ae2d2e1453442c8d5317276018baa0773b89c1d980517edc42d7a99`，未新增崩溃；MATVT-001 仍按原记录跟踪。没有修改手机的全局字号、亮度或 MATVT 配置。

证据保存在 Git 忽略目录 `tools/logs/storage-permission-sh02l-20261019/`：`result.json`、`native-full.txt`、`native-read-only-upgrade.txt`、正式包入口与最终书架 UI XML、`final-library.png`、安装包校验及 crash 前后记录。
