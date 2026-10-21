# 构建指南

现有脚本面向 Windows PowerShell。以下命令均在本仓库根目录运行，无需旧 AAReader 工程。首次构建需要联网下载 Gradle 与 Maven 依赖。

## 工具链

| 项目 | 仓库固定版本 |
| --- | --- |
| JDK | 17（Java 源码兼容级别 8） |
| Gradle | 8.7，包含 Wrapper 与分发包 SHA-256 |
| Android Gradle Plugin | 8.6.1 |
| Android SDK Platform | android-35 |
| Android Build Tools | 34.0.0 |
| 最低 / 目标 API | 27 / 35 |

版本与工具下载地址、校验值见 [TOOLCHAIN.json](../tools/TOOLCHAIN.json)。Gradle 依赖校验记录位于 [verification-metadata.xml](../gradle/verification-metadata.xml)。

## 新环境准备

1. 使用 Git 克隆仓库。构建会读取 Git 提交号，请保留 `.git`。
2. 安装 JDK 17，并将当前 PowerShell 的 `JAVA_HOME` 指向该 JDK；也可放到 `tools/jdk-17/`。
3. 根据工具清单下载 Windows Android command-line tools，核对 SHA-256，解压为 `tools/android-sdk/cmdline-tools/latest/`。确保下面的 `sdkmanager.bat` 路径存在。
4. 安装项目 SDK 并接受其许可：

```powershell
$taskSdk = Join-Path (Get-Location).Path 'tools\android-sdk'
$taskSdkManager = Join-Path $taskSdk 'cmdline-tools\latest\bin\sdkmanager.bat'
& $taskSdkManager "--sdk_root=$taskSdk" 'platform-tools' 'platforms;android-35' 'build-tools;34.0.0'
& $taskSdkManager "--sdk_root=$taskSdk" --licenses
```

SDK 许可需要由安装者阅读和接受。无需为普通构建安装模拟器；需要时另装 `emulator` 与 `system-images;android-27;default;x86`。

脚本固定使用项目内 `tools/android-sdk/`，不会自动使用 Android Studio 的全局 SDK。JDK 按项目内目录、有效 `JAVA_HOME`、`javac` 所属目录的顺序查找。未安装本地 Gradle 时自动使用仓库 Wrapper。

## 编译

```powershell
.\scripts\build.ps1 -Task debug
```

首次构建缺少 debug 签名时，会调用 `prepare-signing.ps1` 生成项目本地签名材料，其中也包含一套新的 release 签名。这些文件保存在忽略的 `tools/signing/`，不会上传到 Git。

| 任务 | 执行范围 / 输出 |
| --- | --- |
| `debug` | `app/build/outputs/apk/debug/app-debug.apk` |
| `verify` | core / EPUB JVM 测试、debug Lint、debug APK 与原生测试 APK |
| `focused` | 同时重建 debug 主包和原生测试 APK，适合定点改动 |
| `test-apk` | 仅构建原生测试 APK，不会同时更新主包 |
| `release` | release Lint 与签名 APK，见 [发布指南](PUBLISHING.md) |
| `connected` | 执行 Gradle connected 测试任务 |

脚本只在运行期间设置 Java、SDK 和缓存的进程环境变量，结束后恢复。工具目录约定见 [tools/README.md](../tools/README.md)。

## 按需验证

完整本地构建检查：

```powershell
.\scripts\build.ps1 -Task verify
```

修改后按风险选择用例；普通文档修改只核对链接、说明与 Git 差异，不需要构建 APK。当前交付的测试数字和范围见 [M3 验证](M3_VALIDATION.md)，它们不是每次改动的必跑清单。

真机测试先启用 USB 调试，连接并唤醒设备，再列出编号：

```powershell
.\tools\android-sdk\platform-tools\adb.exe devices
```

将下面的 `DEVICE_SERIAL` 换成实际编号。此示例只运行写入边界类：

```powershell
.\scripts\build.ps1 -Task focused
.\scripts\test-device.ps1 -Serial DEVICE_SERIAL -Label write-boundary -TestClass com.sh02l.reader.M3WriteBoundaryTest
```

`-TestClass` 可指定类名、`类名#方法名`，或逗号分隔的选择。省略会执行全部原生测试。测试脚本会安装独立的 `com.sh02l.reader.debug` 和 `.debug.test` 包，日志写入忽略的 `tools/logs/`；正式应用数据及公共 Books 原件应保留。

`smoke-release.ps1` 是较早的正式包恢复冒烟工具，包含 Home / force-stop 等步骤。M3 没有执行这套流程，普通构建和文档整理也不需要运行。

## 常见构建问题

- **找不到 SDK**：检查 `tools/android-sdk/platforms/android-35/android.jar`，只设置全局 SDK 路径不足以满足现有脚本。
- **找不到 keytool / JDK**：确认 `JAVA_HOME` 指向 JDK 17，而非 JRE，且包含 `bin/javac.exe` 和 `bin/keytool.exe`。
- **新机器 debug 覆盖失败**：其 debug 签名可能与旧机器不同。仅移除确认可丢弃的旧 debug/test 包后重装，保留正式包。
- **依赖校验失败**：先核对工具版本、下载和依赖来源；不要将 `-WriteVerification` 当作日常修复选项。该开关会更新受版本管理的校验记录。

模块职责与 Java 排版工具说明见 [代码结构](CODE_STRUCTURE.md)。
