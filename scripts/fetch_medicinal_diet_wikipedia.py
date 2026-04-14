#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从中文维基百科公开 REST API 拉取药膳相关条目的百科摘要，并与人工核对的
用料、步骤、体质与禁忌合并后写入 bootstrap/mock-recipes.json。

说明：
- 百科「extract」用于摘要与推荐语中的来源标注，不代表个体诊疗建议。
- 用料、剂量、步骤来自常见药食同源公开表述与教材常见配伍，与百科摘要分列，
  避免把百科中的非食谱叙述误当作操作步骤。
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
from typing import Any

HTTP_TIMEOUT_SEC = 12
FETCH_ATTEMPTS = 2

USER_AGENT = (
    "MedicanCampusDiet/1.1 (educational campus diet demo; "
    "Python/urllib; zh.wikipedia.org REST summary only)"
)

ROOT = Path(__file__).resolve().parents[1]
MOCK_JSON = (
    ROOT
    / "campus-diet-backend"
    / "src"
    / "main"
    / "resources"
    / "bootstrap"
    / "mock-recipes.json"
)

WIKI_SUMMARY = "https://zh.wikipedia.org/api/rest_v1/page/summary/{title}"

REQUEST_PAUSE_SEC = 0.35


def http_json(url: str) -> dict[str, Any]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT_SEC, context=ctx) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_summary(wiki_title: str) -> tuple[str, str | None]:
    """
    返回 (plain_extract, wikipedia_content_url)。
    失败或消歧义页时 extract 为空。
    """
    enc = urllib.parse.quote(wiki_title.replace(" ", "_"))
    url = WIKI_SUMMARY.format(title=enc)
    data: dict[str, Any] | None = None
    for attempt in range(FETCH_ATTEMPTS):
        try:
            data = http_json(url)
            break
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return "", None
            if attempt + 1 >= FETCH_ATTEMPTS:
                return "", None
        except (TimeoutError, urllib.error.URLError, OSError):
            if attempt + 1 >= FETCH_ATTEMPTS:
                return "", None
            time.sleep(0.8 * (attempt + 1))
    if data is None:
        return "", None
    if data.get("type") in ("disambiguation", "not_found"):
        return "", data.get("content_urls", {}).get("desktop", {}).get("page")
    extract = data.get("extract") or ""
    if not isinstance(extract, str):
        extract = str(extract)
    page = (data.get("content_urls") or {}).get("desktop", {}).get("page")
    return extract.strip(), page if isinstance(page, str) else None


def first_sentence(text: str, max_len: int = 220) -> str:
    if not text:
        return ""
    t = re.sub(r"\s+", " ", text).strip()
    for sep in ("。", "！", "？", ".", "!", "?"):
        if sep in t:
            idx = t.index(sep) + 1
            t = t[:idx].strip()
            break
    if len(t) > max_len:
        t = t[: max_len - 1].rstrip() + "…"
    return t


