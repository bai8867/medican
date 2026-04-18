---
description: 
alwaysApply: true
---

# AGENTS

<skills_system priority="1">

## Available Skills

<!-- SKILLS_TABLE_START -->
<usage>
When users ask you to perform tasks, check if any of the available skills below can help complete the task more effectively. Skills provide specialized capabilities and domain knowledge.

How to use skills:
- Invoke: `npx openskills read <skill-name>` (run in your shell)
  - For multiple: `npx openskills read skill-one,skill-two`
- The skill content will load with detailed instructions on how to complete the task
- Base directory provided in output for resolving bundled resources (references/, scripts/, assets/)

Usage notes:
- Only use skills listed in <available_skills> below
- Do not invoke a skill that is already loaded in your context
- Each skill invocation is stateless
</usage>

<available_skills>

<skill>
<name>executing-plans</name>
<description>Use when you have a written implementation plan to execute in a separate session with review checkpoints</description>
<location>project</location>
</skill>

<skill>
<name>requesting-code-review</name>
<description>Use when completing tasks, implementing major features, or before merging to verify work meets requirements</description>
<location>project</location>
</skill>

<skill>
<name>systematic-debugging</name>
<description>Use when encountering any bug, test failure, or unexpected behavior, before proposing fixes</description>
<location>project</location>
</skill>

<skill>
<name>verification-before-completion</name>
<description>Use when about to claim work is complete, fixed, or passing, before committing or creating PRs - requires running verification commands and confirming output before making any success claims; evidence before assertions always</description>
<location>project</location>
</skill>

<skill>
<name>writing-plans</name>
<description>Use when you have a spec or requirements for a multi-step task, before touching code</description>
<location>project</location>
</skill>

</available_skills>
<!-- SKILLS_TABLE_END -->

</skills_system>

## Naming Rule Sync

- 文件命名选择：若项目根目录存在 `AGENTS.md`，使用 `AGENTS.md`；否则使用 `agent.md`。

## 仓库与模块速览

- **`tcm-diet-frontend`**：Vue 3 + Vite 6，开发端口 **11999**（`vite.config.js`），`/api` 代理到 **`VITE_PROXY_TARGET` 或 `http://127.0.0.1:11888`**。开发模式默认 **`VITE_AI_MOCK` 未设为 `0`** 时加载 `vite-plugins/aiApiDevMock.js`。脚本：`npm run dev` / `build` / `lint` / `test` / `test:e2e`；`sync:backend-mock-json` 可将前端 mock 同步为后端 `bootstrap/mock-recipes.json`。
- **`campus-diet-backend`**：Spring Boot **2.7.x**，Java **11**。默认 **`server.port=11888`**，**`server.address=127.0.0.1`**（联调外机设 **`SERVER_ADDRESS=0.0.0.0`**）。数据源默认 **`MYSQL_HOST`/`PORT`/`DATABASE`** 与 **`MYSQL_USERNAME`**（默认 **`tcm_app`**）、**`MYSQL_PASSWORD`**；也可用 **`SPRING_DATASOURCE_URL`** 覆盖整段 JDBC。SQL 初始化由 **`SPRING_SQL_INIT_MODE`**（默认非 `never` 时执行 `schema.sql`、`data.sql` 等）控制。大模型：**`LLM_URL`** 或 **`LLM_HTTP_HOST`/`LLM_HTTP_PORT`**、**`LLM_MODEL`**、**`LLM_API_KEY`**、**`LLM_TIMEOUT_MS`**；生产密钥与 JWT 等见 `application-prod.yml` 与基线校验类。

## 后端 Java / Maven（Windows 与本仓库）

- **项目要求**：`campus-diet-backend` 为 **Java 11**（见该模块 `pom.xml`），不要用 **JRE 8** 跑 Maven。
- **推荐单源工具链（本机）**：只保留 **`C:\dev`**（或任意一个目录），在「用户环境变量」中设置 **`MEDICAN_DEV_TOOLS=C:\dev`**（路径无引号、无末尾反斜杠亦可）。设置后 **`Use-RepoJavaMaven.ps1`** 与 **`启动后端.bat`** **仅扫描该目录**，不再混入仓库内 `dev-tools` / `.devtools`；可删除仓库里重复的 JDK/Maven 解压副本以免混淆。
- **优先用便携 Maven（避免 Wrapper 重复下载）**：在上述目录中放置 **`jdk-11*`** 与 **`apache-maven-3.9.14`**。PowerShell 先 **`.\scripts\Use-RepoJavaMaven.ps1`**，再在 `campus-diet-backend` 执行 **`mvn`**（智能体请在**同一条**命令里先 dot-source 再 `mvn`，避免新会话丢环境）。
- **无便携 Maven 时用 Wrapper**：仓库已提交 **`mvnw` / `mvnw.cmd`** 与 **`.mvn/wrapper/maven-wrapper.properties`**（`distributionType=only-script` 时无需提交 `maven-wrapper.jar`）。在 `campus-diet-backend` 执行  
  `.\mvnw.cmd -s ..\maven-settings-d.xml <goal>`  
  首次运行会把 Maven 发行包缓存在用户目录 **`.m2\wrapper`**（需网络）；Wrapper 与推荐便携版对齐为 **3.9.14**。
- **一键跑后端测试（P0-1 / Windows）**：在仓库根执行  
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-BackendTests.ps1`  
  （内部先 dot-source `Use-RepoJavaMaven.ps1`，再 `mvnw -B -ntp -s maven-settings-d.xml test`；JDK 不可用时回退探测 `C:\Program Files\Microsoft` 等常见 JDK 11 路径）。仅跑安全基线单测时加 **`-SecurityOnly`**。
- **未设置 MEDICAN_DEV_TOOLS 时（可选）**：仍可从 **`dev-tools/`**、**`.devtools/`**、**`C:\dev`** 自动探测（见脚本内顺序）。**`启动后端.bat`** 与脚本一致：有 **`MEDICAN_DEV_TOOLS`** 且路径存在时只认该目录；否则多路径回退。若无便携 **`mvn`** 则回退 **`mvnw.cmd`**。
- **常见环境问题**：
  - **`mvn` 找不到**：要么配置全局 Maven 的 `bin` 到 `PATH`，要么只用 **`.\mvnw.cmd`**，不要重复下载安装包到工程内（除非用户明确要求）。
  - **`JAVA_HOME` 指向已删除的 JDK**（例如曾装过 Adoptium 后又卸载）：请在「系统 / 用户环境变量」中把 **`JAVA_HOME`** 改为本机真实存在的 **JDK 11** 根目录（含 `bin\javac.exe`），并把 **`%JAVA_HOME%\bin`** 放在用户 `PATH` 里 **Oracle `java8path` 之前**，避免终端里 `java -version` 仍是 8。
  - **智能体终端与图形界面环境不一致**：跑 Maven 时优先使用一行命令，例如  
    `powershell -NoProfile -ExecutionPolicy Bypass -Command ". '.\scripts\Use-RepoJavaMaven.ps1'; cd campus-diet-backend; mvn -B -ntp -s ..\maven-settings-d.xml <goal>"`  
    若已设 **`MEDICAN_DEV_TOOLS`**，脚本**只读该目录**并**只选 JDK 11**。Cursor 终端可选用 **「PowerShell (Medican JDK11)」**（见 **`.vscode/settings.json`**：已为该配置文件设置 **`MEDICAN_DEV_TOOLS=C:\dev`**，若你的工具不在 `C:\dev`，请改该 `env` 或改用用户级环境变量）。
