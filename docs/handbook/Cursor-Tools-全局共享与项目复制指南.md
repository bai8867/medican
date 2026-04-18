# Cursor Tools 全局共享与项目复制指南

本文档用于落地“全局共享 + 项目按需复制”的双层配置模式。

## 1) 全局共享（推荐先做）

将基础 MCP 能力放在用户目录：

- Windows: `%USERPROFILE%\.cursor\mcp.json`

可直接使用仓库模板 `templates/cursor-tools/base/mcp.json` 作为全局基线，包含：

- `perplexity`（官方 Perplexity MCP）
- `gemini`（Gemini MCP，社区实现）
- `playwright`（浏览器自动化）
- `firecrawl-mcp`（网页抓取与搜索）

## 2) 项目按需复制（场景化）

本仓库提供 3 种场景模板：

- `base`: 通用全量能力
- `research`: 偏研究检索（Perplexity + Gemini + Firecrawl）
- `browser-heavy`: 偏浏览器自动化（Playwright + Perplexity + Gemini）

执行脚本将模板复制到目标项目：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\apply-cursor-tools-template.ps1 -TargetProjectPath "D:\path\to\your-project" -Scenario base -Mode merge
```

参数说明：

- `-Scenario`: `base | research | browser-heavy`
- `-Mode`:
  - `merge`（默认）：只覆盖同名 `mcpServers`，保留目标项目其它 server
  - `replace`：整体替换目标项目 `.cursor/mcp.json`（脚本会自动备份）

## 3) 密钥管理

复制模板后，目标项目会生成 `.cursor-tools.env.example`，按需填入：

- `PERPLEXITY_API_KEY`
- `GEMINI_API_KEY`
- `FIRECRAWL_API_KEY`（可选）

建议：

- 不要把真实密钥写入 Git 仓库。
- 项目内使用 `.env`/系统环境变量注入，示例文件仅用于共享字段结构。

## 4) 验证

1. 重启 Cursor 或在 MCP 面板重新加载 server。  
2. 在 Cursor MCP 列表确认 `perplexity`、`gemini`、`playwright`、`firecrawl-mcp` 状态。  
3. 分别执行简单任务验证：
   - Perplexity: “搜索今天的 TypeScript 更新”
   - Gemini: “总结当前仓库模块结构”
   - Playwright: “打开 example.com 并截图”

## 5) 风险提示

- `gemini-mcp-server` 为社区实现，升级后参数或行为可能变化。
- 首次运行 `npx` 可能较慢，且依赖网络与 npm 可用性。
- 不同项目若已存在同名 MCP server，`merge` 模式会被模板值覆盖。
