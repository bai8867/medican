# 可观测性栈（方案 A）

## 前置条件

1. 本机已安装 Docker / Docker Compose。
2. **campus-diet-backend** 已启动，且监听地址可被 Docker 访问到：
   - 默认 `application.yml` 中 `server.address` 为 `127.0.0.1` 时，容器内通过 `host.docker.internal:11888` 抓取（`docker-compose.yml` 已配置 `extra_hosts`）。
   - 若仍无法抓取，可设置环境变量 **`SERVER_ADDRESS=0.0.0.0`** 后重启后端。

## 启动

```bash
cd observability
docker compose up -d
```

- Prometheus：http://localhost:9090  
- Grafana：http://localhost:3000（默认用户 `admin`，密码环境变量 **`GRAFANA_ADMIN_PASSWORD`**，未设置时为 `admin`）  
- Alertmanager：http://localhost:9093  

在 Prometheus **Status → Targets** 中确认 `campus-diet-backend` 为 **UP**。

## 生产前必改

1. **`alertmanager/alertmanager.yml`**：将演示用 `httpbin.org` Webhook 替换为钉钉/飞书/邮件等正式接收器。  
2. **`prometheus/prometheus.yml`**：将 `static_configs` 改为你的服务发现或内网地址。  
3. **安全组/防火墙**：禁止将 `9090`、`3000`、`9093` 暴露到公网；指标端点仅内网可达。
