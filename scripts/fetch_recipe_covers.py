#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为药膳封面拉取配图并写入前端 public 目录，同时更新 campus-diet-backend 的 data.sql。

策略（适配网络不稳定环境）：
1) 短超时尝试 Wikimedia Commons API（CC / 公有领域为主）；
2) 失败则使用已验证可下载的 Unsplash 静态直链（与项目原先 data.sql 同源，便于访问）；
3) 再失败则生成本地 SVG 占位图（风格与前端 Recommend.vue 的 svgPlaceholderCover 一致）。

依赖：仅 Python 3 标准库。
"""

from __future__ import annotations

import json
import re
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

USER_AGENT = (
    "MedicanCampusDiet/1.0 (campus diet education demo; "
    "Python/urllib)"
)

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "tcm-diet-frontend" / "public" / "recipe-covers"
DATA_SQL = ROOT / "campus-diet-backend" / "src" / "main" / "resources" / "db" / "data.sql"

COMMONS_API = "https://commons.wikimedia.org/w/api.php"
COMMONS_TIMEOUT = 7
HTTP_TIMEOUT = 35
MIN_WIDTH = 360
REQUEST_PAUSE_SEC = 0.35

# recipe id -> (中文名, Commons 检索词列表)
RECIPE_QUERIES: list[tuple[int, str, list[str]]] = [
    (1, "枸杞菊花茶", ["Chrysanthemum tea cup", "Goji tea", "Chrysanthemum flower tea"]),
    (2, "黄芪炖鸡", ["Chinese chicken soup herbal", "Chicken soup clay pot"]),
    (3, "姜枣茶", ["Ginger jujube tea", "Ginger tea cup"]),
    (4, "酸枣仁茶", ["Herbal tea infusion", "Chinese herbal tea cup"]),
    (5, "绿豆汤", ["Mung bean soup bowl", "Green bean soup"]),
    (6, "当归生姜羊肉汤", ["Lamb stew bowl", "Chinese lamb soup"]),
    (7, "红糖姜枣茶", ["Brown sugar ginger tea", "Ginger tea"]),
    (8, "菊花决明子茶", ["Chrysanthemum tea", "Herbal tea"]),
    (9, "山药红枣粥", ["Chinese congee", "Rice porridge bowl"]),
    (10, "银耳雪梨汤", ["Tremella dessert soup", "Pear soup bowl"]),
]

# 本机实测可 200 下载的 Unsplash 图（药膳示意，非医学保证一一对应实物）
UNSPLASH_FALLBACK: dict[int, str] = {
    1: "https://images.unsplash.com/photo-1473093295043-cdd812d0e601?w=1200&q=85",
    2: "https://images.unsplash.com/photo-1547592166-23ac45744acd?w=1200&q=85",
    3: "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?w=1200&q=85",
    4: "https://images.unsplash.com/photo-1564890369478-c89ca6d9cde9?w=1200&q=85",
    5: "https://images.unsplash.com/photo-1563379926898-05f4575a45d8?w=1200&q=85",
    6: "https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=1200&q=85",
    7: "https://images.unsplash.com/photo-1603048297172-c92544798d5a?w=1200&q=85",
    8: "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=1200&q=85",
    9: "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=1200&q=85",
    10: "https://images.unsplash.com/photo-1567620905732-2d1ec7ab7445?w=1200&q=85",
}


def default_svg(title: str) -> str:
    safe = (title or "膳")[:8]
    safe = safe.replace("&", "&amp;").replace("<", "&lt;")
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360">
<defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1">
<stop offset="0%" stop-color="#ecfdf5"/><stop offset="100%" stop-color="#d8f3dc"/>
</linearGradient></defs>
<rect fill="url(#g)" width="100%" height="100%"/>
<text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle"
 font-family="system-ui,sans-serif" font-size="26" fill="#2d6a4f">{safe}</text>
</svg>"""


def http_json(url: str, timeout: float) -> dict:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
        return json.loads(resp.read().decode("utf-8"))


def http_bytes(url: str, timeout: float) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
        return resp.read()


def commons_image_candidates(query: str, limit: int = 10) -> list[dict]:
    params = {
        "action": "query",
        "format": "json",
        "generator": "search",
        "gsrsearch": query,
        "gsrnamespace": 6,
        "gsrlimit": str(limit),
        "prop": "imageinfo",
        "iiprop": "url|size|mime",
        "iiurlwidth": "1280",
    }
    url = COMMONS_API + "?" + urllib.parse.urlencode(params)
    data = http_json(url, COMMONS_TIMEOUT)
    pages = (data.get("query") or {}).get("pages") or {}
    out: list[dict] = []
    for _pid, page in pages.items():
        infos = page.get("imageinfo") or []
        if not infos:
            continue
        out.append({"info": infos[0]})
    return out


