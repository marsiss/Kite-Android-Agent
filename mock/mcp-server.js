// Minimal MCP server for Kite tests.
// Supports Streamable HTTP at /mcp and legacy SSE at /sse + /messages.
const http = require('http');
const { URL } = require('url');

const PORT = 8090;
let sseResponse = null;
let nextSseId = 1;

function json(res, code, body, headers = {}) {
  const text = JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(text),
    'Mcp-Session-Id': 'mock-session',
    ...headers,
  });
  res.end(text);
}

function tools() {
  return [
    {
      name: 'echo',
      description: '回显一段文本',
      inputSchema: {
        type: 'object',
        properties: { text: { type: 'string', description: '要回显的文本' } },
        required: ['text'],
      },
    },
    {
      name: 'time',
      description: '返回当前时间',
      inputSchema: { type: 'object', properties: {} },
    },
  ];
}

function handleRpc(msg) {
  if (!msg || msg.jsonrpc !== '2.0') return { error: 'bad rpc' };
  const id = msg.id;
  switch (msg.method) {
    case 'initialize':
      return {
        jsonrpc: '2.0',
        id,
        result: {
          protocolVersion: '2024-11-05',
          capabilities: { tools: {} },
          serverInfo: { name: 'Mock MCP', version: '1.0.0' },
        },
      };
    case 'notifications/initialized':
      return null;
    case 'tools/list':
      return { jsonrpc: '2.0', id, result: { tools: tools() } };
    case 'resources/list':
      return {
        jsonrpc: '2.0',
        id,
        result: {
          resources: [
            { uri: 'mock://hello', name: 'Hello', mimeType: 'text/plain', description: '一段测试资源' },
          ],
        },
      };
    case 'resources/read':
      return {
        jsonrpc: '2.0',
        id,
        result: {
          contents: [{ uri: msg.params?.uri || 'mock://hello', mimeType: 'text/plain', text: 'hello resource from MCP' }],
        },
      };
    case 'prompts/list':
      return {
        jsonrpc: '2.0',
        id,
        result: {
          prompts: [{ name: 'greet', description: '打个招呼' }],
        },
      };
    case 'prompts/get':
      return {
        jsonrpc: '2.0',
        id,
        result: {
          messages: [{ role: 'user', content: { type: 'text', text: 'Hello from MCP prompt' } }],
        },
      };
    case 'tools/call': {
      const name = msg.params?.name;
      const args = msg.params?.arguments || {};
      if (name === 'echo') {
        return {
          jsonrpc: '2.0',
          id,
          result: { content: [{ type: 'text', text: `echo: ${args.text || ''}` }], isError: false },
        };
      }
      if (name === 'time') {
        return {
          jsonrpc: '2.0',
          id,
          result: { content: [{ type: 'text', text: `now: ${new Date().toISOString()}` }], isError: false },
        };
      }
      return {
        jsonrpc: '2.0',
        id,
        result: { content: [{ type: 'text', text: `unknown tool: ${name}` }], isError: true },
      };
    }
    default:
      return { jsonrpc: '2.0', id, error: { code: -32601, message: `method not found: ${msg.method}` } };
  }
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  console.log(req.method, url.pathname);

  if (req.method === 'GET' && url.pathname === '/sse') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
      'Mcp-Session-Id': 'mock-sse',
    });
    sseResponse = res;
    res.write(`event: endpoint\ndata: /messages?sessionId=mock-sse\n\n`);
    req.on('close', () => { if (sseResponse === res) sseResponse = null; });
    return;
  }

  if (req.method === 'POST' && url.pathname === '/messages') {
    let body = '';
    req.on('data', (c) => { body += c; });
    req.on('end', () => {
      let msg;
      try { msg = JSON.parse(body); } catch (e) { return json(res, 400, { error: 'bad json' }); }
      const reply = handleRpc(msg);
      res.writeHead(202, { 'Content-Type': 'text/plain' });
      res.end('accepted');
      if (reply && sseResponse) {
        sseResponse.write(`event: message\ndata: ${JSON.stringify(reply)}\n\n`);
      }
    });
    return;
  }

  if (req.method === 'POST' && url.pathname === '/mcp') {
    let body = '';
    req.on('data', (c) => { body += c; });
    req.on('end', () => {
      let msg;
      try { msg = JSON.parse(body); } catch (e) { return json(res, 400, { error: 'bad json' }); }
      const reply = handleRpc(msg);
      if (!reply) {
        res.writeHead(202, { 'Content-Type': 'text/plain' });
        return res.end('accepted');
      }
      json(res, 200, reply);
    });
    return;
  }

  if (req.method === 'GET' && url.pathname === '/') {
    return json(res, 200, { ok: true, mcp: '/mcp', sse: '/sse' });
  }

  json(res, 404, { error: 'not found' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`mock MCP on http://127.0.0.1:${PORT}/mcp and /sse`);
});
