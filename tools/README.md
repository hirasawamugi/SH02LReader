# 新阅读器构建工具

此目录只用于 SH02LReader 的本地构建工具。M0 已安装 Android SDK、Gradle 8.7、API 27 模拟器，并生成新工程独立的签名。

版本与下载校验值见 [TOOLCHAIN.json](TOOLCHAIN.json)。构建使用 JDK 17、AGP 8.6.1、Gradle 8.7、Android API 35 / build-tools 34.0.0；通过脚本设置进程级路径，不修改系统全局 Java，不引用旧工程工具。

本机脚本优先使用 `tools/jdk-17/`，其次使用有效的 `JAVA_HOME` 或已安装的 `javac` 所属 JDK。当前使用本机已有的 JDK 17。Gradle 优先使用已校验解压的 `tools/gradle-8.7/`，也保留标准 Gradle Wrapper 入口。

目录约定：

- `android-sdk/`：SDK、platform-tools、build-tools 和模拟器镜像。
- `gradle-8.7/`、`downloads/`：已校验的 Gradle 与工具下载。
- `gradle-user-home/`、`android-user/`、`avd/`：项目内缓存与模拟器状态。
- `signing/`：release 与 debug 独立签名材料；release 凭据由脚本随机生成。
- `logs/`：构建、原生测试、启动性能和设备诊断的原始证据。

这些安装文件、缓存、日志和签名均被 Git 忽略。`signing/` 需要作为本地发布身份单独备份。

新环境安装 SDK 后需具备 `platforms;android-35`、`build-tools;34.0.0` 与 `platform-tools`。运行模拟器测试时另装 `emulator` 和 `system-images;android-27;default;x86`。工具档案按 `TOOLCHAIN.json` 校验；依赖校验文件位于 `gradle/verification-metadata.xml`。

下载缓存、构建中间文件和签名凭据按用途隔离，并从后续源码版本管理中排除。具体工具版本见 [实施计划](../docs/SH02L_READER_PLAN.md)。
