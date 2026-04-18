# MCP 快速配置与验证

已在项目中写入配置文件：`.cursor/mcp.json`。

## 1) 准备项

- 已安装 Node.js（建议 18+）
- Firecrawl API Key（在 Firecrawl 控制台获取）
- Neon 账号（首次使用会走 OAuth 授权）

## 2) 配置系统环境变量（推荐）

Firecrawl 已改为从系统环境变量读取 `FIRECRAWL_API_KEY`，不再把 key 写进 `.cursor/mcp.json`。

Windows PowerShell（当前会话立即生效）：

```powershell
$env:FIRECRAWL_API_KEY="你的真实Key"
```

Windows PowerShell（写入用户级环境变量，长期生效）：

```powershell
setx FIRECRAWL_API_KEY "你的真实Key"
```

说明：

- 执行 `setx` 后，需要重开一个终端/重启 Cursor 才会生效。
- 若你先用 `$env:...` 验证通过，再执行 `setx` 做持久化，体验最好。

## 3) 重启 Cursor 并启用 MCP

1. 重启 Cursor
2. 打开 Settings -> MCP Servers
3. 确认以下 3 个服务状态是 Enabled:
   - `neon`
   - `magicuidesign-mcp`
   - `firecrawl-mcp`

`neon` 第一次调用时会弹浏览器授权，按提示完成即可。

## 4) 验证提示词（可直接在聊天框粘贴）

### Neon（数据库）

- `请列出我所有 Neon projects。`
- `选择项目 <你的项目名>，列出 main 数据库里的所有表。`
- `在 <你的项目名> 的 <数据库名> 执行 SQL: SELECT now();`

### Magic（设计转代码）

- `用 Magic UI 生成一个响应式登录页，包含邮箱/密码、记住我、忘记密码、登录按钮，使用 React + Tailwind。`
- `把这个页面拆成可复用组件：LoginForm、SocialLoginButtons、AuthLayout。`

### Firecrawl（网页信息抓取）

- `用 firecrawl 抓取 https://example.com，提取正文并输出中文摘要。`
- `爬取 https://example.com/docs 下的主要页面，输出一个包含 title/url/summary 的 JSON 数组。`

## 5) 常见问题

- `npx` 找不到：重装 Node.js，或把 Node 安装目录加入 PATH。
- Firecrawl 报鉴权错误：检查 `FIRECRAWL_API_KEY` 是否正确、是否有额度。
- Neon 无法授权：关闭代理重试，或在浏览器里登录 Neon 后再触发一次请求。
