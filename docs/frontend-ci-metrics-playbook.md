# 前端 CI 指标闭环（P2-1）

**Owner**：前端负责人（单人项目可为本人）。**目标**：用 CI artifact 与 `FRONTEND_*` 阈值做「记录 → 对照 → 超阈处理 → 收紧」的可重复闭环。

## 1. CI 产物在哪

- Workflow：**`.github/workflows/ci.yml`** → job **Frontend Checks**。
- Artifact 名：**`frontend-ci-metrics`**（解压后含）：
  - `build-with-timing.log`：含 `FRONTEND_BUILD_SECONDS=…` 与可选 `FRONTEND_BUILD_GUARD` 文案。
  - `dist-size-report.log`：含 `DIST_TOTAL_BYTES=…`、`DIST_LARGEST_JS_BYTES=` 与 `du`/`find` 摘要。

## 2. 当前阈值（与 workflow 对齐）

| 变量 | 含义 | CI 当前值（见 workflow） |
|------|------|-------------------------|
| `FRONTEND_BUILD_MAX_SECONDS` | 构建耗时上限 | `300` |
| `FRONTEND_DIST_MAX_TOTAL_BYTES` | `dist` 总字节上限 | `9000000` |
| `FRONTEND_DIST_MAX_LARGEST_JS_BYTES` | 最大单 JS 文件字节 | `1048576` |

调阈值时**同时**改 `.github/workflows/ci.yml` 里对应 `env`，并在 PR 说明里写清依据（artifact 中的数值）。

## 3. 推荐操作顺序

1. 在 Actions 中打开一次 **成功** 的 Frontend job，下载 **`frontend-ci-metrics`**。
2. 本地对照：在仓库根执行（路径按解压目录调整）：

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Compare-FrontendCiMetrics.ps1 `
     -BuildLog .\frontend-ci-metrics\build-with-timing.log `
     -DistLog .\frontend-ci-metrics\dist-size-report.log
   ```

   默认使用与 CI 相同的阈值；超阈时脚本 **exit 1**（用于本地预演或自建门禁）。

3. **若超构建耗时**：`vite.config` 拆 chunk、减少全量依赖、或阶段性放宽 `FRONTEND_BUILD_MAX_SECONDS` 并建 issue 跟踪回落。
4. **若超体积**：懒加载路由、分析 `dist-size-report.log` 里 Top JS、或调整 `manualChunks`；再下调 `FRONTEND_DIST_*`。
5. 将本轮 artifact 路径或摘要记入迭代笔记（可选目录：`docs/` 下自建 `build-baselines/`）。

## 4. 本地复现 CI 指标（无 artifact 时）

在 **`tcm-diet-frontend`** 下：

```bash
npm ci
npm run build   # 或 bash ../scripts/run-frontend-build-with-timing.sh
bash ../scripts/report-frontend-dist-size.sh
```

Windows 无 bash 时，以 **WSL / Git Bash** 或依赖 **GitHub Actions** 为准。
