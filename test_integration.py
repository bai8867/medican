#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校园药膳推荐系统 — 自动化联调测试脚本
Python 3.8+（标准库 json / time / sys / urllib）

用法:
  python test_integration.py
  （仅依赖 Python 3.8+ 标准库 + urllib，无需安装 requests）

Windows 说明:
  PowerShell 使用 `> log.txt` 重定向时，控制台管道常把输出存成 UTF-16 LE，中文易乱码。
  需要 UTF-8 文本日志时建议用 cmd 一次执行，例如:
    cmd /c "chcp 65001>nul && python test_integration.py > log.txt"
  或直接让脚本在终端里打印（不重定向），并尽量使用 Windows Terminal。

环境变量（可选，覆盖下方配置区）:
  BASE_URL, TEST_USERNAME 或 TEST_USER, TEST_PASSWORD, ADMIN_USERNAME, ADMIN_PASSWORD,
  STUDENT_USERNAME, STUDENT_PASSWORD（权限越权用例；默认对齐 SeedUsersRunner 的 student 账号）,
  STUDENT_FALLBACK_USERNAME, STUDENT_FALLBACK_PASSWORD（student 不存在时的回退，默认 demo）,
  TEST_USER_ID, RECIPE_ID_FOR_FAVORITE
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from typing import Any, Callable, Dict, List, Optional, Tuple
from urllib.parse import urlencode


def _configure_stdio_utf8_on_windows() -> None:
    """在 Windows 交互式终端下尽量使用 UTF-8 文本流（对 PowerShell `>` 重定向无效，见文件头说明）。"""
    if sys.platform != "win32":
        return
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, OSError, ValueError):
            pass


_configure_stdio_utf8_on_windows()

