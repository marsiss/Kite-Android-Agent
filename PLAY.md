# Google Play 上架资料 — Kite 0.1.0

> 状态：可提交内部测试。下面标 `[ ]` 的项目需要你在 Play Console / 域名侧完成。

## 基本信息

| 项 | 值 |
|---|---|
| 应用名（工作名） | Kite 智能体 |
| 包名 | `app.kite.agent` |
| 版本 | 0.1.0（versionCode 1） |
| 类别 | 效率（Productivity） |
| 价格 | 免费 |
| 广告 | 无 |
| 内购 | 无 |
| 目标 API | 35 |
| 最低 API | 26 |
| 签名 | `kite-release.keystore`（请备份） |
| AAB | `dist/kite-0.1.0-release.aab` |
| APK | `dist/kite-0.1.0-release.apk` |
| 个人版 APK | `dist/kite-0.1.0-personal.apk`（**不要上传 Play**） |

> 只有 `release` AAB 用于上架。`personal` 版本声明了短信、通话记录、通讯录、所有文件访问等敏感权限，仅用于自己侧载，不能提交 Google Play。

Release 版实际权限只有 6 个：

```
INTERNET
ACCESS_NETWORK_STATE
POST_NOTIFICATIONS
FOREGROUND_SERVICE
FOREGROUND_SERVICE_DATA_SYNC
com.android.alarm.permission.SET_ALARM
```

**上架前确认**：`Kite` 这个名字在 Google Play 是否重名/侵权；包名一旦上传不可更改。

## 商店文案（中文）

**短描述（80 字符内）**

> 在手机上读写文件、执行命令、抓取网页的 AI 智能体。

**完整描述**

```
Kite 是一个装在 Android 手机里的 AI 智能体，不只是一个聊天框。

你给它一个任务，它会自己读文件、改代码、执行命令、抓取网页，然后把结果交给你。

· 流式回复，思考过程可折叠
· 工具调用卡片，参数和输出都能展开
· 会搜索、抓网页、调 API、读写文件、执行命令
· 会看图片（需要视觉模型）
· 支持任意 OpenAI 兼容服务，可填端口；能拉取模型列表
· 思考模式：关 / 低 / 中 / 高
· 会读手机信息、打开应用、发通知、朗读、看位置和通讯录
· 支持 MCP：接入远程工具服务器，含 resources / prompts
· 会开可见浏览器：打开网页、读正文、点击、填表
· 复杂任务会写计划，也能派子智能体独立处理
· 支持语音输入
· 会话保存在本机，随时继续
· 工作区文件隔离，Shell 需要手动开启
· API Key 用 Android Keystore 加密
· 支持 DeepSeek / OpenAI / OpenRouter 等兼容接口

Kite 采用「自带 Key」模式：你填写自己的模型服务地址和 Key，请求直接发给你选择的服务商，不经过我们的服务器。

没有账号，没有广告，没有统计 SDK。
```

**关键词**：AI agent, coding, Android, DeepSeek, OpenAI, productivity

## 图形素材

| 素材 | 文件 | 规格 |
|---|---|---|
| 应用图标 | `play/icon-512.png` | 512×512 PNG |
| 特色图片 | `play/feature-1024x500.png` | 1024×500 PNG |
| 手机截图 1 | `play/screenshot-home.png` | 1080×1920 |
| 手机截图 2 | `play/screenshot-chat.png` | 1080×1920（计划面板） |
| 手机截图 3 | `play/screenshot-settings.png` | 1080×1920（自定义接口 / 思考模式） |
| 手机截图 4 | `play/screenshot-models.png` | 1080×1920（模型列表） |
| 手机截图 5 | `play/screenshot-mcp.png` | 1080×1920（MCP） |
| 手机截图 6 | `play/screenshot-browser.png` | 1080×1920（浏览器自动化） |
| 手机截图 7 | `play/screenshot-workspace.png` | 1080×1920（工作区文件） |
| 手机截图 8 | `play/screenshot-shizuku.png` | 1080×1920（Shizuku shell） |

