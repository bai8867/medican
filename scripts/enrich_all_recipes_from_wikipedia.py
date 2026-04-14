#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
遍历 bootstrap/mock-recipes.json 中的全部药膳，补全项目所需字段：

- symptomTags：场景/痛点关键词（缺失时按药膳名写入经核对的标签）
- taboo：禁忌说明（缺失时写入经核对表述）
- summary / recommendReason：在可访问网络时合并中文维基百科条目摘要（幂等，不重复追加）

百科部分使用 REST page/summary（每道菜一次请求），遵守 Wikimedia User-Agent 规范。
"""

from __future__ import annotations

import json
import re
import socket
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

HTTP_TIMEOUT_SEC = 6
FETCH_ATTEMPTS = 1
REQUEST_PAUSE_SEC = 0.15

USER_AGENT = (
    "MedicanCampusDiet/1.2 (campus diet education; enrich mock JSON; "
    "Python/urllib; contact: local.invalid)"
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

MARKER_REF = "[百科参考]"
DEFAULT_TABOO = (
    "实热证、急性炎症期慎用；对所含食材或药材过敏者请勿食用。"
    "孕妇、哺乳期妇女及服药人群请先咨询医师。"
)

# 药膳展示名 -> 首选维基条目（药食主料或成品名）；失败时再 opensearch 药膳名
WIKI_PRIMARY_TITLE: dict[str, str] = {
    "黄芪炖鸡": "黄芪",
    "山药薏米粥": "薏苡仁",
    "百合银耳羹": "银耳",
    "莲子芡实猪肚汤": "芡实",
    "菊花枸杞茶": "枸杞子",
    "冬瓜薏米老鸭汤": "冬瓜",
    "当归生姜羊肉汤": "当归生姜羊肉汤",
    "陈皮山楂麦芽饮": "山楂",
    "沙参玉竹瘦肉汤": "北沙参",
    "酸枣仁小米粥": "酸枣仁",
    "茯苓饼（简化版）": "茯苓",
    "五指毛桃煲鸡汤": "五指毛桃",
    "黑芝麻核桃糊": "黑芝麻",
    "海带绿豆汤": "绿豆",
    "玫瑰花佛手茶": "玫瑰花茶",
    "南瓜小米粥": "南瓜",
    "川贝炖梨": "川贝母",
    "韭菜炒虾仁": "韭菜",
    "四神汤": "四神汤",
    "酸梅汤": "酸梅汤",
    "罗汉果茶": "罗汉果",
    "菊花决明子茶": "决明子",
    "荷叶粥": "莲",
    "黑豆核桃猪腰汤": "黑豆",
}

CURATED_SYMPTOM_TAGS: dict[str, list[str]] = {
    "黄芪炖鸡": ["乏力", "易感冒", "气短", "自汗", "体力透支", "气虚"],
    "山药薏米粥": ["便溏", "头身困重", "食欲不振", "油腻", "腹胀", "水肿"],
    "百合银耳羹": ["口干", "干咳", "失眠", "熬夜", "皮肤干燥", "心烦"],
    "莲子芡实猪肚汤": ["腹泻", "便溏", "乏力", "胃胀", "反酸", "食欲不振"],
    "菊花枸杞茶": ["眼干", "眼涩", "视疲劳", "熬夜", "头痛", "目赤"],
    "冬瓜薏米老鸭汤": ["口渴", "暑热", "浮肿", "油腻", "小便不利", "烦躁"],
    "当归生姜羊肉汤": ["畏寒", "肢冷", "腹痛", "痛经", "乏力", "腰膝酸软"],
    "陈皮山楂麦芽饮": ["腹胀", "嗳气", "积食", "食欲不振", "消化不良"],
    "沙参玉竹瘦肉汤": ["咽干", "干咳", "口渴", "皮肤干燥", "声嘶"],
    "酸枣仁小米粥": ["失眠", "多梦", "焦虑", "心悸", "入睡困难"],
    "茯苓饼（简化版）": ["体倦", "便溏", "头重", "食欲不振", "痰多"],
    "五指毛桃煲鸡汤": ["乏力", "湿气重", "气短", "易困", "肢体沉重"],
    "黑芝麻核桃糊": ["便秘", "脱发", "腰酸", "眼干", "肠燥"],
    "海带绿豆汤": ["暑热", "烦渴", "痤疮", "水肿", "小便黄"],
    "玫瑰花佛手茶": ["胸闷", "胁胀", "情绪抑郁", "嗳气", "压力大"],
    "南瓜小米粥": ["胃胀", "反酸", "消化不良", "乏力", "食欲不振"],
    "川贝炖梨": ["咳嗽", "咽干", "咯痰", "声嘶", "秋燥"],
    "韭菜炒虾仁": ["畏寒", "肢冷", "腰酸", "乏力", "精力不足"],
    "四神汤": ["食欲不振", "便溏", "乏力", "腹胀"],
    "酸梅汤": ["口渴", "烦热", "出汗多", "暑热"],
    "罗汉果茶": ["咽干", "声嘶", "干咳", "口渴"],
    "菊花决明子茶": ["眼干", "目赤", "视疲劳", "熬夜"],
    "荷叶粥": ["头重", "油腻", "暑热", "口渴"],
    "黑豆核桃猪腰汤": ["腰酸", "乏力", "熬夜", "耳鸣"],
}

CURATED_TABOO: dict[str, str] = {
    "黄芪炖鸡": "表实邪盛、气滞湿阻、食积内停者慎用；实热证慎用。对鸡肉、黄芪过敏者禁用。",
    "山药薏米粥": "便秘、溲赤者少用薏米；对山药过敏者禁用。",
    "百合银耳羹": "脾胃虚寒便溏者慎用；风寒咳嗽不宜。糖尿病患者少糖。",
    "莲子芡实猪肚汤": "便秘、腹胀实证者慎用；高尿酸血症者控制动物内脏频次。",
    "菊花枸杞茶": "脾胃虚寒易泻者少饮；低血压者慎用菊花大量久服。",
    "冬瓜薏米老鸭汤": "脾胃虚寒、肾虚腰痛偏寒者慎用；痛经偏寒者少用冬瓜。",
    "当归生姜羊肉汤": "实热证、阴虚火旺、口舌生疮者慎用；孕妇遵医嘱。",
    "陈皮山楂麦芽饮": "胃酸过多、消化道溃疡活动期慎用；孕妇麦芽用量遵医嘱。",
    "沙参玉竹瘦肉汤": "脾胃虚寒、便溏者慎用；痰湿壅盛咳嗽不宜单用养阴。",
    "酸枣仁小米粥": "腹泻便溏者减量；孕妇及哺乳期遵医嘱。",
    "茯苓饼（简化版）": "肾虚多尿、津液亏耗便秘者慎用；糖尿病患者控制蜂蜜。",
    "五指毛桃煲鸡汤": "实热证慎用；对五指毛桃过敏者禁用。",
    "黑芝麻核桃糊": "腹泻便溏者少用；慢性腹泻者慎用。",
    "海带绿豆汤": "脾胃虚寒易泻者少用绿豆；甲亢碘摄入需控制者慎用海带。",
    "玫瑰花佛手茶": "阴虚火旺、月经量过多者慎用；孕妇遵医嘱。",
    "南瓜小米粥": "胃热疼痛、气滞中满者适量；血糖偏高者控制量与搭配。",
    "川贝炖梨": "风寒咳嗽、痰白清稀者不宜；脾胃虚寒便溏者慎用。川贝用量遵医嘱。",
    "韭菜炒虾仁": "阴虚火旺、目疾、疮疡者慎用；痛风急性期少食海鲜。",
    "四神汤": "便秘、实热证及急性腹泻发热期慎用；对所含药材过敏者禁用。孕妇请咨询医师。",
    "酸梅汤": "胃酸过多、消化道溃疡活动期慎用；糖尿病患者少糖或代糖并监测血糖。",
    "罗汉果茶": "脾胃虚寒、便溏者不宜多饮；对罗汉果过敏者禁用。",
    "菊花决明子茶": "脾胃虚寒易泻者少饮；低血压、腹泻者慎用；孕妇请咨询医师。",
    "荷叶粥": "体虚寒凝、胃弱易痛者慎用；孕期请咨询医师。",
    "黑豆核桃猪腰汤": "高血脂、痛风急性期慎用动物内脏；实热证慎用温补类汤品。",
}


def http_json(url: str) -> Any:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT_SEC, context=ctx) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_summary(wiki_title: str) -> tuple[str, str | None]:
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
            time.sleep(0.35 * (attempt + 1))
    if data is None:
        return "", None
    if data.get("type") in ("disambiguation", "not_found"):
        return "", None
    extract = data.get("extract") or ""
    if not isinstance(extract, str):
        extract = str(extract)
    page = (data.get("content_urls") or {}).get("desktop", {}).get("page")
    return extract.strip(), page if isinstance(page, str) else None


def first_sentence(text: str, max_len: int = 200) -> str:
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


def wiki_enrichment_for_recipe(name: str) -> tuple[str, str | None]:
    """返回 (extract_snippet, page_url)。每道菜仅 1 次 HTTP，避免弱网下 opensearch 长时间阻塞。"""
    primary = WIKI_PRIMARY_TITLE.get(name, name)
    time.sleep(REQUEST_PAUSE_SEC)
    ex, url = fetch_summary(primary)
    if ex:
        return first_sentence(ex, 220), url
    return "", None


def merge_summary(cur: str, wiki_snip: str) -> str:
    cur = (cur or "").strip()
    if MARKER_REF in cur:
        return cur
    if not wiki_snip:
        return cur
    if wiki_snip[:40] in cur or cur in wiki_snip:
        return cur
    merged = f"{cur} {MARKER_REF}{wiki_snip}".strip()
    if len(merged) > 280:
        merged = merged[:279] + "…"
    return merged


def merge_recommend_reason(cur: str, wiki_snip: str, page_url: str | None) -> str:
    cur = (cur or "").strip()
    if MARKER_REF in cur:
        return cur
    if not wiki_snip:
        return cur
    extra = f"{MARKER_REF}{wiki_snip}"
    if page_url:
        extra += f"（条目：{page_url}）"
    if not cur:
        return extra
    return f"{cur} {extra}".strip()


def ensure_symptom_tags(item: dict[str, Any], name: str) -> None:
    st = item.get("symptomTags")
    if isinstance(st, list) and len(st) > 0:
        return
    item["symptomTags"] = list(CURATED_SYMPTOM_TAGS.get(name, ["养生调理"]))


def ensure_taboo(item: dict[str, Any], name: str) -> None:
    tb = item.get("taboo")
    if isinstance(tb, str) and tb.strip():
        return
    item["taboo"] = CURATED_TABOO.get(name, DEFAULT_TABOO)


def enrich_one(item: dict[str, Any], use_wiki: bool) -> dict[str, str]:
    """返回本次变更说明（用于打印）。"""
    name = str(item.get("name", "")).strip()
    log: dict[str, str] = {}
    ensure_symptom_tags(item, name)
    log["symptomTags"] = "ok"
    ensure_taboo(item, name)
    log["taboo"] = "ok"
    if not use_wiki:
        log["wiki"] = "skipped"
        return log
    if MARKER_REF in str(item.get("recommendReason", "")):
        log["wiki"] = "already"
        return log
    wiki_snip, url = wiki_enrichment_for_recipe(name)
    if wiki_snip:
        old_s = item.get("summary", "")
        item["summary"] = merge_summary(str(old_s), wiki_snip)
        old_r = item.get("recommendReason", "")
        item["recommendReason"] = merge_recommend_reason(str(old_r), wiki_snip, url)
        log["wiki"] = "merged"
    else:
        log["wiki"] = "empty"
    return log


def main() -> None:
    import argparse

    p = argparse.ArgumentParser()
    p.add_argument(
        "--no-wiki",
        action="store_true",
        help="仅补全 symptomTags/taboo，不请求维基百科",
    )
    args = p.parse_args()
    use_wiki = not args.no_wiki
    socket.setdefaulttimeout(HTTP_TIMEOUT_SEC + 4)

    if not MOCK_JSON.is_file():
        raise SystemExit(f"未找到 {MOCK_JSON}")

    with MOCK_JSON.open(encoding="utf-8") as f:
        data: list[Any] = json.load(f)

    if not isinstance(data, list):
        raise SystemExit("mock-recipes.json 根节点应为数组")

    print(f"共 {len(data)} 条，wiki={'on' if use_wiki else 'off'}", flush=True)
    for i, item in enumerate(data):
        if not isinstance(item, dict):
            continue
        name = str(item.get("name", "")).strip()
        info = enrich_one(item, use_wiki=use_wiki)
        print(f"[{i + 1:02d}] {name} -> {info}", flush=True)

    with MOCK_JSON.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"已写回 {MOCK_JSON.relative_to(ROOT)}", flush=True)


if __name__ == "__main__":
    main()