# ---------------------------------------------------------------------------
# 配置区（可按环境修改；环境变量优先）
# ---------------------------------------------------------------------------
BASE_URL = os.environ.get("BASE_URL", "http://localhost:11888").rstrip("/")
# 与本仓库 SeedUsersRunner 一致：首个用户 admin 通常为 id=1
TEST_USERNAME = os.environ.get("TEST_USERNAME") or os.environ.get("TEST_USER", "admin")
TEST_PASSWORD = os.environ.get("TEST_PASSWORD", "admin123")
ADMIN_USERNAME = os.environ.get("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "admin123")
STUDENT_USERNAME = os.environ.get("STUDENT_USERNAME", "student")
STUDENT_PASSWORD = os.environ.get("STUDENT_PASSWORD", "SeedStudent#2026!")
# 当 student 尚未写入库时，可回退用 demo（同为 USER）做越权断言
STUDENT_FALLBACK_USERNAME = os.environ.get("STUDENT_FALLBACK_USERNAME", "demo")
STUDENT_FALLBACK_PASSWORD = os.environ.get("STUDENT_FALLBACK_PASSWORD", "SeedDemo#2026!")
TEST_USER_ID = int(os.environ.get("TEST_USER_ID", "1"))
RECIPE_ID_FOR_FAVORITE = int(os.environ.get("RECIPE_ID_FOR_FAVORITE", "1"))
REQUEST_TIMEOUT = float(os.environ.get("REQUEST_TIMEOUT", "120"))

# 若网关将 PRD 路径代理到后端，可改这些常量；默认对齐 campus-diet-backend
PATH_HEALTH = "/api/health"
PATH_LOGIN = "/api/auth/login"  # 文档常见为 /api/login，本仓库为 /api/auth/login
PATH_CONSTITUTION_SUBMIT = "/api/user/constitution/survey"
PATH_USER_PROFILE_PUT = "/api/user/profile"  # 本仓库无此接口时使用问卷回退
PATH_USER_PREFERENCES = "/api/user/preferences"
PATH_USER_PROFILE_GET = "/api/user/profile"
PATH_RECOMMEND = "/api/campus/recipes/recommend-feed"
PATH_AI_GENERATE = "/api/ai/generate"
PATH_FAVORITE_ADD = "/api/user/favorites"  # 文档常见 /api/favorite/add，脚本内会优先尝试
PATH_FAVORITE_LIST = "/api/user/favorites"
PATH_FAVORITE_REMOVE_TEMPLATE = "/api/user/favorites/{recipe_id}"  # DELETE
PATH_RECIPE_DETAIL = "/api/recipes/{id}"
PATH_HISTORY_ADD = "/api/user/history"
PATH_HISTORY_LIST = "/api/user/history"
PATH_SCENES_LIST = "/api/scenes"
PATH_SCENE_RECIPES = "/api/scenes/{id}/recipes"
PATH_ADMIN_RECIPE = "/api/admin/recipes"
PATH_ADMIN_DASHBOARD = "/api/admin/dashboard"
PATH_ADMIN_SYSTEM_FLAGS = "/api/admin/system/flags"
PATH_ADMIN_SYSTEM_RECOMMEND = "/api/admin/system/recommend"
PATH_ADMIN_SYSTEM_AI = "/api/admin/system/ai"

# legacy-v1 九题顺序：qixu,yangxu,yinxu,tanshi,shire,xueyu,qiyu,tebing,pinghe
ANSWERS_QIXU = [5, 2, 2, 2, 2, 2, 2, 2, 2]
ANSWERS_YINXU = [2, 2, 5, 2, 2, 2, 2, 2, 2]

# ---------------------------------------------------------------------------
# 终端颜色（Windows 10+ 控制台支持 ANSI）
# ---------------------------------------------------------------------------
_C = {
    "PASS": "\033[92m",
    "FAIL": "\033[91m",
    "WARN": "\033[93m",
    "INFO": "\033[96m",
    "RST": "\033[0m",
}


def _tag(kind: str, msg: str) -> None:
    c = _C.get(kind, _C["INFO"])
    print(f"{c}[{kind}]{_C['RST']} {msg}")


class CaseStats:
    def __init__(self) -> None:
        self.passed = 0
        self.failed = 0
        self.warned = 0
        # (等级, 用例名, 详情) — 用于 Markdown 报告
        self.records: List[Tuple[str, str, str]] = []

    def ok(self, name: str, detail: str = "") -> None:
        self.passed += 1
        self.records.append(("PASS", name, detail))
        _tag("PASS", f"{name}" + (f" — {detail}" if detail else ""))

    def fail(self, name: str, detail: str = "") -> None:
        self.failed += 1
        self.records.append(("FAIL", name, detail))
        _tag("FAIL", f"{name}" + (f" — {detail}" if detail else ""))

    def warn(self, name: str, detail: str = "") -> None:
        self.warned += 1
        self.records.append(("WARN", name, detail))
        _tag("WARN", f"{name}" + (f" — {detail}" if detail else ""))


def _encode_query(params: Optional[Dict[str, Any]]) -> str:
    if not params:
        return ""
    pairs: List[Tuple[str, str]] = []
    for k, v in params.items():
        if v is None:
            continue
        if isinstance(v, bool):
            pairs.append((str(k), "true" if v else "false"))
        else:
            pairs.append((str(k), str(v)))
    return urlencode(pairs)


class _HttpResp:
    __slots__ = ("status_code", "text", "url")

    def __init__(self, status_code: int, text: str, url: str) -> None:
        self.status_code = status_code
        self.text = text
        self.url = url

    def json(self) -> Any:
        return json.loads(self.text)


class HttpSession:
    """与 requests.Session 子集兼容，便于联调脚本零第三方依赖。"""

    def __init__(self) -> None:
        self.headers: Dict[str, str] = {
            "Accept": "application/json",
            "Content-Type": "application/json",
        }

    def get(
        self,
        url: str,
        params: Optional[Dict[str, Any]] = None,
        timeout: float = REQUEST_TIMEOUT,
        headers: Optional[Dict[str, str]] = None,
    ) -> _HttpResp:
        q = _encode_query(params)
        full = f"{url}?{q}" if q else url
        return self._request("GET", full, None, timeout, headers)

    def post(
        self,
        url: str,
        data: Optional[str] = None,
        timeout: float = REQUEST_TIMEOUT,
        headers: Optional[Dict[str, str]] = None,
    ) -> _HttpResp:
        body = data.encode("utf-8") if isinstance(data, str) else data
        return self._request("POST", url, body, timeout, headers)

    def put(
        self,
        url: str,
        data: Optional[str] = None,
        timeout: float = REQUEST_TIMEOUT,
        headers: Optional[Dict[str, str]] = None,
    ) -> _HttpResp:
        body = data.encode("utf-8") if isinstance(data, str) else data
        return self._request("PUT", url, body, timeout, headers)

    def delete(
        self, url: str, timeout: float = REQUEST_TIMEOUT, headers: Optional[Dict[str, str]] = None
    ) -> _HttpResp:
        return self._request("DELETE", url, None, timeout, headers)

    def _request(
        self,
        method: str,
        url: str,
        body: Optional[bytes],
        timeout: float,
        extra_headers: Optional[Dict[str, str]],
    ) -> _HttpResp:
        hdrs = {**self.headers, **(extra_headers or {})}
        req = urllib.request.Request(url, data=body, headers=hdrs, method=method)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
                return _HttpResp(resp.getcode(), raw, resp.geturl())
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")
            return _HttpResp(e.code, raw, getattr(e, "url", None) or url)


def _unwrap_api_json(resp: _HttpResp) -> Tuple[Optional[int], Any]:
    """解析 Spring ApiResponse {code,msg,data}；非 JSON 返回 (None, text)。"""
    try:
        j = resp.json()
    except ValueError:
        return None, resp.text
    if isinstance(j, dict) and "code" in j:
        return int(j.get("code", 0)), j.get("data")
    return None, j


def _session() -> HttpSession:
    return HttpSession()


def _auth_headers(token: Optional[str]) -> Dict[str, str]:
    if not token:
        return {}
    return {"Authorization": f"Bearer {token}"}


def _login_token(http: HttpSession, username: str, password: str) -> Optional[str]:
    """返回 JWT；失败时返回 None。兼容 /api/auth/login 与 /api/login。"""
    candidates = [PATH_LOGIN, "/api/login"]
    last: Optional[_HttpResp] = None
    for path in candidates:
        last = http.post(
            BASE_URL + path,
            data=json.dumps({"username": username, "password": password}),
            timeout=REQUEST_TIMEOUT,
        )
        if last.status_code != 404:
            break
    if last is None:
        return None
    code, data = _unwrap_api_json(last)
    if code == 200 and isinstance(data, dict) and data.get("token"):
        return str(data["token"])
    return None


def _recommend_record_list(data: Any) -> List[Any]:
    if not isinstance(data, dict):
        return []
    raw = data.get("list") or data.get("records") or []
    return raw if isinstance(raw, list) else []


def _ai_fingerprint(data: Dict[str, Any]) -> str:
    """用于两次 AI 调用对比（兼容 PRD 字段名与本仓库字段名）。"""
    if not isinstance(data, dict):
        return str(data)
    dp = data.get("diet_prescription")
    if dp is not None:
        return json.dumps(dp, ensure_ascii=False, sort_keys=True)
    parts = [
        str(data.get("symptomSummary", "")),
        str(data.get("rationale", "")),
        json.dumps(data.get("recipes"), ensure_ascii=False, sort_keys=True),
        json.dumps(data.get("coreIngredients"), ensure_ascii=False, sort_keys=True),
    ]
    return "\n".join(parts)


def _md_escape_cell(s: str) -> str:
    return (s or "").replace("|", "\\|").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>")


def write_integration_markdown_report(
    path: str,
    stats: CaseStats,
    *,
    base_url: str,
    extra_blocks: Optional[List[str]] = None,
) -> None:
    """将联调用例结果写入 Markdown（UTF-8）。"""
    from datetime import datetime

    lines: List[str] = [
        "# 校园药膳推荐系统 — API 联调测试报告",
        "",
        f"- **生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"- **BASE_URL**: `{base_url}`",
        f"- **通过 / 失败 / 警告**: {stats.passed} / {stats.failed} / {stats.warned}",
        "",
        "## 用例明细",
        "",
        "| 结果 | 用例 | 详情 |",
        "| --- | --- | --- |",
    ]
    for kind, name, detail in stats.records:
        emoji = {"PASS": "通过", "FAIL": "失败", "WARN": "警告"}.get(kind, kind)
        lines.append(
            f"| {emoji} | {_md_escape_cell(name)} | {_md_escape_cell(detail)} |"
        )
    lines.append("")
    if extra_blocks:
        for block in extra_blocks:
            lines.append(block)
            lines.append("")
    os.makedirs(os.path.dirname(os.path.abspath(path)) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def _ai_has_required_fields(data: Dict[str, Any]) -> Tuple[bool, str]:
    if not isinstance(data, dict):
        return False, "data 非对象"
    # PRD 字段
    prd = ("ai_flag", "diet_prescription", "ingredients", "taboo", "reason")
    if all(k in data for k in prd):
        return True, "PRD 全字段"
    # 本仓库 AiTherapyPlanService 常见字段
    alt = {
        "ai_flag": ("isGenericPlan",),
        "diet_prescription": ("symptomSummary", "rationale"),
        "ingredients": ("coreIngredients", "recipes"),
        "taboo": ("cautionNotes",),
        "reason": ("rationale",),
    }
    missing = []
    for logical, candidates in alt.items():
        if any(c in data and data.get(c) not in (None, "", [], {}) for c in candidates):
            continue
        missing.append(logical)
    if missing:
        return False, "缺少(逻辑字段): " + ",".join(missing) + " — 请对齐 PRD 或后端 DTO"
    return True, "后端等价字段齐全"


def _parse_report_md_arg(argv: List[str]) -> Optional[str]:
    """支持 `--report-md 路径` 或环境变量 TEST_REPORT_MD。"""
    for i, a in enumerate(argv):
        if a == "--report-md" and i + 1 < len(argv):
            return argv[i + 1]
        if a.startswith("--report-md="):
            return a.split("=", 1)[1]
    return os.environ.get("TEST_REPORT_MD", "").strip() or None


def main(argv: Optional[List[str]] = None) -> int:
    argv = argv if argv is not None else sys.argv[1:]
    report_md_path = _parse_report_md_arg(argv)
    stats = CaseStats()
    sess = _session()
    token: Optional[str] = None
    admin_token: Optional[str] = None

    def run(name: str, fn: Callable[[], None]) -> None:
        try:
            fn()
        except Exception as e:  # noqa: BLE001 — 联调脚本需兜底
            stats.fail(name, f"异常: {e}")

    # ---------------- 一、健康检查 ----------------
    def t_health() -> None:
        r = sess.get(BASE_URL + PATH_HEALTH, timeout=REQUEST_TIMEOUT)
        if r.status_code != 200:
            stats.fail("健康检查 HTTP 状态", f"期望 200 实际 {r.status_code}")
            return
        text = r.text.strip()
        if '"status":"ok"' in r.text or '"status": "ok"' in r.text:
            stats.ok("健康检查", "JSON status=ok")
        elif text == "ok":
            stats.warn("健康检查", "返回纯文本 ok（非 JSON status 字段），连通性正常")
            stats.ok("健康检查(连通)", "200 + body ok")
        else:
            stats.fail("健康检查", f"未识别内容: {text[:200]}")

    run("一-健康检查", t_health)

    # ---------------- 二、登录 ----------------
    def t_login() -> None:
        nonlocal token
        # 兼容文档路径 /api/login
        candidates = [PATH_LOGIN, "/api/login"]
        last = None
        for path in candidates:
            last = sess.post(
                BASE_URL + path,
                data=json.dumps({"username": TEST_USERNAME, "password": TEST_PASSWORD}),
                timeout=REQUEST_TIMEOUT,
            )
            if last.status_code != 404:
                break
        code, data = _unwrap_api_json(last)
        if code == 200 and isinstance(data, dict) and data.get("token"):
            token = data["token"]
            sess.headers.update(_auth_headers(token))
            stats.ok("用户登录", f"path={last.url.split(BASE_URL)[-1]}")
        else:
            stats.fail("用户登录", f"code={code} body={last.text[:300]}")

    run("二-登录", t_login)
    if not token:
        _tag("FAIL", "无 Token，后续需认证用例跳过")
        print(f"\n{_C['INFO']}汇总: 通过 {stats.passed} / 失败 {stats.failed} / 警告 {stats.warned}{_C['RST']}")
        if report_md_path:
            write_integration_markdown_report(
                report_md_path,
                stats,
                base_url=BASE_URL,
                extra_blocks=[
                    "## 说明",
                    "",
                    "登录失败或未拿到 Token 时，后续用例未执行。请先启动后端（默认端口 11888）、"
                    "MySQL 与种子数据，或将 `BASE_URL` 指向可访问环境。",
                ],
            )
            _tag("INFO", f"已写入 Markdown 报告: {report_md_path}")
        return 1

    # ---------------- 三、体质流程 ----------------
    def t_constitution() -> None:
        r = sess.post(
            BASE_URL + PATH_CONSTITUTION_SUBMIT,
            data=json.dumps({"answers": ANSWERS_QIXU}),
            timeout=REQUEST_TIMEOUT,
        )
        code, data = _unwrap_api_json(r)
        if code != 200:
            stats.fail("提交体质问卷(气虚)", f"code={code} {r.text[:200]}")
            return
        label = (data or {}).get("primaryLabel") if isinstance(data, dict) else None
        code_primary = (data or {}).get("primaryCode") if isinstance(data, dict) else None
        if label == "气虚质" or code_primary == "qixu":
            stats.ok("问卷判定气虚质", str(data)[:120])
        else:
            stats.fail("问卷判定气虚质", f"得到 label={label} code={code_primary}")

        # 手动改为阴虚质：优先 PUT profile（若存在）
        body_yinxu = {"constitutionCode": "yinxu", "constitution": "阴虚质"}
        r_put = sess.put(
            BASE_URL + PATH_USER_PROFILE_PUT,
            data=json.dumps(body_yinxu),
            timeout=REQUEST_TIMEOUT,
        )
        if r_put.status_code == 404:
            stats.warn("PUT 体质修正", f"{PATH_USER_PROFILE_PUT} 不存在，改用二次问卷提交")
            r2 = sess.post(
                BASE_URL + PATH_CONSTITUTION_SUBMIT,
                data=json.dumps({"answers": ANSWERS_YINXU}),
                timeout=REQUEST_TIMEOUT,
            )
            c2, d2 = _unwrap_api_json(r2)
            if c2 != 200:
                stats.fail("二次问卷改阴虚质", f"code={c2}")
                return
            lab2 = (d2 or {}).get("primaryLabel")
            if lab2 == "阴虚质" or (d2 or {}).get("primaryCode") == "yinxu":
                stats.ok("体质更新为阴虚质", "问卷回退路径")
            else:
                stats.fail("体质更新为阴虚质", f"label={lab2}")
        else:
            code_p, _ = _unwrap_api_json(r_put)
            if code_p == 200:
                stats.ok("PUT 用户资料更新体质", PATH_USER_PROFILE_PUT)
            else:
                stats.fail("PUT 用户资料", f"status={r_put.status_code} code={code_p}")

        # 与 profile 交叉验证
        rp = sess.get(BASE_URL + PATH_USER_PROFILE_GET, timeout=REQUEST_TIMEOUT)
        c3, prof = _unwrap_api_json(rp)
        if c3 == 200 and isinstance(prof, dict):
            cc = prof.get("constitutionCode")
            if cc == "yinxu":
                stats.ok("Profile 体质为 yinxu", "")
            else:
                stats.warn("Profile 体质", f"期望 yinxu，实际 constitutionCode={cc}")

    run("三-体质采集与修正", t_constitution)

    # ---------------- 四、推荐 ----------------
    def t_recommend() -> None:
        # 文档 GET /api/recommend?user_id= — 本仓库为 recommend-feed，且不读 user_id，用 profile 体质
        doc_path = "/api/recommend"
        r_doc = sess.get(
            BASE_URL + doc_path,
            params={"user_id": TEST_USER_ID},
            timeout=REQUEST_TIMEOUT,
        )
        path_used = PATH_RECOMMEND
        if r_doc.status_code == 200:
            path_used = doc_path
            r = r_doc
        else:
            r = sess.get(
                BASE_URL + PATH_RECOMMEND,
                params={
                    "page": 1,
                    "page_size": 12,
                    "personalized": True,
                    "constitution_code": "yinxu",
                },
                timeout=REQUEST_TIMEOUT,
            )
        code, data = _unwrap_api_json(r)
        if code != 200:
            stats.fail("推荐列表", f"code={code} path={path_used}")
            return
        items = []
        if isinstance(data, dict):
            items = data.get("list") or data.get("records") or []
        if not items:
            stats.fail("推荐列表非空", "list/records 为空")
            return
        stats.ok("推荐列表非空", f"n={len(items)} path={path_used}")

        blob = json.dumps(items, ensure_ascii=False)
        has_const = "体质匹配" in blob or "constitution" in blob.lower()
        has_ai = "AI入口" in blob or "ai" in blob.lower() or "AI" in blob
        if has_const and has_ai:
            stats.ok("推荐卡片类型", "含体质匹配与 AI 入口标记")
        else:
            stats.warn(
                "推荐卡片类型",
                "后端 recommend-feed 仅返回药膳卡片，无「体质匹配/AI入口」类型字段（6:4 为前端拼装）；已跳过严格类型断言",
            )

        r_winter = sess.get(
            BASE_URL + PATH_RECOMMEND,
            params={
                "page": 1,
                "page_size": 12,
                "personalized": True,
                "constitution_code": "yinxu",
                "season_code": "winter",
            },
            timeout=REQUEST_TIMEOUT,
        )
        r_summer = sess.get(
            BASE_URL + PATH_RECOMMEND,
            params={
                "page": 1,
                "page_size": 12,
                "personalized": True,
                "constitution_code": "yinxu",
                "season_code": "summer",
            },
            timeout=REQUEST_TIMEOUT,
        )
        _, d_w = _unwrap_api_json(r_winter)
        _, d_s = _unwrap_api_json(r_summer)
        ids_w = [x.get("id") for x in (d_w or {}).get("list", []) or [] if isinstance(x, dict)]
        ids_s = [x.get("id") for x in (d_s or {}).get("list", []) or [] if isinstance(x, dict)]
        if ids_w != ids_s:
            stats.ok("季节参数影响推荐", "winter vs summer 列表顺序或内容不同")
        else:
            stats.warn("季节参数", "冬/夏推荐列表 id 序列相同，可能池子小或排序巧合")

    run("四-推荐与季节", t_recommend)

    # ---------------- 五、AI ----------------
    def t_ai() -> None:
        symptom = "熬夜复习，眼睛干涩，注意力不集中"
        payload = {"symptom": symptom, "constitution": "yinxu"}

        t0 = time.perf_counter()
        r1 = sess.post(
            BASE_URL + PATH_AI_GENERATE,
            data=json.dumps(payload),
            timeout=REQUEST_TIMEOUT,
        )
        elapsed_ms = (time.perf_counter() - t0) * 1000.0
        code1, data1 = _unwrap_api_json(r1)
        if code1 != 200:
            stats.fail("AI generate 首次", f"code={code1} {r1.text[:400]}")
            return
        if elapsed_ms < 500:
            stats.warn("AI 响应时间", f"{elapsed_ms:.0f}ms < 500ms，疑似 Mock 或未走真实大模型")

        ok_fields, detail = _ai_has_required_fields(data1 if isinstance(data1, dict) else {})
        if ok_fields:
            stats.ok("AI 字段完整性", detail)
        else:
            stats.fail("AI 字段完整性", detail)

        r2 = sess.post(
            BASE_URL + PATH_AI_GENERATE,
            data=json.dumps(payload),
            timeout=REQUEST_TIMEOUT,
        )
        _, data2 = _unwrap_api_json(r2)
        fp1 = _ai_fingerprint(data1 if isinstance(data1, dict) else {})
        fp2 = _ai_fingerprint(data2 if isinstance(data2, dict) else {})
        if fp1 == fp2:
            strict = os.environ.get("LLM_STRICT", "").strip().lower() in ("1", "true", "yes")
            if strict:
                stats.fail(
                    "AI 内容变化检测",
                    "两次指纹相同；已设 LLM_STRICT=1 时视为未接入可变的大模型输出",
                )
            else:
                stats.warn(
                    "AI 内容变化检测",
                    "两次返回相同：未配置 LLM_API_KEY 或走本地兜底时属正常；接入大模型后可设 LLM_STRICT=1 做严格校验",
                )
        else:
            stats.ok("AI 内容变化检测", "两次返回不同")

        # 非常规症状
        r3 = sess.post(
            BASE_URL + PATH_AI_GENERATE,
            data=json.dumps({"symptom": "吃了三斤荔枝，流鼻血", "constitution": "yinxu"}),
            timeout=REQUEST_TIMEOUT,
        )
        _, data3 = _unwrap_api_json(r3)
        blob = json.dumps(data3, ensure_ascii=False) if data3 is not None else ""
        hits = [k for k in ("荔枝", "清热", "上火") if k in blob]
        if hits:
            stats.ok("荔枝/上火语义", "命中: " + ",".join(hits))
        else:
            stats.warn("大模型理解能力", "响应未明显包含 荔枝/清热/上火 等关键词")

    run("五-AI 接入", t_ai)

    # ---------------- 六、收藏与历史 ----------------
    def t_fav_hist() -> None:
        add_candidates = ["/api/favorite/add", PATH_FAVORITE_ADD]
        r_add = None
        for apath in add_candidates:
            body_try = (
                {"recipe_id": RECIPE_ID_FOR_FAVORITE}
                if "favorite" in apath
                else {"recipeId": RECIPE_ID_FOR_FAVORITE}
            )
            r_add = sess.post(BASE_URL + apath, data=json.dumps(body_try), timeout=REQUEST_TIMEOUT)
            if r_add.status_code != 404:
                break
        assert r_add is not None
        c, d = _unwrap_api_json(r_add)
        if c == 200 and isinstance(d, dict) and d.get("ok"):
            stats.ok("收藏添加", f"recipe_id={RECIPE_ID_FOR_FAVORITE}")
        else:
            stats.fail("收藏添加", f"code={c} {r_add.text[:200]}")
            return

        r_list = sess.get(BASE_URL + PATH_FAVORITE_LIST, params={"page": 1, "page_size": 20}, timeout=REQUEST_TIMEOUT)
        if r_list.status_code == 404:
            r_list = sess.get(BASE_URL + "/api/favorite/list", timeout=REQUEST_TIMEOUT)
        c2, page = _unwrap_api_json(r_list)
        ids: List[str] = []
        if c2 == 200 and isinstance(page, dict):
            recs = page.get("records") or []
            for row in recs:
                if isinstance(row, dict) and row.get("id") is not None:
                    ids.append(str(row["id"]))
        sid = str(RECIPE_ID_FOR_FAVORITE)
        if sid in ids:
            stats.ok("收藏列表含该 ID", sid)
        else:
            stats.fail("收藏列表", f"未找到 id={sid} in {ids[:15]}")

        rid = RECIPE_ID_FOR_FAVORITE
        r_del = sess.delete(
            BASE_URL + PATH_FAVORITE_REMOVE_TEMPLATE.format(recipe_id=rid),
            timeout=REQUEST_TIMEOUT,
        )
        c3, d3 = _unwrap_api_json(r_del)
        if c3 == 200 and isinstance(d3, dict) and d3.get("ok"):
            stats.ok("取消收藏", "DELETE /api/user/favorites/{id}")
        else:
            r_alt = sess.post(
                BASE_URL + "/api/favorite/remove",
                data=json.dumps({"recipe_id": rid}),
                timeout=REQUEST_TIMEOUT,
            )
            ca, da = _unwrap_api_json(r_alt)
            if r_alt.status_code != 404 and ca == 200:
                stats.warn("取消收藏", f"DELETE 未成功(code={c3})，已用 /api/favorite/remove 兼容路径")
            else:
                stats.fail("取消收藏", f"DELETE code={c3}；兼容接口 status={r_alt.status_code}")

        detail_url = BASE_URL + PATH_RECIPE_DETAIL.format(id=rid)
        r_det = sess.get(detail_url, timeout=REQUEST_TIMEOUT)
        c4, _ = _unwrap_api_json(r_det)
        if c4 == 200:
            stats.ok("药膳详情", PATH_RECIPE_DETAIL.format(id=rid))
        else:
            stats.fail("药膳详情", f"code={c4}")

        sess.post(
            BASE_URL + PATH_HISTORY_ADD,
            data=json.dumps({"recipeId": rid}),
            timeout=REQUEST_TIMEOUT,
        )
        r_hist = sess.get(BASE_URL + PATH_HISTORY_LIST, params={"limit": 30}, timeout=REQUEST_TIMEOUT)
        c5, hdata = _unwrap_api_json(r_hist)
        found = False
        if c5 == 200 and isinstance(hdata, dict):
            for row in hdata.get("records") or []:
                if isinstance(row, dict) and str(row.get("recipeId")) == sid:
                    found = True
                    break
        if found:
            stats.ok("浏览历史", f"含 recipeId={sid}")
        else:
            stats.fail("浏览历史", "记录中未找到该药膳（需先 POST /api/user/history）")

    run("六-收藏与历史", t_fav_hist)

    # ---------------- 七、设置 ----------------
    def t_settings() -> None:
        r_get = sess.get(BASE_URL + "/api/user/settings", timeout=REQUEST_TIMEOUT)
        if r_get.status_code == 404:
            r_get = sess.get(BASE_URL + PATH_USER_PROFILE_GET, timeout=REQUEST_TIMEOUT)
        c, data = _unwrap_api_json(r_get)
        if c != 200:
            stats.fail("获取用户设置", f"code={c}")
            return
        stats.ok("获取用户设置/资料", "settings 或 profile")

        body = {"recommend_enabled": False}
        r_put = sess.put(BASE_URL + "/api/user/settings", data=json.dumps(body), timeout=REQUEST_TIMEOUT)
        if r_put.status_code == 404:
            r_put = sess.put(
                BASE_URL + PATH_USER_PREFERENCES,
                data=json.dumps({"recommendEnabled": False}),
                timeout=REQUEST_TIMEOUT,
            )
        c2, _ = _unwrap_api_json(r_put)
        if c2 == 200:
            stats.ok("关闭个性化推荐", "recommend_enabled / recommendEnabled")
        else:
            stats.fail("更新设置", f"code={c2} {r_put.text[:200]}")
            return

        r_chk = sess.get(BASE_URL + PATH_USER_PROFILE_GET, timeout=REQUEST_TIMEOUT)
        c3, prof = _unwrap_api_json(r_chk)
        if c3 == 200 and isinstance(prof, dict) and prof.get("recommendEnabled") is False:
            stats.ok("设置已持久化", "recommendEnabled=false")
        else:
            stats.warn("设置校验", f"profile.recommendEnabled={prof!s}")

        # 恢复开启，避免弄脏数据
        sess.put(
            BASE_URL + PATH_USER_PREFERENCES,
            data=json.dumps({"recommendEnabled": True}),
            timeout=REQUEST_TIMEOUT,
        )

    run("七-用户设置", t_settings)

    # ---------------- 八、十大场景 ----------------
    def t_scenes() -> None:
        r = sess.get(BASE_URL + PATH_SCENES_LIST, timeout=REQUEST_TIMEOUT)
        c, data = _unwrap_api_json(r)
        if c != 200:
            stats.fail("场景列表", f"code={c}")
            return
        lst = (data or {}).get("list") if isinstance(data, dict) else None
        n = len(lst) if isinstance(lst, list) else 0
        if n == 10:
            stats.ok("场景数量", "10")
        else:
            stats.fail("场景数量", f"期望 10 实际 {n}")

        r2 = sess.get(BASE_URL + PATH_SCENE_RECIPES.format(id=1), timeout=REQUEST_TIMEOUT)
        c2, sol = _unwrap_api_json(r2)
        recipes = (sol or {}).get("recipes") if isinstance(sol, dict) else None
        if c2 == 200 and isinstance(recipes, list) and len(recipes) > 0:
            stats.ok("场景药膳", f"scene 1 recipes={len(recipes)}")
        else:
            stats.fail("场景药膳", f"code={c2} recipes={recipes!r}")

    run("八-场景食疗", t_scenes)

    # ---------------- 九、后台管理 ----------------
    def t_admin() -> None:
        nonlocal admin_token
        lr = sess.post(
            BASE_URL + PATH_LOGIN,
            data=json.dumps({"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}),
            timeout=REQUEST_TIMEOUT,
        )
        c, d = _unwrap_api_json(lr)
        if c != 200 or not isinstance(d, dict) or not d.get("token"):
            stats.fail("管理员登录", f"code={c}")
            return
        admin_token = d["token"]
        h = {**sess.headers, **_auth_headers(admin_token)}

        new_name = f"联测药膳_{int(time.time())}"
        create_body = {
            "name": new_name,
            "coverUrl": "https://example.com/cover.jpg",
            "efficacySummary": "联调测试写入",
            "collectCount": 0,
            "seasonTags": "spring,summer",
            "constitutionTags": "pinghe,yinxu",
            "efficacyTags": "清热,养阴",
            "instructionSummary": "测试说明",
            "stepsJson": '{"steps":[{"text":"测试步骤"}],"ingredients":[{"name":"山药","amount":"10g"}]}',
            "contraindication": "联调数据",
            "status": 1,
            "sceneIds": [1],
        }
        r_cr = sess.post(
            BASE_URL + PATH_ADMIN_RECIPE,
            data=json.dumps(create_body),
            timeout=REQUEST_TIMEOUT,
            headers=h,
        )
        c2, recipe = _unwrap_api_json(r_cr)
        if c2 == 200 and isinstance(recipe, dict) and recipe.get("id"):
            stats.ok("管理员新增药膳", f"id={recipe.get('id')}")
        else:
            stats.fail("管理员新增药膳", f"code={c2} {r_cr.text[:300]}")

        r_dash = sess.get(BASE_URL + PATH_ADMIN_DASHBOARD, timeout=REQUEST_TIMEOUT, headers=h)
        c3, dash = _unwrap_api_json(r_dash)
        if c3 != 200 or not isinstance(dash, dict):
            stats.fail("管理看板", f"code={c3}")
            return
        keys = set(dash.keys())
        ok_user = "hot_collect_top10" in keys and "season_recipe_top5" in keys
        ok_srv = "topCollected" in keys and "seasonalPicks" in keys
        if ok_user:
            stats.ok("看板字段(PRD)", "hot_collect_top10 + season_recipe_top5")
        elif ok_srv:
            stats.ok("看板字段(后端)", "topCollected + seasonalPicks（与 PRD 命名不同）")
        else:
            stats.fail("看板字段", f"键集: {sorted(keys)}")

    run("九-后台管理", t_admin)

    # ---------------- 十、关键路径：推荐边界 / 权限越权 / 系统开关联动 ----------------
    def t_deep_paths() -> None:
        admin_http = _session()
        admin_tok = _login_token(admin_http, ADMIN_USERNAME, ADMIN_PASSWORD)
        if not admin_tok:
            stats.fail("深度用例-管理员鉴权", "无法登录管理员账号，系统开关用例跳过")
            return
        h_admin = {**admin_http.headers, **_auth_headers(admin_tok)}

        # --- 推荐边界（不改变全局开关）---
        kw = f"__no_match_kw_{int(time.time() * 1000)}__"
        r_kw = admin_http.get(
            BASE_URL + PATH_RECOMMEND,
            params={"page": 1, "page_size": 12, "keyword": kw},
            timeout=REQUEST_TIMEOUT,
            headers=h_admin,
        )
        c_kw, d_kw = _unwrap_api_json(r_kw)
        rec_kw = _recommend_record_list(d_kw)
        if c_kw == 200 and len(rec_kw) == 0:
            stats.ok("推荐边界-无命中关键词", "records/list 为空")
        else:
            stats.fail("推荐边界-无命中关键词", f"code={c_kw} n={len(rec_kw)}")

        r_far = admin_http.get(
            BASE_URL + PATH_RECOMMEND,
            params={"page": 999999, "page_size": 8, "constitution_code": "yinxu"},
            timeout=REQUEST_TIMEOUT,
            headers=h_admin,
        )
        c_far, d_far = _unwrap_api_json(r_far)
        rec_far = _recommend_record_list(d_far)
        tot = (d_far or {}).get("total") if isinstance(d_far, dict) else None
        if c_far == 200 and len(rec_far) == 0:
            stats.ok("推荐边界-超大分页偏移", f"total={tot}")
        else:
            stats.warn("推荐边界-超大分页", f"code={c_far} n={len(rec_far)} total={tot}（池子极大时可能仍有数据）")

        r_ps0 = admin_http.get(
            BASE_URL + PATH_RECOMMEND,
            params={"page": 1, "page_size": 0, "constitution_code": "pinghe"},
            timeout=REQUEST_TIMEOUT,
            headers=h_admin,
        )
        c0, d0 = _unwrap_api_json(r_ps0)
        rec0 = _recommend_record_list(d0)
        if c0 == 200 and len(rec0) >= 1:
            stats.ok("推荐边界-page_size=0 回退", f"至少返回 1 条（后端 clamp） n={len(rec0)}")
        elif c0 == 200 and len(rec0) == 0:
            stats.warn("推荐边界-page_size=0", "返回 0 条（可能合法若候选池为空）")

        # --- 权限越权：普通用户不可读系统开关、不可改全局开关 ---
        stu_http = _session()
        stu_tok = _login_token(stu_http, STUDENT_USERNAME, STUDENT_PASSWORD)
        stu_label = STUDENT_USERNAME
        if not stu_tok:
            stu_tok = _login_token(stu_http, STUDENT_FALLBACK_USERNAME, STUDENT_FALLBACK_PASSWORD)
            stu_label = STUDENT_FALLBACK_USERNAME
        if not stu_tok and TEST_USERNAME.strip().lower() != "admin":
            # 主流程已用非管理员账号登录时，直接复用其 JWT
            stu_tok = token
            stu_label = TEST_USERNAME
        if not stu_tok:
            stats.warn(
                "权限越权-普通用户",
                f"无法登录 {STUDENT_USERNAME} / {STUDENT_FALLBACK_USERNAME}，且主账号为 admin，跳过 403 断言",
            )
        else:
            h_stu = {**stu_http.headers, **_auth_headers(stu_tok)}
            r_forbidden = stu_http.get(
                BASE_URL + PATH_ADMIN_SYSTEM_FLAGS,
                timeout=REQUEST_TIMEOUT,
                headers=h_stu,
            )
            if r_forbidden.status_code == 403:
                stats.ok("权限越权-普通用户读系统开关", f"HTTP 403（{stu_label}）")
            else:
                c_f, _ = _unwrap_api_json(r_forbidden)
                stats.fail("权限越权-普通用户读系统开关", f"期望 HTTP 403，实际 status={r_forbidden.status_code} code={c_f}")

            r_put_stu = stu_http.put(
                BASE_URL + PATH_ADMIN_SYSTEM_RECOMMEND + "?enabled=true",
                timeout=REQUEST_TIMEOUT,
                headers=h_stu,
            )
            if r_put_stu.status_code == 403:
                stats.ok("权限越权-普通用户改推荐总开关", f"HTTP 403（{stu_label}）")
            else:
                c_ps, _ = _unwrap_api_json(r_put_stu)
                stats.fail("权限越权-普通用户改推荐总开关", f"期望 HTTP 403，实际 status={r_put_stu.status_code} code={c_ps}")

        # --- 系统开关联动：推荐总开关、AI 总开关 ---
        def _put_recommend(enabled: bool) -> _HttpResp:
            q = "true" if enabled else "false"
            return admin_http.put(
                BASE_URL + PATH_ADMIN_SYSTEM_RECOMMEND + f"?enabled={q}",
                timeout=REQUEST_TIMEOUT,
                headers=h_admin,
            )

        def _put_ai(enabled: bool) -> _HttpResp:
            q = "true" if enabled else "false"
            return admin_http.put(
                BASE_URL + PATH_ADMIN_SYSTEM_AI + f"?enabled={q}",
                timeout=REQUEST_TIMEOUT,
                headers=h_admin,
            )

        try:
            pr_off = _put_recommend(False)
            co, _ = _unwrap_api_json(pr_off)
            if co != 200:
                stats.fail("系统开关-关闭推荐", f"PUT recommend code={co} {pr_off.text[:200]}")
            else:
                r_empty = admin_http.get(
                    BASE_URL + PATH_RECOMMEND,
                    params={"page": 1, "page_size": 12},
                    timeout=REQUEST_TIMEOUT,
                    headers=h_admin,
                )
                ce, de = _unwrap_api_json(r_empty)
                ne = _recommend_record_list(de)
                if ce == 200 and len(ne) == 0:
                    stats.ok("系统开关-关闭推荐后 Feed", "列表为空")
                else:
                    stats.fail("系统开关-关闭推荐后 Feed", f"code={ce} n={len(ne)}")

            pr_on = _put_recommend(True)
            c_on, _ = _unwrap_api_json(pr_on)
            if c_on != 200:
                stats.fail("系统开关-恢复推荐", f"code={c_on}")
            else:
                r_back = admin_http.get(
                    BASE_URL + PATH_RECOMMEND,
                    params={"page": 1, "page_size": 8},
                    timeout=REQUEST_TIMEOUT,
                    headers=h_admin,
                )
                cb, db = _unwrap_api_json(r_back)
                nb = _recommend_record_list(db)
                if cb == 200 and len(nb) > 0:
                    stats.ok("系统开关-恢复推荐后 Feed", f"n={len(nb)}")
                else:
                    stats.fail("系统开关-恢复推荐后 Feed", f"code={cb} n={len(nb)}")

            ai_off = _put_ai(False)
            ca, _ = _unwrap_api_json(ai_off)
            if ca != 200:
                stats.fail("系统开关-关闭 AI", f"code={ca}")
            else:
                r_ai = admin_http.post(
                    BASE_URL + PATH_AI_GENERATE,
                    data=json.dumps({"symptom": "联调开关探测", "constitution": "yinxu"}),
                    timeout=REQUEST_TIMEOUT,
                    headers=h_admin,
                )
                c_ai, d_ai = _unwrap_api_json(r_ai)
                en = (d_ai or {}).get("enabled") if isinstance(d_ai, dict) else None
                msg = (d_ai or {}).get("message") if isinstance(d_ai, dict) else None
                if c_ai == 200 and en is False:
                    stats.ok("系统开关-关闭 AI 后生成接口", "data.enabled=false")
                else:
                    stats.fail("系统开关-关闭 AI 后生成接口", f"code={c_ai} enabled={en!r} msg={msg!r}")

            ai_on = _put_ai(True)
            c_ai_on, d_ai_on = _unwrap_api_json(ai_on)
            if c_ai_on != 200:
                stats.fail("系统开关-恢复 AI", f"code={c_ai_on}")
            else:
                r_ai2 = admin_http.post(
                    BASE_URL + PATH_AI_GENERATE,
                    data=json.dumps({"symptom": "联调开关恢复探测", "constitution": "yinxu"}),
                    timeout=REQUEST_TIMEOUT,
                    headers=h_admin,
                )
                c2, d2 = _unwrap_api_json(r_ai2)
                en2 = (d2 or {}).get("enabled") if isinstance(d2, dict) else None
                if c2 == 200 and en2 is not False:
                    stats.ok("系统开关-恢复 AI 后生成接口", "enabled 不为 false")
                else:
                    stats.fail("系统开关-恢复 AI 后生成接口", f"code={c2} enabled={en2!r}")
        finally:
            _put_recommend(True)
            _put_ai(True)

    run("十-推荐边界权限与系统开关", t_deep_paths)

    # ---------------- 汇总 ----------------
    total = stats.passed + stats.failed + stats.warned
    print()
    _tag("INFO", f"汇总 — 通过 {stats.passed} / 失败 {stats.failed} / 警告 {stats.warned} / 计次合计 {total}")
    if report_md_path:
        write_integration_markdown_report(report_md_path, stats, base_url=BASE_URL)
        _tag("INFO", f"已写入 Markdown 报告: {report_md_path}")
    if stats.failed > 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
