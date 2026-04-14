#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一键测试入口：前端 Node 单元测试 + 后端 API 联调（test_integration.py），
合并输出为根目录「系统测试报告.md」。

用法:
  python run_test_suite.py
  python run_test_suite.py --report 自定义报告.md

环境变量与 test_integration 一致，例如 BASE_URL=http://127.0.0.1:11888
"""

from __future__ import annotations

import argparse
import datetime as _dt
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FRONTEND = ROOT / "tcm-diet-frontend"
DEFAULT_REPORT = ROOT / "系统测试报告.md"


def _http_head_or_get(url: str, timeout: float = 5.0) -> tuple[bool, str]:
    """返回 (是否成功, 一行说明)。"""
    try:
        req = urllib.request.Request(url, method="GET", headers={"Accept": "*/*"})
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read(512).decode("utf-8", errors="replace")
            return True, f"HTTP {resp.status}，正文前 80 字: {raw[:80].replace(chr(10), ' ')!r}"
    except urllib.error.HTTPError as e:
        return False, f"HTTP {e.code} {url}"
    except Exception as e:  # noqa: BLE001
        return False, f"{type(e).__name__}: {e}"


def _run(cmd: list[str], cwd: Path, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        cwd=str(cwd),
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env or os.environ.copy(),
        shell=False,
    )


def main() -> int:
    ap = argparse.ArgumentParser(description="校园药膳推荐系统 — 聚合测试与 Markdown 报告")
    ap.add_argument("--report", default=str(DEFAULT_REPORT), help="Markdown 报告输出路径")
    args = ap.parse_args()
    report_path = Path(args.report).resolve()

    base_url = os.environ.get("BASE_URL", "http://127.0.0.1:11888").rstrip("/")
    fe_check = os.environ.get("FRONTEND_CHECK_URL", "http://127.0.0.1:11999/")
    if not fe_check.endswith("/"):
        fe_check = fe_check + "/"

    be_ok, be_msg = _http_head_or_get(f"{base_url}/api/health")
    fe_ok, fe_msg = _http_head_or_get(fe_check)

    sections: list[str] = [
        "# 校园药膳推荐系统 — 系统测试报告",
        "",
        f"- **生成时间**: {_dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"- **仓库根目录**: `{ROOT}`",
        "",
        "## 1. 测试范围",
        "",
        "| 层级 | 内容 |",
        "| --- | --- |",
        "| 前端 | `tcm-diet-frontend`：`npm test`（体质问卷算法）；开发态可用 `npm run dev`（默认 **11999**，Vite 代理 `/api` → 后端） |",
        "| 后端 API | 根目录 `test_integration.py`（健康检查、登录、体质、推荐、AI、收藏、历史、设置、场景、管理端） |",
        "| 未纳入 | 后端暂无 `src/test` 单测；Playwright E2E 未默认执行 |",
        "",
        "## 2. 运行环境与前后端就绪（探测）",
        "",
        "本节前执行一次 HTTP 探测，用于说明**测试执行时**前后端是否可达（与 `npm test` 无必然关系）。",
        "",
        "| 探测项 | URL | 结果 |",
        "| --- | --- | --- |",
        f"| 后端健康检查 | `{base_url}/api/health` | **{'可达' if be_ok else '不可达'}** — {be_msg} |",
        f"| 前端开发服务（可选） | `{fe_check}` | **{'可达' if fe_ok else '不可达'}** — {fe_msg} |",
        "",
        "环境变量：`BASE_URL`（默认 `http://127.0.0.1:11888`）、`FRONTEND_CHECK_URL`（默认 `http://127.0.0.1:11999/`）。",
        "",
    ]

    # --- 前端 ---
    sections.append("## 3. 前端单元测试（`npm test`）")
    sections.append("")
    if not FRONTEND.is_dir():
        sections.append("**结果**: 跳过 — 未找到 `tcm-diet-frontend` 目录。")
        sections.append("")
        fe_code = 1
        fe_out = ""
    else:
        npm_cmd = "npm.cmd" if sys.platform == "win32" else "npm"
        proc = _run([npm_cmd, "test", "--silent"], cwd=FRONTEND)
        fe_code = proc.returncode
        fe_out = (proc.stdout or "") + (proc.stderr or "")
        status = "通过" if fe_code == 0 else "失败"
        sections.append(f"**退出码**: `{fe_code}` — **结论**: {status}")
        sections.append("")
        sections.append("```text")
        sections.append(fe_out.strip() or "(无输出)")
        sections.append("```")
        sections.append("")

    # --- API 联调：写入临时 md 再读入 ---
    api_md = ROOT / "_api_integration_report.tmp.md"
    env = os.environ.copy()
    env["TEST_REPORT_MD"] = str(api_md)
    proc_api = _run([sys.executable, str(ROOT / "test_integration.py"), "--report-md", str(api_md)], cwd=ROOT, env=env)
    api_code = proc_api.returncode
    api_console = (proc_api.stdout or "") + (proc_api.stderr or "")

    sections.append("## 4. 后端 API 联调（`test_integration.py`）")
    sections.append("")
    sections.append(f"**退出码**: `{api_code}`（非 0 表示存在失败用例或无法连接服务）。")
    sections.append("")
    if api_md.is_file():
        body = api_md.read_text(encoding="utf-8")
        api_md.unlink(missing_ok=True)
        # 避免文档内重复一级标题：子报告首行降为三级标题
        lines = body.splitlines()
        if lines and lines[0].startswith("# 校园药膳推荐系统 — API"):
            lines[0] = "### API 联调明细（自动生成）"
        elif lines and lines[0].lstrip().startswith("#"):
            lines[0] = "### " + lines[0].lstrip().removeprefix("#").lstrip()
        body = "\n".join(lines)
        sections.append(body)
    else:
        sections.append("未生成联调 Markdown 片段，控制台输出如下：")
        sections.append("")
        sections.append("```text")
        sections.append(api_console.strip() or "(无输出)")
        sections.append("```")
        sections.append("")

    sections.append("")
    sections.append("## 5. 总评")
    sections.append("")
    sections.append(
        f"- 运行探测：后端 `/api/health` **{'正常' if be_ok else '异常'}**；"
        f"前端开发地址 **{'正常' if fe_ok else '未启动或不可达'}**（仅作环境说明）。"
    )
    sections.append("")
    if fe_code != 0:
        sections.append("- 前端单元测试：**未通过**，请查看第 3 节日志。")
    else:
        sections.append("- 前端单元测试：**通过**。")
    if api_code != 0:
        sections.append(
            "- 后端 API 联调：**未通过**或**未启动服务**（常见为 `localhost:11888` 连接被拒绝）。"
            " 请先按 README 启动 MySQL 与 `campus-diet-backend`，再重新执行 `python run_test_suite.py`。"
        )
    else:
        sections.append("- 后端 API 联调：**通过**（在当前 `BASE_URL` 环境下）。")
    sections.append("")
    sections.append(
        "> 说明：联调中的 AI、大模型相关用例可能产生 **警告**（如响应过快、两次结果相同），"
        " 不代表接口失败；以报告中「失败」行为准。"
    )
    sections.append("")

    report_path.write_text("\n".join(sections), encoding="utf-8")
    print(f"已写入: {report_path}")
    return 0 if fe_code == 0 and api_code == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
