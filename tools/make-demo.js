// Regenerates play/demo-session.json — the fake conversation used for screenshots.
// Run: node tools/make-demo.js
const fs = require('fs');
const path = require('path');

const playDir = path.join(__dirname, '..', 'play');
fs.mkdirSync(playDir, { recursive: true });

const now = Date.now();
const html = `<!doctype html>
<html lang="zh">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>番茄钟</title>
  <style>
    body { margin:0; font-family:system-ui,sans-serif; background:#0b0d11; color:#f4efe6;
           display:grid; place-items:center; height:100vh; }
    .time { font-size:76px; font-weight:800; letter-spacing:-3px; }
    button { border:0; border-radius:16px; padding:12px 18px; margin:4px;
             background:#ff6b3d; color:#0b0d11; font-weight:700; }
  </style>
</head>
<body>
  <div>
    <div class="time" id="t">25:00</div>
    <button onclick="toggle()">开始 / 暂停</button>
    <button onclick="reset()">重置</button>
  </div>
  <script>
    let left = 25 * 60, timer = null;
    const t = document.getElementById('t');
    function draw() { t.textContent = String(Math.floor(left / 60)).padStart(2, '0') + ':' + String(left % 60).padStart(2, '0'); }
    function toggle() {
      if (timer) { clearInterval(timer); timer = null; return; }
      timer = setInterval(() => { if (left > 0) left--; draw(); }, 1000);
    }
    function reset() { clearInterval(timer); timer = null; left = 25 * 60; draw(); }
    draw();
  </script>
</body>
</html>`;

const session = {
  id: 'demo-pomodoro',
  title: '做一个番茄钟网页',
  createdAt: now - 1000 * 60 * 42,
  updatedAt: now - 1000 * 60 * 12,
  messages: [
    {
      id: 'demo-m1',
      role: 'USER',
      content: '做一个番茄钟网页，放在工作区里。要能开始、暂停和重置，手机浏览器直接能打开。',
      createdAt: now - 1000 * 60 * 42,
      error: false,
      toolCalls: [],
    },
    {
      id: 'demo-m2',
      role: 'ASSISTANT',
      content: '',
      createdAt: now - 1000 * 60 * 41,
      error: false,
      toolCalls: [
        {
          id: 'call_demo_1',
          name: 'write_file',
          arguments: JSON.stringify({ path: 'pomodoro/index.html', content: html }),
        },
      ],
    },
    {
      id: 'demo-m3',
      role: 'TOOL',
      content: '已写入 1286 字符到 pomodoro/index.html',
      createdAt: now - 1000 * 60 * 41 + 800,
      error: false,
      toolCalls: [],
      toolCallId: 'call_demo_1',
      toolName: 'write_file',
      toolOk: true,
    },
    {
      id: 'demo-m4',
      role: 'ASSISTANT',
      content: '番茄钟已经做好了，文件在 `pomodoro/index.html`。\n\n包含：\n- 25 分钟专注计时，开始 / 暂停\n- 一键重置\n- 深色界面，手机浏览器直接打开就能用\n\n需要我再加上「专注结束提示音」或「自定义时长」吗？',
      createdAt: now - 1000 * 60 * 40,
      error: false,
      toolCalls: [],
    },
  ],
};

fs.writeFileSync(path.join(playDir, 'demo-session.json'), JSON.stringify(session, null, 2), 'utf8');
console.log('wrote demo session', JSON.stringify(session).length, 'bytes');
