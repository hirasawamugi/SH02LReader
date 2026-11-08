# 发布与 GitHub 指南

源码仓库和可安装 APK 分开维护。当前试用预发布版本为 `0.6.3-m4.3`，正式包名 `com.sh02l.reader`；后续修补沿用签名并递增版本号。

源码仓库：[hirasawamugi/SH02LReader](https://github.com/hirasawamugi/SH02LReader)，默认分支 `main`，2026-10-21 已完成首次源码上传。下面保留新仓库连接和后续版本发布的方法。

## 提交源码

Git 管理应用源码、Gradle Wrapper、构建脚本、工具版本与项目文档。SDK / JDK 安装、缓存、日志、签名和 `dist/` 发布目录由 `.gitignore` 排除。提交前查看实际差异：

```powershell
git status --short
git diff --check
git diff --stat
```

不要使用 `git add -f` 加入签名、个人图书、设备完整日志或本机构建产物。项目许可证尚未选择；[NOTICE.md](../NOTICE.md) 记录第三方来源，不替代项目许可证。

## 首次上传 GitHub

先在自己的 GitHub 账户中创建仓库，自行选择公开或私有。为保留本地提交历史，创建时不额外初始化 README、许可证或 `.gitignore`。

创建后复制仓库 HTTPS 或 SSH 地址。以下示例中的地址需替换：

```powershell
git remote add origin YOUR_GITHUB_REPOSITORY_URL
git remote -v
git branch --show-current
git push -u origin main
```

使用 HTTPS 时，Windows Git Credential Manager 可引导 GitHub 登录授权；SSH 则需要对应账户已配置公钥。上传使用的账户与仓库写入权限需单独确认。已有 `origin` 时先确认其地址，不要重复添加或直接覆盖。

GitHub 默认分支为 `main`，当前包含 M4.3。阶段开发分支在本地完成验证后合入 `main`；远端没有新增提交时优先使用快进合并，推送前先 fetch 并核对分歧。原有 `master` 与阶段开发分支保留用于追溯，其中 `master` 仍是较早的 M1.5。历史验证文档可能含设备现场信息，决定公开范围时也应考虑过往提交。

## 签名与本地产物

维护已有正式版时，先安全恢复原有 `tools/signing/`，再构建。新生成的 release 密钥代表新的签名身份，不能覆盖旧身份的正式应用；应将签名材料单独安全备份。

1. 更新应用版本，完成与改动相关的验证，提交源码。
2. 使用正式发布签名构建：

```powershell
.\scripts\build.ps1 -Task release
```

3. 保持 Git 工作区干净，导出本地发布目录：

```powershell
.\scripts\publish-local.ps1 -Version 0.6.3-m4.3
```

输出位于 `dist/v0.6.3-m4.3/`，包括 APK、`SHA256SUMS.txt`、签名信息、`build-info.json`、变更与验证记录。脚本会核对签名、APK 对齐、版本，以及 APK 内嵌提交与当前编译源码的一致性。

已存在的版本不允许用不同 APK、哈希或源码来源覆盖；有产品变化时先递增版本。文档可以晚于已验证 APK 提交，`build-info.json` 的 `sourceCommit` 指实际编译源码，`publicationCommit` 指导出时的仓库提交。维护新阶段版本时，还需核对发布脚本中的阶段文档映射。

## GitHub Release

仓库上传完成后，按版本创建 tag 和 GitHub Release，依据该版本的变更与验证记录填写发布说明。上传 APK、`SHA256SUMS.txt` 和 `build-info.json`，验证附件完整后发布。上传前查看具体附件内容，签名私钥和原始日志不属于发布附件；不要修改已发布版本的 tag 或替换成不同的 APK。

### M4.3 发布记录

2026-11-08 已将 M4–M4.3 的源码快进合入 `main`，并发布 [v0.6.3-m4.3](https://github.com/hirasawamugi/SH02LReader/releases/tag/v0.6.3-m4.3)，标记为试用预发布。tag 指向 `d98b5069bd9afb26d1cf7f050d43c40fb0b72c17`；APK 编译来源为 `014e8033b0ab252823252541ae0883fc708f2cba`，编译模块一致。之后的下载入口文档提交保留在 `main`。

附件为 `SH02LReader-v0.6.3-m4.3.apk`（139,956 字节）、`SHA256SUMS.txt`、`build-info.json`。从公开下载地址回读三者，SHA-256 均与本地一致。APK 校验值：

```text
d18a2b1950537a8e4b140fa392218eb5710ccaa36a7cd9d8f2543f4248a17a7b
```

此次发布沿用已验证安装包，没有重建应用或重复 Android 测试。详细功能、升级和设备检查见 [M4.3 验证记录](M4_3_VALIDATION.md)。

### M3 发布记录

M3 APK 的实际编译来源为 `894672fcfce231535fee39ab776c506f645c8502`，SHA-256 为：

```text
250466cea5fabb21d6ffaa6b4c75ef7378aa1a022b8a16e725d01db3b20b2532
```

2026-10-21 已发布 [v0.5.0-m3](https://github.com/hirasawamugi/SH02LReader/releases/tag/v0.5.0-m3)，标记为 M3 里程碑预发布，附件为 APK、`SHA256SUMS.txt` 和 `build-info.json`。tag 指向 `79a1b703d42fa1b815d8a6d94e17cd06d390daf5`，编译模块与 APK 内嵌来源 `894672f` 一致；其后的文档提交继续保留在 `main`。

发布前核对签名、对齐、版本、哈希与源码来源；发布后从公开下载地址回读三个附件，校验值均与本地一致。没有重建应用或重跑 Android 测试。M3 本地交付证据见 [验证记录](M3_VALIDATION.md)。