# 每条：百科条目名（wiki_title）、展示名（name）、以及经核对的结构化食谱字段。
# 功效用语与常见教材/《中国药膳学》类公开表述一致方向；个体以医师为准。
CURATED: list[dict[str, Any]] = [
    {
        "name": "四神汤",
        "wiki_title": "四神汤",
        "default_summary": "以茯苓、山药、莲子、芡实（或薏苡仁）为主的健脾祛湿方意，温和调理脾胃。",
        "effect": "健脾祛湿、益肾固精",
        "effectTags": ["健脾", "祛湿", "养胃"],
        "suitConstitutions": ["痰湿质", "气虚质", "平和质"],
        "seasonFit": ["summer", "autumn"],
        "symptomTags": ["食欲不振", "便溏", "乏力", "腹胀"],
        "cookTime": "约 90 分钟",
        "difficulty": "中等",
        "collectCount": 756,
        "recommendReason": "健脾祛湿，适合便溏、乏力、腹胀等脾虚湿困倾向者。",
        "ingredients": [
            {"name": "茯苓", "amount": "15g", "note": "药食同源"},
            {"name": "山药", "amount": "30g"},
            {"name": "莲子", "amount": "20g"},
            {"name": "芡实", "amount": "20g"},
            {"name": "猪瘦肉或排骨", "amount": "300g"},
        ],
        "steps": [
            {"text": "药材稍冲洗；肉类焯水去沫。"},
            {"text": "冷水下锅，大火煮沸后转小火煲 70–90 分钟。"},
            {"text": "少盐调味即可。", "tip": "便秘、溲赤或实热明显者慎用。"},
        ],
        "taboo": "便秘、实热证及急性腹泻发热期慎用；对所含药材过敏者禁用。孕妇请咨询医师。",
    },
    {
        "name": "酸梅汤",
        "wiki_title": "酸梅汤",
        "default_summary": "以乌梅为主料的传统消暑饮品，生津止渴。",
        "effect": "生津止渴、清热解暑",
        "effectTags": ["养阴", "清热"],
        "suitConstitutions": ["湿热质", "阴虚质", "平和质"],
        "seasonFit": ["summer"],
        "symptomTags": ["口渴", "烦热", "出汗多"],
        "cookTime": "约 35 分钟",
        "difficulty": "简单",
        "collectCount": 1644,
        "recommendReason": "生津解暑，适合夏季口渴烦热、出汗偏多者少量频饮。",
        "ingredients": [
            {"name": "乌梅", "amount": "25g"},
            {"name": "山楂", "amount": "15g"},
            {"name": "甘草", "amount": "3g", "note": "不宜久服大量"},
            {"name": "陈皮", "amount": "5g"},
            {"name": "冰糖", "amount": "适量"},
        ],
        "steps": [
            {"text": "材料（除冰糖）加水浸泡 30 分钟。"},
            {"text": "煮沸后转小火煮 25 分钟，过滤。"},
            {"text": "趁热调冰糖，放凉后饮用。", "tip": "胃酸过多、胃溃疡者少饮。"},
        ],
        "taboo": "胃酸过多、消化道溃疡活动期慎用；糖尿病患者少糖或代糖并监测血糖。",
    },
    {
        "name": "罗汉果茶",
        "wiki_title": "罗汉果",
        "default_summary": "罗汉果味甘性凉，传统用于润肺利咽、生津止渴。",
        "effect": "润肺止咳、生津止渴",
        "effectTags": ["健脾润肺", "养阴"],
        "suitConstitutions": ["阴虚质", "平和质", "湿热质"],
        "seasonFit": ["autumn", "summer"],
        "symptomTags": ["咽干", "声嘶", "干咳"],
        "cookTime": "约 15 分钟",
        "difficulty": "简单",
        "collectCount": 1320,
        "recommendReason": "润肺利咽，适合咽干声嘶、秋燥干咳倾向者。",
        "ingredients": [
            {"name": "罗汉果", "amount": "1/4～1/2 个", "note": "味浓可减量"},
            {"name": "水", "amount": "500ml"},
        ],
        "steps": [
            {"text": "罗汉果掰碎，冷水下锅煮沸后小火 10 分钟。"},
            {"text": "代茶分次饮用。", "tip": "便溏者减量。"},
        ],
        "taboo": "脾胃虚寒、便溏者不宜多饮；对罗汉果过敏者禁用。",
    },
    {
        "name": "菊花决明子茶",
        "wiki_title": "决明子",
        "default_summary": "菊花配决明子，清肝明目、辅助缓解视疲劳方向。",
        "effect": "清肝明目、润肠通便",
        "effectTags": ["清热", "明目"],
        "suitConstitutions": ["阴虚质", "气郁质", "平和质"],
        "seasonFit": ["spring", "summer", "autumn"],
        "symptomTags": ["眼干", "目赤", "视疲劳", "熬夜"],
        "cookTime": "约 10 分钟",
        "difficulty": "简单",
        "collectCount": 980,
        "recommendReason": "清肝明目，适合熬夜用眼、目干目赤倾向者。",
        "ingredients": [
            {"name": "杭白菊", "amount": "5g"},
            {"name": "决明子", "amount": "10g", "note": "炒制后性较平和"},
        ],
        "steps": [
            {"text": "决明子干锅小火清炒至微香（可选）。"},
            {"text": "与菊花一起用 90℃ 左右热水冲泡，焖 6–8 分钟。"},
        ],
        "taboo": "脾胃虚寒易泻者少饮；低血压、腹泻者慎用；孕妇请咨询医师。",
    },
    {
        "name": "荷叶粥",
        "wiki_title": "莲",
        "default_summary": "荷叶升清降浊、解暑化湿，与粳米煮粥为清淡调理方向。",
        "effect": "清暑化湿、升发清阳",
        "effectTags": ["祛湿", "清热"],
        "suitConstitutions": ["痰湿质", "湿热质", "平和质"],
        "seasonFit": ["summer"],
        "symptomTags": ["头重", "油腻", "暑热"],
        "cookTime": "约 40 分钟",
        "difficulty": "简单",
        "collectCount": 610,
        "recommendReason": "清暑化湿，适合夏季身重、口腻、暑热烦渴者作清淡主食搭配。",
        "ingredients": [
            {"name": "干荷叶", "amount": "6g", "note": "药食同源，宜包煎"},
            {"name": "粳米", "amount": "100g"},
        ],
        "steps": [
            {"text": "荷叶装入茶包或纱布扎紧。"},
            {"text": "与粳米同煮至粥稠，取出荷叶包。"},
            {"text": "可少量盐或配小菜；体寒者减量。"},
        ],
        "taboo": "体虚寒凝、胃弱易痛者慎用；孕期请咨询医师。",
    },
    {
        "name": "黑豆核桃猪腰汤",
        "wiki_title": "黑豆",
        "default_summary": "黑豆补肾利水，与核桃、猪腰同煮为民间温补方向（适量）。",
        "effect": "补肾益阴、强腰健骨",
        "effectTags": ["养阴", "益气"],
        "suitConstitutions": ["阴虚质", "血瘀质", "平和质"],
        "seasonFit": ["autumn", "winter"],
        "symptomTags": ["腰酸", "乏力", "熬夜"],
        "cookTime": "约 100 分钟",
        "difficulty": "中等",
        "collectCount": 540,
        "recommendReason": "补肾益阴，适合腰酸乏力、熬夜后需温和食补者（控制频次与盐量）。",
        "ingredients": [
            {"name": "黑豆", "amount": "40g", "note": "提前浸泡 4h 以上"},
            {"name": "核桃仁", "amount": "20g"},
            {"name": "猪腰", "amount": "1 副", "note": "处理干净去臊腺"},
            {"name": "生姜", "amount": "4 片"},
        ],
        "steps": [
            {"text": "黑豆泡发；猪腰切花焯水。"},
            {"text": "全部材料小火炖 80–100 分钟。"},
            {"text": "少盐调味。", "tip": "高尿酸血症者控制内脏摄入频次。"},
        ],
        "taboo": "高血脂、痛风急性期慎用动物内脏；实热证慎用温补类汤品。",
    },
]


