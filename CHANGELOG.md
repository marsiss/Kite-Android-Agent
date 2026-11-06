# 更新日志

本文件记录 Kite 的所有重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

### 计划中

- 语音唤醒词与连续语音对话
- MCP OAuth 自动授权（当前为 Header Token）
- 浏览器截图、多标签、下载管理
- 代码 diff 预览与语法高亮
- 多工作区切换

---

## [0.1.0] — 2026-11-05

首个可用版本。可安装、可上架测试，Play 版与个人版双轨构建。

### 新增

**对话与模型**
- 流式对话（SSE），`reasoning_content` 思考过程折叠显示
- OpenAI 兼容接口，预设 DeepSeek / OpenAI / OpenRouter / 硅基流动 / 智谱 / Ollama，支持自定义 Base URL 与端口
- 「获取模型」拉取 `/models` 列表供选择
- 思考模式：关 / 低 / 中 / 高（`reasoning_effort`）
- 图片附件：相册选图，压缩后按 OpenAI 多模态格式发送
- 会话本地 JSON 持久化，最近任务列表，长按删除，会话重命名
- 任务执行期间前台服务保活，离开 App 不中断

**Agent 循环**
- 多轮工具调用循环，工具结果回注模型
- 子智能体 `spawn_subagent`（子智能体不可再嵌套）
- 任务计划 `todo_write`，聊天页顶部可折叠进度面板
- 上下文 token 估算，超过约 24k 自动压缩（保留最近 30 条）
- 工具调用卡片：参数、输出、成功/失败状态可展开

**文件与工作区**
- `list_dir` / `read_file` / `write_file` / `replace_in_file` / `grep`
- `move_path` / `copy_path` / `delete_path` / `make_dir` / `file_info`
- 路径穿越拦截，工作区隔离
- 一级撤销：破坏性写操作前自动备份，`undo_last` 还原
- **文档解析**：PDF（PDFBox-Android）、DOCX / XLSX / PPTX（自研 OOXML 解析）、纯文本回退，上限 12 万字符

**系统能力**
- `run_shell`（默认关闭）：普通沙盒 / Root / Shizuku 三种级别
- 手机工具：`device_info` / `list_apps` / `open_app` / `open_url` / `clipboard_read` / `clipboard_write` / `share_text` / `notify` / `speak` / `get_location` / `read_contacts`
- 需确认的系统界面操作：`compose_sms` / `dial_number` / `create_calendar_event` / `set_alarm`
- WebView 浏览器自动化：`browser_open` / `read` / `click` / `type` / `back` / `close`，支持下载到公共下载目录
- **无障碍跨 App 自动化**（仅个人版）：`screen_read` / `tap` / `type` / `scroll` / `back` / `home`

**扩展机制**
- **MCP**：Streamable HTTP 与旧版 SSE，自动 `initialize` + `tools/list`，工具以 `mcp__<server>__<tool>` 注入；支持 resources / prompts；凭证 Keystore 加密
- **技能（Skills）**：`.kite/skills/<name>/SKILL.md`（带 frontmatter），配套 `list_skills` / `read_skill` / `write_skill`，技能清单动态注入系统提示词
- **定时任务**：WorkManager 后台执行 Agent 回合，支持重复间隔与完成通知
- **工具审批门**：危险工具挂起等用户确认，120 秒超时按拒绝处理

**界面**
- 原创视觉：黑曜石底 + 红色扫描灯（KITT 风格致敬）、风筝菱形标记
- 首页 / 对话 / 设置 / 权限 / MCP / 技能 / 定时任务 / 开源许可 路由
- 轻量 Markdown 渲染与代码块
- 系统分享入口：其他 App 分享文本可直接开新会话
- 长按麦克风连续语音输入

**隐私与合规**
- API Key 与 MCP 凭证使用 Android Keystore AES-GCM 加密
- 无统计 SDK、无广告、无账号体系、无云同步
- 应用内开源许可页（`设置 → 关于 → 开源许可`）
- `PRIVACY.md` 与 `privacy.html`

**构建**
- 双构建变体：`release`（Play，6 权限）与 `personal`（侧载，54 权限）
- Play 商店素材生成脚本（`make-play-assets.py`）
- 本地模型 Mock 服务（`mock/server.js`）

### 已知限制

- 华为 NOP-AN00（Kirin 9000，锁定 BL）**无法获取 root**；Shizuku 上限为 UID 2000
- 后台虚拟屏幕需要 `ADD_TRUSTED_DISPLAY`，非 root 不可得
- 音频 / 视频转写未实现
- Office / PDF 仅支持文本提取，不做保格式编辑
- MCP 暂无 OAuth，仅 Header Token
- 仅中文界面

### 安全

- 报告漏洞请见 [SECURITY.md](SECURITY.md)，不要开公开 issue。

---

[未发布]: https://github.com/marsiss/Kite-Android-Agent/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/marsiss/Kite-Android-Agent/releases/tag/v0.1.0
