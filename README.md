# medican

校园药膳推荐相关工程。**产品主体为浏览器 Web 应用**（`tcm-diet-frontend`，Vue 3 + Vite + Element Plus / Vant）；**`campus-diet-backend`** 为 Spring Boot 2.7（Java 11）REST API。

## 当前主路线与文档索引（先看这里）

单人维护时仍建议固定「默认看哪几份清单」，避免多份分析文档互相抢注意力：

| 用途 | 文档 |
|------|------|
| 前端/测试缺口、构建与仓库卫生（C-1） | [`docs/C-1-未完成事项.md`](docs/C-1-未完成事项.md) |
| CI、typecheck、后端 Wrapper、安全基线进度（路径 2） | [`路径2-未完成项清单.md`](路径2-未完成项清单.md) |
| 问题归纳与优先级（与上两者互补） | [`优化pro.md`](优化pro.md) |
| 本地 JDK/Maven、端口、脚本约定 | [`AGENTS.md`](AGENTS.md) |
| AI Skill 与问答演进专项 | [`AI问答与Skill集成优化方向.md`](AI问答与Skill集成优化方向.md) |

## 仓库结构（常用）

| 路径 | 说明 |
|------|------|
| `tcm-diet-frontend/` | 前端源码、`npm run dev` / `build` |
| `campus-diet-backend/` | 后端源码、Maven / `mvnw` |
| `scripts/` | `Use-RepoJavaMaven.ps1`、`Run-BackendTests.ps1`、数据脚本等 |
| `maven-settings-d.xml` | 本地 Maven 仓库等配置（与后端 `-s` 参数一致） |
| `mysql-dev-my.ini` | 本机 MySQL 开发实例配置（数据目录勿提交） |
| `大模型调用调试说明.md` | 内网/本机大模型联调说明 |

## 快速启动 Web（推荐顺序）

1. **MySQL**：默认连接 **`127.0.0.1:3306`**、库名 **`tcm_diet`**。账号密码由环境变量覆盖，**`application.yml` 默认用户名为 `tcm_app`**、密码占位 **`change-this-db-password`**（请本机改为真实密码并建库授权，或设置 **`MYSQL_USERNAME` / `MYSQL_PASSWORD`** 等，见后端配置节）。首次建库与表结构见后端 **`classpath:db/schema.sql`**，启动时可选执行 **`data.sql`**（可用 **`SPRING_SQL_INIT_MODE=never`** 关闭）。本机可用 `winget install -e --id Oracle.MySQL` 安装后，在仓库根用 **`mysql-dev-my.ini`**（数据目录建议在 **`.devtools/mysql-data`** 等本地路径、勿提交）配合 **`mysqld --defaults-file=...`**；空数据目录需一次 **`mysqld --defaults-file=mysql-dev-my.ini --initialize-insecure`** 再建用户与 **`CREATE DATABASE tcm_diet`**。也可双击 **`启动MySQL开发实例.bat`**（在已初始化且 3306 未被占用时）。

2. **后端**：默认 **`http://127.0.0.1:11888`**，健康检查 **`/health`**。默认仅监听 **`127.0.0.1`**；若需局域网访问后端，设置环境变量 **`SERVER_ADDRESS=0.0.0.0`**。Windows 根目录双击 **`启动后端.bat`**，或在 **`campus-diet-backend`** 执行 **`mvn`** / **`.\mvnw.cmd`**：`-s ..\maven-settings-d.xml spring-boot:run`。Maven 本地仓库见 **`maven-settings-d.xml`** 的 `localRepository`。JDK / Maven 约定见 **`AGENTS.md`**。  
   **无全局 Maven 时**：本仓库已包含 **Maven Wrapper**（`campus-diet-backend/mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`），仅需 **JDK 11** 与网络（首次会下载 Maven 发行包到用户目录 **`.m2/wrapper`**）；CI 与本地均可 **`cd campus-diet-backend` → `./mvnw` / `mvnw.cmd test`**。  
   **种子账号**：`SeedUsersRunner` 会确保 **`demo`** 等用户存在；密码见 **`campus-diet-backend/src/main/resources/application.yml`** 的 **`campus.seed-users.demo-password`**（默认 **`SeedDemo#2026!`**）、**`campus.seed-users.admin-password`**（可用环境变量 **`SEED_ADMIN_PASSWORD`** / **`SEED_DEMO_PASSWORD`** 等覆盖，键名以 yml 为准）。生产勿保留默认种子口令。

   **环境变量速查（与 `application.yml` 一致）**：`SERVER_ADDRESS`；`MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` 或整段 **`SPRING_DATASOURCE_URL`**；`SPRING_SQL_INIT_MODE`（如已有库设 **`never`**）；种子口令 **`SEED_ADMIN_PASSWORD`**、**`SEED_DEMO_PASSWORD`**、**`SEED_CANTEEN_PASSWORD`**、**`SEED_STUDENT_PASSWORD`**；联调 JWT 密钥 **`CAMPUS_JWT_SECRET`**；前端跨域 **`CAMPUS_CORS_ALLOWED_ORIGIN_PATTERNS`**。