def build_recipe_row(cur: dict[str, Any], wiki_extract: str, wiki_url: str | None) -> dict[str, Any]:
    base_summary = cur["default_summary"]
    wiki_bit = first_sentence(wiki_extract, 200)
    if wiki_bit:
        summary = f"{base_summary} {wiki_bit}".strip()
    else:
        summary = base_summary
    if len(summary) > 280:
        summary = summary[:279] + "…"

    base_rr = (cur.get("recommendReason") or "").strip()
    reason_parts: list[str] = []
    if base_rr:
        reason_parts.append(base_rr)
    if wiki_bit:
        reason_parts.append(f"百科摘录：{wiki_bit}")
    if wiki_url:
        reason_parts.append(f"参考条目：{wiki_url}")
    reason_parts.append("（内容仅供校园健康教育参考，不构成医疗建议。）")
    recommend = " ".join(reason_parts).strip()

    out: dict[str, Any] = {
        "name": cur["name"],
        "summary": summary,
        "effect": cur["effect"],
        "effectTags": cur["effectTags"],
        "suitConstitution": cur["suitConstitutions"][0],
        "suitConstitutions": cur["suitConstitutions"],
        "seasonFit": cur["seasonFit"],
        "symptomTags": cur["symptomTags"],
        "collectCount": cur["collectCount"],
        "recommendReason": recommend,
        "cookTime": cur["cookTime"],
        "difficulty": cur["difficulty"],
        "coverUrl": "",
        "ingredients": cur["ingredients"],
        "steps": cur["steps"],
        "taboo": cur["taboo"],
    }
    return out


def main() -> None:
    if not MOCK_JSON.is_file():
        raise SystemExit(f"未找到 {MOCK_JSON}")

    with MOCK_JSON.open(encoding="utf-8") as f:
        existing: list[dict[str, Any]] = json.load(f)

    names = {str(x.get("name", "")).strip() for x in existing if isinstance(x, dict)}

    next_id = 1
    for x in existing:
        m = re.match(r"demo-(\d+)", str(x.get("id", "")))
        if m:
            next_id = max(next_id, int(m.group(1)) + 1)

    added: list[dict[str, Any]] = []
    for cur in CURATED:
        name = cur["name"]
        if name in names:
            continue
        time.sleep(REQUEST_PAUSE_SEC)
        extract, url = fetch_summary(cur["wiki_title"])
        row = build_recipe_row(cur, extract, url)
        row["id"] = f"demo-{next_id:03d}"
        next_id += 1
        added.append(row)
        names.add(name)
        tag = "wiki" if extract else "local-only"
        print(f"[{tag}] {name} wiki_title={cur['wiki_title']!r} extract_len={len(extract)}")

    if not added:
        print("没有新增条目（名称均已存在或未配置）。")
        return

    merged = existing + added
    with MOCK_JSON.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"已写入 {len(added)} 条到 {MOCK_JSON.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
