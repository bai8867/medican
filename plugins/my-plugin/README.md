# my-plugin

Local plugin scaffold for publish/testing in this repository.

## Included

- `.codex-plugin/plugin.json` plugin manifest
- `skills/` example skill
- `hooks.json` plus `hooks/` script
- `.mcp.json` MCP server mapping placeholder
- `.app.json` app integration placeholder
- `assets/` placeholder directory for icon/logo/screenshots

## Quick test

1. Ensure `.agents/plugins/marketplace.json` includes `my-plugin`.
2. Reload Cursor/Codex so plugin manifests are re-indexed.
3. Trigger the sample skill prompt from plugin UI/composer.
4. End a session to verify hook execution logs.
