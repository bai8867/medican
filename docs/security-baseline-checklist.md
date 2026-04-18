# 安全基线核查清单（P0-2）

## 1. 核查范围

本清单用于校验生产环境最小安全基线，覆盖以下维度：

- 默认账号/默认口令
- 默认密钥/弱密钥
- 生产配置必填项
- 高风险初始化开关
- CI 阻断与演练

## 2. 生产环境硬性要求

### 2.1 默认账号与口令

- `spring.datasource.username` 不允许为空，不允许为 `root`
- `spring.datasource.password` 不允许为空，不允许为 `root`，且不允许使用 `application.yml` 中的占位默认值 `change-this-db-password` 及常见弱口令（与 `ProdSecurityBaselineValidator` 中 `FORBIDDEN_DATASOURCE_PASSWORDS` 对齐）
- `campus.seed-users.admin-password` 不允许使用开发默认值 `admin123`
- `campus.seed-users.canteen-password` 不允许使用开发默认值 `canteen123`
- `campus.seed-users.demo-password` 不允许使用开发默认值 `demo123`
- `campus.seed-users.student-password` 不允许使用开发默认值 `123456`

### 2.2 密钥与必填配置

- `CAMPUS_JWT_SECRET` 必须满足：
  - 长度至少 32
  - 不能包含 `dev-only` 或 `change-before-prod` 默认标识
- `LLM_API_KEY` 必须非空

### 2.3 高风险配置

- `spring.sql.init.mode` 在生产必须为 `never`
- 以下开关在生产必须为 `false`：
  - `campus.diet.seed-mock-recipes`
  - `campus.diet.seed-demo-interactions`
  - `campus.diet.seed-weekly-calendar`

## 3. 自动化阻断点

- **启动时阻断**：`ProdSecurityBaselineValidator` 在 `prod` profile 下执行校验，不满足则启动失败
- **CI 阻断（静态）**：`scripts/check-prod-security-baseline.sh` 校验关键配置与校验器覆盖项
- **CI 阻断（动态）**：`ProdSecurityBaselineValidatorTest` 校验默认值会触发失败、合规配置可通过

## 4. 演练步骤（至少执行一次）

1. 在 `prod` profile 环境中，故意将 `spring.datasource.username` 设为 `root`
2. 启动应用，预期启动失败并报安全基线异常
3. 恢复为合规配置后再次启动，预期可启动
4. 执行 CI（或本地模拟）：
   - `bash scripts/check-prod-security-baseline.sh`（需 Git Bash / WSL / Linux；仓库根目录执行）
   - **无 Bash 的 Windows**：`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-prod-security-baseline.ps1`（与 `.sh` 断言等价，exit 0 表示通过）
   - 仅跑安全基线单测（仓库根 → 后端目录）：
     - Linux / macOS / Git Bash：`cd campus-diet-backend && ./mvnw -B -ntp -Dtest=ProdSecurityBaselineValidatorTest test`
     - Windows PowerShell：`cd campus-diet-backend; .\mvnw.cmd -B -ntp "-Dtest=ProdSecurityBaselineValidatorTest" test`（**`-Dtest=...` 必须加引号**，否则会被拆成错误参数）
5. 将结果填入 **下方第 5 节**（可提交到本仓库，或复制到运维台账 / Wiki）。

## 5. 演练记录（模板）

> 完成第 4 节后，复制本段一次并填写；每次演练保留一条，便于审计。  
> **归档示例与自动化证据**：见目录 `docs/security-baseline-drill-logs/`（如 `2026-04-16-p0-closeout.md`）。

| 字段 | 填写 |
|------|------|
| 演练日期 | YYYY-MM-DD |
| 执行人 | |
| 环境 | 例如：本机 prod profile / 预发 k8s |
| 违规项（故意配置） | 例如：`spring.datasource.username=root` |
| 启动结果 | 预期失败 / 实际：□ 失败 □ 误成功 |
| 异常摘要 | 粘贴 `IllegalStateException` 首行或日志关键字 |
| 恢复后启动 | □ 可启动 |
| `check-prod-security-baseline.sh` | □ 通过（粘贴命令与 exit 0） |
| `ProdSecurityBaselineValidatorTest` | □ 通过 |
| 备注 | 非 GitHub 发布流水线是否已接入同一脚本：□ 是 □ 否 / N/A |
