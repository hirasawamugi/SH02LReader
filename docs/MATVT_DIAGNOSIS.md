# MATVT 崩溃补查

补查日期：2026-10-18。此前只记录了 MATVT 弹窗阻挡正式包测试、关闭弹窗后重测通过，没有完成原因分析。本次从手机仍保留的 crash/main/system/events 缓冲区及 DropBox 提取证据，没有运行新的操作测试。

## 已确认的故障

手机上的 `io.github.virresh.matvt` 为 versionCode 103 / versionName 1.0.3。系统保留两次该进程的原生崩溃，均发生在 `RenderThread`，调用栈为 `EglManager::createSurface → OpenGLPipeline::setSurface`，最终 `SIGABRT`：

```text
Failed to create EGLSurface ... eglErr = EGL_BAD_ALLOC
```

两次进程 PID 分别为 2821、3000。手机日志时钟为 1970-01-06，不能作为实际发生日期；以下仅按同一设备时钟比较事件顺序。

| 手机日志时间 | 事件 |
| --- | --- |
| 03:03:26 | MATVT PID 2821 首次同类原生崩溃，前置 main/system 日志已不在缓冲区 |
| 03:18:03.229 | 屏幕转为开启状态 |
| 03:18:03.245–.296 | 阅读器 Activity 恢复到前台 |
| 03:18:03.414 | 图形服务报告 `connect: BufferQueue has been abandoned`；MATVT 的 `native_window_api_connect` 返回 `0xffffffed`（-19） |
| 03:18:03.415–.421 | MATVT 返回 `EGL_BAD_ALLOC`，渲染线程中止 |
| 03:18:05.616 | 系统显示 MATVT 崩溃弹窗 |
| 03:18:06.658 | 系统重新启动 MATVT 的 `MouseEventService` |

第二次崩溃的直接失败路径已经明确：**唤醒后的 MATVT 绘制窗口连接失败，伴随已废弃的 BufferQueue，随后 Android 的硬件渲染器主动中止进程**。图形服务日志没有给出窗口所属包，但它与 MATVT 的连接错误同毫秒出现，错误返回值及后续调用链相符，因此将两者关联属于有依据的推断。

Android 8.1 的 [BufferQueueProducer::connect](https://android.googlesource.com/platform/frameworks/native/+/android-8.1.0_r1/libs/gui/BufferQueueProducer.cpp#1138) 在队列已废弃时返回 `NO_INIT`；[eglCreateWindowSurface](https://android.googlesource.com/platform/frameworks/native/+/android-8.1.0_r1/opengl/libs/EGL/eglApi.cpp#688) 将窗口连接失败映射为 `EGL_BAD_ALLOC`；[EglManager::createSurface](https://android.googlesource.com/platform/frameworks/base/+/android-8.1.0_r1/libs/hwui/renderthread/EglManager.cpp#304) 对创建失败执行致命断言。这些 AOSP 源码与实机错误链一致，但不等于已经审计了夏普固件的全部实现。

因此，这里的 `EGL_BAD_ALLOC` 不能直接解释成手机 RAM 用尽，也不能作为 TXT 大文件导入导致 OOM 的证据。现存日志优先指向窗口/Surface 在唤醒时的恢复时序问题。

## 结论的边界

- 此次弹窗对应 MATVT 进程的原生崩溃；“关闭弹窗后阅读器重测通过”只说明测试可以继续，不能证明两者完全没有触发关系。
- 屏幕唤醒、阅读器恢复与崩溃紧邻发生。正式包测试脚本包含唤醒操作，但保留记录不能区分这次唤醒来自自动输入还是实体操作，也不能确定普通日常唤醒是否同样复现。
- 尚不能把根因确定到 MATVT 某一行代码，也不能分清 MATVT 悬浮窗管理、系统窗口恢复或夏普固件的具体责任。第一次崩溃仅有同类堆栈，不能补推其前置操作。
- 上游 [v1.03 的 OverlayView](https://github.com/virresh/matvt/blob/1a163bcad9e17a84aa9d05a2db10813018509d2d/app/src/main/java/io/github/virresh/matvt/view/OverlayView.java) 创建全屏透明悬浮层；上游版本元数据与实机相符。尚未验证实机 APK 与该源码一致，不能据此宣布已找到确定的代码缺陷或已有修复。

后续兼容验证应控制前台应用和 MATVT 状态，比较阅读器/系统界面在相同熄屏—唤醒过程中的结果，并同时捕获窗口和图形日志；MATVT 设置调整需由用户明确授权。本次保持 M1 收尾状态，没有启动 M2、修改应用或调整手机设置。

## 证据留存

原始日志和核对用的上游源码保存于被 Git 忽略的 `tools/logs/matvt-investigation-20261018/`：

- `matvt-native-dropbox.txt`：两次 MATVT 原生崩溃。
- `crash-buffer.txt`：系统 crash 缓冲区，另包含此前 M0 测试辅助代码的旧异常，不能与 MATVT 混淆。
- `matvt-pid3000.txt`、`graphics-errors.txt`、`crash-context.txt`：第二次崩溃的图形错误及弹窗/服务重启记录。
- `reader-correlation.txt`、`matvt-events.txt`：屏幕、Activity 恢复和 MATVT 事件顺序。
- `aosp-8.1-*.cpp`、`matvt-v1.03-*`：核对用的公开源码；不作为新阅读器依赖。

此次仅将读取到的记录保存到电脑，未向手机写入测试文件。