3. **前端**：开发服务 **`http://localhost:11999`**（`vite.config.js` 中 **`strictPort: true`**）。Windows 根目录双击 **`启动前端.bat`**（会检查 **`http://localhost:11888/health`**，可用 **`SKIP_BACKEND_CHECK=1`** 跳过）。前端使用 **PATH 中的 Node / npm**（建议 **Node 18+**），**不依赖 Conda**。默认通过 **Vite 代理**：相对路径 **`/api`** → **`http://127.0.0.1:11888`**（可用 **`VITE_PROXY_TARGET`** 改代理目标）；**不再依赖在脚本里写死 `VITE_API_BASE_URL`** 即可联调本机后端。

手动启动前端示例：

```bash
cd tcm-diet-frontend
npm install
npm run dev
```

### 后端 JDK / Maven（Windows）

- **推荐单源工具目录**：例如 **`C:\dev`**，放置 **`jdk-11*`** 与 **`apache-maven-3.9.14`**；设置 **`MEDICAN_DEV_TOOLS=C:\dev`** 后，**`启动后端.bat`** 与 **`scripts/Use-RepoJavaMaven.ps1`** 只从该目录解析 JDK/Maven，可避免与仓库内 **`dev-tools`** / **`.devtools`** 混用。
- **未设置 `MEDICAN_DEV_TOOLS`**：仍按 **`.devtools` → `dev-tools` → `C:\dev`** 等顺序探测（见脚本）。
- **PowerShell**：跑 **`mvn`** 前先 **`.\scripts\Use-RepoJavaMaven.ps1`**；自动化建议一行命令，例如：  
  `powershell -NoProfile -ExecutionPolicy Bypass -Command ". '.\scripts\Use-RepoJavaMaven.ps1'; cd campus-diet-backend; mvn -B -ntp -s ..\maven-settings-d.xml <goal>"`  
  Cursor 可选用终端配置 **「PowerShell (Medican JDK11)」**（见 **`.vscode/settings.json`**，默认 **`MEDICAN_DEV_TOOLS=C:\dev`**，可按本机修改）。
- **无便携 `mvn` 时**：在 **`campus-diet-backend`** 使用 **`.\mvnw.cmd -s ..\maven-settings-d.xml …`**（首次可能下载到用户目录 **`.m2\wrapper`**）。

### 大模型（可选）

默认 OpenAI 兼容地址由 **`LLM_HTTP_HOST`** / **`LLM_HTTP_PORT`** 等拼接（与《大模型调用调试说明.md》一致，常见为内网 **`ds.local.ai:30080`**）。可用 **`LLM_URL`** 覆盖完整 URL；本机 **Ollama** 等可配合设置 **`LLM_MODEL`**、**`LLM_API_KEY`**（可空）。前端开发模式下默认启用 **AI 相关 Vite 插件 mock**；若需关闭，在 **`tcm-diet-frontend`** 环境文件中设 **`VITE_AI_MOCK=0`**。

## 常用命令

- **后端单测（Windows）**：仓库根  
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-BackendTests.ps1`  
  仅安全基线相关可加 **`-SecurityOnly`**。
- **前端**：`npm run lint`、`npm run test`、`npm run test:e2e`（Playwright，需本机浏览器依赖）。**CI** 在 **`npm run build`** 之后跑 **`npm run test:e2e:ci`**（脚本会检查 `dist/`，用 **preview + 127.0.0.1:11998** 跑 `e2e/ci-gate.spec.js`，不依赖后端）；本地请先 **`npm run build`** 再执行 **`npm run test:e2e:ci`**。
- **依赖安全**：对前端执行 `npm audit --registry https://registry.npmjs.org`；**CI** 已跑 **`npm audit --audit-level=critical`**（与脚本 **`npm run audit:ci`** 等价）。另见 GitHub Actions **`Frontend supply chain audit`**（`.github/workflows/frontend-supply-chain-audit.yml`）：**手动触发**与**每周定时**复跑同一阈值，不重复 lint/build。`xlsx`（SheetJS 社区版）在 advisory 中常为 **high** 且无无破环升级路径，管理端导出仅处理可信表格数据，若需零 advisory 可再评估换用 `exceljs` 等（与体积/ API 取舍）。
- **进程内指标（本地）**：管理端具备内容管理权限的会话下可拉取 **`GET /api/admin/dashboard/runtime-metrics`** 与 **`/observability-summary`**；字段说明见 **`docs/observability-metrics.md`**（与 `RuntimeMetricService` 对齐）。

## 其它

- 单人维护、文档风格约定见 **`.cursor/rules/solo-developer.mdc`**。
- 智能体读仓库约定见 **`AGENTS.md`**。