def pick_commons_url(candidates: list[dict]) -> tuple[str, str] | None:
    for c in candidates:
        info = c["info"]
        mime = (info.get("mime") or "").lower()
        if mime not in ("image/jpeg", "image/png", "image/webp"):
            continue
        w = int(info.get("width") or 0)
        if w < MIN_WIDTH:
            continue
        url = info.get("thumburl") or info.get("url")
        if url:
            return url, mime
    return None


def ext_for_mime(mime: str) -> str:
    if mime == "image/png":
        return "png"
    if mime == "image/webp":
        return "webp"
    return "jpg"


def try_commons(queries: list[str]) -> tuple[bytes, str] | None:
    for q in queries:
        try:
            cands = commons_image_candidates(q)
            picked = pick_commons_url(cands)
            if not picked:
                continue
            url, mime = picked
            data = http_bytes(url, COMMONS_TIMEOUT)
            if len(data) < 8000:
                continue
            return data, ext_for_mime(mime)
        except (urllib.error.URLError, TimeoutError, OSError, ValueError, json.JSONDecodeError):
            time.sleep(REQUEST_PAUSE_SEC)
        time.sleep(REQUEST_PAUSE_SEC)
    return None


def try_unsplash(recipe_id: int) -> bytes | None:
    url = UNSPLASH_FALLBACK.get(recipe_id)
    if not url:
        return None
    try:
        return http_bytes(url, HTTP_TIMEOUT)
    except (urllib.error.URLError, TimeoutError, OSError):
        return None


def download_one(recipe_id: int, name_zh: str, queries: list[str]) -> str:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    src = try_commons(queries)
    ext = "jpg"
    body: bytes | None = None
    if src:
        body, ext = src
        print(f"  [commons] id={recipe_id}")
    if body is None:
        body = try_unsplash(recipe_id)
        ext = "jpg"
        if body:
            print(f"  [unsplash-fallback] id={recipe_id}")
    if body is None or len(body) < 2000:
        ph = OUT_DIR / f"recipe-{recipe_id}-placeholder.svg"
        ph.write_text(default_svg(name_zh), encoding="utf-8")
        print(f"  [svg-placeholder] id={recipe_id}")
        return f"/recipe-covers/{ph.name}"

    fname = f"recipe-{recipe_id}.{ext}"
    (OUT_DIR / fname).write_bytes(body)
    return f"/recipe-covers/{fname}"


def ensure_default_svg() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "default-cover.svg").write_text(default_svg("药膳"), encoding="utf-8")


def patch_data_sql(url_by_id: dict[int, str]) -> None:
    """仅替换 `INSERT IGNORE INTO recipe` 段落内的 cover_url，避免误改 campus_scene 等同名 id 行。"""
    text = DATA_SQL.read_text(encoding="utf-8")
    marker = "INSERT IGNORE INTO `recipe`"
    start = text.find(marker)
    if start == -1:
        raise RuntimeError(f"{DATA_SQL} 中缺少 {marker!r}")
    head, tail = text[:start], text[start:]
    for rid, cover in url_by_id.items():
        esc = cover.replace("\\", "\\\\").replace("'", "''")
        tail, n = re.subn(
            rf"(\({rid},\s*'[^']*',\s*)'[^']*'",
            rf"\1'{esc}'",
            tail,
            count=1,
        )
        if n != 1:
            raise RuntimeError(
                f"recipe 段内未替换 id={rid} 的 cover_url（次数 {n}），请检查 data.sql 格式"
            )
    DATA_SQL.write_text(head + tail, encoding="utf-8")


def main() -> None:
    print("输出目录:", OUT_DIR, flush=True)
    ensure_default_svg()
    url_by_id: dict[int, str] = {}
    for rid, name_zh, queries in RECIPE_QUERIES:
        print(f"处理 recipe_id={rid} {name_zh} …", flush=True)
        url = download_one(rid, name_zh, queries)
        url_by_id[rid] = url
        print(f"  -> {url}", flush=True)
        time.sleep(REQUEST_PAUSE_SEC)

    patch_data_sql(url_by_id)
    print("已更新:", DATA_SQL, flush=True)


if __name__ == "__main__":
    main()
