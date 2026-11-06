// Local mock of an OpenAI-compatible endpoint for Kite UI/agent tests.
// Usage: node server.js
const http = require('http');

const PORT = 8080;

function sendJson(res, code, body) {
  const data = JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(data),
  });
  res.end(data);
}

function sse(res) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'Access-Control-Allow-Origin': '*',
  });
}

function chunk(res, payload) {
  res.write(`data: ${JSON.stringify(payload)}\n\n`);
}

function delta(res, content) {
  chunk(res, {
    id: 'chatcmpl-mock',
    object: 'chat.completion.chunk',
    model: 'mock',
    choices: [{ index: 0, delta: { content }, finish_reason: null }],
  });
}

const server = http.createServer((req, res) => {
  console.log(req.method, req.url);
  if (req.method === 'GET' && req.url === '/v1/models') {
    return sendJson(res, 200, {
      object: 'list',
      data: [
        { id: 'mock', object: 'model', owned_by: 'kite' },
        { id: 'mock-vision', object: 'model', owned_by: 'kite' },
        { id: 'mock-reasoner', object: 'model', owned_by: 'kite' },
      ],
    });
  }

  if (req.method === 'POST' && req.url === '/v1/chat/completions') {
    let body = '';
    req.on('data', (c) => { body += c; });
    req.on('end', () => {
      let parsed = {};
      try { parsed = JSON.parse(body); } catch (e) { return sendJson(res, 400, { error: 'bad json' }); }
      console.log('reasoning_effort=', parsed.reasoning_effort, 'reasoning=', JSON.stringify(parsed.reasoning || null));
      const messages = parsed.messages || [];
      const hasToolResult = messages.some((m) => m.role === 'tool');
      console.log('messages', messages.map((m) => m.role).join(','));

      const lastUser = [...messages].reverse().find((m) => m.role === 'user');
      const userText = typeof lastUser?.content === 'string'
        ? lastUser.content
        : JSON.stringify(lastUser?.content || '');
      console.log('user content', userText.slice(0, 180));

      let tool = {
        name: 'write_file',
        arguments: JSON.stringify({ path: 'hello.txt', content: '你好，Kite！\n这是工具写出来的文件。' }),
      };
      if (/device|设备|手机信息|系统信息/i.test(userText)) {
        tool = { name: 'device_info', arguments: '{}' };
      } else if (/search|搜索/i.test(userText)) {
        tool = { name: 'web_search', arguments: JSON.stringify({ query: 'Android 15 edge-to-edge', max: 4 }) };
      } else if (/apps|应用列表|装了什么/i.test(userText)) {
        tool = { name: 'list_apps', arguments: '{}' };
      } else if (/speak|朗读/i.test(userText)) {
        tool = { name: 'speak', arguments: JSON.stringify({ text: '你好，我是 Kite。' }) };
      } else if (/mcp/i.test(userText)) {
        tool = { name: 'mcp__mcp__echo', arguments: JSON.stringify({ text: 'hello from MCP' }) };
      } else if (/subagent/i.test(userText)) {
        tool = { name: 'spawn_subagent', arguments: JSON.stringify({ task: '写一个 hello.txt' }) };
      } else if (/plan|todo|计划/i.test(userText)) {
        tool = {
          name: 'todo_write',
          arguments: JSON.stringify({
            items: [
              { text: '读取需求', status: 'done' },
              { text: '写 hello.txt', status: 'in_progress' },
              { text: '检查结果', status: 'pending' },
            ],
          }),
        };
      } else if (/browser/i.test(userText)) {
        if (/read|读取/i.test(userText)) {
          tool = { name: 'browser_read', arguments: '{}' };
        } else if (/click|点击/i.test(userText)) {
          tool = { name: 'browser_click', arguments: JSON.stringify({ selector: 'a' }) };
        } else {
          tool = { name: 'browser_open', arguments: JSON.stringify({ url: 'https://example.com' }) };
        }
      } else if (/resource/i.test(userText)) {
        tool = { name: 'mcp_list_resources', arguments: '{}' };
      } else if (/prompt/i.test(userText)) {
        tool = { name: 'mcp_list_prompts', arguments: '{}' };
      }

      if (!hasToolResult) {
        sse(res);
        chunk(res, {
          id: 'chatcmpl-mock',
          object: 'chat.completion.chunk',
          model: 'mock',
          choices: [{ index: 0, delta: { role: 'assistant' }, finish_reason: null }],
        });
        chunk(res, {
          id: 'chatcmpl-mock',
          object: 'chat.completion.chunk',
          model: 'mock',
          choices: [{
            index: 0,
            delta: {
              tool_calls: [{
                index: 0,
                id: 'call_mock_1',
                type: 'function',
                function: {
                  name: tool.name,
                  arguments: tool.arguments,
                },
              }],
            },
            finish_reason: null,
          }],
        });
        chunk(res, {
          id: 'chatcmpl-mock',
          object: 'chat.completion.chunk',
          model: 'mock',
          choices: [{ index: 0, delta: {}, finish_reason: 'tool_calls' }],
        });
        res.write('data: [DONE]\n\n');
        return res.end();
      }

      sse(res);
      const text = '已经写好了。文件在工作区的 hello.txt，内容是一句问候。';
      let i = 0;
      const timer = setInterval(() => {
        if (i < text.length) {
          delta(res, text.slice(i, i + 2));
          i += 2;
        } else {
          chunk(res, {
            id: 'chatcmpl-mock',
            object: 'chat.completion.chunk',
            model: 'mock',
            choices: [{ index: 0, delta: {}, finish_reason: 'stop' }],
          });
          res.write('data: [DONE]\n\n');
          res.end();
          clearInterval(timer);
        }
      }, 40);
      res.on('close', () => clearInterval(timer));
    });
    return;
  }

  sendJson(res, 404, { error: 'not found' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`mock provider on http://127.0.0.1:${PORT}/v1`);
});
