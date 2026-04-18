#!/usr/bin/env python3
"""
体质问卷校准脚本（研究版）：
1) 读取作答明细 CSV；
2) 输出每个体质维度的 Cronbach's alpha；
3) 输出题项-总分相关；
4) 若存在 expert_label 列，扫描阈值并给出 F1 最优阈值建议。

CSV 最低要求列：
- q1 ... q45（1-5）
- 可选：expert_label（九种体质 code）
"""

from __future__ import annotations

import argparse
import csv
import math
from dataclasses import dataclass
from typing import Dict, List, Sequence, Tuple


CONSTITUTION_CODES = [
    "pinghe",
    "qixu",
    "yangxu",
    "yinxu",
    "tanshi",
    "shire",
    "xueyu",
    "qiyu",
    "tebing",
]

# 与前后端研究版题库一致：每个体质 5 题
QUESTION_IDS_BY_CODE: Dict[str, List[int]] = {
    "pinghe": [1, 2, 3, 4, 5],
    "qixu": [6, 7, 8, 9, 10],
    "yangxu": [11, 12, 13, 14, 15],
    "yinxu": [16, 17, 18, 19, 20],
    "tanshi": [21, 22, 23, 24, 25],
    "shire": [26, 27, 28, 29, 30],
    "xueyu": [31, 32, 33, 34, 35],
    "qiyu": [36, 37, 38, 39, 40],
    "tebing": [41, 42, 43, 44, 45],
}


@dataclass
class Row:
    answers: List[float]
    expert_label: str | None


def variance(values: Sequence[float]) -> float:
    if not values:
        return 0.0
    m = sum(values) / len(values)
    return sum((v - m) ** 2 for v in values) / len(values)


def pearson(x: Sequence[float], y: Sequence[float]) -> float:
    if len(x) != len(y) or len(x) == 0:
        return 0.0
    mx = sum(x) / len(x)
    my = sum(y) / len(y)
    num = sum((a - mx) * (b - my) for a, b in zip(x, y))
    den_x = math.sqrt(sum((a - mx) ** 2 for a in x))
    den_y = math.sqrt(sum((b - my) ** 2 for b in y))
    if den_x == 0 or den_y == 0:
        return 0.0
    return num / (den_x * den_y)


def cronbach_alpha(items_matrix: List[List[float]]) -> float:
    if not items_matrix:
        return 0.0
    k = len(items_matrix)
    if k < 2:
        return 0.0
    item_vars = [variance(col) for col in items_matrix]
    totals = [sum(vals) for vals in zip(*items_matrix)]
    total_var = variance(totals)
    if total_var == 0:
        return 0.0
    return (k / (k - 1)) * (1 - (sum(item_vars) / total_var))


def transform_score(raw: float, item_count: int) -> float:
    min_score = item_count
    max_score = item_count * 5
    if max_score <= min_score:
        return 0.0
    value = ((raw - min_score) / (max_score - min_score)) * 100
    return max(0.0, min(100.0, value))


def load_rows(path: str) -> List[Row]:
    rows: List[Row] = []
    with open(path, "r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for idx, r in enumerate(reader, start=2):
            answers = []
            for qid in range(1, 46):
                key = f"q{qid}"
                if key not in r:
                    raise ValueError(f"缺少列 {key}（第 {idx} 行）")
                try:
                    v = float(r[key])
                except ValueError as exc:
                    raise ValueError(f"列 {key} 非数字（第 {idx} 行）") from exc
                if v < 1 or v > 5:
                    raise ValueError(f"列 {key} 超出 1-5（第 {idx} 行）")
                answers.append(v)
            label = (r.get("expert_label") or "").strip() or None
            rows.append(Row(answers=answers, expert_label=label))
    return rows


def dimension_scores(row: Row) -> Dict[str, float]:
    out: Dict[str, float] = {}
    for code, qids in QUESTION_IDS_BY_CODE.items():
        raw = sum(row.answers[qid - 1] for qid in qids)
        out[code] = transform_score(raw, len(qids))
    return out


def best_threshold(rows: List[Row], code: str) -> Tuple[float, float]:
    scored = [(dimension_scores(r)[code], r.expert_label == code) for r in rows if r.expert_label is not None]
    best_t = 50.0
    best_f1 = -1.0
    for t in range(30, 81):
        tp = fp = fn = 0
        for score, positive in scored:
            pred = score >= t
            if pred and positive:
                tp += 1
            elif pred and not positive:
                fp += 1
            elif (not pred) and positive:
                fn += 1
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        if f1 > best_f1:
            best_f1 = f1
            best_t = float(t)
    return best_t, best_f1


def main() -> None:
    parser = argparse.ArgumentParser(description="Constitution calibration pipeline")
    parser.add_argument("csv_path", help="作答 CSV 路径（含 q1-q45）")
    args = parser.parse_args()

    rows = load_rows(args.csv_path)
    print(f"[info] loaded rows: {len(rows)}")

    for code, qids in QUESTION_IDS_BY_CODE.items():
        # 维度 alpha
        matrix = [[r.answers[qid - 1] for r in rows] for qid in qids]
        alpha = cronbach_alpha(matrix)

        # 题项-总分相关
        totals = [sum(r.answers[qid - 1] for qid in qids) for r in rows]
        correlations = []
        for qid in qids:
            item = [r.answers[qid - 1] for r in rows]
            correlations.append((qid, pearson(item, totals)))

        print(f"\n[{code}] alpha={alpha:.3f}")
        for qid, corr in correlations:
            print(f"  q{qid} item-total corr={corr:.3f}")

    has_expert = any(r.expert_label for r in rows)
    if has_expert:
        print("\n[threshold-scan] by expert_label F1 optimization")
        for code in CONSTITUTION_CODES:
            t, f1 = best_threshold(rows, code)
            print(f"  {code}: best_threshold={t:.0f}, best_f1={f1:.3f}")
    else:
        print("\n[threshold-scan] skipped: no expert_label column values")


if __name__ == "__main__":
    main()
