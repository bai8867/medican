# 部署说明（生产）

本文档用于部署 `campus-diet-backend` 与 `tcm-diet-frontend` 到生产环境。

## 1. 前置条件

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8+
- 可访问的大模型网关（如使用 AI 能力）

## 2. 环境变量

复制仓库根目录 `.env.example`，按生产参数填写后导入运行环境（systemd、容器、CI/CD Secret 均可）。

重点必填：

- `SPRING_PROFILES_ACTIVE=prod`
- `CAMPUS_JWT_SECRET`（至少 32 位，不能用开发默认值）
- `LLM_API_KEY`
- `MYSQL_*`

## 3. 后端部署

在 `campus-diet-backend` 目录执行：

```bash
mvn -s ../maven-settings-d.xml clean package -DskipTests
java -jar target/campus-diet-backend-1.0.0-SNAPSHOT.jar
```

说明：

- `prod` profile 默认关闭 SQL 初始化与演示数据写入。
- 启动时会执行生产基线校验；密钥不合规会直接启动失败（预期行为）。

## 4. 前端部署

在 `tcm-diet-frontend` 目录执行：

```bash
npm ci
npm run build
```

将 `dist/` 发布到静态站点服务（Nginx/CDN/对象存储均可）。

如果前后端分域部署，请确保：

- 后端 `CAMPUS_CORS_ALLOWED_ORIGIN_PATTERNS` 包含前端域名
- 前端 `VITE_API_BASE_URL` 指向后端网关地址（如 `https://api.example.com/api`）

## 5. 验收清单

- 健康检查：`GET /health` 返回 200
- 登录接口：错误凭据返回 HTTP 401（同时 body 仍带业务码）
- 管理接口：无权限返回 HTTP 403
- 前端登录与推荐流程可用
- E2E 测试通过（需后端在线）

## 6. 常见问题

- **启动失败：生产密钥校验不通过**
  - 检查 `CAMPUS_JWT_SECRET` 是否长度不足或仍为开发默认值。
- **前端报跨域**
  - 检查 `CAMPUS_CORS_ALLOWED_ORIGIN_PATTERNS` 是否包含当前站点。
- **E2E 失败且日志出现 `ECONNREFUSED 127.0.0.1:11888`**
  - 后端未启动或端口不可达，请先启动后端。
