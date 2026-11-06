# 参与贡献

感谢你有兴趣改进 Kite。这个项目是**一个人的自用工具长出来的**，所以请把「简单、能跑、不引额外依赖」放在第一位。

## 先读这三份

| 文件 | 内容 |
|---|---|
| [README.md](README.md) | 功能、构建、结构 |
| [THIRD-PARTY.md](THIRD-PARTY.md) | 依赖与致谢，**加依赖前必读** |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 代码分层与关键流程 |

## 环境

- JDK 17
- Android SDK Platform 35 + Build Tools 35
- Gradle 8.13（用仓库里的 `gradlew`，不要用系统 Gradle）

```bash
git clone https://github.com/<owner>/kite.git
cd kite
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
```

Windows：

```powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
.\gradlew.bat :app:assembleDebug
```

## 分支与提交

- 从 `main` 切分支：`feat/xxx`、`fix/xxx`、`docs/xxx`
- 提交信息用祈使句，一行说清做了什么：

```
agent: 工具调用失败时保留原始错误输出
ui: 聊天页工具卡片默认折叠
```

- 一个 PR 只做一件事。功能 PR 请附上真机截图或录屏。

## 代码约定

- **Kotlin 官方风格**（`kotlin.code.style=official`），4 空格缩进。
- 注释和 UI 文案用**中文**；标识符、日志、提交信息用英文。
- 不做过度抽象。这个项目里 `Tools.kt` 就是一个大 `when`，它跑得很好。
- 新工具必须在 `Prompts.kt` 里登记，否则模型看不到。

## 加新工具

1. 在 `agent/Tools.kt` 的 `toolDefinitions()` 里加 JSON Schema；
2. 在同文件的分发逻辑里实现执行分支；
3. 如果是危险操作（写、删、执行命令），加进危险工具集合，让 `ApprovalGate` 接管；
4. 如果是破坏性写操作，先调用 `UndoStore.backup()`；
5. 更新 README 的工具表。

## 加新依赖

**先开 issue 讨论。** 这个项目刻意保持依赖精简，目前只有 11 个运行时依赖。判断标准：

- 能不能用 JDK / Android SDK 自带能力实现？（例如 OOXML 解析就是用 `java.util.zip` 手写的，没引 POI）
- 许可证是否与 Apache-2.0 兼容？**LGPL / GPL 的库不要引**（见 THIRD-PARTY.md 里对 Operit 的说明）。
- 体积多大？APK 已经因为 PDFBox 变大了，别再随便加。

加完依赖后，**必须同步更新三处**：

1. `app/build.gradle.kts`
2. `app/src/main/java/app/kite/agent/data/OpenSource.kt`（应用内许可页的数据源）
3. `THIRD-PARTY.md`

## 安全相关

- 不要提交 `keystore.properties`、`local.properties`、`*.keystore`、`*.jks`。
- 不要硬编码 API Key、Token、内网地址。
- 涉及权限、文件访问、Shell、无障碍的改动，请在 PR 描述里说明**风险**和**用户可见的后果**。
- 发现漏洞请走私密渠道，见 [SECURITY.md](SECURITY.md)，不要开公开 issue。

## 两个版本

改动时注意两套构建：

- `release` → Google Play 版，6 个基础权限，**不能**依赖 root / 所有文件访问 / 无障碍。
- `personal` → 自用侧载版，54 个权限，可用 Shizuku、无障碍、全盘文件。

个人版专属代码请放在 `app/src/personal/`，或用 `BuildConfig.PERSONAL` 判断。
**不要让 Play 版编译依赖 personal 的类**，否则 `:app:assembleRelease` 会挂。

## 提交前自检

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:assemblePersonal
```

三个都过再发 PR。CI 也会跑同样的命令。

## 许可证

提交即表示你同意以 [Apache-2.0](LICENSE) 授权你的贡献。
