## 这个 PR 做了什么

<!-- 一句话说清。如果是修 bug，写「修复了 #123」。 -->

Fixes #

## 改动类型

- [ ] Bug 修复
- [ ] 新功能
- [ ] 重构（不改变行为）
- [ ] 文档
- [ ] 构建 / CI
- [ ] 新增依赖 ← **必须在下面说明理由**

## 怎么验证的

<!-- 别只写「编译通过」。说清楚你实际跑了什么、看到什么。 -->

- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] `./gradlew :app:assembleRelease` 通过
- [ ] `./gradlew :app:assemblePersonal` 通过
- [ ] 真机安装并实际操作过（设备 / 系统版本：______）

复现 / 验证步骤：

1.
2.

## 截图或录屏

<!-- 界面改动必须附。 -->

## 检查清单

- [ ] 代码风格符合 Kotlin 官方风格，注释与 UI 文案用中文
- [ ] 新增工具已在 `Prompts.kt` 登记
- [ ] 危险工具已接入 `ApprovalGate`，破坏性写操作已接入 `UndoStore`
- [ ] 如果加了依赖：已同步更新 `build.gradle.kts`、`data/OpenSource.kt`、`THIRD-PARTY.md`
- [ ] 没有提交 `keystore.properties` / `local.properties` / `*.keystore`
- [ ] 没有硬编码 API Key、Token 或内网地址
- [ ] 没有让 Play 版（release）依赖 personal 专属代码

## 涉及安全 / 权限的改动

<!--
如果这个 PR 改了权限声明、文件访问范围、Shell 执行、无障碍行为，
请在这里写清楚：
  - 风险是什么
  - 用户会看到什么变化
  - 有没有加防护
没有就写「无」。
-->

无

## 新增依赖的理由（如有）

<!--
项目刻意保持依赖精简。请回答：
  - 为什么 JDK / Android SDK 自带能力做不到？
  - 许可证是什么？与 Apache-2.0 兼容吗？
  - APK 体积增加了多少？
-->

无