素材可用 `python tools/make-play-assets.py` 重新生成（需要 Pillow 和中文字体）。
截图是模拟器上 1080×1920 的真实界面；示例会话是本地数据，不含真实 Key。

## App Access（审核访问）

Kite 需要用户自备 OpenAI 兼容 API Key，审核时需要提供可用的测试凭据：

- 在 Play Console → App content → App access 选择「所有功能都需要登录/凭据」。
- 提供：
  - 接口地址：`https://api.deepseek.com/v1`（或你提供的测试网关）
  - 模型：`deepseek-chat`
  - 测试 Key：`[待填]`
- 审核步骤：
  1. 打开 App → 右上角设置
  2. 填接口地址 / 模型 / Key → 保存 → 测试
  3. 返回首页，输入「做一个番茄钟网页」→ 发送
  4. 观察工具卡片与最终回复

> 如果不想暴露真实 Key，建议部署一个限额的测试网关，只允许 `deepseek-chat` 与低额度。

## Data safety（数据安全）

| 问题 | 建议填写 |
|---|---|
| 是否收集数据？ | 否（开发者不收集） |
| 是否共享数据？ | 是：用户内容（提示词、工作区文件片段）共享给用户自行配置的模型服务商，用于提供核心功能 |
| 是否加密传输？ | 是（HTTPS）；用户可自行配置 HTTP 本地地址，App 会提示风险 |
| 能否删除数据？ | 卸载 App 即删除本机会话/Key；工作区文件在应用私有目录，一并删除 |
| 是否含广告/统计？ | 否 |
| 是否收集位置/联系人/设备 ID？ | 否 |

Play 的 Data safety 以「App 是否把数据传出设备」为准；Kite 的模型请求由用户配置，开发者不接收数据。建议在隐私政策中写清楚。

## 内容分级

- 问卷建议：效率工具 / 无用户生成内容社区 / 无赌博 / 无暴力 / 无成人内容。
- 预期分级：Everyone 或 Everyone 10+。
- 注意：模型可生成任意文本与代码，Shell 可执行命令；在描述中明确「开发者工具，需用户自行配置模型」。

## 目标受众

- 13 岁以上；不适合儿童。
- 不需要「面向儿童」计划。

## 隐私政策

- 需要可公开访问的 HTTPS URL。
- 文本见 `PRIVACY.md`，可直接部署 `privacy.html` 到 GitHub Pages / Cloudflare Pages。
- 如果本仓库是公开的，最省事的做法是开 GitHub Pages：
  **Settings → Pages → Source: Deploy from a branch → `main` / `root`**，
  然后填 `https://marsiss.github.io/Kite-Android-Agent/privacy.html`。
- 把 `[联系邮箱]`、`[开发者名称]` 替换成真实信息（`PRIVACY.md` 和 `privacy.html` 两处都要改）。
- Play Console → App content → Privacy policy 填 URL。

## 上架检查清单

- [x] 签名 AAB
- [x] targetSdk 35
- [x] 512 图标
- [x] 1024×500 特色图片
- [x] 8 张手机截图
- [x] 中文商店文案
- [ ] 隐私政策 URL
- [ ] Data safety 表单
- [ ] 内容分级问卷
- [ ] App access 测试 Key
- [ ] 应用名称/商标确认
- [ ] 内部测试轨道上传并真机验收
- [ ] 生产轨道发布

## 内部测试建议

1. 上传 `dist/kite-0.1.0-release.aab` 到 Internal testing。
2. 用测试 Key 在真机走一遍：设置 → 保存 → 测试 → 新建任务 → 工具调用 → 杀进程重开 → 会话仍在。
3. 检查 Android 13+ 通知权限、Android 14 前台服务、Android 15 edge-to-edge。
4. 记录崩溃与 ANR；`adb logcat` 过滤 `app.kite.agent`。

## 版本迭代

- 每次上传递增 `versionCode`。
- 修改 `app/build.gradle.kts` 的 `versionCode` / `versionName`。
- 保留 `kite-release.keystore` 与密码；丢失将无法更新已上架应用。
