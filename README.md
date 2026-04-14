# medican

校园药膳推荐相关工程。**产品主体为浏览器 Web 应用**（`tcm-diet-frontend`）；`campus-diet-backend` 为后端 API。

## 快速启动 Web（推荐）

**数据库（MySQL）**：`application.yml` 默认 `127.0.0.1:3306`、库名 **`tcm_diet`**、用户 **`root` / `root`**。本机可用 `winget install -e --id Oracle.MySQL` 安装后，在仓库根目录用 **`mysql-dev-my.ini`**（数据目录在 **`.devtools/mysql-data`**，D 盘、勿提交）配合 **`mysqld --defaults-file=...`** 启动；首次需对空数据目录执行一次 **`mysqld --defaults-file=mysql-dev-my.ini --initialize-insecure`**，再用客户端执行 `ALTER USER ...`、`CREATE DATABASE tcm_diet`。也可双击 **`启动MySQL开发实例.bat`**（在已初始化且未占用 3306 时启动 `mysqld`）。

先启动后端：Windows 可在根目录双击 **`启动后端.bat`**（若已将 JDK 17+、Maven 解压到仓库根目录 **`.devtools`**，脚本会自动使用；Maven 依赖缓存见根目录 **`maven-settings-d.xml`** 中的 `localRepository`，落在 D 盘工程路径下）。也可自行在 `campus-diet-backend` 执行 `mvn -s ..\maven-settings-d.xml spring-boot:run`（默认 **11888**，监听 **0.0.0.0**）。**大模型（内网联调）**：默认与《大模型调用调试说明》一致，指向 **`http://ds.local.ai:30080/v1/chat/completions`**；需能解析该主机（hosts 或内网 DNS）。若改用本机 **Ollama**，请设置环境变量 **`LLM_URL`** / **`LLM_MODEL`**（`LLM_API_KEY` 可留空）。

在已启动后端（默认 http://localhost:11888/health 返回 200）的前提下，Windows 请在项目根目录双击 **`启动前端.bat`**（或 Linux / macOS / Git Bash 使用 `start.sh`）拉起 `tcm-diet-frontend`（默认端口见 `vite.config.js`，与脚本中 `PORT` 一致）。**前端脚本默认不再写入直连 `VITE_API_BASE_URL`**，页面用相对路径 `/api` 经 Vite 代理到本机后端；`start.bat` 仅打印上述两脚本说明。**前端使用系统 PATH 中的 Node / npm，不依赖 Conda。**

也可手动：

```bash
cd tcm-diet-frontend
npm install
npm run dev
```

## 其它

- 大模型网关联调说明见仓库根目录《大模型调用调试说明.md》。
